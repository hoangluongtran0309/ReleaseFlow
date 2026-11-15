package com.hoangluongtran0309.releaseflow.audience;

import com.hoangluongtran0309.releaseflow.account.InvalidOutputLanguageException;
import com.hoangluongtran0309.releaseflow.account.OrganizationRegistered;
import com.hoangluongtran0309.releaseflow.account.OutputLanguage;
import com.hoangluongtran0309.releaseflow.account.OutputLanguageService;
import com.hoangluongtran0309.releaseflow.account.ReleaseFlowPrincipal;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * An Organization's audiences: the kinds of reader it writes release notes for. Every
 * operation is scoped by Organization, and an Organization always keeps at least one
 * audience, so an approved release always gets a note. Each audience has a template for
 * every target language the output language does not cover, made when the language or
 * the audience is added.
 */
@Service
public class AudienceService {

    static final int MAX_AUDIENCES = 20;

    private static final String CODE_CONSTRAINT = "audience_definitions_code_unique";
    private static final String NOTES_CONSTRAINT = "release_audience_notes_audience_fk";
    private static final String RULE_CONSTRAINT = "automation_rule_actions_audience_fk";
    private static final String RUN_CONSTRAINT = "automation_action_runs_audience_fk";
    private static final String KEEP_AN_ACTIVE_AUDIENCE = "audience_definitions_keep_an_active_audience";

