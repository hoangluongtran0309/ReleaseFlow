#!/usr/bin/env bash
# Fails on any high or critical advisory in the documentation site's npm dependencies, except
# those listed below. An exception is an advisory no release fixes yet, with the reason it
# cannot reach the site. Once npm stops reporting one, this fails too, so the exception is
# removed rather than left to hide a later advisory of the same ID.
#
#   ./scripts/ci/audit-website.sh
set -euo pipefail

# GHSA-vfj7-8cjw-p6xm: braces <= 3.0.3 overflows the stack on deeply nested brace patterns,
# and no release fixes it. Only the build's own glob patterns reach braces, and what it builds
# is a static site, so nothing an attacker writes is ever expanded.
readonly ALLOWED=(
  GHSA-vfj7-8cjw-p6xm
)

cd "$(dirname "$0")/../../website"

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

failed=0
while read -r id package severity; do
  [ -n "${id}" ] || continue
  if printf '%s\n' "${ALLOWED[@]}" | grep -qx "${id}"; then
    echo "ok - ${id} (${package}, ${severity}) is a listed exception."
  else
    echo "not ok - ${id} (${package}, ${severity}); update the dependency, or list it with its reason."
    failed=1
  fi
done <<< "${found}"

for id in "${ALLOWED[@]}"; do
  if ! grep -q "^${id} " <<< "${found}"; then
    echo "not ok - ${id} is no longer reported; remove its exception."
    failed=1
  fi
done

exit "${failed}"
