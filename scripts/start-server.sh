#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# start-server.sh — build (if needed) and run the dedicated game server.
#
# Usage:
#   ./scripts/start-server.sh                  # port 7777, bind 0.0.0.0
#   ./scripts/start-server.sh --port 8888
#   ./scripts/start-server.sh --bind 127.0.0.1 --port 7777
#
# The server runs autonomously and loops indefinitely, hosting one match at
# a time.  Kill it with Ctrl+C.
#
# NOTE: The game client auto-launches this server as a subprocess when it
# starts.  Use this script only when you want to run the server manually
# (e.g. to keep it alive between client restarts, or to host on a separate
# machine before port-forwarding / Steam is set up).
# ---------------------------------------------------------------------------
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$SCRIPT_DIR/.."

# Build the fat jar if it doesn't already exist.
JAR=$(find "$ROOT/server/build/libs" -name "*-server-*.jar" 2>/dev/null | head -1)
if [ -z "$JAR" ]; then
  echo "[start-server] Server jar not found — building now..."
  (cd "$ROOT" && ./gradlew server:jar --quiet)
  JAR=$(find "$ROOT/server/build/libs" -name "*-server-*.jar" | head -1)
fi

if [ -z "$JAR" ]; then
  echo "[start-server] ERROR: could not find or build server jar." >&2
  exit 1
fi

echo "[start-server] Starting: $JAR $*"
exec java -jar "$JAR" "$@"
