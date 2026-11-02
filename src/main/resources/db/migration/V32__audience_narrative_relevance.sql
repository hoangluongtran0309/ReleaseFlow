-- What a change says to each audience becomes a relevance, a headline, and a body
-- (ADR-0035). A narrative stored as a plain string becomes a full one with no headline,
-- so the code reads one shape and those changes render as they did.
UPDATE changes
SET audience_narratives = (
    SELECT jsonb_object_agg(
        narrative.key,
        CASE
            WHEN jsonb_typeof(narrative.value) = 'string'
                THEN jsonb_build_object('relevance', 'FULL', 'headline', '', 'body', narrative.value)
            ELSE narrative.value
        END
    )
    FROM jsonb_each(audience_narratives) AS narrative
)
WHERE audience_narratives IS NOT NULL
  AND EXISTS (
    SELECT 1 FROM jsonb_each(audience_narratives) AS narrative
    WHERE jsonb_typeof(narrative.value) = 'string'
  );
