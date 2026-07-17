#!/usr/bin/env sh
set -eu
URL="${1:-http://127.0.0.1:3000/api/health}"
wget -qO- "$URL"
echo
