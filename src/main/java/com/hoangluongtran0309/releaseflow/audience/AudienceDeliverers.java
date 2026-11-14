package com.hoangluongtran0309.releaseflow.audience;

import java.util.List;
import java.util.UUID;

/**
 * The enabled automation rules that deliver an audience, so the page that retires it can
 * name them (ADR-0044). Automation answers it; the audience capability never reaches into
 * automation itself.
 */
public interface AudienceDeliverers {

    List<String> enabledRulesDelivering(UUID organizationId, UUID audienceId);
}
