#!/usr/bin/env sh
set -eu

BASE_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
MVNW_REPO="${BASE_DIR}/.mvn/wrapper"
PROPS="${MVNW_REPO}/maven-wrapper.properties"

if command -v mvn >/dev/null 2>&1; then
  exec mvn "$@"
fi

if ! command -v curl >/dev/null 2>&1; then
  echo "mvn is not installed and curl is unavailable. Install Maven 3.9+ or curl." >&2
  exit 1
fi

DIST_URL="$(grep '^distributionUrl=' "$PROPS" | cut -d= -f2-)"
DIST_NAME="$(basename "$DIST_URL" .tar.gz)"
DIST_DIR="${MVNW_REPO}/${DIST_NAME}"

if [ ! -x "${DIST_DIR}/bin/mvn" ]; then
  mkdir -p "$MVNW_REPO"
  TMP_TGZ="${MVNW_REPO}/${DIST_NAME}.tar.gz"
  echo "Downloading Maven distribution: ${DIST_URL}"
  curl -fsSL "$DIST_URL" -o "$TMP_TGZ"
  tar -xzf "$TMP_TGZ" -C "$MVNW_REPO"
fi

exec "${DIST_DIR}/bin/mvn" "$@"
