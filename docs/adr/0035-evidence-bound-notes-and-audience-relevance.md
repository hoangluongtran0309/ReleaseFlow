# ADR-0035: Notes bound to their evidence, and a selection per audience

- Status: Accepted
- Date: 2026-10-26
- Amends: [ADR-0009](0009-automatic-ai-classification.md), for what the AI request sends
  and returns, and [ADR-0011](0011-audience-release-notes.md), for narratives, the
  digest, template variables, and presets

## Context

Notes published from dbvault, the project used to test ReleaseFlow end to end, state
things their evidence does not hold
([#15](https://github.com/hoangluongtran0309/ReleaseFlow/issues/15)):

- consequences nobody described: a CLI change "blocks startup" and asks readers to
  "restore service", or no longer "crashes" where it only reported an internal error;
- places in the product: a console language "chosen through your browser", where it is
  chosen with a link and the browser is only the fallback;
- another project's changelog: a Gradle step, quoted by a dependency update, offered to
  a Maven project;
- code a tracker issue never changed: a Linear issue "added a dependabot.yml file";
- a fact turned into reassurance: "No direct action is required", where the evidence says
  that existing passwords keep the old scheme until each target is next saved;
- remarks about the gap itself ("technical specifics are limited in this summary"), and
  an item whose whole text is "fix".

The prompt forbids adding facts only for narratives, and the model knows nothing of the
product beyond the pull request. `migration_step` is "what to do when upgrading", so it
covers both what must be done and what is merely advisable. Every change is sent as a
`pull_request`, a Jira or Linear issue included. Nothing marks text quoted from upstream.
A change without a summary falls back to its title, however short.

Separately, an audience changes only one sentence of a note
([#16](https://github.com/hoangluongtran0309/ReleaseFlow/issues/16)). Every change appears
in every audience's note, the bold line is always `whatChanged`, and the counts are the
same. dbvault v1.1.0's end-user note lists the pinning of container base images by digest,
followed by "No changes will be seen while using the product".

Both concern the one AI request per change and the digest that renders its answer, so
they are decided together and the response contract changes once.

## Decision

### What the AI is told

- **A Project has a context**: optional plain text of at most 2000 characters that
  administrators write and members can read. It says what the product is made of (what is
  a client, what is a server), where settings live, how people reach its features, and
  what stays in English by design. It is managed on its own page, linked from the
  Project's card like its sensitive paths, and through `GET` and
  `PUT /api/projects/{projectId}/context`.
  - Every AI request for one of the Project's changes sends it as `project_context`. The
    prompt treats it as facts about the product from an administrator, never as
    instructions about the answer.
  - Like a Project's sensitive paths ([ADR-0014](0014-project-sensitive-paths.md)), an
    edit applies to changes classified afterwards. Nothing is classified again.
  - It is also DeepL's `context` for the Project's translations. DeepL does not translate
    or bill it, and the translation input hash includes it, so an edit is picked up at
    the next approval and unchanged input is still never sent twice.
- **The request says what kind of item it describes.** The user message names it
  `change`, with `kind` set to `code_change` for a GitHub pull request or a GitLab merge
  request, and `tracker_issue` for a Jira or Linear issue. A tracker issue is a record of
  work: the prompt forbids naming files, code, or configuration it changed unless its own
  text does.
- **The prompt forbids, for every field and not only narratives:**
  - consequences the input does not state, such as a crash, a hang, an outage, a blocked
    startup, lost data, or a service to restore;
  - where something is found in the product, or how a person reaches it, unless the input
    or the Project context says so;
  - turning text the change quotes from another project (a dependency's release notes or
    changelog) into a change or an action of this one, unless the change's own words
    apply it;
  - writing about what the evidence lacks: a field the input says nothing about is empty.
- **`what_changed` is one sentence.** It is the bold line of every template that prints
  `whatChanged`, so it has to read as one.
- **A dependency update reaches the AI without what it quotes.** The first replays showed
  that the prompt rule alone does not stop a Spring Boot patch bump from getting the Gradle
  step its quoted release notes describe, once as a step "if it applies to you" and once
  as a breaking change.
  - A change is a dependency update when its author is `dependabot[bot]` or
    `renovate[bot]`, or its head branch starts with `dependabot/` or `renovate/`. A GitLab
    merge event names whoever merged, not the author, so the branch is what recognizes a
    Renovate merge request there. The head branch of a GitHub pull request or a GitLab merge
    request, delivered or imported, is therefore recorded on the change (`head_branch`,
    Flyway `V31`); [#19](https://github.com/hoangluongtran0309/ReleaseFlow/issues/19)
    builds on it to find issue keys.
  - For such a change, the description the AI receives drops `<details>` and
    `<blockquote>` elements, Markdown blockquotes, and the sections headed "Release notes",
    "Changelog", or "Commits". The change's own lines, what is bumped and from which version
    to which, stay. The change keeps its description as delivered; only the AI's copy is
    trimmed.
  - The user message says `dependency_update: true`, and the prompt adds that a dependency
    update is breaking only when the change's own words say so, and has a migration step
    only when its own words ask for one.

### What the AI returns

- `neutral_core` gains two fields:
  - **`migration_kind`**, one of:
    - `none`: nothing to do, and `migration_step` is empty;
    - `required`: existing installations stop working, or lose a protection, until the
      step is done, as when a change says that a renamed setting's old name is no longer
      read;
    - `conditional`: only some installations need it, and the step names the condition;
    - `recommended`: advisable, but nothing breaks without it.

    A step is `required` only when the evidence says so. A kind that does not match the
    step is normalized rather than rejected: with one request per change and no automatic
    retry ([ADR-0009](0009-automatic-ai-classification.md)), a rejected answer would cost
    the category, the summary, and every narrative. `none` with a step becomes
    `recommended`, and any other kind without a step becomes `none`. Either correction
    adds a `MIGRATION_KIND_MISMATCH` review trigger, whose detail is the kind the AI
    returned, so a person checks the step. A step whose kind is missing reads as
    `unspecified`, since nothing says how much it asks, and adds the same trigger; a
    step with an unknown kind becomes `recommended`. A missing kind with no step is
    simply `none`, since there is nothing to check.

    A step that only says nothing needs doing — "No immediate action is required", "No
    migration required", "Nothing to do" — is no step, so the parser drops it before the
    kind is read, and the kind becomes `none`. Read as a step, `none` would become
    `recommended`, the wrong direction. A fact written beside that verdict, such as that
    existing passwords move to the new key when edited, belongs in `limitations`, where
    the replays already put it. A step is kept when the verdict is qualified ("unless",
    "if", "for most installations"), follows something to do ("no further action"), or
    sits beside a clause that still asks something ("must", "should", or an imperative
    such as "Rename"). A kind other than `none` for a dropped step is corrected like any
    kind for no step.
  - **`limitations`**: only what the change's own text says it deliberately does not do
    yet, such as "certificate validation is enforced only for MongoDB", or that existing
    passwords keep the old scheme until each target is next saved. That is a fact the
    note states, not a step: the evidence asks nobody to re-save anything. Otherwise it is
    empty, and an answer that leaves it out reads as empty too.

    The replays still returned remarks about the input as limitations, so the parser
    drops, deterministically, every sentence of an AI's limitations that talks about the
    input rather than the change: "not provided", "not specified", "not detailed", "no
    information", and their kin in English and Vietnamese. It drops, the same way, every
    sentence that only states the change's scope: "does not affect…", "has no impact
    on…", "only affects the CI environment", "only the tests were changed", "is
    unchanged". Scope is what an audience's relevance decides (below), not something the
    change does not do yet. A sentence that names where a real limitation applies, such as
    "certificate validation is enforced only for MongoDB", is kept. A person's limitations
    are kept as written.

    Both filters work on the answer alone: the prompt and everything else sent to the
    provider are unchanged, so recorded replays of the request stay valid.
- **Summaries recorded before this decision have a fifth kind, `unspecified`**, when
  they have a migration step, and no limitations. Their steps were written as "what to do
  when upgrading" and some say "no direct action is required", so reading them as
  `required` would print that sentence under a required label. An `unspecified` step is
  rendered with the neutral label templates used before, and is never treated as
  required, conditional, or recommended. The AI never returns it; an answer whose step
  has no kind is read as one (above).
- Both fields are part of the neutral summary everywhere it goes: Edit summary,
  translation, and templates, which get `limitations`, `migrationKind`, and the sections
  `migrationRequired`, `migrationConditional`, `migrationRecommended`, and
  `migrationUnspecified`. Edit summary offers all five kinds. A request that names no
  kind, as API callers written before this decision send, records `unspecified` for a
  step and `none` without one.

### A change that cannot stand on its title

- A change whose title, after the Conventional Commit prefix and issue keys are removed,
  has fewer than three words needs a summary a person wrote, unless its description gave
  the AI something to work from. An AI summary is enough only when the description has at
  least ten words, not counting HTML comments such as a pull request template's hints,
  and the AI did not find the context insufficient. Otherwise the AI can only restate the
  title: the replays turned "fix", with no description, into "A fix was made in the
  codebase." With no summary at all, the title is all the note has.
- Approval answers `409 change_summary_required` and names those changes. The release page
  marks each one and links its Edit summary. Once a person writes the summary, approval
  goes ahead.
- The check is deterministic and never calls AI. A review decision is not enough to
  clear it, because a decision records a judgement, not the words a reader will see.

### A fixed temperature

- No classification request set a temperature, so every provider used its default of
  1.0, and the same change came back with different answers: one replayed change's
  migration kind was `recommended` once and `none` twice. Every provider is now asked at
  temperature `0`, the lowest each accepts, and the value is fixed rather than
  configurable.
- The trade-off: it narrows the variance but does not remove it, since none of the three
  providers guarantees the same answer twice even at `0`, so the replay still scores
  several samples. What is lost is variety of wording, which a summary of evidence does
  not need. A model that accepts no temperature but its default, as OpenAI's reasoning
  models do, refuses every request; each one then becomes a `CLASSIFIER_FALLBACK`, so such
  a model cannot be used for classification.

### A narrative per audience

- **A narrative becomes `{relevance, headline, body}`:**
  - `relevance` is `none` (this reader would neither notice nor need the change),
    `brief`, or `full`;
  - `headline` is that audience's bold line: one line, at most 20 words, without links;
  - `body` is what `narrative` was.
- The response schema requires one such object per audience code. A malformed narrative
  never makes the whole answer unusable ([ADR-0011](0011-audience-release-notes.md)):
  an unknown relevance reads as `full`, a headline over 20 words or over several lines is
  dropped, and a narrative with neither headline nor body is left out, as an empty one is
  today.
- `audience_narratives` stores the objects. A new migration rewrites each stored string
  as `{relevance: full, headline: "", body: <string>}`, so the code reads one shape and
  those changes render as they did.
- **The digest selects per audience.** It leaves out a change whose relevance for the
  audience is `none`, unless the change is breaking: a breaking change is in every note,
  and `none` on it reads as `brief`. A change with no narrative for the audience (no AI,
  a failure, an audience added later) is included, as it is now. Counts and the breaking
  warning are computed for each audience from what its note shows.
- An audience left with nothing gets a note all the same: title, release summary, and one
  line saying that nothing in the release concerns it. Approval still writes one note per
  audience and language, and publication still needs every note.
- **An automation action delivers such a note as it is.** Each action already names one
  audience, chosen by an administrator, and the note says plainly that nothing concerns
  that audience. Skipping it would need an outcome that is neither success nor failure,
  and a rule for whether it stops the actions behind it
  ([ADR-0020](0020-automation-rules-and-runs.md)); no option is added for it.
- Templates gain:
  - `headline`, the audience's headline, or `whatChanged` when it has none, so it always
    has a value;
  - `relevance`, `brief` or `full`;
  - `brief`, a section that is true for a brief change.

  `{{narrative}}` keeps meaning the body. Templates that print `whatChanged` keep doing
  so; resetting a preset adopts the new one.
- The presets use `**{{headline}}**`, keep their detail lines inside `{{^brief}}`, and
  print a required migration step whatever the relevance.
- Headline and body are translated like the other texts of a change, as `headline.<code>`
  and `narrative.<code>`. A relevance is not text and is never translated.
- **Edit summary** shows a relevance, a headline, and a body for each audience. The API
  accepts either an object or a plain string, which means `full` with no headline. A
  relevance a person chose stands like the rest of a person-written summary: the AI never
  replaces it.
- **Presets.** Each preset intent ends by saying when a change is `none` for it: for end
  users, build, CI, dependency, test, and internal changes they cannot see. A fourth
  preset, `support`, joins the ones registration seeds. It covers what customers may ask
  about, what to tell them, and whether a step is required or only advisable.
  Organizations that existed before can add an audience coded `support` and reset it to
  the preset; V13 is not changed.
- The release summary stays one text for every audience. A headline line per audience
  needs the whole release in view and belongs to
  [#23](https://github.com/hoangluongtran0309/ReleaseFlow/issues/23).

## Consequences

- Each change still costs exactly one AI request. The answer is longer: two summary
  fields, and three fields instead of one per audience.
- The Project context leaves the system for the AI provider and DeepL, as pull request
  text already does when they are configured.
- Notes of changes classified before this decision render as they did. Changes
  classified afterwards can leave some audiences' notes, even in templates nobody reset.
- A release whose changes have short titles and no summary cannot be approved until a
  person writes them. Without AI this is the only new step, and it applies only to those
  titles.
- A note can list no change at all, and every delivery that sends notes must accept one.
- A malformed migration kind costs a review, never the answer.
- A dependency update's quoted release notes never reach the AI, whatever they say;
  something they announce that does affect the project has to be in the change's own words,
  or added by a person.
- A short-titled change with an AI summary can still wait for a person when its
  description was empty or thin.
- An operator who chose an OpenAI reasoning model for classification has to choose another
  one.
- The PR is checked against the dbvault regression fixtures (render and full replay)
  before it is merged; [#18](https://github.com/hoangluongtran0309/ReleaseFlow/issues/18)
  brings that into CI.
