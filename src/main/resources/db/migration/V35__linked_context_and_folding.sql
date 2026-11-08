-- ADR-0041: Linear identifiers link like Jira keys, a release's linked issues are read again
-- when its review is requested, and a tracker change may be folded into a code change.

-- The key a Linear team's identifiers start with, such as OPS in OPS-7. A source connected
-- before this version has none until its first enrichment reads it.
ALTER TABLE integration_sources
    ADD COLUMN external_team_key VARCHAR(100),
    ADD CONSTRAINT integration_sources_team_key_linear CHECK (
        external_team_key IS NULL OR (source_type = 'LINEAR' AND external_team_key ~ '^[A-Z][A-Z0-9_]*$')
    );

-- One read of a release's linked issues, queued when its review is requested. A request
-- for review again queues it again.
CREATE TABLE linked_issue_reads (
    release_id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    project_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempts INTEGER NOT NULL,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    claimed_at TIMESTAMP WITH TIME ZONE,
    last_error VARCHAR(100),
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT linked_issue_reads_release_fk
        FOREIGN KEY (release_id, organization_id, project_id)
        REFERENCES releases (id, organization_id, project_id)
        ON DELETE CASCADE,
    CONSTRAINT linked_issue_reads_status_known CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT linked_issue_reads_attempts_non_negative CHECK (attempts >= 0),
    CONSTRAINT linked_issue_reads_completion CHECK ((status IN ('SUCCEEDED', 'FAILED')) = (completed_at IS NOT NULL))
);

CREATE INDEX linked_issue_reads_due ON linked_issue_reads (next_attempt_at) WHERE status = 'PENDING';

-- Each linked issue of a release as of its review, and a person's acceptance of a status
-- a note may then show. A row may hold an acceptance of a status that was never read.
CREATE TABLE release_linked_issues (
    release_id UUID NOT NULL,
    organization_id UUID NOT NULL,
    project_id UUID NOT NULL,
    tracker VARCHAR(10) NOT NULL,
    issue_key VARCHAR(100) NOT NULL,
    status VARCHAR(200),
    status_category VARCHAR(10),
    read_at TIMESTAMP WITH TIME ZONE,
    accepted_status VARCHAR(200),
    accepted_by UUID,
    accepter_name VARCHAR(120),
    accepted_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT release_linked_issues_pk PRIMARY KEY (release_id, tracker, issue_key),
    CONSTRAINT release_linked_issues_release_fk
        FOREIGN KEY (release_id, organization_id, project_id)
        REFERENCES releases (id, organization_id, project_id)
        ON DELETE CASCADE,
    CONSTRAINT release_linked_issues_accepter_fk
        FOREIGN KEY (accepted_by, organization_id)
        REFERENCES app_users (id, organization_id),
    CONSTRAINT release_linked_issues_tracker_known CHECK (tracker IN ('JIRA', 'LINEAR')),
    CONSTRAINT release_linked_issues_category_known CHECK (status_category IS NULL OR status_category IN ('open', 'done')),
    CONSTRAINT release_linked_issues_read_recorded CHECK ((read_at IS NULL) = (status IS NULL)),
    CONSTRAINT release_linked_issues_acceptance_recorded CHECK (
        (accepted_status IS NULL) = (accepted_at IS NULL)
        AND (accepted_at IS NULL) = (accepted_by IS NULL)
        AND (accepted_by IS NULL) = (accepter_name IS NULL)
    )
);

-- A person's decision on a suggestion to fold a tracker change into a code change of the
-- same release, recorded once per tracker change and release.
CREATE TABLE release_fold_decisions (
    release_id UUID NOT NULL,
    tracker_change_id UUID NOT NULL,
    code_change_id UUID NOT NULL,
    organization_id UUID NOT NULL,
    project_id UUID NOT NULL,
    evidence VARCHAR(20) NOT NULL,
    issue_key VARCHAR(100) NOT NULL,
    decision VARCHAR(20) NOT NULL,
    removal VARCHAR(20),
    reason VARCHAR(500),
    decided_by UUID NOT NULL,
    decider_name VARCHAR(120) NOT NULL,
    decided_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT release_fold_decisions_pk PRIMARY KEY (release_id, tracker_change_id),
    CONSTRAINT release_fold_decisions_release_fk
        FOREIGN KEY (release_id, organization_id, project_id)
        REFERENCES releases (id, organization_id, project_id)
        ON DELETE CASCADE,
    CONSTRAINT release_fold_decisions_tracker_change_fk
        FOREIGN KEY (tracker_change_id, organization_id, project_id)
        REFERENCES changes (id, organization_id, project_id),
    CONSTRAINT release_fold_decisions_code_change_fk
        FOREIGN KEY (code_change_id, organization_id, project_id)
        REFERENCES changes (id, organization_id, project_id),
    CONSTRAINT release_fold_decisions_decider_fk
        FOREIGN KEY (decided_by, organization_id)
        REFERENCES app_users (id, organization_id),
    CONSTRAINT release_fold_decisions_distinct CHECK (tracker_change_id <> code_change_id),
    CONSTRAINT release_fold_decisions_evidence_known CHECK (evidence IN ('LINKED_KEY', 'CONFIRMED_DUPLICATE')),
    CONSTRAINT release_fold_decisions_decision_known CHECK (decision IN ('ACCEPTED', 'DISMISSED')),
    CONSTRAINT release_fold_decisions_removal CHECK (
        (decision = 'ACCEPTED') = (removal IS NOT NULL)
        AND (removal IS NULL OR removal IN ('REJECTED', 'EXCLUDED'))
        AND ((removal = 'EXCLUDED') = (reason IS NOT NULL))
    )
);
