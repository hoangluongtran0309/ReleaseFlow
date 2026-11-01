-- An Organization's glossary (ADR-0036): terms kept as written in every language, and
-- fixed translations for one language pair, named by primary languages such as en and vi.
-- Uniqueness is exact, as DeepL treats "Image" and "image" as two entries.
CREATE TABLE glossary_kept_terms (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations (id),
    term VARCHAR(100) NOT NULL,
    created_by UUID NOT NULL,
    creator_name VARCHAR(120) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT glossary_kept_terms_creator_fk
        FOREIGN KEY (created_by, organization_id) REFERENCES app_users (id, organization_id),
    CONSTRAINT glossary_kept_terms_unique UNIQUE (organization_id, term),
    CONSTRAINT glossary_kept_terms_term_valid CHECK (
        term = BTRIM(term) AND term <> '' AND term !~ '[[:cntrl:]]'
    )
);

CREATE TABLE glossary_translations (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations (id),
    source_language VARCHAR(3) NOT NULL,
    target_language VARCHAR(3) NOT NULL,
    source_term VARCHAR(100) NOT NULL,
    target_term VARCHAR(100) NOT NULL,
    created_by UUID NOT NULL,
    creator_name VARCHAR(120) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT glossary_translations_creator_fk
        FOREIGN KEY (created_by, organization_id) REFERENCES app_users (id, organization_id),
    CONSTRAINT glossary_translations_unique UNIQUE (organization_id, source_language, target_language, source_term),
    CONSTRAINT glossary_translations_languages_valid CHECK (
        source_language ~ '^[a-z]{2,3}$' AND target_language ~ '^[a-z]{2,3}$' AND source_language <> target_language
    ),
    CONSTRAINT glossary_translations_terms_valid CHECK (
        source_term = BTRIM(source_term) AND source_term <> '' AND source_term !~ '[[:cntrl:]]'
        AND target_term = BTRIM(target_term) AND target_term <> '' AND target_term !~ '[[:cntrl:]]'
    )
);

-- The Organization's one DeepL glossary. Its row is also what a sync locks, so one sync
-- per Organization runs at a time.
CREATE TABLE translation_glossaries (
    organization_id UUID PRIMARY KEY REFERENCES organizations (id),
    deepl_glossary_id VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- One language pair's dictionary in DeepL: the version a translation asked for, the
-- version DeepL holds, and the bounded job that brings the two together.
CREATE TABLE glossary_syncs (
    organization_id UUID NOT NULL REFERENCES organizations (id),
    source_language VARCHAR(3) NOT NULL,
    target_language VARCHAR(3) NOT NULL,
    wanted_version VARCHAR(64) NOT NULL,
    synced_version VARCHAR(64),
    status VARCHAR(20) NOT NULL,
    attempts INTEGER NOT NULL,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    claimed_at TIMESTAMP WITH TIME ZONE,
    last_error VARCHAR(100),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (organization_id, source_language, target_language),
    CONSTRAINT glossary_syncs_status_known CHECK (status IN ('PENDING', 'RUNNING', 'SYNCED', 'UNSUPPORTED', 'FAILED')),
    CONSTRAINT glossary_syncs_attempts_non_negative CHECK (attempts >= 0),
    CONSTRAINT glossary_syncs_synced_has_version CHECK (status <> 'SYNCED' OR synced_version IS NOT NULL)
);

CREATE INDEX glossary_syncs_due_idx ON glossary_syncs (status, next_attempt_at);

-- A translation asks for one version of its pair's glossary, which is part of its input
-- hash, and records whether it used it. Jobs recorded before asked for none.
ALTER TABLE translation_jobs
    ADD COLUMN glossary_version VARCHAR(64) NOT NULL DEFAULT '',
    ADD COLUMN glossary_outcome VARCHAR(40),
    ADD CONSTRAINT translation_jobs_glossary_outcome_known CHECK (
        glossary_outcome IN ('applied', 'unsupported_language_pair', 'glossary_sync_failed', 'glossary_answer_untranslated')
    );

-- What a note's translations did with the glossary, as the release page shows it.
ALTER TABLE release_audience_notes
    ADD COLUMN glossary_status VARCHAR(20) NOT NULL DEFAULT 'NONE',
    ADD COLUMN glossary_reason VARCHAR(40),
    ADD CONSTRAINT release_audience_notes_glossary_status_known CHECK (
        glossary_status IN ('NONE', 'PENDING', 'APPLIED', 'NOT_APPLIED')
    ),
    ADD CONSTRAINT release_audience_notes_glossary_reason CHECK (
        (glossary_status = 'NOT_APPLIED') = (glossary_reason IS NOT NULL)
    );
