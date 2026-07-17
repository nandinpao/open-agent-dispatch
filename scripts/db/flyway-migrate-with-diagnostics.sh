#!/bin/sh
set -eu

log() {
  printf '%s %s\n' "[flyway-diagnostics]" "$*"
}

remove_macos_metadata_files() {
  [ -d /flyway/sql ] || return 0
  found=0
  find /flyway/sql -maxdepth 1 -type f \( -name '._*' -o -name '.DS_Store' \) | sort | while read -r file; do
    base="$(basename "$file")"
    log "macos-metadata-file name=$base action=detected"
    if rm -f "$file" 2>/dev/null; then
      log "macos-metadata-file name=$base action=removed"
    else
      log "macos-metadata-file name=$base action=remove-failed reason=flyway-sql-volume-may-be-read-only"
      exit 17
    fi
    found=1
  done
}

assert_no_macos_metadata_files() {
  [ -d /flyway/sql ] || return 0
  bad_files="$(find /flyway/sql -maxdepth 1 -type f \( -name '._*' -o -name '.DS_Store' \) -print | sort || true)"
  if [ -n "$bad_files" ]; then
    log "ERROR: macOS AppleDouble metadata files remain in /flyway/sql after cleanup"
    printf '%s\n' "$bad_files" | while read -r file; do
      [ -n "$file" ] || continue
      log "remaining-macos-metadata-file name=$(basename "$file")"
    done
    log "ERROR: rebuild the migration Docker volume after excluding AppleDouble files. Suggested: make down-v && make cd-local"
    exit 18
  fi
}

command_name="migrate"
base_args=""
for arg in "$@"; do
  case "$arg" in
    migrate|validate|info|repair|clean)
      command_name="$arg"
      ;;
    *)
      base_args="$base_args $arg"
      ;;
  esac
done
# The wrapper is intentionally for migrate. If another command is supplied, still
# diagnose first, then execute that command so the compose contract remains clear.

log "starting command=$command_name"
log "remove macOS AppleDouble metadata files before Flyway scans migrations"
remove_macos_metadata_files
assert_no_macos_metadata_files
log "flyway version follows"
flyway -v || true

log "migration location inventory: /flyway/sql"
if [ -d /flyway/sql ]; then
  find /flyway/sql -maxdepth 1 -type f | sort | while read -r file; do
    base="$(basename "$file")"
    bytes="$(wc -c < "$file" | tr -d ' ')"
    sha="$(sha256sum "$file" 2>/dev/null | awk '{print $1}' || true)"
    log "migration-file name=$base bytes=$bytes sha256=$sha"
  done
else
  log "ERROR: /flyway/sql does not exist"
fi

invalid_count=0
if [ -d /flyway/sql ]; then
  while IFS= read -r file; do
    [ -n "$file" ] || continue
    base="$(basename "$file")"
    case "$base" in
      V[0-9]*__*.sql|R__*.sql|U[0-9]*__*.sql)
        ;;
      *)
        invalid_count=$((invalid_count + 1))
        log "invalid-migration-filename name=$base expected='V<version>__<description>.sql or R__<description>.sql'"
        ;;
    esac
  done <<EOF
$(find /flyway/sql -maxdepth 1 -type f | sort || true)
EOF
fi
log "invalid migration filename count=$invalid_count"
if [ "$invalid_count" -gt 0 ]; then
  log "ERROR: invalid migration filenames detected before Flyway execution. Fix or remove the files listed above."
  exit 19
fi

# shellcheck disable=SC2086
log "flyway info before preflight validate"
# shellcheck disable=SC2086
flyway $base_args -validateMigrationNaming=true info || true

log "flyway preflight validate with validateMigrationNaming=true and ignoreMigrationPatterns=*:pending"
log "pending migrations are expected before a fresh baseline migrate; checksum mismatches and applied-migration drift must still fail."
if [ "${OPENDISPATCH_FLYWAY_DEBUG:-false}" = "true" ]; then
  # shellcheck disable=SC2086
  if ! flyway -X $base_args -validateMigrationNaming=true -ignoreMigrationPatterns='*:pending' validate; then
    log "preflight validate failed. If this reports checksum mismatch for V1 after clean-baseline development, do not use repair unless you intentionally accept a schema-history-only update. For local development, reset the PostgreSQL volume so V1 is recreated from the current file. Suggested: make down-v && make cd-local"
    exit 1
  fi
else
  # shellcheck disable=SC2086
  if ! flyway $base_args -validateMigrationNaming=true -ignoreMigrationPatterns='*:pending' validate; then
    log "preflight validate failed. If this reports checksum mismatch for V1 after clean-baseline development, do not use repair unless you intentionally accept a schema-history-only update. For local development, reset the PostgreSQL volume so V1 is recreated from the current file. Suggested: make down-v && make cd-local"
    exit 1
  fi
fi

if [ "$command_name" = "validate" ]; then
  log "flyway validate requested explicitly; running strict validate without ignoreMigrationPatterns."
  if [ "${OPENDISPATCH_FLYWAY_DEBUG:-false}" = "true" ]; then
    # shellcheck disable=SC2086
    exec flyway -X $base_args -validateMigrationNaming=true validate
  fi
  # shellcheck disable=SC2086
  exec flyway $base_args -validateMigrationNaming=true validate
fi

log "flyway $command_name"
if [ "${OPENDISPATCH_FLYWAY_DEBUG:-false}" = "true" ]; then
  # shellcheck disable=SC2086
  exec flyway -X $base_args -validateMigrationNaming=true "$command_name"
fi
# shellcheck disable=SC2086
exec flyway $base_args -validateMigrationNaming=true "$command_name"
