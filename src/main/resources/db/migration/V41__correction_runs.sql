-- ADR-0046 part 5, as amended on 2026-11-21: a correction run carries the reason a destination
-- shows, written by the person who delivers it, and each of its action runs names the delivery
-- it corrects. No existing row changes: no run before this migration is a correction.

-- The reason a correction run gives, in its note's language: the line a corrected public entry
-- shows. A correction always has one, and no other run does.
ALTER TABLE automation_runs
    ADD COLUMN reason VARCHAR(500),
    ADD CONSTRAINT automation_runs_reason_with_correction
        CHECK ((trigger_type = 'CORRECTION') = (reason IS NOT NULL)),
    ADD CONSTRAINT automation_runs_reason_not_blank CHECK (reason IS NULL OR BTRIM(reason) <> '');

-- The successful delivery a correction replaces: the target the earlier run recorded, found by
-- that run rather than by the audience's name now. Null on every action run that corrects
-- nothing.
ALTER TABLE automation_action_runs
    ADD COLUMN corrects_action_run_id UUID,
    ADD CONSTRAINT automation_action_runs_corrects_fk
        FOREIGN KEY (corrects_action_run_id, organization_id) REFERENCES automation_action_runs (id, organization_id),
    ADD CONSTRAINT automation_action_runs_corrects_not_itself
        CHECK (corrects_action_run_id IS NULL OR corrects_action_run_id <> id),
    ADD CONSTRAINT automation_action_runs_corrects_with_note
        CHECK (corrects_action_run_id IS NULL OR note_content_snapshot IS NOT NULL);

CREATE INDEX automation_action_runs_corrects_idx
    ON automation_action_runs (corrects_action_run_id)
    WHERE corrects_action_run_id IS NOT NULL;
