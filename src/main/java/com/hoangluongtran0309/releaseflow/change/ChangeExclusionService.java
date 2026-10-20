package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.account.ReleaseFlowPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * Excludes a change from releases, or takes that back. The change row is locked first, so
 * a release that is adding it either finished before, and the change is in a release, or
 * waits and is then refused by the database (ADR-0033).
 */
@Service
public class ChangeExclusionService {

    private final ChangeRepository changeRepository;
    private final Clock clock;

    ChangeExclusionService(ChangeRepository changeRepository, Clock clock) {
        this.changeRepository = changeRepository;
        this.clock = clock;
    }

    @Transactional
    public ChangeView exclude(ReleaseFlowPrincipal administrator, UUID projectId, UUID changeId, ChangeExclusionRequest request) {
        Change change = lock(administrator, projectId, changeId);
        if (changeRepository.inAnyRelease(change.getId(), administrator.organizationId())) {
            throw new ChangeInReleaseException();
        }
        change.exclude(administrator.userId(), administrator.displayName(), request.getReason(), clock.instant());
        return ChangeView.from(change);
    }

    @Transactional
    public ChangeView include(ReleaseFlowPrincipal administrator, UUID projectId, UUID changeId) {
        Change change = lock(administrator, projectId, changeId);
        change.include();
        return ChangeView.from(change);
    }

    private Change lock(ReleaseFlowPrincipal administrator, UUID projectId, UUID changeId) {
        return changeRepository.lockByIdAndOrganizationIdAndProjectId(changeId, administrator.organizationId(), projectId)
                .orElseThrow(ChangeNotFoundException::new);
    }
}
