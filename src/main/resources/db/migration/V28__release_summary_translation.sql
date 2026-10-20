-- A release's own summary is translated through the same queue as its changes' content.
-- A job now belongs to exactly one of a change or a release; the jobs that exist already
-- all belong to a change and keep doing so.
ALTER TABLE translation_jobs
    ALTER COLUMN change_id DROP NOT NULL,
    ADD COLUMN release_id UUID,
    ADD CONSTRAINT translation_jobs_release_fk
        FOREIGN KEY (release_id, organization_id, project_id)
        REFERENCES releases (id, organization_id, project_id)
        ON DELETE CASCADE,
    ADD CONSTRAINT translation_jobs_one_subject CHECK (num_nonnulls(change_id, release_id) = 1),
    ADD CONSTRAINT translation_jobs_release_input_unique UNIQUE (release_id, target_language, input_hash);
