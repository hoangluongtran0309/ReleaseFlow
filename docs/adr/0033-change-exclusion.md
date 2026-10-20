# ADR-0033: Excluding a change from releases

- Status: Accepted
- Date: 2026-10-17
- Builds on: [ADR-0010](0010-release-review-lifecycle.md), whose rejection returns a change
  to the available ones

## Context

A change that finished processing is offered to every release until one holds it.
Rejecting it during review, or discarding the draft, makes it available again, so
**Add all available** offers it to the next release and the one after. Some changes
never ship: a tracker issue that duplicates the pull request that did the work, a fix
and the revert that undid it, an operations task with nothing to tell readers. The only
way to keep them out would be a placeholder draft holding them, which shows in the
release list and its counts, and which anybody could approve and publish.

## Decision

- An administrator can **exclude a change from releases**, with a reason of at most
  500 characters. The change records who excluded it, when, and why, as a review
  records its reviewer. The decision can be taken back, and that removes the record:
  the change is offered again.
- Excluding deletes nothing and settles nothing. The change keeps its classification
  and its review state, stays in the Change Inbox with a badge and the reason, and can
  still be reviewed. Only the overview's count of changes needing review leaves it out,
  since nothing waits for its review.
- An excluded change is not available: **Add all available** skips it, and adding it
  by name answers `409 change_excluded`. A change in any release, published ones
  included, cannot be excluded (`409 change_in_release`): it is removed from a draft or
  rejected in review first. A `PROCESSING` change cannot be excluded either.
- The database holds the rule as well. A trigger on `release_changes` reads the change
  `FOR SHARE` and refuses an excluded one, and an exclusion locks the change row before
  it checks the releases, so the two never race past each other.
- `POST` and `DELETE /api/projects/{projectId}/changes/{changeId}/exclusion` and the
  Inbox forms do the same thing, for administrators only. The Inbox can show only the
  excluded changes (`release=excluded`).

## Consequences

- No placeholder release is needed, and nothing that will never ship can be published
  by mistake.
- One exclusion per change is kept, like one review: taking it back and excluding
  again records the newer decision only.
- A team that wants a change back in a release includes it again first; ReleaseFlow
  never does it on its own.
