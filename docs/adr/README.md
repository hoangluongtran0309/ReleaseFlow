# Architecture decision records

Each record states one decision, the context that forced it, and its consequences. A
record is not rewritten once accepted: a later decision that changes it says so in its
own header, and this index shows the relation in both directions.

New records take the next number, use the same headings (`Context`, `Decision`,
`Consequences`), and are added to this table in the same pull request as the behavior
they describe.

| ADR | Decision | Status | Date | Relations |
| --- | --- | --- | --- | --- |
| [0001](0001-shared-schema-tenant-isolation.md) | Shared-schema tenant isolation | Accepted | 2026-09-09 | |
| [0002](0002-per-integration-webhook-credentials.md) | Per-integration webhook credentials | Accepted | 2026-09-10 | |
| [0003](0003-frontend-toolchain.md) | Tailwind, DaisyUI, and Alpine for server-rendered pages | Accepted | 2026-09-10 | Amended by [0028](0028-parity-ui-and-browser-tests.md) (browser tests) |
| [0004](0004-openai-classification-as-reviewed-suggestion.md) | OpenAI classification as a reviewed suggestion | Superseded | 2026-09-11 | Superseded by [0009](0009-automatic-ai-classification.md) |
| [0005](0005-immutable-release-note-snapshots.md) | Immutable Release Note snapshots | Accepted | 2026-09-11 | |
| [0006](0006-organization-roles-and-invitations.md) | Organization roles and invitations | Accepted | 2026-09-14 | |
| [0007](0007-ci-and-container-supply-chain.md) | CI gates and container supply chain | Accepted | 2026-09-14 | Amended by [0026](0026-deployment-observability.md) (health check on the management port) |
| [0008](0008-durable-change-processing.md) | Durable change processing and changed-file review triggers | Accepted | 2026-09-14 | |
| [0009](0009-automatic-ai-classification.md) | Automatic AI classification and neutral summaries | Accepted | 2026-09-14 | Supersedes [0004](0004-openai-classification-as-reviewed-suggestion.md); amended by [0035](0035-evidence-bound-notes-and-audience-relevance.md) (Project context, migration kind, limitations) |
| [0010](0010-release-review-lifecycle.md) | Release review lifecycle | Accepted | 2026-09-14 | |
| [0011](0011-audience-release-notes.md) | Audiences and a release note per audience | Accepted | 2026-09-15 | Amended by [0035](0035-evidence-bound-notes-and-audience-relevance.md) (a selection and headline per audience) and [0037](0037-security-changes-and-release-note-structure.md) (security and release-wide note structure) |
| [0012](0012-category-catalog.md) | Category catalog per Organization | Accepted | 2026-09-15 | |
| [0013](0013-context-sufficiency-and-duplicates.md) | Context sufficiency and possible duplicates | Accepted | 2026-09-15 | |
| [0014](0014-project-sensitive-paths.md) | Sensitive paths per Project | Accepted | 2026-09-15 | |
| [0015](0015-multilingual-release-notes.md) | Release notes in several languages | Accepted | 2026-09-15 | Amended by [0036](0036-translation-glossary.md) (a glossary for translations) |
| [0016](0016-integration-sources-and-history-import.md) | Integration sources and history import | Accepted | 2026-09-15 | |
| [0017](0017-gitlab-sources.md) | GitLab sources and provider seams | Accepted | 2026-09-16 | |
| [0018](0018-linear-sources.md) | Linear sources | Accepted | 2026-09-16 | |
| [0019](0019-jira-sources-and-linked-context.md) | Jira sources and linked context | Accepted | 2026-09-17 | |
| [0020](0020-automation-rules-and-runs.md) | Automation rules and runs | Accepted | 2026-09-19 | Amended by [0025](0025-teams-and-zendesk-actions.md) (Teams on scheduled triggers) |
| [0021](0021-scheduled-and-signed-automation-triggers.md) | Scheduled and signed automation triggers | Accepted | 2026-09-20 | |
| [0022](0022-public-changelog.md) | A public changelog | Accepted | 2026-09-20 | Amended by [0025](0025-teams-and-zendesk-actions.md) (Teams on scheduled triggers) |
| [0023](0023-scoped-csrf-exemptions.md) | CSRF exemptions are scoped, never switched off | Accepted | 2026-09-20 | |
| [0024](0024-notion-and-confluence-actions.md) | Notion and Confluence actions | Accepted | 2026-09-21 | |
| [0025](0025-teams-and-zendesk-actions.md) | Microsoft Teams and Zendesk actions | Accepted | 2026-09-21 | Amends [0020](0020-automation-rules-and-runs.md) and [0022](0022-public-changelog.md) |
| [0026](0026-deployment-observability.md) | Deployment observability | Accepted | 2026-09-21 | Amends [0007](0007-ci-and-container-supply-chain.md) |
| [0027](0027-ui-localization.md) | UI localization | Accepted | 2026-09-22 | Amended by [0034](0034-account-language-per-request.md) (the account's language read on each request) |
| [0028](0028-parity-ui-and-browser-tests.md) | A dashboard, error pages, and a browser test suite | Accepted | 2026-09-22 | Amends [0003](0003-frontend-toolchain.md) |
| [0029](0029-one-module-and-the-apache-licence.md) | One module, and the Apache licence | Accepted | 2026-09-22 | |
| [0030](0030-release-automation.md) | Release automation | Accepted | 2026-10-10 | Amends [CONTRIBUTING.md](../../CONTRIBUTING.md) |
| [0031](0031-quickstart-bundle.md) | Quickstart bundle | Accepted | 2026-10-02 | Builds on [0030](0030-release-automation.md) |
| [0032](0032-documentation-site.md) | Documentation site | Accepted | 2026-10-12 | Builds on [0007](0007-ci-and-container-supply-chain.md) and [0030](0030-release-automation.md) |
| [0033](0033-change-exclusion.md) | Excluding a change from releases | Accepted | 2026-10-17 | Builds on [0010](0010-release-review-lifecycle.md) |
| [0034](0034-account-language-per-request.md) | The account's interface language is read on each request | Accepted | 2026-10-23 | Amends [0027](0027-ui-localization.md) |
| [0035](0035-evidence-bound-notes-and-audience-relevance.md) | Notes bound to their evidence, and a selection per audience | Accepted | 2026-10-26 | Amends [0009](0009-automatic-ai-classification.md) and [0011](0011-audience-release-notes.md); amended by [0038](0038-configurable-ai-temperature.md) (a configurable temperature) |
| [0036](0036-translation-glossary.md) | An Organization glossary for translated notes | Accepted | 2026-10-27 | Amends [0015](0015-multilingual-release-notes.md); refers to [0035](0035-evidence-bound-notes-and-audience-relevance.md) |
| [0037](0037-security-changes-and-release-note-structure.md) | Security changes and release-wide note structure | Accepted | 2026-10-29 | Amends [0011](0011-audience-release-notes.md); refers to [0009](0009-automatic-ai-classification.md), [0010](0010-release-review-lifecycle.md), [0035](0035-evidence-bound-notes-and-audience-relevance.md), and [0036](0036-translation-glossary.md) |
| [0038](0038-configurable-ai-temperature.md) | A configurable AI temperature | Accepted | 2026-10-31 | Amends [0035](0035-evidence-bound-notes-and-audience-relevance.md) |

Dates are as each record states them; 0031 carries an earlier date than 0030, which it
builds on. Records 0029 and 0030 mention a slice plan (`feature-parity-slice-plan.md`, slice `S24`)
that was a working document and is not part of this repository.