    private final AudienceRepository audienceRepository;
    private final AudienceTemplateVariantRepository variantRepository;
    private final AudienceStatusChangeRepository statusChangeRepository;
    private final TargetLanguages targetLanguages;
    private final OutputLanguageService outputLanguageService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    AudienceService(
            AudienceRepository audienceRepository,
            AudienceTemplateVariantRepository variantRepository,
            AudienceStatusChangeRepository statusChangeRepository,
            TargetLanguages targetLanguages,
            OutputLanguageService outputLanguageService,
            PlatformTransactionManager transactionManager,
            Clock clock
    ) {
        this.audienceRepository = audienceRepository;
        this.variantRepository = variantRepository;
        this.statusChangeRepository = statusChangeRepository;
        this.targetLanguages = targetLanguages;
        this.outputLanguageService = outputLanguageService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<AudienceView> list(UUID organizationId) {
        List<AudienceTemplateVariant> variants = variantRepository.findAllByOrganizationIdOrderByLanguageAsc(organizationId);
        return audienceRepository.findAllByOrganizationIdOrderByDisplayNameAscCodeAsc(organizationId).stream()
                .map(audience -> view(audience, variants))
                .toList();
    }

    /**
     * The audiences the AI writes narratives for, ordered by code: the active ones. With none
     * retired this is every audience, so the request is what it always was (ADR-0044).
     */
    @Transactional(readOnly = true)
    public List<AudienceBrief> briefs(UUID organizationId) {
        return audienceRepository.findAllByOrganizationIdOrderByCodeAsc(organizationId).stream()
                .filter(audience -> !audience.isRetired())
                .map(audience -> new AudienceBrief(audience.getCode(), audience.getCommunicationIntent()))
                .toList();
    }

    /** The audiences an approval writes notes for: the active ones, in the order {@link #list} gives. */
    @Transactional(readOnly = true)
    public List<AudienceView> active(UUID organizationId) {
        return list(organizationId).stream().filter(audience -> !audience.retired()).toList();
    }

    /** Whether the audience is retired; an unknown one is not, and fails elsewhere as before. */
    @Transactional(readOnly = true)
    public boolean isRetired(UUID organizationId, UUID audienceId) {
        return audienceRepository.findByIdAndOrganizationId(audienceId, organizationId)
                .map(AudienceDefinition::isRetired)
                .orElse(false);
    }

    /** The codes of retired audiences, whose narratives are kept but never translated or asked for. */
    @Transactional(readOnly = true)
    public Set<String> retiredCodes(UUID organizationId) {
        return audienceRepository.findAllByOrganizationIdOrderByCodeAsc(organizationId).stream()
                .filter(AudienceDefinition::isRetired)
                .map(AudienceDefinition::getCode)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** The most recent return of each audience brought back, for the page that lists them. */
    @Transactional(readOnly = true)
    public Map<UUID, AudienceStatusChangeView> lastRestorations(UUID organizationId) {
        Map<UUID, AudienceStatusChangeView> latest = new HashMap<>();
        statusChangeRepository.findAllByOrganizationIdOrderByChangedAtDescIdDesc(organizationId).stream()
                .filter(change -> change.getKind() == AudienceStatusChange.Kind.RESTORED)
                .forEach(change -> latest.putIfAbsent(change.getAudienceId(),
                        new AudienceStatusChangeView(change.getChangerName(), change.getChangedAt())));
        return latest;
    }

    /**
     * Retires an audience: no narrative in the AI request, no note at the next approval, no
     * translation. Its row, templates, narratives, and published notes stay. The audiences of
     * the Organization are locked first, so two administrators who retire the last two at
     * once run one after the other; the database's trigger refuses the same at commit.
     */
    public AudienceView retire(ReleaseFlowPrincipal administrator, UUID audienceId) {
        return guarded(() -> {
            UUID organizationId = administrator.organizationId();
            List<AudienceDefinition> audiences = audienceRepository.lockAllByOrganizationId(organizationId);
            AudienceDefinition audience = audiences.stream()
                    .filter(candidate -> candidate.getId().equals(audienceId))
                    .findFirst()
                    .orElseThrow(AudienceNotFoundException::new);
            if (audience.isRetired()) {
                throw AudienceConflictException.alreadyRetired();
            }
            if (audiences.stream().filter(candidate -> !candidate.isRetired()).count() <= 1) {
                throw AudienceConflictException.lastActive();
            }
            Instant now = clock.instant();
            audience.retire(administrator.userId(), administrator.displayName(), now);
            audienceRepository.flush();
            statusChangeRepository.save(new AudienceStatusChange(organizationId, audienceId,
                    AudienceStatusChange.Kind.RETIRED, administrator.userId(), administrator.displayName(), now));
            return view(audience);
        });
    }

    /** Brings a retired audience back: the next AI request and the next approval include it again. */
    @Transactional
    public AudienceView restore(ReleaseFlowPrincipal administrator, UUID audienceId) {
        UUID organizationId = administrator.organizationId();
        AudienceDefinition audience = find(organizationId, audienceId);
        audience.restore();
        Instant now = clock.instant();
        audienceRepository.flush();
        statusChangeRepository.save(new AudienceStatusChange(organizationId, audienceId,
                AudienceStatusChange.Kind.RESTORED, administrator.userId(), administrator.displayName(), now));
        return view(audience);
    }

    private <T> T guarded(Supplier<T> work) {
        try {
            return transactionTemplate.execute(status -> work.get());
        } catch (RuntimeException exception) {
            // The trigger fires at commit, whatever wrote the change.
            if (!(exception instanceof AudienceConflictException) && causedBy(exception, KEEP_AN_ACTIVE_AUDIENCE)) {
                throw AudienceConflictException.lastActive();
            }
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public AudienceView get(UUID organizationId, UUID audienceId) {
        return view(find(organizationId, audienceId));
    }

    @Transactional
    public AudienceView create(UUID organizationId, NewAudienceRequest request) {
        List<AudienceDefinition> existing = audienceRepository.lockAllByOrganizationId(organizationId);
        if (existing.size() >= MAX_AUDIENCES) {
            throw AudienceConflictException.limit();
        }
        if (audienceRepository.existsByOrganizationIdAndCode(organizationId, request.getCode())) {
            throw AudienceConflictException.codeTaken();
        }
        AudienceTemplate.validate(request.getTemplateBody());
        String footer = request.getFooterTemplateBody() == null ? "" : request.getFooterTemplateBody();
        AudienceTemplate.validateFooter(footer);
        AudienceDefinition audience = new AudienceDefinition(
                UUID.randomUUID(),
                organizationId,
                request.getCode(),
                request.getDisplayName(),
                request.intent(),
                request.getTemplateBody(),
                footer,
                false,
                clock.instant()
        );
        try {
            audienceRepository.saveAndFlush(audience);
        } catch (DataIntegrityViolationException exception) {
            throw violates(exception, CODE_CONSTRAINT) ? AudienceConflictException.codeTaken() : exception;
        }
        addMissingVariants(List.of(audience), targetLanguages.needingVariants(organizationId));
        return view(audience);
    }

    @Transactional
    public AudienceView update(UUID organizationId, UUID audienceId, AudienceRequest request) {
        AudienceDefinition audience = find(organizationId, audienceId);
        AudienceTemplate.validate(request.getTemplateBody());
        String footer = request.getFooterTemplateBody() == null
                ? audience.getFooterTemplateBody()
                : request.getFooterTemplateBody();
        AudienceTemplate.validateFooter(footer);
        audience.update(request.getDisplayName(), request.intent(), request.getTemplateBody(), footer, clock.instant());
        audienceRepository.flush();
        return view(audience);
    }

    /**
     * Writes an audience's templates for one of the Organization's target languages. A
     * footer left out keeps the one the language has.
     *
     * @param footerBody the footer, or null to keep it
     * @throws InvalidAudienceTemplateException when the language is not a target language
     *                                          or a template does not render
     */
    @Transactional
    public AudienceView updateTemplate(
            UUID organizationId,
            UUID audienceId,
            String languageTag,
            String templateBody,
            String footerBody
    ) {
        AudienceDefinition audience = find(organizationId, audienceId);
        String language = targetLanguage(organizationId, languageTag);
        AudienceTemplate.validate(templateBody);
        Optional<AudienceTemplateVariant> existing =
                variantRepository.findByAudienceIdAndOrganizationIdAndLanguage(audienceId, organizationId, language);
        String footer = footerBody != null
                ? footerBody
                : existing.map(AudienceTemplateVariant::getFooterTemplateBody).orElseGet(() -> variantFooter(audience, language));
        AudienceTemplate.validateFooter(footer);
        Instant now = clock.instant();
        existing.ifPresentOrElse(
                variant -> variant.update(templateBody, footer, now),
                () -> variantRepository.save(new AudienceTemplateVariant(organizationId, audienceId, language,
                        templateBody, footer, now))
        );
        variantRepository.flush();
        return view(audience);
    }

    /**
     * Deletes an audience that nothing names, unless it is the last one. Release notes, a
     * rule's action, and a run's history each keep it, and the refusal says which: an
     * archived rule cannot be changed, and run history must outlive the rule that made it,
     * so neither is removed to make the delete possible.
     */
    @Transactional
    public void delete(UUID organizationId, UUID audienceId) {
        List<AudienceDefinition> audiences = audienceRepository.lockAllByOrganizationId(organizationId);
        AudienceDefinition audience = audiences.stream()
                .filter(candidate -> candidate.getId().equals(audienceId))
                .findFirst()
                .orElseThrow(AudienceNotFoundException::new);
        // An Organization keeps an active audience (ADR-0044): deleting the last active one is
        // refused even when retired ones remain.
        if (!audience.isRetired() && audiences.stream().filter(candidate -> !candidate.isRetired()).count() <= 1) {
            throw AudienceConflictException.last();
        }
        String use = audienceRepository.use(organizationId, audienceId);
        if (use != null) {
            throw switch (use) {
                case "notes" -> AudienceConflictException.inUse();
                case "rule" -> AudienceConflictException.usedByRule();
                case "archivedRule" -> AudienceConflictException.usedByArchivedRule();
                default -> AudienceConflictException.usedByRunHistory();
            };
        }
        try {
            audienceRepository.delete(audience);
            audienceRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            // Something named the audience between the check and the delete.
            if (violates(exception, NOTES_CONSTRAINT)) {
                throw AudienceConflictException.inUse();
            }
            if (violates(exception, RULE_CONSTRAINT)) {
                throw AudienceConflictException.usedByRule();
            }
            if (violates(exception, RUN_CONSTRAINT)) {
                throw AudienceConflictException.usedByRunHistory();
            }
            throw exception;
        }
    }

    /**
     * Restores the name, intent, template, and footer of the shipped audience with this
     * audience's code in the current output language, and each of its language templates
     * and footers to the shipped version in that language. An audience an administrator
     * added under a shipped code, such as {@code support} in an Organization registered
     * before that preset, becomes a preset here, so its later language templates and resets
     * follow the preset too (ADR-0035). A code nothing ships is refused.
     */
    @Transactional
    public AudienceView resetToPreset(UUID organizationId, UUID audienceId) {
        AudienceDefinition audience = find(organizationId, audienceId);
        OutputLanguage language = outputLanguageService.outputLanguage(organizationId);
        AudiencePresets.Preset preset = AudiencePresets.find(audience.getCode(), language.tag())
                .orElseThrow(AudienceConflictException::notPreset);
        Instant now = clock.instant();
        audience.resetTo(preset, now);
        variantRepository.findAllByOrganizationIdOrderByLanguageAsc(organizationId).stream()
                .filter(variant -> variant.getAudienceId().equals(audienceId))
                .forEach(variant -> variant.update(variantBody(audience, variant.getLanguage()),
                        variantFooter(audience, variant.getLanguage()), now));
        audienceRepository.flush();
        return view(audience);
    }

    /**
     * Renders a template with sample values, followed by the footer when one is given; both
     * are validated exactly as on save.
     */
    public AudiencePreview preview(String templateBody, String footerBody) {
        String markdown = AudienceTemplate.sample(templateBody);
        String footer = AudienceTemplate.sampleFooter(footerBody);
        if (!footer.isEmpty()) {
            markdown = markdown.strip() + "\n\n" + footer + "\n";
        }
        return new AudiencePreview(markdown, MarkdownHtml.render(markdown));
    }

    /** Gives every audience a template for each of these languages it does not have yet. */
    @Transactional
    void addMissingVariants(UUID organizationId, List<String> languages) {
        addMissingVariants(audienceRepository.findAllByOrganizationIdOrderByCodeAsc(organizationId), languages);
    }

    // Runs inside the registration transaction; a failure rolls the registration back.
    @EventListener
    void seedPresets(OrganizationRegistered event) {
        Instant now = clock.instant();
        List<AudienceDefinition> presets = AudiencePresets.forLanguage(event.outputLanguage().tag()).stream()
                .map(preset -> new AudienceDefinition(
                        UUID.randomUUID(),
                        event.organizationId(),
                        preset.code(),
                        preset.displayName(),
                        preset.communicationIntent(),
                        preset.templateBody(),
                        preset.footerBody(),
                        true,
                        now
                ))
                .toList();
        audienceRepository.saveAll(presets);
    }

    private void addMissingVariants(List<AudienceDefinition> audiences, List<String> languages) {
        if (audiences.isEmpty() || languages.isEmpty()) {
            return;
        }
        UUID organizationId = audiences.getFirst().getOrganizationId();
        Set<String> existing = variantRepository.findAllByOrganizationIdOrderByLanguageAsc(organizationId).stream()
                .map(variant -> variant.getAudienceId() + "/" + variant.getLanguage())
                .collect(Collectors.toSet());
        Instant now = clock.instant();
        for (AudienceDefinition audience : audiences) {
            for (String language : languages) {
                if (!existing.contains(audience.getId() + "/" + language)) {
                    variantRepository.save(new AudienceTemplateVariant(organizationId, audience.getId(), language,
                            variantBody(audience, language), variantFooter(audience, language), now));
                }
            }
        }
        variantRepository.flush();
    }

    // A shipped audience starts from its shipped template in that language; any other from its main template.
    private static String variantBody(AudienceDefinition audience, String language) {
        if (!audience.isPreset()) {
            return audience.getTemplateBody();
        }
        return AudiencePresets.find(audience.getCode(), language)
                .map(AudiencePresets.Preset::templateBody)
                .orElse(audience.getTemplateBody());
    }

    // The same for the footer: the shipped one in that language, or the main footer.
    private static String variantFooter(AudienceDefinition audience, String language) {
        if (!audience.isPreset()) {
            return audience.getFooterTemplateBody();
        }
        return AudiencePresets.find(audience.getCode(), language)
                .map(AudiencePresets.Preset::footerBody)
                .orElse(audience.getFooterTemplateBody());
    }

    private String targetLanguage(UUID organizationId, String languageTag) {
        final String language;
        try {
            language = OutputLanguage.parse(languageTag).tag();
        } catch (InvalidOutputLanguageException exception) {
            throw InvalidAudienceTemplateException.languageNotTargeted(languageTag);
        }
        if (!targetLanguages.of(organizationId).contains(language)) {
            throw InvalidAudienceTemplateException.languageNotTargeted(language);
        }
        return language;
    }

    private AudienceView view(AudienceDefinition audience) {
        return view(audience, variantRepository.findAllByOrganizationIdOrderByLanguageAsc(audience.getOrganizationId()));
    }

    // The audience with its language templates and footers, each keyed by language.
    private static AudienceView view(AudienceDefinition audience, List<AudienceTemplateVariant> variants) {
        Map<String, String> templates = new HashMap<>();
        Map<String, String> footers = new HashMap<>();
        for (AudienceTemplateVariant variant : variants) {
            if (variant.getAudienceId().equals(audience.getId())) {
                templates.put(variant.getLanguage(), variant.getTemplateBody());
                footers.put(variant.getLanguage(), variant.getFooterTemplateBody());
            }
        }
        return audience.view(templates, footers);
    }

    private AudienceDefinition find(UUID organizationId, UUID audienceId) {
        return audienceRepository.findByIdAndOrganizationId(audienceId, organizationId)
                .orElseThrow(AudienceNotFoundException::new);
    }

    private static boolean causedBy(Throwable exception, String constraint) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().contains(constraint)) {
                return true;
            }
        }
        return false;
    }

    private static boolean violates(DataIntegrityViolationException exception, String constraint) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().contains(constraint)) {
                return true;
            }
        }
        return false;
    }
}
