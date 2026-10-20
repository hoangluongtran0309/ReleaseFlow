#!/usr/bin/env bash
# Fails once the Spring Boot parent manages a version at least as new as one that pom.xml
# overrides. An override is there to pull a fix in early. Left behind after a Boot upgrade,
# it would quietly hold that dependency back at an older version, which is the opposite.
# Dependabot cannot see these properties, because only the parent's BOM reads them, so
# this check is what tells a Boot upgrade's pull request which overrides to remove.
#
#   ./scripts/ci/check-version-overrides.sh
set -euo pipefail

# Every property in pom.xml that overrides a version spring-boot-dependencies manages.
readonly OVERRIDES=(
  tomcat.version
  jackson-bom.version
  jackson-2-bom.version
)

cd "$(dirname "$0")/../.."

evaluate() {
  ./mvnw -B -q --no-transfer-progress help:evaluate -Dexpression="$1" -DforceStdout
}

parent_version="$(evaluate project.parent.version)"
local_repository="$(evaluate settings.localRepository)"
bom="${local_repository}/org/springframework/boot/spring-boot-dependencies/${parent_version}/spring-boot-dependencies-${parent_version}.pom"
[ -f "${bom}" ] || { echo "${bom} is not in the local repository; run ./mvnw validate first." >&2; exit 1; }

stale=0
for property in "${OVERRIDES[@]}"; do
  ours="$(evaluate "${property}")"
  managed="$(sed -n "s|^ *<${property}>\\(.*\\)</${property}>.*|\\1|p" "${bom}" | head -n 1)"
  if [ -z "${managed}" ]; then
    echo "not ok - Spring Boot ${parent_version} no longer manages ${property}; remove the override."
    stale=1
  elif [ "$(printf '%s\n%s\n' "${ours}" "${managed}" | sort -V | tail -n 1)" = "${managed}" ]; then
    echo "not ok - Spring Boot ${parent_version} manages ${property} ${managed}, and pom.xml overrides it with ${ours}; remove the override."
    stale=1
  else
    echo "ok - ${property} ${ours} is still newer than Spring Boot ${parent_version}'s ${managed}."
  fi
done
exit "${stale}"
