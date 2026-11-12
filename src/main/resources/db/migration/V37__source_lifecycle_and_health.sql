-- ADR-0042: a source can be disconnected, reconnected under its own ID, and have its webhook
-- secret rotated; and it keeps the figures its health is shown from. No existing row changes
-- a column any ciphertext is bound to, so every stored secret and token still decrypts.

ALTER TABLE integration_sources
    ADD COLUMN disconnected_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN disconnected_by UUID,
    ADD COLUMN secret_updated_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN signature_failure_count BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN last_signature_failure_at TIMESTAMP WITH TIME ZONE;

-- A secret written before this version was written when its source was connected.
UPDATE integration_sources SET secret_updated_at = created_at WHERE secret_ciphertext IS NOT NULL;

ALTER TABLE integration_sources
    ADD CONSTRAINT integration_sources_disconnected_by_fk
        FOREIGN KEY (disconnected_by, organization_id) REFERENCES app_users (id, organization_id),
    ADD CONSTRAINT integration_sources_disconnection_complete CHECK (
        (disconnected_at IS NULL) = (disconnected_by IS NULL)
    ),
    -- A disconnected source holds nothing that decrypts to a credential, and is read on no schedule.
    ADD CONSTRAINT integration_sources_disconnected_clean CHECK (
        disconnected_at IS NULL
        OR (webhook_id IS NULL AND secret_nonce IS NULL AND secret_ciphertext IS NULL
            AND token_nonce IS NULL AND token_ciphertext IS NULL AND next_poll_at IS NULL)
    ),
    ADD CONSTRAINT integration_sources_signature_failures_valid CHECK (
        signature_failure_count >= 0
        AND (signature_failure_count = 0 OR last_signature_failure_at IS NOT NULL)
    ),
    -- A connected source has exactly what its type needs, as before; a disconnected one none of it.
    DROP CONSTRAINT integration_sources_webhook_present,
    ADD CONSTRAINT integration_sources_webhook_present CHECK (
        CASE WHEN disconnected_at IS NULL
            THEN (webhook_auth_mode <> 'NONE') = (webhook_id IS NOT NULL)
            ELSE webhook_id IS NULL
        END
    ),
    DROP CONSTRAINT integration_sources_poll_schedule,
    ADD CONSTRAINT integration_sources_poll_schedule CHECK (
        CASE WHEN disconnected_at IS NULL
            THEN (source_type = 'JIRA') = (next_poll_at IS NOT NULL AND poll_cursor_at IS NOT NULL)
            ELSE (source_type = 'JIRA') = (poll_cursor_at IS NOT NULL)
        END
    ),
    -- A key is connected in at most one Project of the Organization, and held at most once by
    -- each Project, so reconnecting it reuses the Project's own source.
    DROP CONSTRAINT integration_sources_external_unique,
    ADD CONSTRAINT integration_sources_external_project_unique
        UNIQUE (organization_id, project_id, source_type, external_project_key);

CREATE UNIQUE INDEX integration_sources_external_unique
    ON integration_sources (organization_id, source_type, external_project_key)
    WHERE disconnected_at IS NULL;

-- A source's rhythm is read from the changes it was delivered, newest 90 days first.
CREATE INDEX changes_source_delivered_idx
    ON changes (source_id, received_at)
    WHERE origin = 'WEBHOOK';
