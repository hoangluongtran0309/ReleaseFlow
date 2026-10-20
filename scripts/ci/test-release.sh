#!/usr/bin/env bash
# Runs scripts/changelog.sh and scripts/release.sh through a whole release against a
# throwaway repository and a local bare remote, with a stand-in for gh: a first release,
# release candidates, finishing it, the refusals, and a hotfix. Nothing reaches GitHub.
#
#   ./scripts/ci/test-release.sh
set -euo pipefail

repository_root="$(cd "$(dirname "$0")/../.." && pwd)"
work="$(mktemp -d)"
trap 'rm -rf "${work}"' EXIT

fail() { echo "not ok - $*" >&2; exit 1; }
pass() { echo "ok - $*"; }

# Adds a Fixed entry under [Unreleased] in file $1, as a contributor would.
add_unreleased_fix() {
  awk -v fix="$2" '{ print } $0 == "## [Unreleased]" { print ""; print "### Fixed"; print ""; print "- " fix }' \
    "$1" > "$1.new"
  mv "$1.new" "$1"
}

readonly URL="https://github.com/example/ReleaseFlow"
today="$(date -u +%Y-%m-%d)"

# ---- changelog.sh on its own -------------------------------------------------------------

changelog="${work}/CHANGELOG.md"
cat > "${changelog}" <<'EOF'
# Changelog

Intro.

## [Unreleased]

The first release.

### Added

- Everything.
EOF

"${repository_root}/scripts/changelog.sh" stamp 1.0.0 2026-10-10 "${URL}" "${changelog}"
expected="$(cat <<EOF
# Changelog

Intro.

## [Unreleased]

## [1.0.0] - 2026-10-10

The first release.

### Added

- Everything.

[Unreleased]: ${URL}/compare/v1.0.0...HEAD
[1.0.0]: ${URL}/releases/tag/v1.0.0
EOF
)"
[ "$(cat "${changelog}")" = "${expected}" ] \
  || { diff -u <(printf '%s\n' "${expected}") "${changelog}" >&2 || true; fail "stamp writes the first release"; }
pass "stamp writes the first release and its link references"

if "${repository_root}/scripts/changelog.sh" stamp 1.0.1 2026-10-11 "${URL}" "${changelog}" 2>/dev/null; then
  fail "stamp accepts an empty [Unreleased]"
fi
pass "stamp refuses an empty [Unreleased]"

add_unreleased_fix "${changelog}" "A bug."
if "${repository_root}/scripts/changelog.sh" stamp 1.0.0 2026-10-11 "${URL}" "${changelog}" 2>/dev/null; then
  fail "stamp accepts a version that already has an entry"
fi
pass "stamp refuses a version that already has an entry"

"${repository_root}/scripts/changelog.sh" stamp 1.0.1 2026-10-11 "${URL}" "${changelog}"
expected_links="$(cat <<EOF
[Unreleased]: ${URL}/compare/v1.0.1...HEAD
[1.0.1]: ${URL}/releases/tag/v1.0.1
[1.0.0]: ${URL}/releases/tag/v1.0.0
EOF
)"
[ "$(grep '^\[' "${changelog}")" = "${expected_links}" ] || fail "a second stamp orders the links newest first"
[ "$(grep -c '^## \[Unreleased\]$' "${changelog}")" = 1 ] || fail "a second stamp leaves one [Unreleased]"
pass "a second stamp keeps one [Unreleased] and orders the links newest first"

[ "$("${repository_root}/scripts/changelog.sh" section 1.0.1 "${changelog}")" = "$(printf '### Fixed\n\n- A bug.')" ] \
  || fail "section prints the entry without its heading"
[ "$("${repository_root}/scripts/changelog.sh" section 1.0.0 - < "${changelog}" | head -n 1)" = "The first release." ] \
  || fail "section reads standard input"
pass "section prints one entry, from a file or standard input"

if "${repository_root}/scripts/changelog.sh" section 2.0.0 "${changelog}" 2>/dev/null; then
  fail "section finds a version that has no entry"
fi
if "${repository_root}/scripts/changelog.sh" section Unreleased "${changelog}" 2>/dev/null; then
  fail "section finds content in an empty [Unreleased]"
fi
printf '## [3.0.0]\n\n- Undated.\n' > "${work}/undated.md"
if "${repository_root}/scripts/changelog.sh" section 3.0.0 "${work}/undated.md" 2>/dev/null; then
  fail "section accepts an entry without a date"
fi
pass "section refuses a missing, empty, or undated entry"

# ---- release.sh against a bare remote ------------------------------------------------------

export GIT_AUTHOR_NAME=test GIT_AUTHOR_EMAIL=test@example.com
export GIT_COMMITTER_NAME=test GIT_COMMITTER_EMAIL=test@example.com
export GIT_CONFIG_GLOBAL=/dev/null GIT_CONFIG_NOSYSTEM=1

# gh records what it was asked and claims to be signed in.
mkdir -p "${work}/bin"
cat > "${work}/bin/gh" <<EOF
#!/usr/bin/env bash
printf '%s\n' "\$*" >> "${work}/gh.log"
EOF
chmod +x "${work}/bin/gh"
export PATH="${work}/bin:${PATH}"

