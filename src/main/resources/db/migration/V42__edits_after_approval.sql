-- ADR-0047: an approved release's summary and upgrade guide link change without a return to
-- draft, and the release records who changed each after approval. A note also records when a
-- person last edited it, which the release page compares against the latest such change, and
-- the digest of the text it had when it was last ready, so that its update time moves only
-- when that text does. No existing release changes, and only notes of approved releases are
-- written.

-- Who last changed the summary, and the upgrade guide link, after approval: under their name
-- then, and when. Each set is all or none, and exists only once the release was approved; a
-- return to draft clears it with the approval, and publication freezes it with the release.
ALTER TABLE releases
    ADD COLUMN summary_changed_by UUID,
    ADD COLUMN summary_changer_name VARCHAR(120),
    ADD COLUMN summary_changed_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN upgrade_guide_changed_by UUID,
    ADD COLUMN upgrade_guide_changer_name VARCHAR(120),
    ADD COLUMN upgrade_guide_changed_at TIMESTAMP WITH TIME ZONE,
    ADD CONSTRAINT releases_summary_changer_fk
        FOREIGN KEY (summary_changed_by, organization_id) REFERENCES app_users (id, organization_id),
    ADD CONSTRAINT releases_upgrade_guide_changer_fk
        FOREIGN KEY (upgrade_guide_changed_by, organization_id) REFERENCES app_users (id, organization_id),
    ADD CONSTRAINT releases_summary_change_recorded CHECK (
        (summary_changed_by IS NULL) = (summary_changer_name IS NULL)
        AND (summary_changed_by IS NULL) = (summary_changed_at IS NULL)
        AND (summary_changer_name IS NULL OR BTRIM(summary_changer_name) <> '')
    ),
    ADD CONSTRAINT releases_upgrade_guide_change_recorded CHECK (
        (upgrade_guide_changed_by IS NULL) = (upgrade_guide_changer_name IS NULL)
        AND (upgrade_guide_changed_by IS NULL) = (upgrade_guide_changed_at IS NULL)
        AND (upgrade_guide_changer_name IS NULL OR BTRIM(upgrade_guide_changer_name) <> '')
    ),
    ADD CONSTRAINT releases_changes_after_approval_only CHECK (
        status IN ('APPROVED', 'PUBLISHED')
        OR (summary_changed_by IS NULL AND upgrade_guide_changed_by IS NULL)
    );

-- When a person last edited the note. Every edit from now on sets it with last_edited_by;
-- the check is one-way because a published release's notes cannot be written
-- (release_audience_notes_follow_release_status), so a note a person edited before this
-- migration on a release already published keeps no time of its own.
--
-- The SHA-256 of the note's text when it was last ready, in lowercase hex. While its language
-- is translating, or a translation failed, a note shows its change's text untranslated; its
-- update time moves only when it is ready again with text other than this. Null for a note
-- that was never ready, and for every note of a release already published.
ALTER TABLE release_audience_notes
    ADD COLUMN last_edited_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN ready_content_sha256 VARCHAR(64),
    ADD CONSTRAINT release_audience_notes_last_edited_at_with_editor
        CHECK (last_edited_at IS NULL OR last_edited_by IS NOT NULL),
    ADD CONSTRAINT release_audience_notes_ready_content_sha256_hex
        CHECK (ready_content_sha256 IS NULL OR ready_content_sha256 ~ '^[0-9a-f]{64}$');

-- Only an approved release's notes are rendered again or read against a change. A re-render
-- never writes a note a person edited, so its updated_at is still the time of that edit; a
-- ready note's text is the text it had when it was last ready.
UPDATE release_audience_notes note
SET last_edited_at = CASE WHEN note.last_edited_by IS NOT NULL THEN note.updated_at END,
    ready_content_sha256 = CASE WHEN note.translation_status = 'READY'
        THEN encode(sha256(convert_to(note.content, 'UTF8')), 'hex') END
FROM releases release
WHERE release.id = note.release_id
  AND release.status = 'APPROVED';
