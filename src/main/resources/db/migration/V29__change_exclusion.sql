-- An administrator can say a change will never ship (ADR-0033). The
-- decision is recorded like a review — who, when, and why — can be taken back, and
-- deletes nothing: the change only stops being offered to releases.
ALTER TABLE changes
    ADD COLUMN excluded_by UUID,
    ADD COLUMN excluder_name VARCHAR(120),
    ADD COLUMN excluded_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN exclusion_reason VARCHAR(500),
    ADD CONSTRAINT changes_excluder_fk
        FOREIGN KEY (excluded_by, organization_id)
        REFERENCES app_users (id, organization_id),
    ADD CONSTRAINT changes_exclusion_recorded CHECK (
        (excluded_by IS NULL) = (excluded_at IS NULL)
        AND (excluded_by IS NULL) = (excluder_name IS NULL)
        AND (excluded_by IS NULL) = (exclusion_reason IS NULL)
        AND (exclusion_reason IS NULL OR btrim(exclusion_reason) <> '')
    );

-- An excluded change never enters a release. The change row is read FOR SHARE, so an
-- exclusion that has locked it is waited for and then seen; the service checks the
-- other way round under the same lock.
CREATE FUNCTION require_change_not_excluded() RETURNS trigger
    LANGUAGE plpgsql AS
$$
DECLARE
    excluded TIMESTAMP WITH TIME ZONE;
BEGIN
    SELECT excluded_at INTO excluded FROM changes WHERE id = NEW.change_id FOR SHARE;
    IF excluded IS NOT NULL THEN
        RAISE EXCEPTION 'An excluded change cannot be added to a release (change %, release_changes_change_not_excluded).',
            NEW.change_id
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER release_changes_change_not_excluded
    BEFORE INSERT ON release_changes
    FOR EACH ROW EXECUTE FUNCTION require_change_not_excluded();
