-- ADR-0041: accepting a fold carries the tracker change's breaking and security flags into the
-- code change, as a review decision of that code change that names the folded change.

-- Which flags an accepted fold added to the code change; a dismissed one adds none.
ALTER TABLE release_fold_decisions
    ADD COLUMN adds_breaking BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN adds_security BOOLEAN NOT NULL DEFAULT false,
    ADD CONSTRAINT release_fold_decisions_flags_accepted CHECK (
        decision = 'ACCEPTED' OR (NOT adds_breaking AND NOT adds_security)
    );

-- The tracker change whose fold made this decision; a person's own decision names none.
ALTER TABLE release_change_reviews
    ADD COLUMN folded_change_id UUID,
    ADD CONSTRAINT release_change_reviews_folded_change_fk
        FOREIGN KEY (folded_change_id, organization_id, project_id)
        REFERENCES changes (id, organization_id, project_id),
    ADD CONSTRAINT release_change_reviews_folded_distinct CHECK (folded_change_id IS NULL OR folded_change_id <> change_id);
