#!/usr/bin/env bash
# Cuts a ReleaseFlow release. The version is never edited into a file: it is the tag, and
# the Release workflow builds the tagged commit with -Drevision=<version>. See ADR-0030.
#
#   release.sh start <version> [--hotfix] [--dry-run]
#       Branches release/<version> from develop (hotfix/<version> from main), turns the
#       [Unreleased] entry of CHANGELOG.md into [<version>] - <today>, commits, pushes,
#       and opens the pull request into main.
#   release.sh rc <version> [--dry-run]
#       Tags the tip of release/<version> (or hotfix/<version>) as the next
#       v<version>-rc.<n> and pushes it, which publishes a pre-release for testing.
#   release.sh finish <version> [--dry-run]
#       Once that pull request is merged: tags main as v<version> and pushes the tag,
#       which publishes the release, then opens the pull request that merges main back
#       into develop.
#
# --dry-run checks everything and prints what would change, without changing anything.
# The remote is origin unless RELEASE_REMOTE says otherwise. Needs git, and gh signed in.
set -euo pipefail

readonly CORE_SEMVER='^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$'
readonly REMOTE="${RELEASE_REMOTE:-origin}"

dry_run=false
hotfix=false

usage() {
  sed -n '4,19p' "$0" | sed 's/^# \{0,1\}//' >&2
  exit 2
}

fail() {
  echo "release.sh: $*" >&2
  exit 1
}

step() {
  printf '\n==> %s\n' "$*"
}

# Runs a command that changes something, or only prints it on a dry run.
run() {
  if "${dry_run}"; then
    printf '    (dry run) %s\n' "$*"
  else
    "$@"
  fi
}

require_tools() {
  command -v git >/dev/null || fail "git is not installed."
  if ! "${dry_run}"; then
    command -v gh >/dev/null || fail "the GitHub CLI (gh) is not installed: https://cli.github.com"
    gh auth status >/dev/null 2>&1 || fail "gh is not signed in; run 'gh auth login'."
  fi
}

require_clean_tree() {
  if [ -n "$(git status --porcelain --untracked-files=normal)" ]; then
    fail "the working tree has uncommitted changes; commit or stash them first."
  fi
}

# https://github.com/<owner>/<repository>, whichever form the remote is written in.
repository_url() {
  local url
  # Read as written: get-url would apply any insteadOf rewrite.
  url="$(git config --get "remote.${REMOTE}.url")" || fail "there is no remote named ${REMOTE}."
  url="${url%.git}"
  case "${url}" in
    git@*:*) url="${url#git@}"; url="https://${url/://}" ;;
    ssh://git@*) url="https://${url#ssh://git@}" ;;
  esac
  printf '%s\n' "${url}"
}

# Succeeds when version $1 is greater than version $2; both are X.Y.Z.
version_greater() {
  local a1 a2 a3 b1 b2 b3
  IFS=. read -r a1 a2 a3 <<<"$1"
  IFS=. read -r b1 b2 b3 <<<"$2"
  [ "${a1}" -ne "${b1}" ] && { [ "${a1}" -gt "${b1}" ]; return; }
  [ "${a2}" -ne "${b2}" ] && { [ "${a2}" -gt "${b2}" ]; return; }
  [ "${a3}" -gt "${b3}" ]
}

# The newest published version, ignoring pre-releases, or nothing before the first one.
latest_release() {
  git tag --list 'v*' \
    | sed -n 's/^v\([0-9][0-9]*\.[0-9][0-9]*\.[0-9][0-9]*\)$/\1/p' \
    | sort -t. -k1,1n -k2,2n -k3,3n \
    | tail -n 1
}

fetch() {
  step "Fetching ${REMOTE}"
  git fetch --quiet --prune --tags "${REMOTE}"
}

check_version() {
  local version="$1" latest
  [[ "${version}" =~ ${CORE_SEMVER} ]] \
    || fail "${version} is not a version of the form MAJOR.MINOR.PATCH, such as 1.0.0."
  if git rev-parse --quiet --verify "refs/tags/v${version}" >/dev/null; then
    fail "v${version} is already tagged."
  fi
  latest="$(latest_release)"
  if [ -n "${latest}" ] && ! version_greater "${version}" "${latest}"; then
    fail "${version} is not newer than the latest release, ${latest}."
  fi
}

