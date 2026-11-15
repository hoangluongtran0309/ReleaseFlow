package com.hoangluongtran0309.releaseflow.project;

import com.hoangluongtran0309.releaseflow.source.SourceType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Turns sources into the views every page and the API read, with their health. A source is
 * quiet against its own rhythm, never the Project's: the 90th percentile of the gaps
 * between the changes it was delivered by webhook in the last {@value #RHYTHM_DAYS} days,
 * never less than an hour, and only once it has at least {@value #MINIMUM_GAPS} gaps. A dead
 * address silences every source behind it at once, so the busy ones show it, and a rarely
 * used source is not flagged for being rare.
 */
@Component
class SourceViews {

    static final int RHYTHM_DAYS = 90;
    static final int MINIMUM_GAPS = 10;
    static final Duration QUIET_FLOOR = Duration.ofHours(1);
    private static final String POLLING_DELIVERY = "POLLING";
    private static final String WEBHOOK_DELIVERY = "WEBHOOK";

    private final IntegrationSourceRepository sourceRepository;
    private final Duration pollInterval;
    private final Clock clock;

    SourceViews(
            IntegrationSourceRepository sourceRepository,
            @Value("${releaseflow.jira.poll-interval}") Duration pollInterval,
            Clock clock
    ) {
        this.sourceRepository = sourceRepository;
        this.pollInterval = pollInterval;
        this.clock = clock;
    }

    /** One query for the rhythms of every delivered source given, whatever their Projects. */
    List<IntegrationSourceView> views(UUID organizationId, List<IntegrationSource> sources) {
        Instant now = clock.instant();
        List<UUID> delivered = sources.stream()
                .filter(source -> source.isConnected() && !source.getSourceType().isPolled())
                .map(IntegrationSource::getId)
                .toList();
        Map<UUID, Duration> usualGaps = delivered.isEmpty()
                ? Map.of()
                : sourceRepository.deliveryRhythms(organizationId, delivered, now.minus(Duration.ofDays(RHYTHM_DAYS)))
                        .stream()
                        .filter(rhythm -> rhythm.getGapCount() >= MINIMUM_GAPS)
                        .collect(Collectors.toMap(
                                IntegrationSourceRepository.DeliveryRhythm::getSourceId,
                                rhythm -> max(Duration.ofMillis(Math.round(rhythm.getUsualGapSeconds() * 1000)), QUIET_FLOOR),
                                (first, second) -> first
                        ));
        return sources.stream().map(source -> view(source, usualGaps.get(source.getId()), now)).toList();
    }

    IntegrationSourceView view(IntegrationSource source) {
        return views(source.getOrganizationId(), List.of(source)).getFirst();
    }

    /** Views keyed by source, for a page that looks sources up by ID. */
    Map<UUID, IntegrationSourceView> byId(UUID organizationId, List<IntegrationSource> sources) {
        return views(organizationId, sources).stream()
                .collect(Collectors.toMap(IntegrationSourceView::id, Function.identity()));
    }

    private IntegrationSourceView view(IntegrationSource source, Duration usualGap, Instant now) {
        return new IntegrationSourceView(
                source.getId(),
                source.getSourceType(),
                source.getSourceType().isPolled() ? POLLING_DELIVERY : WEBHOOK_DELIVERY,
                source.getExternalProjectKey(),
                source.getRepositoryOwner(),
                source.getRepositoryName(),
                source.getApiBaseUrl(),
                source.getExternalWorkspaceKey(),
                source.getCredentialIdentity(),
                source.getWebhookAuthMode(),
                source.getWebhookId(),
                IntegrationSourceService.webhookPath(source.getSourceType(), source.getWebhookId()),
                source.getCreatedAt(),
                source.getLastDeliveryAt(),
                source.hasAccessToken(),
                source.getTokenUpdatedAt(),
                source.getConnectionStatus(),
                source.getLastSyncAt(),
                source.getLastErrorCode(),
                source.getNextPollAt(),
                health(source, usualGap, now)
        );
    }

    private SourceHealth health(IntegrationSource source, Duration usualGap, Instant now) {
        boolean connected = source.isConnected();
        Instant quietSince = null;
        boolean quiet = false;
        boolean overdue = false;
        if (connected && source.getSourceType() == SourceType.JIRA) {
            overdue = source.getNextPollAt() != null && now.isAfter(source.getNextPollAt().plus(pollInterval));
        } else if (connected) {
            quietSince = latest(source.getLastDeliveryAt(), source.getSecretUpdatedAt());
            quiet = usualGap != null && quietSince != null && now.isAfter(quietSince.plus(usualGap));
        }
        return new SourceHealth(
                connected,
                source.getDisconnectedAt(),
                source.getSecretUpdatedAt(),
                connected ? source.getSignatureFailureCount() : 0,
                connected ? source.getLastSignatureFailureAt() : null,
                quiet,
                quiet ? quietSince : null,
                usualGap == null ? null : Math.max(1, Math.round(usualGap.toMinutes() / 60.0)),
                overdue
        );
    }

    private static Instant latest(Instant first, Instant second) {
        if (first == null) {
            return second;
        }
        return second == null || first.isAfter(second) ? first : second;
    }

    private static Duration max(Duration first, Duration second) {
        return first.compareTo(second) >= 0 ? first : second;
    }
}
