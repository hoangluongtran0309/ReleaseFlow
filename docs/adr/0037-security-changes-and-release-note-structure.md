# ADR-0037: Security changes and release-wide note structure

- Status: Proposed
- Date: 2026-10-29
- Amends: [ADR-0011](0011-audience-release-notes.md), for the digest sections,
  audience templates, template snapshots, and presets
- Refers to: [ADR-0009](0009-automatic-ai-classification.md), for the one AI
  classification request; [ADR-0010](0010-release-review-lifecycle.md), for release
  decisions; [ADR-0035](0035-evidence-bound-notes-and-audience-relevance.md), for
  limitations and audience relevance; and
  [ADR-0036](0036-translation-glossary.md), whose glossary behavior is unchanged

## Context

A release note still makes an upgrader assemble the release-wide story from individual
items ([#17](https://github.com/hoangluongtran0309/ReleaseFlow/issues/17)):

- dbvault v0.2.0 put #25, which derives a different key for every target's stored
  password, under Bug Fixes. A security fix is still a fix, so a Security category in
  the Fix group would leave it there.
- dbvault v0.3.0 put "CSRF protection was removed from the API" at the end of #34's
  breaking headline. The change is both breaking and security-relevant, but neither fact
  should hide the other or make the item appear twice.
- dbvault v1.0.0 warned that a breaking change required action without linking #47's
  upgrade guide, although the guide shipped in the same release.
- A note has no compare link, release link, installation line, or place for other
  release-wide information.
- ADR-0035 added `limitations`, but a custom template written before it will not show the
  field. The dbvault v1.0.0 limitation that server certificate validation is not yet
  enforced except for MongoDB was consequently buried in implementation detail.

Security is orthogonal to category, as breaking already is. A fix remains in the Fix
category for classification and filtering, while an upgrader needs to find it under a
security heading. Release-wide links likewise do not belong in the template that renders
one change: they need a template rendered once for the release.

This slice touches the paid AI request for one reason only: one security field. The
breaking rules, dependency-update rule, narratives, Project context, and glossary must
not drift while the dbvault request fixtures are recorded again.

## Decision

### Security is a flag, not a category

- A change gains a boolean **security flag** beside `breaking`. It does not add a category
  or category group, and it does not change the category snapshot a change keeps.
- Deterministic rules set the flag from evidence that is unambiguous without an AI:
  - a label equal, ignoring case and surrounding whitespace, to `security`,
    `security-fix`, `security fix`, or `vulnerability`;
  - a CVE identifier in the title, description, or a label, matched case-insensitively as
    `CVE-` followed by four digits, a hyphen, and at least four digits.

  The match is recorded with the other classification reasons. Broad words such as
  `auth`, `password`, or `encryption` do not set the flag: they remain sensitive-path or
  sensitive-keyword evidence because they do not by themselves say that the change is a
  security change.
- The AI response schema gains exactly one required boolean, `security_change`. The
  prompt asks whether the change changes a security property or remediates or introduces
  a vulnerability, including authentication, authorization, secret or credential
  protection, cryptography, request forgery, and transport protection. It must use only
  the change and Project context as evidence.
  - The rule result OR the AI proposal is stored. The AI can add security but cannot
    clear what a rule found, just as it cannot clear a breaking flag.
  - The instructions, strict schemas, provider mappings, parser tests, stubs, and dbvault
    recordings change together before the request is considered ready. A later finding
    about an answer is handled by normalization or filtering on the response side; it
    does not revise the request again in this slice.
  - Nothing changes the breaking rules, the dependency-update rule, narratives,
    temperature, Project context, or what reaches the glossary.
- A security change always needs human review. Database constraints enforce that it can
  leave review only with a recorded reviewer, as they do for a breaking change.
  - Change Inbox review and
    `POST /api/projects/{projectId}/changes/{changeId}/review` gain `security` beside
    `category` and `breaking`.
  - A release decision and
    `PUT /api/projects/{projectId}/releases/{releaseId}/changes/{changeId}/decision` gain
    the same field. `APPROVE` must match the current category, breaking flag, and security
    flag; `EDIT` may correct any of the three. Both still review the change through
    `ChangeReviewService`.
  - The pages always submit an explicit value. For API compatibility, a caller that
    omits the new field confirms or edits the current security value; explicit `false`
    clears it. Changing security makes the classification source `HUMAN`, like changing
    category or breaking.
  - Change, review, release-decision, and release responses add the security value without
    removing or changing an existing field.
- Changes recorded before the migration start with `security = false` and are not sent to
  the AI or reclassified. A person may mark one security in either review path.

### One security section, without duplication

- A digest orders its sections as Breaking, **🔒 Security**, Features, Fixes,
  Performance, Documentation, Maintenance, and Other. The Security title, its singular
  and plural count labels, the marker described below, and the empty-note wording come
  from the English and Vietnamese release-note bundles. The Vietnamese section is
  **🔒 Bảo mật**.
- Placement is exclusive:
  - every breaking change goes in Breaking;
  - every security change that is not breaking goes in Security;
  - every other change goes in its category-group section.

  A change that is both breaking and security therefore appears once, in Breaking. The
  digest adds a localized `🔒 Security` marker to that item independently of the audience's
  item template, so a custom template cannot hide the second fact.
- Counts describe the sections the note actually shows. A breaking-and-security change
  counts once, as breaking; a non-breaking security change counts once, as security. The
  What's New overview includes the Security count when that section is non-empty and
  never presents a double-counted total.
- ADR-0035's relevance selection is extended for the audience codes `operator`,
  `contributor`, and `support`: a security change is always included, and `none` reads as
  `brief`, as it already does for a breaking change. A security-only change may still be
  omitted from an `end_user` or another custom audience when that audience's reviewed
  relevance is `none`. A breaking-and-security change remains in every audience because
  it is breaking.
- Security is a classification fact, not text. It is not translated and never reaches
  DeepL. Only the section, count, and marker labels are localized.

### A previous release and an upgrade guide belong to the release

- A release gains:
  - an optional `upgradeGuideUrl`, an absolute HTTP or HTTPS URL of at most 2,000
    characters with no user information; query strings and fragments are allowed;
  - an optional `previousReleaseId`, which must name a published release of the same
    Project.
- Creating a release without `previousReleaseId` snapshots the Project's most recently
  published release by publication time. The first release has none. A person may choose
  another published release or explicitly clear the field while the release is a draft;
  the choice does not move when another release is later published.
- The create and draft-edit pages expose both fields. The existing release create and
  update request bodies add them, and release responses add `upgradeGuideUrl`,
  `previousReleaseId`, and `previousVersion`. An older client that omits the new fields
  gets the default on create and leaves both values unchanged on update; an explicit null
  clears one. No endpoint or existing field is removed or renamed.
- When a release has breaking changes and an upgrade guide, the breaking warning links
  its localized call to action to that URL. Without one, the warning keeps its current
  unlinked form.

### One footer template per audience

- An audience gains a **footer template**, separate from the item template. Its main
  template follows the Organization's output language, and each audience language
  variant has its own footer beside its item template.
- A footer is Mustache in the same standards mode as an item template, with strict
  sections and no escaping. It is rendered once, after the last digest section (or after
  the nothing-for-this-audience line), and may use only:
  - `version`;
  - `previousVersion`, empty when the release has no previous release;
  - `compareUrl`;
  - `upgradeGuideUrl`;
  - `releaseUrl`.

  Empty strings are false, so a footer can use sections such as
  `{{#compareUrl}}...{{/compareUrl}}`. A blank footer is valid and adds nothing.
- Footer validation compiles and renders sample values on create, update, language-
  variant update, reset, and preview, and rejects an unknown variable with the existing
  `template_invalid` contract. Item variables are not visible to a footer, and footer
  variables are not added to an item template.
- The audience REST bodies and views add `footerTemplateBody` beside `templateBody`; the
  preview request may add it and renders the sample item followed by the sample footer.
  Omission preserves an existing footer and means blank when a custom audience is
  created, so callers written for 1.0 keep working.
- Approval snapshots both templates on every audience note. Automatic notes rendered
  again after approval use those snapshots; a manual note remains manual; publication
  freezes the rendered footer with the rest of the note.

### Release and compare links come from one code host

- Link construction is deterministic and makes no provider call. It considers GitHub and
  GitLab sources only; Linear and Jira sources neither provide a link nor make a sole code
  host ambiguous. With no code-host source or more than one, `compareUrl` and `releaseUrl`
  are empty.
- With exactly one code-host source, the stored, validated source coordinates produce:

  | Source | `compareUrl` | `releaseUrl` |
  | --- | --- | --- |
  | GitHub | `https://github.com/{owner}/{repository}/compare/{previousVersion}...{version}` | `https://github.com/{owner}/{repository}/releases/tag/{version}` |
  | GitLab | `{origin}/{projectPath}/-/compare/{previousVersion}...{version}` | `{origin}/{projectPath}/-/releases/{version}` |

  Each path value is encoded as a path segment while the slashes of a GitLab project path
  stay separators. Versions are used exactly as ReleaseFlow records them; it does not add
  or remove a `v`. `compareUrl` is empty without a previous version, while `releaseUrl`
  still names the current version.
- The operator and contributor preset footers, in English and Vietnamese, conditionally
  show three separately labelled lines: **Install** links the version to `releaseUrl`,
  **Compare** links `previousVersion...version` to `compareUrl`, and **Upgrade guide**
  links a read-before-upgrading call to `upgradeGuideUrl`. The Vietnamese labels are
  **Cài đặt**, **So sánh**, and **Hướng dẫn nâng cấp**. A line disappears when its URL is
  empty. The end-user preset footer is blank; support is blank by default too. Resetting
  a preset adopts the shipped footer for every configured note language.
- Existing operator and contributor preset audiences receive the new footer in each of
  their languages because no footer existed to overwrite. Existing custom audiences get
  a blank footer. An administrator can change either afterwards.

### Limitations stay visible in upgrader-facing item templates

- The operator, contributor, DBA-style, and support preset or regression templates print
  `limitations` as its own labelled line, not as part of Detail or Implementation. The
  labels are **Limitations** in English and **Giới hạn** in Vietnamese. End-user remains
  concise and does not print it by default.
- `dba-style` describes an upgrader-facing custom/regression template; it is not a fifth
  seeded audience code. ADR-0035's four shipped audience codes remain unchanged, so this
  decision does not otherwise change the audiences in an AI request.
- The release-note guide and its Vietnamese translation carry an upgrade note for custom
  audience templates. It shows how to add the localized `{{#limitations}}` item line and
  how to populate the new footer with conditional release, compare, and upgrade-guide
  links. Existing custom item templates are never rewritten silently.
- `limitations` keeps ADR-0035's meaning, filtering, human-edit precedence, translation,
  and glossary behavior. This decision changes where presets print it, not what the AI is
  asked to write there.

## Consequences

- A security fix remains a Fix everywhere classification is used, but an upgrader finds
  it under 🔒 Security. A removal of a protection can be both breaking and security
  without being duplicated or hidden.
- Every security classification costs a human decision. Rules and AI may raise the flag;
  only a recorded review may confirm or clear it.
- The paid classification request changes once, and every dbvault request fixture has to
  be recorded again for the new required boolean. Later replay findings in this slice
  cannot be fixed by another prompt or schema edit.
- A note can link the exact comparison, release, and upgrade guide without putting
  release-wide values into every item. The provider URLs may not exist until their tags
  or releases do; rendering remains offline and deterministic.
- A Project with several code hosts gets no guessed provider link. A Project with one code
  host and any number of tracker sources still gets one.
- Existing published notes stay immutable. Older changes are not retroactively called
  security, and custom audiences keep their item templates and start with no footer until
  an administrator follows the upgrade note.
- The REST API only adds request and response fields, so 1.1.0 remains a minor release.
- The glossary, its versions, translation hashes, and caches are unchanged. Per-language
  footer labels come from the audience template itself, while versions and URLs are never
  sent for translation.
