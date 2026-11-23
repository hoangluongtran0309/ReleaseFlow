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

# The pause before each attempt after the first, longer each time, so an outage of Maven
# Central of up to about two minutes is outlasted (#128).
readonly PAUSES=(10 20 40 60)
readonly ATTEMPTS=$(( ${#PAUSES[@]} + 1 ))

cd "$(dirname "$0")/../.."

jar=.mvn/wrapper/maven-wrapper.jar
expected="$(sed -n 's/^wrapperSha256Sum=//p' .mvn/wrapper/maven-wrapper.properties)"

# A retry runs with MVNW_VERBOSE and keeps its output, so the log shows the URL, the
# download, and why it failed; the first attempt stays quiet. mvnw prints the URL and its
# downloader's own messages; CI sets no MVNW_USERNAME or MVNW_PASSWORD, and mvnw passes
# those, when set, to wget or curl without echoing them.
started() {
  if [ "$1" = 1 ]; then
    ./mvnw -B --no-transfer-progress --version >/dev/null
  else
    MVNW_VERBOSE=true ./mvnw -B --no-transfer-progress --version
  fi
}

for attempt in $(seq 1 "${ATTEMPTS}"); do
  if started "${attempt}"; then
    exit 0
  fi
  # A jar that does not match is a download that came back wrong; mvnw keeps it and would
  # fail on it again, so it goes before the next attempt.
  if [ -f "${jar}" ] && ! echo "${expected}  ${jar}" | sha256sum --check --status; then
    rm -f "${jar}"
  fi
  if [ "${attempt}" -lt "${ATTEMPTS}" ]; then
    pause="${PAUSES[$(( attempt - 1 ))]}"
    echo "The Maven wrapper did not start (attempt ${attempt} of ${ATTEMPTS}); trying again in ${pause} s." >&2
    sleep "${pause}"
  fi
done
echo "The Maven wrapper did not start after ${ATTEMPTS} attempts." >&2
exit 1
