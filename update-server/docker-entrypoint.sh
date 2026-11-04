#!/bin/sh
set -e

# A mounted volume is usually owned by root. Hand it to the unprivileged user, then drop root.
chown -R node:node "${DATA_DIR:-/data}" 2>/dev/null || true
exec su-exec node "$@"
