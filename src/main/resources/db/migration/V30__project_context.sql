-- A Project's context (ADR-0035): what its administrators say about the product, sent
-- with every AI request for its changes and to DeepL as context. Like a Project's
-- sensitive paths, it records who changed it last and when.
ALTER TABLE projects
    ADD COLUMN context VARCHAR(2000) NOT NULL DEFAULT '',
    ADD COLUMN context_updated_by UUID,
    ADD COLUMN context_updater_name VARCHAR(120),
    ADD COLUMN context_updated_at TIMESTAMP WITH TIME ZONE,
    ADD CONSTRAINT projects_context_updater_fk
        FOREIGN KEY (context_updated_by, organization_id)
        REFERENCES app_users (id, organization_id),
    ADD CONSTRAINT projects_context_recorded CHECK (
        (context_updated_by IS NULL) = (context_updated_at IS NULL)
        AND (context_updated_by IS NULL) = (context_updater_name IS NULL)
    );

-- A translation keeps the context it was asked with, so a job sends exactly what its
-- input hash names. Jobs recorded before had none.
ALTER TABLE translation_jobs
    ADD COLUMN context VARCHAR(2000) NOT NULL DEFAULT '';
