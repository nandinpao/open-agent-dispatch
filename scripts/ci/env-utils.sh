#!/usr/bin/env bash
set -euo pipefail
# Shared helpers for reading Docker/CI .env files without shell-evaluating them.
# This keeps values with spaces, such as NEXT_PUBLIC_APP_NAME=AI Event Gateway Admin,
# from being interpreted as commands when host-side scripts load CI settings.

load_dotenv_file() {
  local env_file="${1:-}"
  [[ -n "$env_file" && -f "$env_file" ]] || return 0

  local line key value
  while IFS= read -r line || [[ -n "$line" ]]; do
    line="${line%$'\r'}"
    [[ -z "$line" ]] && continue
    [[ "$line" =~ ^[[:space:]]*# ]] && continue

    if [[ "$line" == export\ * ]]; then
      line="${line#export }"
    fi

    [[ "$line" == *=* ]] || continue
    key="${line%%=*}"
    value="${line#*=}"

    # Trim whitespace around the variable name only. Values intentionally keep spaces.
    key="${key#${key%%[![:space:]]*}}"
    key="${key%${key##*[![:space:]]}}"

    if [[ ! "$key" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
      echo "Ignoring invalid env key in ${env_file}: ${key:-<empty>}" >&2
      continue
    fi

    # Accept common dotenv quote styles without eval/source.
    if [[ ${#value} -ge 2 ]]; then
      if [[ "${value:0:1}" == '"' && "${value: -1}" == '"' ]]; then
        value="${value:1:${#value}-2}"
      elif [[ "${value:0:1}" == "'" && "${value: -1}" == "'" ]]; then
        value="${value:1:${#value}-2}"
      fi
    fi

    export "${key}=${value}"
  done < "$env_file"
}