git init --quiet --bare --initial-branch=main "${work}/remote.git"
git init --quiet --initial-branch=main "${work}/checkout"
cd "${work}/checkout"
# The remote is named as GitHub would name it, which release.sh turns into links, and
# rewritten to the bare repository whenever git actually talks to it.
git config "url.${work}/remote.git.insteadOf" "git@github.com:example/ReleaseFlow.git"
git remote add origin "git@github.com:example/ReleaseFlow.git"
mkdir scripts
cp "${repository_root}/scripts/changelog.sh" "${repository_root}/scripts/release.sh" scripts/
printf '# Changelog\n\n## [Unreleased]\n\n### Added\n\n- The first release.\n' > CHANGELOG.md
git add . && git commit --quiet --message "chore: start"
git push --quiet origin main
git push --quiet origin main:develop

output="$(scripts/release.sh start 1.0.0 --dry-run)"
grep -qF "(dry run) git push" <<<"${output}" || fail "a dry run prints the push"
grep -qF "+## [1.0.0] - ${today}" <<<"${output}" || fail "a dry run shows the CHANGELOG change"
[ "$(git symbolic-ref --short HEAD)" = main ] || fail "a dry run switches branch"
git diff --quiet || fail "a dry run changes the working tree"
[ ! -s "${work}/gh.log" ] || fail "a dry run calls gh"
pass "start --dry-run shows the change and makes none"

scripts/release.sh start 1.0.0 >/dev/null
git rev-parse --quiet --verify refs/remotes/origin/release/1.0.0 >/dev/null || fail "start pushes release/1.0.0"
git show origin/release/1.0.0:CHANGELOG.md | grep -qxF "## [1.0.0] - ${today}" || fail "start dates the entry"
git show origin/release/1.0.0:CHANGELOG.md | grep -qxF "[1.0.0]: ${URL}/releases/tag/v1.0.0" \
  || fail "start links the entry to the GitHub repository"
[ "$(git log -1 --format=%s origin/release/1.0.0)" = "chore(release): prepare 1.0.0" ] || fail "start commits"
grep -q -- "pr create --base main --head release/1.0.0" "${work}/gh.log" || fail "start opens the pull request"
pass "start branches, dates the entry, commits, pushes, and opens the pull request"

scripts/release.sh rc 1.0.0 >/dev/null
scripts/release.sh rc 1.0.0 >/dev/null
[ "$(git ls-remote --tags origin 'v1.0.0-rc.*' | grep -vc '\^{}')" = 2 ] || fail "rc pushes numbered candidates"
[ "$(git rev-parse 'v1.0.0-rc.2^{commit}')" = "$(git rev-parse origin/release/1.0.0)" ] \
  || fail "rc tags the release branch"
pass "rc tags the release branch as v1.0.0-rc.1, then rc.2"

if scripts/release.sh finish 1.0.0 >/dev/null 2>&1; then
  fail "finish runs before the release is merged"
fi
pass "finish refuses a release that is not on main yet"

# The pull request is merged, with a merge commit.
git switch --quiet main
git merge --quiet --no-ff --no-edit origin/release/1.0.0
git push --quiet origin main

scripts/release.sh finish 1.0.0 >/dev/null
[ "$(git ls-remote origin 'refs/tags/v1.0.0^{}' | cut -f1)" = "$(git rev-parse origin/main)" ] \
  || fail "finish tags main"
git merge-base --is-ancestor origin/main origin/chore/back-merge-1.0.0 || fail "finish merges main back"
grep -q -- "pr create --base develop --head chore/back-merge-1.0.0" "${work}/gh.log" \
  || fail "finish opens the back-merge pull request"
pass "finish tags main as v1.0.0 and opens the back-merge"

git switch --quiet main
if scripts/release.sh start 1.0.0 >/dev/null 2>&1; then fail "start accepts a version already tagged"; fi
if scripts/release.sh start 0.9.0 >/dev/null 2>&1; then fail "start accepts an older version"; fi
if scripts/release.sh start 1.1 >/dev/null 2>&1; then fail "start accepts a version that is not X.Y.Z"; fi
if scripts/release.sh rc 1.0.0 >/dev/null 2>&1; then fail "rc accepts a released version"; fi
pass "start and rc refuse a released, older, or malformed version"

# A hotfix starts from main, where [Unreleased] is empty until the fix is committed.
output="$(scripts/release.sh start 1.0.1 --hotfix)"
[ "$(git symbolic-ref --short HEAD)" = hotfix/1.0.1 ] || fail "a hotfix branches from main"
grep -qF "Commit the fix" <<<"${output}" || fail "a hotfix asks for the fix first"
add_unreleased_fix CHANGELOG.md "The bug."
git commit --quiet --all --message "fix: the bug"
scripts/release.sh start 1.0.1 --hotfix >/dev/null
git show origin/hotfix/1.0.1:CHANGELOG.md | grep -qxF "## [1.0.1] - ${today}" || fail "a hotfix dates its entry"
pass "a hotfix branches from main, waits for the fix, then dates its entry"

echo "All release checks passed."
