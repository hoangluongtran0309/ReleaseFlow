-- ADR-0046 Slice A: a published note is corrected by a new immutable revision. The note as
-- published is revision 1 and is the release_audience_notes row, which never changes;
-- revisions 2 and up are rows here. A public changelog entry gains revisions the same way,
-- and an Action run records which revision of its note it carried. No existing row changes.

-- The keys the revisions point at, so a revision can only name a note of its own release,
-- Organization, and Project, and a public revision an entry of its own Organization.
ALTER TABLE release_audience_notes
    ADD CONSTRAINT release_audience_notes_id_scope_unique UNIQUE (id, release_id, organization_id, project_id);

ALTER TABLE public_changelog_entries
    ADD CONSTRAINT public_changelog_entries_id_organization_unique UNIQUE (id, organization_id);

-- Revision 2 and up of one audience and language note. Only the Markdown changes: the
-- version, audience, language, and release are the note's. The person is recorded under
-- the name they had then, and a removed member's row is never deleted (ADR-0043). The
-- foreign keys take no action on delete: nothing that could remove a note or its release
-- may take a revision with it.
CREATE TABLE release_note_revisions (
    id UUID PRIMARY KEY,
    note_id UUID NOT NULL,
    release_id UUID NOT NULL,
    organization_id UUID NOT NULL REFERENCES organizations (id),
    project_id UUID NOT NULL,
    revision INTEGER NOT NULL,
    content TEXT NOT NULL,
    origin VARCHAR(20) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    author_id UUID NOT NULL,
    author_name VARCHAR(120) NOT NULL,
    glossary_status VARCHAR(20) NOT NULL,
    glossary_reason VARCHAR(40),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT release_note_revisions_note_fk
        FOREIGN KEY (note_id, release_id, organization_id, project_id)
        REFERENCES release_audience_notes (id, release_id, organization_id, project_id),
    CONSTRAINT release_note_revisions_author_fk
        FOREIGN KEY (author_id, organization_id) REFERENCES app_users (id, organization_id),
    CONSTRAINT release_note_revisions_number_unique UNIQUE (note_id, revision),
    CONSTRAINT release_note_revisions_number_after_published CHECK (revision >= 2),
    CONSTRAINT release_note_revisions_origin_known CHECK (origin IN ('MANUAL', 'RENDERED', 'TRANSLATED')),
    CONSTRAINT release_note_revisions_content_not_blank CHECK (BTRIM(content) <> ''),
    CONSTRAINT release_note_revisions_reason_not_blank CHECK (BTRIM(reason) <> ''),
    CONSTRAINT release_note_revisions_author_name_not_blank CHECK (BTRIM(author_name) <> ''),
    -- A revision is complete when it is written, so its glossary is never pending, and a
    -- text a person wrote claims nothing.
    CONSTRAINT release_note_revisions_glossary_status_known CHECK (
        glossary_status IN ('NONE', 'APPLIED', 'NOT_APPLIED')
        AND (origin <> 'MANUAL' OR glossary_status = 'NONE')
    ),
    CONSTRAINT release_note_revisions_glossary_reason CHECK (
        (glossary_status = 'NOT_APPLIED') = (glossary_reason IS NOT NULL)
    )
);

CREATE INDEX release_note_revisions_release_idx ON release_note_revisions (release_id, note_id, revision);

-- A revision is never changed or removed, whatever asks: an update, a delete, or a delete
-- some other table's cascade would make.
CREATE FUNCTION reject_release_note_revision_mutation() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'Release note revisions are immutable (revision % of note %).', OLD.revision, OLD.note_id
        USING ERRCODE = 'integrity_constraint_violation';
END;
$$;

CREATE TRIGGER release_note_revisions_immutable
    BEFORE UPDATE OR DELETE ON release_note_revisions
    FOR EACH ROW EXECUTE FUNCTION reject_release_note_revision_mutation();

-- A revision corrects a published note, and supersedes the one numbered just below it.
-- The note's row is locked first, so of two corrections that both read revision 1 as the
-- latest, the second waits, then reads the first's revision 2 and is refused; the unique
-- number is the backstop.
CREATE FUNCTION enforce_release_note_revision() RETURNS trigger
    LANGUAGE plpgsql AS
$$
DECLARE
    latest INTEGER;
BEGIN
    PERFORM 1 FROM release_audience_notes WHERE id = NEW.note_id FOR UPDATE;
    IF NOT EXISTS (SELECT 1 FROM releases WHERE id = NEW.release_id AND status = 'PUBLISHED') THEN
        RAISE EXCEPTION 'Only a published release note can be corrected (release %).', NEW.release_id
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    SELECT COALESCE(MAX(revision), 1) INTO latest FROM release_note_revisions WHERE note_id = NEW.note_id;
    IF NEW.revision <> latest + 1 THEN
        RAISE EXCEPTION 'Revision % of note % is not the next one (latest is %).', NEW.revision, NEW.note_id, latest
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER release_note_revisions_next_number
    BEFORE INSERT ON release_note_revisions
    FOR EACH ROW EXECUTE FUNCTION enforce_release_note_revision();

