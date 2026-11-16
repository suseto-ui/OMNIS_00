#!/usr/bin/env bash
set -euo pipefail

# Applies PostgreSQL role passwords exclusively from environment variables (.env / Secret Manager)
psql_exec() {
  psql -v ON_ERROR_STOP=1 --username "${POSTGRES_USER:-postgres}" --dbname "${POSTGRES_DB:-omnis_db}" "$@"
}

if [[ -n "${POSTGRES_APP_PASSWORD:-}" ]]; then
  psql_exec -v pw="${POSTGRES_APP_PASSWORD}" -c "ALTER ROLE omnis_app_rw WITH PASSWORD :'pw';"
fi

if [[ -n "${POSTGRES_AUDIT_PASSWORD:-}" ]]; then
  psql_exec -v pw="${POSTGRES_AUDIT_PASSWORD}" -c "ALTER ROLE omnis_audit_append WITH PASSWORD :'pw';"
fi

if [[ -n "${POSTGRES_SYNC_PASSWORD:-}" ]]; then
  psql_exec -v pw="${POSTGRES_SYNC_PASSWORD}" -c "ALTER ROLE omnis_sync_repl WITH PASSWORD :'pw';"
fi
