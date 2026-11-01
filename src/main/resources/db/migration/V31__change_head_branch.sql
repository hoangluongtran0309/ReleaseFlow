-- The branch a pull or merge request was merged from (ADR-0035), so a dependency update
-- is recognized by its branch as well as its author. Only a code host's change has one;
-- changes recorded before keep none.
ALTER TABLE changes
    ADD COLUMN head_branch VARCHAR(255),
    ADD CONSTRAINT changes_head_branch_code_host CHECK (
        head_branch IS NULL OR source_type IS NULL OR source_type IN ('GITHUB', 'GITLAB')
    );
