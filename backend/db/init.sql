-- ==============================================================================
-- O.M.N.I.S. APEX v4.5 - PostgreSQL 17 Open-Source Schema & Security Invariants
-- Extensions: vector (pgvector), age (Apache AGE), pgcrypto
-- ==============================================================================

CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS age;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

LOAD 'age';
SET search_path = ag_catalog, "$user", public;

-- ==============================================================================
-- 1. Least-Privilege Database Roles (Idempotent Provisioning)
-- Passwords should be rotated immediately in production via .env / ALTER ROLE.
-- ==============================================================================
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'omnis_app_rw') THEN
        CREATE ROLE omnis_app_rw WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'omnis_audit_append') THEN
        CREATE ROLE omnis_audit_append WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'omnis_sync_repl') THEN
        CREATE ROLE omnis_sync_repl WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE REPLICATION;
    END IF;
    EXECUTE format('GRANT CREATE ON DATABASE %I TO omnis_sync_repl', current_database());
END
$$;

GRANT CREATE, USAGE ON SCHEMA public TO omnis_app_rw, omnis_sync_repl;

SET search_path = public, ag_catalog;


-- ==============================================================================
-- 2. 64D Vector RAG Memory (pgvector + HNSW Cosine Index)
-- ==============================================================================
CREATE TABLE IF NOT EXISTS public.rag_memory_64d (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(128) NOT NULL,
    thread_id VARCHAR(128) NOT NULL DEFAULT 'thread_main',
    domain VARCHAR(64) NOT NULL DEFAULT 'SYSTEMS_INTELLIGENCE',
    content TEXT NOT NULL,
    embedding vector(64) NOT NULL,
    importance_score DOUBLE PRECISION NOT NULL DEFAULT 1.0 CHECK (importance_score >= 0.0 AND importance_score <= 1.0),
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    idempotency_key VARCHAR(128) UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS ix_rag_memory_64d_user_thread
    ON public.rag_memory_64d (user_id, thread_id, created_at DESC);

CREATE INDEX IF NOT EXISTS ix_rag_memory_64d_hnsw_cosine
    ON public.rag_memory_64d USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 128);

