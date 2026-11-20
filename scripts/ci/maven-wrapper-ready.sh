#!/usr/bin/env bash
# Starts the Maven wrapper once, retrying, so that maven-wrapper.jar and the Maven
# distribution are in place before the first real Maven step (#128).
#
# mvnw downloads the jar from Maven Central when it is absent and tries once. A failed
# download is deleted, and mvnw then reports "Failed to validate Maven wrapper SHA-256,
# your Maven wrapper might be compromised", though nothing was tampered with: the jar was
# never fetched. A jar restored from the Actions cache is still checked before it runs,
# so this only helps when the jar had to be downloaded.
#
#   ./scripts/ci/maven-wrapper-ready.sh
set -euo pipefail

readonly ATTEMPTS=3
readonly PAUSE_SECONDS=10

cd "$(dirname "$0")/../.."

jar=.mvn/wrapper/maven-wrapper.jar
expected="$(sed -n 's/^wrapperSha256Sum=//p' .mvn/wrapper/maven-wrapper.properties)"

for attempt in $(seq 1 "${ATTEMPTS}"); do
  if ./mvnw -B --no-transfer-progress --version >/dev/null; then
    exit 0
  fi
  # A jar that does not match is a download that came back wrong; mvnw keeps it and would
  # fail on it again, so it goes before the next attempt.
  if [ -f "${jar}" ] && ! echo "${expected}  ${jar}" | sha256sum --check --status; then
    rm -f "${jar}"
  fi
  if [ "${attempt}" -lt "${ATTEMPTS}" ]; then
    echo "The Maven wrapper did not start (attempt ${attempt} of ${ATTEMPTS}); trying again in ${PAUSE_SECONDS} s." >&2
    sleep "${PAUSE_SECONDS}"
  fi
done
echo "The Maven wrapper did not start after ${ATTEMPTS} attempts." >&2
exit 1