start() {
  local version="$1" base kind branch current url today preview
  if "${hotfix}"; then base=main; kind=hotfix; else base=develop; kind=release; fi
  branch="${kind}/${version}"

  require_tools
  require_clean_tree
  fetch
  check_version "${version}"
  url="$(repository_url)"
  today="$(date -u +%Y-%m-%d)"
  current="$(git symbolic-ref --quiet --short HEAD || true)"

  # Running it again on the branch it created picks up where it stopped: after a hotfix
  # is committed, or after a push that failed.
  if [ "${current}" = "${branch}" ]; then
    step "Continuing on ${branch}"
  else
    if git rev-parse --quiet --verify "refs/heads/${branch}" >/dev/null \
        || git rev-parse --quiet --verify "refs/remotes/${REMOTE}/${branch}" >/dev/null; then
      fail "${branch} already exists; switch to it and run this again, or delete it."
    fi
    if ! "${hotfix}" \
        && ! git show "${REMOTE}/${base}:CHANGELOG.md" | scripts/changelog.sh section Unreleased - >/dev/null 2>&1; then
      fail "nothing is listed under [Unreleased] in CHANGELOG.md on ${base}, so there is nothing to release."
    fi
    step "Branching ${branch} from ${REMOTE}/${base}"
    run git switch --quiet --create "${branch}" --no-track "${REMOTE}/${base}"
  fi

  # A dry run works on a copy, from the branch it would have created.
  preview=""
  if "${dry_run}"; then
    preview="$(mktemp)"
    if [ "${current}" = "${branch}" ]; then
      cp CHANGELOG.md "${preview}"
    else
      git show "${REMOTE}/${base}:CHANGELOG.md" > "${preview}"
    fi
  fi

  if scripts/changelog.sh section "${version}" "${preview:-CHANGELOG.md}" >/dev/null 2>&1; then
    step "CHANGELOG.md already has the ${version} entry"
  elif ! scripts/changelog.sh section Unreleased "${preview:-CHANGELOG.md}" >/dev/null 2>&1; then
    if [ -n "${preview}" ]; then rm -f "${preview}"; fi
    if "${hotfix}"; then
      echo
      echo "On ${branch}. Commit the fix with an entry under [Unreleased] in CHANGELOG.md,"
      echo "then run this again: scripts/release.sh start ${version} --hotfix"
      return
    fi
    fail "nothing is listed under [Unreleased] in CHANGELOG.md."
  elif [ -n "${preview}" ]; then
    step "Writing the ${version} entry of CHANGELOG.md, dated ${today}"
    cp "${preview}" "${preview}.stamped"
    scripts/changelog.sh stamp "${version}" "${today}" "${url}" "${preview}.stamped"
    diff -u "${preview}" "${preview}.stamped" | sed 's/^/    /' || true
    rm -f "${preview}.stamped"
    run git commit --message "chore(release): prepare ${version}" -- CHANGELOG.md
  else
    step "Writing the ${version} entry of CHANGELOG.md, dated ${today}"
    scripts/changelog.sh stamp "${version}" "${today}" "${url}" CHANGELOG.md
    run git commit --quiet --message "chore(release): prepare ${version}" -- CHANGELOG.md
  fi
  if [ -n "${preview}" ]; then rm -f "${preview}"; fi

  step "Pushing ${branch} and opening its pull request into main"
  run git push --quiet --set-upstream "${REMOTE}" "${branch}"
  run gh pr create --base main --head "${branch}" \
    --title "chore(release): prepare ${version}" \
    --body "$(pr_body_release "${version}")"

  echo
  echo "Next: let the checks pass, merge with a merge commit (not squash), then run"
  echo "    scripts/release.sh finish ${version}"
}

