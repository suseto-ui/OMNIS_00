#!/usr/bin/env bash
set -euo pipefail

# Applies PostgreSQL role passwords exclusively from environment variables (.env)
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

# Ensure omnis_sync_repl has replication privileges, database CREATE rights, and owns the ElectricSQL publication
psql_exec -c "GRANT CREATE ON DATABASE \"${POSTGRES_DB:-omnis_db}\" TO omnis_sync_repl;"
psql_exec -c "GRANT CREATE, USAGE ON SCHEMA public TO omnis_app_rw, omnis_sync_repl;"
psql_exec -c "DO \$\$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_publication WHERE pubname = 'electric_publication_default') THEN
    CREATE PUBLICATION electric_publication_default FOR TABLE public.messages, public.goals, public.rag_fragments, public.tensor_states_8d;
  END IF;
  ALTER PUBLICATION electric_publication_default OWNER TO omnis_sync_repl;
END \$\$;"
psql_exec -c "GRANT ALL ON TABLE public.messages, public.goals, public.rag_fragments, public.tensor_states_8d TO omnis_sync_repl;"
psql_exec -c "ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO omnis_sync_repl;"

