package com.hoangluongtran0309.releaseflow.change;

import com.hoangluongtran0309.releaseflow.jira.LinkedIssue;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * What folding a tracker change into a code change of the same release needs from changes
 * (ADR-0041): the pairs a person confirmed as duplicates, and keeping a folded issue as a
 * code change's linked context. Every query is scoped by Organization and Project.
 */
@Service
public class ChangeFoldingService {

    private final ChangeRepository changeRepository;
    private final DuplicateCandidateRepository duplicateRepository;

    ChangeFoldingService(ChangeRepository changeRepository, DuplicateCandidateRepository duplicateRepository) {
        this.changeRepository = changeRepository;
        this.duplicateRepository = duplicateRepository;
    }

    /** A pair of changes a person confirmed as possible duplicates (ADR-0013). */
    public record ConfirmedDuplicate(UUID changeId, UUID duplicateOfId, String deciderName) {

        public boolean pairs(UUID first, UUID second) {
            return (changeId.equals(first) && duplicateOfId.equals(second))
                    || (changeId.equals(second) && duplicateOfId.equals(first));
        }
    }

    /** The confirmed duplicates both of whose changes are among these. */
    @Transactional(readOnly = true)
    public List<ConfirmedDuplicate> confirmedDuplicates(UUID organizationId, UUID projectId, Collection<UUID> changeIds) {
        Set<UUID> among = Set.copyOf(changeIds);
        return duplicateRepository
                .findAllByOrganizationIdAndProjectIdAndStatus(organizationId, projectId, DuplicateCandidateStatus.CONFIRMED)
                .stream()
                .filter(candidate -> among.contains(candidate.getChangeId()) && among.contains(candidate.getDuplicateOfId()))
                .map(candidate -> new ConfirmedDuplicate(candidate.getChangeId(), candidate.getDuplicateOfId(),
                        candidate.getDeciderName()))
                .toList();
    }

    /** Keeps the issue as the code change's linked context, unless it already holds it. */
    @Transactional
    public ChangeView foldIssue(UUID organizationId, UUID projectId, UUID codeChangeId, LinkedIssue issue) {
        Change change = changeRepository.lockByIdAndOrganizationIdAndProjectId(codeChangeId, organizationId, projectId)
                .orElseThrow(ChangeNotFoundException::new);
        change.foldIssue(issue);
        return ChangeView.from(change);
    }
}