pr_body_release() {
  cat <<EOF
Prepares ReleaseFlow $1. The only change is the CHANGELOG entry; the version comes from
the tag, so nothing else needs editing.

- [ ] The entry reads well as release notes: the GitHub release is written from it.
- [ ] Merge with **a merge commit**, not squash or rebase, so main keeps develop's history.
- [ ] Then run \`scripts/release.sh finish $1\` to tag and publish.
EOF
}

rc() {
  local version="$1" branch="" candidate number last tag
  require_tools
  require_clean_tree
  fetch
  check_version "${version}"

  for candidate in "release/${version}" "hotfix/${version}"; do
    if git rev-parse --quiet --verify "refs/remotes/${REMOTE}/${candidate}" >/dev/null; then
      branch="${candidate}"
    fi
  done
  [ -n "${branch}" ] \
    || fail "neither release/${version} nor hotfix/${version} is on ${REMOTE}; run 'start' first."
  git show "${REMOTE}/${branch}:CHANGELOG.md" | scripts/changelog.sh section "${version}" - >/dev/null \
    || fail "CHANGELOG.md on ${branch} has no dated entry for ${version}."

  last="$(git tag --list "v${version}-rc.*" | sed -n 's/^.*-rc\.\([0-9][0-9]*\)$/\1/p' | sort -n | tail -n 1)"
  number=$(( ${last:-0} + 1 ))
  tag="v${version}-rc.${number}"

  step "Tagging ${REMOTE}/${branch} as ${tag}"
  run git tag --annotate "${tag}" --message "ReleaseFlow ${version}-rc.${number}" "${REMOTE}/${branch}"
  run git push --quiet "${REMOTE}" "refs/tags/${tag}"

  echo
  echo "The Release workflow now publishes ${tag} as a pre-release:"
  echo "    $(repository_url)/actions/workflows/release.yml"
}

finish() {
  local version="$1" url tag="v$1" back_merge="chore/back-merge-$1"
  require_tools
  require_clean_tree
  fetch
  check_version "${version}"
  url="$(repository_url)"

  git show "${REMOTE}/main:CHANGELOG.md" | scripts/changelog.sh section "${version}" - >/dev/null \
    || fail "CHANGELOG.md on main has no dated entry for ${version}; merge the release pull request first."

  step "Tagging ${REMOTE}/main ($(git rev-parse --short "${REMOTE}/main")) as ${tag}"
  run git tag --annotate "${tag}" --message "ReleaseFlow ${version}" "${REMOTE}/main"
  run git push --quiet "${REMOTE}" "refs/tags/${tag}"

  step "Merging main back into develop on ${back_merge}"
  if git rev-parse --quiet --verify "refs/heads/${back_merge}" >/dev/null; then
    fail "${back_merge} already exists; delete it, or finish the back-merge from it by hand."
  fi
  if git merge-base --is-ancestor "${REMOTE}/main" "${REMOTE}/develop"; then
    echo "    develop already contains main; there is nothing to merge back."
  else
    run git switch --quiet --create "${back_merge}" --no-track "${REMOTE}/develop"
    if ! run git merge --quiet --no-ff --no-edit \
        --message "chore: merge main back into develop after ${version}" "${REMOTE}/main"; then
      echo >&2
      echo "The merge stopped on a conflict, most likely in CHANGELOG.md. ${tag} is pushed" >&2
      echo "and publishing. Resolve the conflict, commit, then push ${back_merge} and open" >&2
      echo "its pull request into develop." >&2
      exit 1
    fi
    run git push --quiet --set-upstream "${REMOTE}" "${back_merge}"
    run gh pr create --base develop --head "${back_merge}" \
      --title "chore: merge main back into develop after ${version}" \
      --body "Brings the ${version} release commit back to develop. Merge with **a merge commit**, not squash, so the two branches keep one history."
  fi

  echo
  echo "${tag} is pushed. The Release workflow is publishing it:"
  echo "    ${url}/actions/workflows/release.yml"
  echo "When it is done: ${url}/releases/tag/${tag}"
}

main() {
  local command="${1:-}" version="${2:-}"
  [ -n "${command}" ] && [ -n "${version}" ] || usage
  shift 2
  while [ "$#" -gt 0 ]; do
    case "$1" in
      --dry-run) dry_run=true ;;
      --hotfix) hotfix=true ;;
      *) usage ;;
    esac
    shift
  done
  if "${hotfix}" && [ "${command}" != start ]; then usage; fi

  cd "$(git rev-parse --show-toplevel)"
  case "${command}" in
    start) start "${version}" ;;
    rc) rc "${version}" ;;
    finish) finish "${version}" ;;
    *) usage ;;
  esac
}

main "$@"
