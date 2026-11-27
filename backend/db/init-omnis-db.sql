-- ==============================================================================
-- O.M.N.I.S. APEX v4.5 - PostgreSQL 17 Zero-Cost Sovereign Database Schema
-- Extensions: vector (pgvector), age (Apache AGE), pgcrypto
-- ==============================================================================

CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS age;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

LOAD 'age';
SET search_path = ag_catalog, "$user", public;

-- ==============================================================================
-- 1. Least-Privilege Database Roles
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
-- 2. 64D Vector RAG Memory & Embeddings (`rag_embeddings_64d`)
-- ==============================================================================
CREATE TABLE IF NOT EXISTS public.rag_embeddings_64d (
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

CREATE INDEX IF NOT EXISTS ix_rag_embeddings_64d_user_thread
    ON public.rag_embeddings_64d (user_id, thread_id, created_at DESC);

CREATE INDEX IF NOT EXISTS ix_rag_embeddings_64d_hnsw_cosine
    ON public.rag_embeddings_64d USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 128);

-- ==============================================================================
-- 3. Causal Nodes & Edges (Synchronized with CSR Matrix)
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

-- ==============================================================================
-- 4. Retraction Watch Blacklist (Gate G5 Protection)
-- ==============================================================================
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
-- 5. Strict Append-Only ZK Audit Ledger (SHA-256 Hash Chaining)
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
    RAISE EXCEPTION 'SECURITY_VIOLATION: zk_audit_ledger is strictly append-only; % is prohibited.', TG_OP
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

-- ==============================================================================
-- 6. Apache AGE Graph (`omnis_causal_graph`)
-- ==============================================================================
DO $$
BEGIN
    SET LOCAL search_path = ag_catalog, "$user", public;
    IF NOT EXISTS (SELECT 1 FROM ag_catalog.ag_graph WHERE name = 'omnis_causal_graph') THEN
        PERFORM ag_catalog.create_graph('omnis_causal_graph');
    END IF;
END
$$;

-- Seed baseline 8D domains and edges in Apache AGE
DO $$
DECLARE
    v_count BIGINT := 0;
BEGIN
    SET LOCAL search_path = ag_catalog, "$user", public;
    SELECT count(*) INTO v_count
      FROM ag_catalog.cypher('omnis_causal_graph', $$ MATCH (n:Domain) RETURN n $$) AS (n agtype);

    IF v_count = 0 THEN
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
                (soc)-[:CAUSES {weight: 0.65, mechanism: 'regulatory_mandate'}]->(law)
        $$) AS (result agtype);
    END IF;
END
$$;

-- Seed relational tables for causal nodes and edges
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
('SOC', 'LAW', 0.65, 'regulatory_mandate')
ON CONFLICT (source_code, target_code) DO NOTHING;

-- ==============================================================================
-- 7. Grant Permissions to Least-Privilege Roles
-- ==============================================================================
GRANT USAGE, CREATE ON SCHEMA public TO omnis_app_rw, omnis_sync_repl;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.rag_embeddings_64d, public.causal_nodes, public.causal_edges, public.retraction_watch_blacklist TO omnis_app_rw;
GRANT SELECT ON public.zk_audit_ledger TO omnis_app_rw;
GRANT INSERT, SELECT ON public.zk_audit_ledger TO omnis_audit_append;
GRANT USAGE, SELECT ON SEQUENCE public.zk_audit_ledger_seq_id_seq TO omnis_audit_append;

-- ElectricSQL Logical Replication Publication
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_publication WHERE pubname = 'electric_publication_default') THEN
        CREATE PUBLICATION electric_publication_default FOR ALL TABLES;
    END IF;
    ALTER PUBLICATION electric_publication_default OWNER TO omnis_sync_repl;
END
$$;

GRANT ALL ON ALL TABLES IN SCHEMA public TO omnis_sync_repl;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO omnis_app_rw, omnis_sync_repl;