-- ==============================================================================
-- 3. 8D Tensor States & 28-Pair Correlation Matrix
-- ==============================================================================
CREATE TABLE IF NOT EXISTS public.tensor_states_8d (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(128) NOT NULL,
    session_id VARCHAR(128) NOT NULL DEFAULT 'thread_main',
    val_sys DOUBLE PRECISION NOT NULL CHECK (val_sys >= 0.0 AND val_sys <= 1.0),
    val_econ DOUBLE PRECISION NOT NULL CHECK (val_econ >= 0.0 AND val_econ <= 1.0),
    val_psych DOUBLE PRECISION NOT NULL CHECK (val_psych >= 0.0 AND val_psych <= 1.0),
    val_eco DOUBLE PRECISION NOT NULL CHECK (val_eco >= 0.0 AND val_eco <= 1.0),
    val_law DOUBLE PRECISION NOT NULL CHECK (val_law >= 0.0 AND val_law <= 1.0),
    val_sec DOUBLE PRECISION NOT NULL CHECK (val_sec >= 0.0 AND val_sec <= 1.0),
    val_phys DOUBLE PRECISION NOT NULL CHECK (val_phys >= 0.0 AND val_phys <= 1.0),
    val_soc DOUBLE PRECISION NOT NULL CHECK (val_soc >= 0.0 AND val_soc <= 1.0),
    correlation_tensor_28 JSONB NOT NULL DEFAULT '[]'::jsonb,
    system_stress_sigma DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    shannon_entropy DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    percolation_active BOOLEAN NOT NULL DEFAULT FALSE,
    priority_profile VARCHAR(64) NOT NULL DEFAULT 'Balanced Core',
    state_hash VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(128) UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS ix_tensor_states_8d_user_session
    ON public.tensor_states_8d (user_id, session_id, created_at DESC);

-- ==============================================================================
-- 4. Messages & Goals (Synced via ElectricSQL Logical Replication)
-- ==============================================================================
CREATE TABLE IF NOT EXISTS public.messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(128) NOT NULL,
    thread_id VARCHAR(128) NOT NULL DEFAULT 'thread_main',
    thread_title VARCHAR(255) NOT NULL DEFAULT 'Hlavni vlakno',
    role VARCHAR(32) NOT NULL CHECK (role IN ('user', 'assistant', 'system')),
    content TEXT NOT NULL,
    cognitive_process TEXT NOT NULL DEFAULT '',
    follow_up_questions JSONB NOT NULL DEFAULT '[]'::jsonb,
    composite_score DOUBLE PRECISION NOT NULL DEFAULT 0.5 CHECK (composite_score >= 0.0 AND composite_score <= 1.0),
    defense_tier VARCHAR(32) NOT NULL DEFAULT 'APPROVED',
    domain VARCHAR(64) NOT NULL DEFAULT 'SYSTEMS_INTELLIGENCE',
    audit_commitment_hash VARCHAR(128),
    idempotency_key VARCHAR(128) UNIQUE,
    timestamp_ms BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS ix_messages_user_thread_ts
    ON public.messages (user_id, thread_id, timestamp_ms ASC);

CREATE TABLE IF NOT EXISTS public.goals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(128) NOT NULL,
    title VARCHAR(500) NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    status VARCHAR(64) NOT NULL DEFAULT 'ACTIVE',
    priority INTEGER NOT NULL DEFAULT 3 CHECK (priority >= 1 AND priority <= 5),
    progress DOUBLE PRECISION NOT NULL DEFAULT 0.0 CHECK (progress >= 0.0 AND progress <= 100.0),
    tasks_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    idempotency_key VARCHAR(128) UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS ix_goals_user_status
    ON public.goals (user_id, status, updated_at DESC);

-- Alias view for ElectricSQL shape compatibility (`rag_fragments` -> `rag_memory_64d`)
CREATE TABLE IF NOT EXISTS public.rag_fragments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(128) NOT NULL,
    thread_id VARCHAR(128) NOT NULL DEFAULT 'thread_main',
    domain VARCHAR(64) NOT NULL DEFAULT 'SYSTEMS_INTELLIGENCE',
    title VARCHAR(255) NOT NULL DEFAULT '',
    summary TEXT NOT NULL DEFAULT '',
    importance_score DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    embedding_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    idempotency_key VARCHAR(128) UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS ix_rag_fragments_user_thread
    ON public.rag_fragments (user_id, thread_id, updated_at DESC);

-- ==============================================================================
-- 4b. Causal Domain Registry & Retraction Watch Blacklist (Gate G3 & G5)
-- ==============================================================================
CREATE TABLE IF NOT EXISTS public.causal_nodes (
    code VARCHAR(32) PRIMARY KEY,
    label VARCHAR(255) NOT NULL,
    baseline_weight DOUBLE PRECISION NOT NULL DEFAULT 0.80 CHECK (baseline_weight >= 0.0 AND baseline_weight <= 1.0),
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.causal_edges (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_code VARCHAR(32) NOT NULL REFERENCES public.causal_nodes(code) ON DELETE CASCADE,
    target_code VARCHAR(32) NOT NULL REFERENCES public.causal_nodes(code) ON DELETE CASCADE,
    weight DOUBLE PRECISION NOT NULL DEFAULT 0.75 CHECK (weight >= 0.0 AND weight <= 1.0),
    mechanism VARCHAR(128) NOT NULL DEFAULT 'causal_link',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_causal_edge_pair UNIQUE (source_code, target_code)
);

CREATE INDEX IF NOT EXISTS ix_causal_edges_source_target
    ON public.causal_edges (source_code, target_code);

INSERT INTO public.causal_nodes (code, label, baseline_weight) VALUES
    ('SYS', 'Systems Intelligence', 0.85),
    ('ECON', 'Macro & Token Economics', 0.72),
    ('PSYCH', 'Cognitive & Behavioral', 0.65),
    ('ECO', 'Ecological & Energy', 0.58),
    ('LAW', 'EU AI Act / NIS2 / ISO 42001', 0.90),
    ('SEC', 'Zero-Trust Security / NIST 800-207', 0.92),
    ('PHYS', 'Physical Hardware & Latency', 0.80),
    ('SOC', 'Societal Impact & Governance', 0.70)
ON CONFLICT (code) DO NOTHING;

INSERT INTO public.causal_edges (source_code, target_code, weight, mechanism) VALUES
    ('SYS', 'SEC', 0.85, 'telemetry_integrity'),
    ('SEC', 'LAW', 0.75, 'zero_trust_compliance'),
    ('SYS', 'PHYS', 0.80, 'hardware_envelope'),
    ('PHYS', 'ECO', 0.68, 'power_budget'),
    ('ECO', 'ECON', 0.65, 'sustainability_cost'),
    ('ECON', 'SYS', 0.60, 'compute_allocation'),
    ('PSYCH', 'SOC', 0.70, 'trust_adoption'),
    ('SOC', 'LAW', 0.65, 'regulatory_mandate'),
    ('SEC', 'PHYS', 0.55, 'containment_isolation'),
    ('LAW', 'ECON', 0.62, 'audit_liability')
ON CONFLICT (source_code, target_code) DO NOTHING;

CREATE TABLE IF NOT EXISTS public.retraction_watch_blacklist (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    doi VARCHAR(255) UNIQUE,
    title TEXT NOT NULL,
    reason TEXT NOT NULL DEFAULT 'Retracted publication',
    retraction_date DATE,
    source_url TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS ix_retraction_watch_doi
    ON public.retraction_watch_blacklist (doi);

-- ==============================================================================
-- 5. Strictly Append-Only Cryptographic Audit Ledger (SHA-256 Chain)
-- Formula: SHA-256("OMNIS-ZK-PROOF" || prev_hash || QueryHash || R_systemic || Tier || Nonce [|| OVERRIDE_ACTIVE])
-- ==============================================================================
CREATE TABLE IF NOT EXISTS public.zk_audit_ledger (
    seq_id BIGSERIAL PRIMARY KEY,
    entry_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    user_id VARCHAR(128) NOT NULL,
    query_hash CHAR(64) NOT NULL,
    r_systemic NUMERIC(6,4) NOT NULL,
    tier VARCHAR(32) NOT NULL,
    nonce VARCHAR(64) NOT NULL,
    override_active BOOLEAN NOT NULL DEFAULT FALSE,
    prev_hash VARCHAR(64) NOT NULL DEFAULT 'GENESIS',
    commitment_hash CHAR(64) NOT NULL UNIQUE,
    payload_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE OR REPLACE FUNCTION public.zk_audit_ledger_before_insert()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_last_hash VARCHAR(64);
    v_canonical_payload TEXT;
BEGIN
    -- Serialize all concurrent appends to guarantee gapless linear hash chaining
    PERFORM pg_advisory_xact_lock(hashtext('omnis_zk_audit_ledger_chain_lock'));

    SELECT commitment_hash
      INTO v_last_hash
      FROM public.zk_audit_ledger
     ORDER BY seq_id DESC
     LIMIT 1;

    NEW.prev_hash := COALESCE(v_last_hash, 'GENESIS');

    IF NEW.nonce IS NULL OR LENGTH(TRIM(NEW.nonce)) = 0 THEN
        NEW.nonce := encode(gen_random_bytes(8), 'hex');
    END IF;

    v_canonical_payload := 'OMNIS-ZK-PROOF||'
        || NEW.prev_hash || '||'
        || NEW.query_hash || '||'
        || to_char(NEW.r_systemic, 'FM0.0000') || '||'
        || NEW.tier || '||'
        || NEW.nonce
        || CASE WHEN NEW.override_active THEN '||OVERRIDE_ACTIVE' ELSE '' END;

    NEW.commitment_hash := encode(digest(v_canonical_payload, 'sha256'), 'hex');
    NEW.created_at := NOW();

    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION public.zk_audit_ledger_forbid_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'SECURITY_VIOLATION: zk_audit_ledger is strictly append-only; % is prohibited even for administrators.', TG_OP
        USING ERRCODE = '42501';
END;
$$;

DROP TRIGGER IF EXISTS trg_zk_audit_ledger_before_insert ON public.zk_audit_ledger;
CREATE TRIGGER trg_zk_audit_ledger_before_insert
    BEFORE INSERT ON public.zk_audit_ledger
    FOR EACH ROW
    EXECUTE FUNCTION public.zk_audit_ledger_before_insert();

DROP TRIGGER IF EXISTS trg_zk_audit_ledger_immutable ON public.zk_audit_ledger;
CREATE TRIGGER trg_zk_audit_ledger_immutable
    BEFORE UPDATE OR DELETE ON public.zk_audit_ledger
    FOR EACH ROW
    EXECUTE FUNCTION public.zk_audit_ledger_forbid_mutation();

DROP TRIGGER IF EXISTS trg_zk_audit_ledger_no_truncate ON public.zk_audit_ledger;
CREATE TRIGGER trg_zk_audit_ledger_no_truncate
    BEFORE TRUNCATE ON public.zk_audit_ledger
    FOR EACH STATEMENT
    EXECUTE FUNCTION public.zk_audit_ledger_forbid_mutation();

-- ==============================================================================
-- 6. Apache AGE Persistent Causal Graph (`omnis_causal_graph`) + LISTEN/NOTIFY
-- ==============================================================================
DO $$
BEGIN
    SET LOCAL search_path = ag_catalog, "$user", public;
    IF NOT EXISTS (SELECT 1 FROM ag_catalog.ag_graph WHERE name = 'omnis_causal_graph') THEN
        PERFORM ag_catalog.create_graph('omnis_causal_graph');
    END IF;
END
$$;

-- Seed baseline 8D causal graph nodes & weighted edges if empty
DO $$
DECLARE
    v_node_count BIGINT := 0;
BEGIN
    SET LOCAL search_path = ag_catalog, "$user", public;
    SELECT count(*) INTO v_node_count
      FROM ag_catalog.cypher('omnis_causal_graph', $$ MATCH (n:Domain) RETURN n $$) AS (n agtype);

    IF v_node_count = 0 THEN
        PERFORM * FROM ag_catalog.cypher('omnis_causal_graph', $$
            CREATE
                (sys:Domain {code: 'SYS', label: 'Systems Intelligence', baseline_weight: 0.85}),
                (econ:Domain {code: 'ECON', label: 'Macro & Token Economics', baseline_weight: 0.72}),
                (psych:Domain {code: 'PSYCH', label: 'Cognitive & Behavioral', baseline_weight: 0.65}),
                (eco:Domain {code: 'ECO', label: 'Ecological & Energy', baseline_weight: 0.58}),
                (law:Domain {code: 'LAW', label: 'EU AI Act / NIS2 / ISO 42001', baseline_weight: 0.90}),
                (sec:Domain {code: 'SEC', label: 'Zero-Trust Security / NIST 800-207', baseline_weight: 0.92}),
                (phys:Domain {code: 'PHYS', label: 'Physical Hardware & Latency', baseline_weight: 0.80}),
                (soc:Domain {code: 'SOC', label: 'Societal Impact & Governance', baseline_weight: 0.70}),
                (sys)-[:CAUSES {weight: 0.85, mechanism: 'telemetry_integrity'}]->(sec),
                (sec)-[:CAUSES {weight: 0.75, mechanism: 'zero_trust_compliance'}]->(law),
                (sys)-[:CAUSES {weight: 0.80, mechanism: 'hardware_envelope'}]->(phys),
                (phys)-[:CAUSES {weight: 0.68, mechanism: 'power_budget'}]->(eco),
                (eco)-[:CAUSES {weight: 0.65, mechanism: 'sustainability_cost'}]->(econ),
                (econ)-[:CAUSES {weight: 0.60, mechanism: 'compute_allocation'}]->(sys),
                (psych)-[:CAUSES {weight: 0.70, mechanism: 'trust_adoption'}]->(soc),
                (soc)-[:CAUSES {weight: 0.65, mechanism: 'regulatory_mandate'}]->(law),
                (sec)-[:CAUSES {weight: 0.55, mechanism: 'containment_isolation'}]->(phys),
                (law)-[:CAUSES {weight: 0.62, mechanism: 'audit_liability'}]->(econ)
        $$) AS (result agtype);
    END IF;
END
$$;

CREATE OR REPLACE FUNCTION public.notify_omnis_graph_invalidated()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    PERFORM pg_notify('omnis_graph_invalidated', COALESCE(TG_TABLE_NAME, 'omnis_causal_graph'));
    RETURN NULL;
END;
$$;

-- ==============================================================================
-- 7. Row-Level Security (RLS) & Logical Replication Publication for ElectricSQL
-- ==============================================================================
ALTER TABLE public.messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.goals ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.rag_fragments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.rag_memory_64d ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.tensor_states_8d ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS omnis_messages_owner_policy ON public.messages;
CREATE POLICY omnis_messages_owner_policy ON public.messages
    USING (
        current_setting('omnis.current_role', true) = 'ADMIN_OPERATOR'
        OR user_id = current_setting('omnis.current_user_id', true)
        OR current_user IN ('omnis_sync_repl', 'postgres')
    )
    WITH CHECK (
        current_setting('omnis.current_role', true) = 'ADMIN_OPERATOR'
        OR user_id = current_setting('omnis.current_user_id', true)
    );

DROP POLICY IF EXISTS omnis_goals_owner_policy ON public.goals;
CREATE POLICY omnis_goals_owner_policy ON public.goals
    USING (
        current_setting('omnis.current_role', true) = 'ADMIN_OPERATOR'
        OR user_id = current_setting('omnis.current_user_id', true)
        OR current_user IN ('omnis_sync_repl', 'postgres')
    )
    WITH CHECK (
        current_setting('omnis.current_role', true) = 'ADMIN_OPERATOR'
        OR user_id = current_setting('omnis.current_user_id', true)
    );

DROP POLICY IF EXISTS omnis_rag_fragments_owner_policy ON public.rag_fragments;
CREATE POLICY omnis_rag_fragments_owner_policy ON public.rag_fragments
    USING (
        current_setting('omnis.current_role', true) = 'ADMIN_OPERATOR'
        OR user_id = current_setting('omnis.current_user_id', true)
        OR current_user IN ('omnis_sync_repl', 'postgres')
    )
    WITH CHECK (
        current_setting('omnis.current_role', true) = 'ADMIN_OPERATOR'
        OR user_id = current_setting('omnis.current_user_id', true)
    );

DROP POLICY IF EXISTS omnis_rag_memory_64d_owner_policy ON public.rag_memory_64d;
CREATE POLICY omnis_rag_memory_64d_owner_policy ON public.rag_memory_64d
    USING (
        current_setting('omnis.current_role', true) = 'ADMIN_OPERATOR'
        OR user_id = current_setting('omnis.current_user_id', true)
        OR current_user IN ('omnis_sync_repl', 'postgres')
    )
    WITH CHECK (
        current_setting('omnis.current_role', true) = 'ADMIN_OPERATOR'
        OR user_id = current_setting('omnis.current_user_id', true)
    );

DROP POLICY IF EXISTS omnis_tensor_states_owner_policy ON public.tensor_states_8d;
CREATE POLICY omnis_tensor_states_owner_policy ON public.tensor_states_8d
    USING (
        current_setting('omnis.current_role', true) = 'ADMIN_OPERATOR'
        OR user_id = current_setting('omnis.current_user_id', true)
        OR current_user IN ('omnis_sync_repl', 'postgres')
    )
    WITH CHECK (
        current_setting('omnis.current_role', true) = 'ADMIN_OPERATOR'
        OR user_id = current_setting('omnis.current_user_id', true)
    );

-- ElectricSQL requires FULL replica identity on synced tables
ALTER TABLE public.messages REPLICA IDENTITY FULL;
ALTER TABLE public.goals REPLICA IDENTITY FULL;
ALTER TABLE public.rag_fragments REPLICA IDENTITY FULL;
ALTER TABLE public.tensor_states_8d REPLICA IDENTITY FULL;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_publication WHERE pubname = 'electric_publication_default') THEN
        CREATE PUBLICATION electric_publication_default
            FOR TABLE public.messages, public.goals, public.rag_fragments, public.tensor_states_8d;
    END IF;
    ALTER PUBLICATION electric_publication_default OWNER TO omnis_sync_repl;
END
$$;

-- ==============================================================================
-- 8. Explicit Role Grants (Principle of Least Privilege)
-- ==============================================================================
GRANT USAGE, CREATE ON SCHEMA public TO omnis_app_rw, omnis_sync_repl;
GRANT USAGE ON SCHEMA public TO omnis_audit_append;
GRANT USAGE ON SCHEMA ag_catalog TO omnis_app_rw;
GRANT USAGE ON SCHEMA omnis_causal_graph TO omnis_app_rw;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA omnis_causal_graph TO omnis_app_rw;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA omnis_causal_graph TO omnis_app_rw;

GRANT SELECT, INSERT, UPDATE, DELETE ON public.rag_memory_64d, public.tensor_states_8d, public.messages, public.goals, public.rag_fragments, public.causal_nodes, public.causal_edges, public.retraction_watch_blacklist TO omnis_app_rw;
GRANT SELECT ON public.zk_audit_ledger TO omnis_app_rw;

GRANT INSERT, SELECT ON public.zk_audit_ledger TO omnis_audit_append;
GRANT USAGE, SELECT ON SEQUENCE public.zk_audit_ledger_seq_id_seq TO omnis_audit_append;

GRANT ALL ON TABLE public.messages, public.goals, public.rag_fragments, public.tensor_states_8d TO omnis_sync_repl;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO omnis_app_rw, omnis_sync_repl;

