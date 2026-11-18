#!/usr/bin/env bash
# Fails on any high or critical advisory in one npm package directory's dependencies, except
# those listed below for that directory. An exception is an advisory no release fixes yet,
# with the reason it cannot reach what ReleaseFlow ships. Once npm stops reporting one for
# its directory, this fails too, so the exception is removed rather than left to hide a later
# advisory of the same ID.
#
#   ./scripts/ci/audit-npm.sh .          the application's own: the CSS build
#   ./scripts/ci/audit-npm.sh website    the documentation site's
set -euo pipefail

# Each exception is the directory it applies to, as the repository names it, and the advisory.
readonly ALLOWED=(
  # GHSA-fc5q-39xc-vjpf, picomatch ReDoS through repeated extglobs: every version up to 4.0.7,
  # the latest, and no release fixes it. picomatch runs only at build time, on ReleaseFlow's
  # own fixed glob patterns: through postcss-cli -> tinyglobby -> picomatch in the CSS build,
  # and through Docusaurus (micromatch, chokidar) in the site's build. It ships in neither the
  # JAR nor the image, the site is static, and it never sees request input.
  ". GHSA-fc5q-39xc-vjpf"
  "website GHSA-fc5q-39xc-vjpf"
)

[ "$#" -eq 1 ] || { echo "usage: $0 <package directory>, such as . or website" >&2; exit 2; }
root="$(cd "$(dirname "$0")/../.." && pwd)"
cd "${root}/$1"
[ -f package.json ] || { echo "$1 has no package.json" >&2; exit 2; }
# The directory as the list names it: "." for the repository root, "website" for the site.
directory="$(realpath --relative-to="${root}" .)"

# npm audit exits non-zero whenever it finds anything; the JSON says what.
report="$(npm audit --json || true)"
found="$(printf '%s' "${report}" | node -e '
  let input = "";
  process.stdin.on("data", (chunk) => (input += chunk)).on("end", () => {
    const audit = JSON.parse(input);
    if (audit.error) {
      console.error(audit.error.summary);
      process.exit(2);
    }
    const advisories = new Set();
    for (const vulnerability of Object.values(audit.vulnerabilities ?? {})) {
      for (const via of vulnerability.via) {
        if (typeof via === "object" && ["high", "critical"].includes(via.severity)) {
          advisories.add([via.url.split("/").pop(), via.name, via.severity].join(" "));
        }
      }
    }
    console.log([...advisories].sort().join("\n"));
  });
')"

# The advisories this directory may report. Compared in the shell: piping the list into
# grep -q could cut printf off mid-write, which pipefail would read as "not listed".
allowed=()
for entry in "${ALLOWED[@]}"; do
  [ "${entry%% *}" = "${directory}" ] && allowed+=("${entry#* }")
done

listed() {
  local id
  for id in "${allowed[@]}"; do
    [ "${id}" = "$1" ] && return 0
  done
  return 1
}

failed=0
while read -r id package severity; do
  [ -n "${id}" ] || continue
  if listed "${id}"; then
    echo "ok - ${id} (${package}, ${severity}) in ${directory} is a listed exception."
  else
    echo "not ok - ${id} (${package}, ${severity}) in ${directory}; update the dependency, or list it with its reason."
    failed=1
  fi
done <<< "${found}"

for id in "${allowed[@]}"; do
  if ! grep -q "^${id} " <<< "${found}"; then
    echo "not ok - ${id} is no longer reported in ${directory}; remove its exception."
    failed=1
  fi
done

exit "${failed}"
