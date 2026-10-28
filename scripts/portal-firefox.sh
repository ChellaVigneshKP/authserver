#!/bin/sh
# Launch Playwright's Firefox build despite the macOS 27 profile-load failure.
#
# Firefox 157 started by direct exec cannot read
# ~/Library/Application Support/Firefox (Mozilla bugs 2069536/2078511, fixed in
# Firefox 158), so it aborts with "Your Firefox profile cannot be loaded". This
# wrapper points the app-data roots at an owned temporary directory and removes
# it afterwards, so no system profile is read, created or modified.
#
# Usage: scripts/portal-firefox.sh [url] [firefox arguments...]
set -eu

root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)

node=${PORTAL_NODE:-}
if [ -z "$node" ]; then
  if command -v node >/dev/null 2>&1; then
    node=node
  else
    node="$root/.local/portal-node24/node_modules/node/bin/node"
  fi
fi

if [ -n "${PORTAL_FIREFOX:-}" ]; then
  binary=$PORTAL_FIREFOX
else
  binary=$(cd "$root/web" && "$node" -e 'process.stdout.write(require("playwright").firefox.executablePath())')
fi

if [ ! -x "$binary" ]; then
  echo "portal-firefox: no executable Firefox at $binary" >&2
  exit 1
fi

profile=$(mktemp -d "${TMPDIR:-/tmp}/portal-firefox-cli.XXXXXX")
mkdir -p "$profile/app-data" "$profile/local-app-data"

status=0
MOZ_APP_DATA="$profile/app-data" MOZ_LOCAL_APP_DATA="$profile/local-app-data" \
  "$binary" "$@" || status=$?

"$node" -e 'require("node:fs").rmSync(process.argv[1], { recursive: true, force: true })' "$profile"
exit "$status"
