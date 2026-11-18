-- ADR-0044: an administrator retires an audience and brings it back. A retired audience
-- gets no narrative, no note, and no translation; its row, templates, and published notes
-- stay. An Organization keeps at least one active audience.

ALTER TABLE audience_definitions
    ADD COLUMN retired_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN retired_by UUID,
    ADD COLUMN retirer_name VARCHAR(120),
    ADD CONSTRAINT audience_definitions_retirer_fk
        FOREIGN KEY (retired_by, organization_id) REFERENCES app_users (id, organization_id),
    ADD CONSTRAINT audience_definitions_retirement_complete CHECK (
        (retired_at IS NULL) = (retired_by IS NULL) AND (retired_at IS NULL) = (retirer_name IS NULL)
    );

-- Every retirement and return, with who made it under the name they had then. Rows are only
-- ever inserted.
CREATE TABLE audience_status_changes (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations (id),
    audience_id UUID NOT NULL,
    kind VARCHAR(20) NOT NULL,
    changed_by UUID NOT NULL,
    changer_name VARCHAR(120) NOT NULL,
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT audience_status_changes_audience_fk
        FOREIGN KEY (audience_id, organization_id) REFERENCES audience_definitions (id, organization_id),
    CONSTRAINT audience_status_changes_changer_fk
        FOREIGN KEY (changed_by, organization_id) REFERENCES app_users (id, organization_id),
    CONSTRAINT audience_status_changes_kind_known CHECK (kind IN ('RETIRED', 'RESTORED')),
    CONSTRAINT audience_status_changes_changer_name_not_blank CHECK (BTRIM(changer_name) <> '')
);

CREATE INDEX audience_status_changes_audience_idx ON audience_status_changes (audience_id, changed_at DESC, id);

-- The backstop for the last active audience: whatever wrote the change, it cannot commit an
-- Organization without one. The Organization row is locked first, so of two transactions
-- that each retire one of the last two, the second waits for the first, then reads its
-- commit in a fresh snapshot and fails.
CREATE FUNCTION audience_definitions_keep_an_active_audience() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    PERFORM 1 FROM organizations WHERE id = NEW.organization_id FOR UPDATE;
    IF NOT EXISTS (
        SELECT 1 FROM audience_definitions
        WHERE organization_id = NEW.organization_id AND retired_at IS NULL
    ) THEN
        RAISE EXCEPTION 'audience_definitions_keep_an_active_audience: organization % has no active audience left',
            NEW.organization_id
            USING ERRCODE = 'check_violation', CONSTRAINT = 'audience_definitions_keep_an_active_audience';
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER audience_definitions_keep_an_active_audience
    AFTER UPDATE ON audience_definitions
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
    WHEN (OLD.retired_at IS DISTINCT FROM NEW.retired_at)
    EXECUTE FUNCTION audience_definitions_keep_an_active_audience();

-- An action for a retired audience is skipped, not failed, so it never stops the actions
-- behind it. Like a failure it says why and has an end.
ALTER TABLE automation_action_runs
    DROP CONSTRAINT automation_action_runs_status_known,
    ADD CONSTRAINT automation_action_runs_status_known
        CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'UNKNOWN', 'CANCELLED', 'SKIPPED')),
    DROP CONSTRAINT automation_action_runs_error_when_failed,
    ADD CONSTRAINT automation_action_runs_error_when_failed
        CHECK ((status IN ('FAILED', 'UNKNOWN', 'SKIPPED')) = (error_code IS NOT NULL)),
    DROP CONSTRAINT automation_action_runs_completion_consistent,
    ADD CONSTRAINT automation_action_runs_completion_consistent CHECK (
        (status IN ('SUCCEEDED', 'FAILED', 'UNKNOWN', 'CANCELLED', 'SKIPPED')) = (completed_at IS NOT NULL)
    );