-- Revision 2 and up of one public entry, appended by the correction run that delivered it.
-- The entry row never changes: it stays revision 1. The person is never recorded here, so
-- no public page can name them. A public revision records the note revision it copied,
-- which need not share its number: a note corrected twice before its first delivery is
-- public revision 2.
CREATE TABLE public_changelog_entry_revisions (
    id UUID PRIMARY KEY,
    entry_id UUID NOT NULL,
    organization_id UUID NOT NULL REFERENCES organizations (id),
    revision INTEGER NOT NULL,
    note_revision INTEGER NOT NULL,
    action_run_id UUID NOT NULL,
    content_snapshot TEXT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    corrected_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT public_changelog_entry_revisions_entry_fk
        FOREIGN KEY (entry_id, organization_id) REFERENCES public_changelog_entries (id, organization_id),
    CONSTRAINT public_changelog_entry_revisions_action_run_fk
        FOREIGN KEY (action_run_id, organization_id) REFERENCES automation_action_runs (id, organization_id),
    CONSTRAINT public_changelog_entry_revisions_number_unique UNIQUE (entry_id, revision),
    -- One revision per Action run, so a retry cannot quietly append a second copy.
    CONSTRAINT public_changelog_entry_revisions_action_run_unique UNIQUE (action_run_id),
    CONSTRAINT public_changelog_entry_revisions_number_after_published CHECK (revision >= 2),
    CONSTRAINT public_changelog_entry_revisions_note_revision_corrected CHECK (note_revision >= 2),
    CONSTRAINT public_changelog_entry_revisions_content_not_blank CHECK (BTRIM(content_snapshot) <> ''),
    CONSTRAINT public_changelog_entry_revisions_reason_not_blank CHECK (BTRIM(reason) <> '')
);

CREATE INDEX public_changelog_entry_revisions_feed_idx
    ON public_changelog_entry_revisions (organization_id, corrected_at DESC, id);

CREATE TRIGGER public_changelog_entry_revisions_immutable
    BEFORE UPDATE OR DELETE ON public_changelog_entry_revisions
    FOR EACH ROW EXECUTE FUNCTION reject_public_changelog_mutation();

-- The entry is locked first, as a note is, so two deliveries cannot both write revision 2.
CREATE FUNCTION enforce_public_changelog_entry_revision() RETURNS trigger
    LANGUAGE plpgsql AS
$$
DECLARE
    latest INTEGER;
BEGIN
    PERFORM 1 FROM public_changelog_entries WHERE id = NEW.entry_id FOR UPDATE;
    SELECT COALESCE(MAX(revision), 1) INTO latest FROM public_changelog_entry_revisions WHERE entry_id = NEW.entry_id;
    IF NEW.revision <> latest + 1 THEN
        RAISE EXCEPTION 'Revision % of public entry % is not the next one (latest is %).', NEW.revision, NEW.entry_id,
            latest
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER public_changelog_entry_revisions_next_number
    BEFORE INSERT ON public_changelog_entry_revisions
    FOR EACH ROW EXECUTE FUNCTION enforce_public_changelog_entry_revision();

-- The revision of its note an Action run carried. Null when it carried no note, and on
-- every run written before this migration, which carried revision 1: there was no other.
ALTER TABLE automation_action_runs
    ADD COLUMN note_revision INTEGER,
    ADD CONSTRAINT automation_action_runs_note_revision_positive CHECK (note_revision IS NULL OR note_revision >= 1),
    ADD CONSTRAINT automation_action_runs_note_revision_with_note
        CHECK (note_revision IS NULL OR note_content_snapshot IS NOT NULL);

-- A correction run: an administrator starts it for one rule and one published release. It
-- is a run trigger only; no rule is ever written with it. A person asks for it, so it
-- carries a request ID and its requester, as a manual run does.
ALTER TABLE automation_runs
    DROP CONSTRAINT automation_runs_trigger_known,
    ADD CONSTRAINT automation_runs_trigger_known CHECK (trigger_type IN (
        'RELEASE_PUBLISHED',
        'MANUAL',
        'SCHEDULED_CRON',
        'UPCOMING_RELEASE_REMINDER',
        'EXTERNAL_WEBHOOK',
        'CORRECTION'
    )),
    DROP CONSTRAINT automation_runs_requester_matches_trigger,
    ADD CONSTRAINT automation_runs_requester_matches_trigger CHECK (
        (trigger_type IN ('MANUAL', 'EXTERNAL_WEBHOOK', 'CORRECTION')) = (request_id IS NOT NULL)
        AND (initiated_by IS NULL) = (initiator_name IS NULL)
        AND (initiated_by IS NULL OR trigger_type IN ('MANUAL', 'CORRECTION'))
        AND (trigger_type <> 'CORRECTION' OR initiated_by IS NOT NULL)
    );
