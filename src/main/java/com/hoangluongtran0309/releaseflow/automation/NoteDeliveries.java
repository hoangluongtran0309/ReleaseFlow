package com.hoangluongtran0309.releaseflow.automation;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Where a release's notes went (ADR-0046): for every action that delivered one, the revision
 * that destination last received, so a page can say which destinations are behind a
 * correction. It reads only; a delivery is made by a run.
 */
@Component
public class NoteDeliveries {

    private final AutomationActionRunRepository actionRunRepository;
    private final AutomationRunRepository runRepository;

    NoteDeliveries(AutomationActionRunRepository actionRunRepository, AutomationRunRepository runRepository) {
        this.actionRunRepository = actionRunRepository;
        this.runRepository = runRepository;
    }

    /** Each destination's latest successful delivery of one of the release's notes. */
    @Transactional(readOnly = true)
    public List<Delivery> latest(UUID organizationId, UUID releaseId) {
        List<AutomationActionRun> deliveries = actionRunRepository.findLatestDeliveries(organizationId, releaseId);
        if (deliveries.isEmpty()) {
            return List.of();
        }
        Map<UUID, AutomationRun> runs = runRepository
                .findAllById(deliveries.stream().map(AutomationActionRun::getRunId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(AutomationRun::getId, Function.identity()));
        return deliveries.stream()
                .map(delivery -> new Delivery(
                        delivery.getAudienceId(),
                        delivery.getLanguageSnapshot(),
                        delivery.getActionType().getLabelKey(),
                        runs.get(delivery.getRunId()).getRuleNameSnapshot(),
                        delivery.getNoteRevision(),
                        delivery.getCompletedAt(),
                        delivery.getExternalReference()
                ))
                .sorted(Comparator.comparing(Delivery::ruleName).thenComparing(Delivery::destinationKey))
                .toList();
    }

    /**
     * One destination: the kind of action as a bundle key, the rule's name as the run recorded
     * it, the revision it carried, when, and the link it recorded if any.
     */
    public record Delivery(
            UUID audienceId,
            String language,
            String destinationKey,
            String ruleName,
            int revision,
            Instant deliveredAt,
            String externalReference
    ) {
    }
}
