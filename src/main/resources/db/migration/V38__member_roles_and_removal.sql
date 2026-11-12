-- ADR-0043: an administrator changes a member's role or removes a member. The row stays as
-- the record of who the person was; its email is free again, nothing it holds can sign in,
-- and every session it had stops on its next request.

ALTER TABLE app_users
    ADD COLUMN removed_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN removed_by UUID,
    -- Raised by every role change and by removal; a session made with another is refused.
    ADD COLUMN session_version INTEGER NOT NULL DEFAULT 0,
    ALTER COLUMN password_hash DROP NOT NULL,
    ADD CONSTRAINT app_users_removed_by_fk
        FOREIGN KEY (removed_by, organization_id) REFERENCES app_users (id, organization_id),
    ADD CONSTRAINT app_users_removal_complete CHECK ((removed_at IS NULL) = (removed_by IS NULL)),
    -- A removed member keeps nothing that could sign in.
    ADD CONSTRAINT app_users_credential_matches_membership CHECK ((removed_at IS NULL) = (password_hash IS NOT NULL)),
    ADD CONSTRAINT app_users_session_version_valid CHECK (session_version >= 0),
    DROP CONSTRAINT app_users_email_unique;

-- An address belongs to at most one member who is not removed, so a removed person can be
-- invited back or join another Organization. The index keeps the old constraint's name.
CREATE UNIQUE INDEX app_users_email_unique ON app_users (email) WHERE removed_at IS NULL;

CREATE INDEX app_users_active_role_idx ON app_users (organization_id, role) WHERE removed_at IS NULL;

-- Who changed a member's role or removed them, and when. Rows are only ever inserted.
CREATE TABLE membership_changes (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations (id),
    member_id UUID NOT NULL,
    kind VARCHAR(20) NOT NULL,
    previous_role VARCHAR(20) NOT NULL,
    new_role VARCHAR(20),
    revoked_invitations INTEGER NOT NULL DEFAULT 0,
    changed_by UUID NOT NULL,
    changer_name VARCHAR(120) NOT NULL,
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT membership_changes_member_fk
        FOREIGN KEY (member_id, organization_id) REFERENCES app_users (id, organization_id),
    CONSTRAINT membership_changes_changer_fk
        FOREIGN KEY (changed_by, organization_id) REFERENCES app_users (id, organization_id),
    CONSTRAINT membership_changes_kind_known CHECK (kind IN ('ROLE_CHANGED', 'REMOVED')),
    CONSTRAINT membership_changes_roles_known CHECK (
        previous_role IN ('ADMIN', 'MEMBER') AND (new_role IS NULL OR new_role IN ('ADMIN', 'MEMBER'))
    ),
    CONSTRAINT membership_changes_roles_match_kind CHECK (
        CASE kind WHEN 'REMOVED' THEN new_role IS NULL ELSE new_role IS NOT NULL AND new_role <> previous_role END
    ),
    CONSTRAINT membership_changes_revoked_valid CHECK (revoked_invitations >= 0),
    CONSTRAINT membership_changes_changer_name_not_blank CHECK (BTRIM(changer_name) <> '')
);

CREATE INDEX membership_changes_organization_idx ON membership_changes (organization_id, changed_at DESC, id);

-- The backstop for the last administrator: whatever wrote the change, it cannot commit an
-- Organization without an administrator. The Organization row is locked first, so of two
-- transactions that each demote the other, the second waits for the first, then reads its
-- commit in a fresh snapshot and fails.
CREATE FUNCTION app_users_keep_an_administrator() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    PERFORM 1 FROM organizations WHERE id = NEW.organization_id FOR UPDATE;
    IF NOT EXISTS (
        SELECT 1 FROM app_users
        WHERE organization_id = NEW.organization_id AND role = 'ADMIN' AND removed_at IS NULL
    ) THEN
        RAISE EXCEPTION 'app_users_keep_an_administrator: organization % has no administrator left',
            NEW.organization_id
            USING ERRCODE = 'check_violation', CONSTRAINT = 'app_users_keep_an_administrator';
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER app_users_keep_an_administrator
    AFTER UPDATE ON app_users
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
    WHEN (OLD.role IS DISTINCT FROM NEW.role OR OLD.removed_at IS DISTINCT FROM NEW.removed_at)
    EXECUTE FUNCTION app_users_keep_an_administrator();
