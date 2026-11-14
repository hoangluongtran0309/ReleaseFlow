package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.audience.AudienceService;
import com.hoangluongtran0309.releaseflow.audience.AudienceView;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The changes of a release that lack a narrative for an active audience, and the request
 * that asks the AI for the missing ones (ADR-0044). Listing them never calls the AI; the
 * estimate is the length of the request that would be sent, over four.
 */
@Component
public class MissingNarratives {

    private final ChangeAiClassificationService aiClassificationService;
    private final AudienceService audienceService;

    MissingNarratives(ChangeAiClassificationService aiClassificationService, AudienceService audienceService) {
        this.aiClassificationService = aiClassificationService;
        this.audienceService = audienceService;
    }

    /** The changes the AI classified that have no narrative for some active audience. */
    public List<MissingNarrative> of(UUID organizationId, UUID projectId, List<ChangeView> changes) {
        List<AudienceView> active = audienceService.active(organizationId);
        boolean ai = aiClassificationService.isEnabled();
        List<MissingNarrative> missing = new ArrayList<>();
        for (ChangeView change : changes) {
            if (change.aiStatus() != AiStatus.SUCCEEDED) {
                continue;
            }
            List<String> names = active.stream()
                    .filter(audience -> !change.audienceNarrativeDetails().containsKey(audience.code()))
                    .map(AudienceView::displayName)
                    .toList();
            if (!names.isEmpty()) {
                missing.add(new MissingNarrative(change.id(), change.reference(), change.title(), names,
                        ai ? aiClassificationService.estimateMissingNarrativeTokens(organizationId, projectId, change.id()) : 0));
            }
        }
        return missing;
    }

    /** Asks the AI for one change's missing narratives; only those are written. */
    public ChangeView request(UUID organizationId, UUID projectId, UUID changeId) {
        return aiClassificationService.requestMissingNarratives(organizationId, projectId, changeId);
    }

    public boolean aiAvailable() {
        return aiClassificationService.isEnabled();
    }
}
