#!/usr/bin/env bash
# Reads and writes CHANGELOG.md for a release, so that scripts/release.sh, which prepares
# a release, and the Release workflow, which publishes it, agree on what an entry is.
#
#   changelog.sh section <version|Unreleased> [file]
#       Prints the entry for <version> without its heading, and fails if there is none or
#       it is empty. A released entry must be headed `## [<version>] - YYYY-MM-DD`.
#   changelog.sh stamp <version> <date> <repository-url> [file]
#       Turns `## [Unreleased]` into `## [<version>] - <date>`, opens an empty
#       `## [Unreleased]` above it, and points the link references at the new tag.
#
# [file] defaults to CHANGELOG.md; `section` reads standard input when it is `-`.
# Only POSIX awk is used, so it runs the same on Linux and macOS.
set -euo pipefail

readonly SEMVER='^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(-[0-9A-Za-z.-]+)?$'

usage() {
  sed -n '4,12p' "$0" | sed 's/^# \{0,1\}//' >&2
  exit 2
}

fail() {
  echo "changelog.sh: $*" >&2
  exit 1
}

# Prints the lines of one entry, trimmed of blank lines at either end.
section() {
  local version="$1" file="$2"
  awk -v version="${version}" '
    function is_heading(line) { return line ~ /^## / }
    function is_link_reference(line) { return line ~ /^\[[^]]+\]: / }
    function is_dated(rest) {
      return rest ~ /^ - [0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]$/
    }
    !found && is_heading($0) {
      prefix = "## [" version "]"
      if (version == "Unreleased" && $0 == prefix) { found = 1; next }
      if (version != "Unreleased" && substr($0, 1, length(prefix)) == prefix \
          && is_dated(substr($0, length(prefix) + 1))) { found = 1; next }
    }
    found && (is_heading($0) || is_link_reference($0)) { exit }
    found {
      lines[++count] = $0
      if ($0 !~ /^[[:space:]]*$/) { if (!first) first = count; last = count }
    }
    END {
      if (!found || !first) exit 1
      for (i = first; i <= last; i++) print lines[i]
    }
  ' "${file}"
}

stamp() {
  local version="$1" date="$2" repository_url="$3" file="$4"

  [[ "${version}" =~ ${SEMVER} ]] || fail "${version} is not a semantic version."
  [[ "${date}" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]] || fail "${date} is not a YYYY-MM-DD date."
  [[ "${repository_url}" =~ ^https://[^[:space:]]+[^/]$ ]] \
    || fail "${repository_url} is not an https URL without a trailing slash."
  [ -f "${file}" ] || fail "${file} does not exist."

  grep -qxF '## [Unreleased]' "${file}" || fail "${file} has no '## [Unreleased]' heading."
  if grep -qF "## [${version}]" "${file}"; then
    fail "${file} already has an entry for ${version}."
  fi
  section Unreleased "${file}" >/dev/null \
    || fail "nothing is listed under [Unreleased] in ${file}, so there is nothing to release."

  local stamped
  stamped="$(mktemp)"
  # Link references are written newest first, so the new ones go above the first
  # version's. The old [Unreleased] reference is dropped and written again.
  awk -v version="${version}" -v date="${date}" -v url="${repository_url}" '
    function references() {
      print "[Unreleased]: " url "/compare/v" version "...HEAD"
      print "[" version "]: " url "/releases/tag/v" version
      written = 1
    }
    $0 == "## [Unreleased]" && !stamped {
      print
      print ""
      print "## [" version "] - " date
      stamped = 1
      next
    }
    /^\[Unreleased\]: / { next }
    !written && /^\[[0-9]+\.[0-9]+\.[0-9]+[^]]*\]: / { references() }
    { print; previous = $0 }
    END {
      if (!written) {
        if (previous != "") print ""
        references()
      }
    }
  ' "${file}" > "${stamped}"
  # cat rather than mv, so the file keeps its permissions and any hard link.
  cat "${stamped}" > "${file}"
  rm -f "${stamped}"
}

command="${1:-}"
case "${command}" in
  section)
    [ "$#" -ge 2 ] && [ "$#" -le 3 ] || usage
    file="${3:-CHANGELOG.md}"
    if [ "${file}" = "-" ]; then file=/dev/stdin; fi
    section "$2" "${file}" || fail "no entry for $2 with any content in ${3:-CHANGELOG.md}."
    ;;
  stamp)
    [ "$#" -ge 4 ] && [ "$#" -le 5 ] || usage
    stamp "$2" "$3" "$4" "${5:-CHANGELOG.md}"
    ;;
  *)
    usage
    ;;
esac
