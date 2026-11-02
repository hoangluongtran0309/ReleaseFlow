-- A change gains a security flag beside breaking (ADR-0037). Changes recorded before start
-- as not security and are never reclassified. Like a breaking change, a security change
-- leaves review only through a recorded review.
ALTER TABLE changes
    ADD COLUMN security BOOLEAN NOT NULL DEFAULT FALSE,
    DROP CONSTRAINT changes_review_required,
    ADD CONSTRAINT changes_review_required CHECK (
        needs_review OR reviewed_at IS NOT NULL OR (NOT breaking AND NOT security AND category <> 'UNKNOWN')
    );

-- A release may link an upgrade guide and name the published release it follows, which
-- gives a note its compare link. Both foreign key columns carry the Organization and
-- Project, so a release can only follow one of its own Project.
ALTER TABLE releases
    ADD COLUMN upgrade_guide_url VARCHAR(2000),
    ADD COLUMN previous_release_id UUID,
    ADD CONSTRAINT releases_previous_release_fk
        FOREIGN KEY (previous_release_id, organization_id, project_id)
        REFERENCES releases (id, organization_id, project_id),
    ADD CONSTRAINT releases_previous_release_other CHECK (previous_release_id IS NULL OR previous_release_id <> id),
    ADD CONSTRAINT releases_upgrade_guide_url_not_blank CHECK (
        upgrade_guide_url IS NULL OR BTRIM(upgrade_guide_url) <> ''
    );

CREATE FUNCTION require_published_previous_release() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    IF NEW.previous_release_id IS NOT NULL
        AND (TG_OP = 'INSERT' OR NEW.previous_release_id IS DISTINCT FROM OLD.previous_release_id)
        AND NOT EXISTS (SELECT 1 FROM releases WHERE id = NEW.previous_release_id AND status = 'PUBLISHED') THEN
        RAISE EXCEPTION 'A previous release must be published (release %).', NEW.id
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER releases_previous_release_published
    BEFORE INSERT OR UPDATE ON releases
    FOR EACH ROW EXECUTE FUNCTION require_published_previous_release();

-- Each audience gains a footer template, rendered once per note; blank adds nothing.
ALTER TABLE audience_definitions
    ADD COLUMN footer_template_body TEXT NOT NULL DEFAULT '';

ALTER TABLE audience_template_variants
    ADD COLUMN footer_template_body TEXT NOT NULL DEFAULT '';

-- A note keeps the footer it was written with, as it keeps its item template. Notes
-- written before had none.
ALTER TABLE release_audience_notes
    ADD COLUMN footer_template_snapshot TEXT NOT NULL DEFAULT '';

-- The shipped operator and contributor audiences adopt the shipped footer in each of their
-- languages, since no footer existed to overwrite: Vietnamese for vi, English otherwise,
-- as AudiencePresets falls back. Custom audiences keep a blank footer.
CREATE TEMPORARY TABLE preset_footers (language VARCHAR(16) PRIMARY KEY, body TEXT NOT NULL) ON COMMIT DROP;

-- Escaped literals, since a dollar quote followed by braces reads to Flyway as a placeholder.
INSERT INTO preset_footers (language, body)
VALUES
    ('en', E'{{#compareUrl}}\n**Compare:** [{{previousVersion}}...{{version}}]({{compareUrl}})\n{{/compareUrl}}\n\n{{#upgradeGuideUrl}}\n**Upgrade guide:** [Read before upgrading]({{upgradeGuideUrl}})\n{{/upgradeGuideUrl}}\n'),
    ('vi', E'{{#compareUrl}}\n**So sánh:** [{{previousVersion}}...{{version}}]({{compareUrl}})\n{{/compareUrl}}\n\n{{#upgradeGuideUrl}}\n**Hướng dẫn nâng cấp:** [Đọc trước khi nâng cấp]({{upgradeGuideUrl}})\n{{/upgradeGuideUrl}}\n');

UPDATE audience_definitions a
SET footer_template_body = f.body
FROM organizations o, preset_footers f
WHERE o.id = a.organization_id
  AND a.preset
  AND a.code IN ('operator', 'contributor')
  AND f.language = CASE WHEN o.output_language = 'vi' OR o.output_language LIKE 'vi-%' THEN 'vi' ELSE 'en' END;

UPDATE audience_template_variants v
SET footer_template_body = f.body
FROM audience_definitions a, preset_footers f
WHERE a.id = v.audience_id
  AND a.preset
  AND a.code IN ('operator', 'contributor')
  AND f.language = CASE WHEN v.language = 'vi' OR v.language LIKE 'vi-%' THEN 'vi' ELSE 'en' END;

DROP TABLE preset_footers;
