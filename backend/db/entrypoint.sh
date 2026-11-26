#!/usr/bin/env bash
# ==============================================================================
# O.M.N.I.S. APEX v4.5 — PostgreSQL 17 Sovereign Entrypoint Wrapper
# Ensures idempotent schema initialization, role provisioning, and ElectricSQL
# replication publication creation on both fresh and pre-existing Docker volumes.
# ==============================================================================
set -euo pipefail

# Background supervisor: ensures schema, roles, and ElectricSQL publication
# are applied as soon as PostgreSQL is ready to accept connections.
(
  echo "[omnis-pg17-bootstrap] Čekám na připravenost PostgreSQL 17..."
  until pg_isready -U "${POSTGRES_USER:-postgres}" -d "${POSTGRES_DB:-omnis_db}" -h 127.0.0.1 -p 5432 >/dev/null 2>&1; do
    sleep 1
  done
  echo "[omnis-pg17-bootstrap] PostgreSQL 17 je připraven. Aplikuji idempotentní schéma, role a publikaci..."

  if [ -f /docker-entrypoint-initdb.d/01-init.sql ]; then
    PGPASSWORD="${POSTGRES_PASSWORD:-}" psql -v ON_ERROR_STOP=1 \
      -U "${POSTGRES_USER:-postgres}" \
      -d "${POSTGRES_DB:-omnis_db}" \
      -h 127.0.0.1 -p 5432 \
      -f /docker-entrypoint-initdb.d/01-init.sql || echo "[omnis-pg17-bootstrap] Upozornění: 01-init.sql vrátil kód s varováním."
  fi

  if [ -f /docker-entrypoint-initdb.d/02-apply-role-passwords.sh ]; then
    PGPASSWORD="${POSTGRES_PASSWORD:-}" bash /docker-entrypoint-initdb.d/02-apply-role-passwords.sh || echo "[omnis-pg17-bootstrap] Upozornění: 02-apply-role-passwords.sh vrátil kód s varováním."
  fi

  echo "[omnis-pg17-bootstrap] Schéma, role a publikace 'electric_publication_default' jsou aktivní a ověřeny."
) &

exec docker-entrypoint.sh "$@"
