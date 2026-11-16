"""
O.M.N.I.S. Apache AGE Causal Graph Repository (backend/graph/age_repository.py)
Perzistentní property graf `omnis_causal_graph` v PostgreSQL 17 + Apache AGE (openCypher).
Kompiluje neměnný `CsrGraphSnapshot` (`scipy.sparse.csr_matrix` + `CsrKnowledgeGraph`)
s lock-free atomickou výměnou reference při události `NOTIFY omnis_graph_invalidated`.
"""

from __future__ import annotations

import asyncio
import json
import logging
import re
import time
from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional, Tuple

try:
    import numpy as np
except ImportError:  # pragma: no cover
    np = None

try:
    from scipy.sparse import csr_matrix as scipy_csr_matrix
except ImportError:  # pragma: no cover
    scipy_csr_matrix = None

try:
    import asyncpg
except ImportError:  # pragma: no cover
    asyncpg = None

from backend.csr_graph import CsrKnowledgeGraph, DOMAINS_8D

logger = logging.getLogger("omnis.age_graph")

_VALID_DOMAIN_CODE_RE = re.compile(r"^[A-Z0-9_]{2,32}$")
_VALID_GRAPH_NAME_RE = re.compile(r"^[a-zA-Z_][a-zA-Z0-9_]{1,62}$")

DEFAULT_AGE_EDGES: List[Tuple[str, str, float, str]] = [
    ("SYS", "SEC", 0.85, "telemetry_integrity"),
    ("SEC", "LAW", 0.75, "zero_trust_compliance"),
    ("SYS", "PHYS", 0.80, "hardware_envelope"),
    ("PHYS", "ECO", 0.68, "power_budget"),
    ("ECO", "ECON", 0.65, "sustainability_cost"),
    ("ECON", "SYS", 0.60, "compute_allocation"),
    ("PSYCH", "SOC", 0.70, "trust_adoption"),
    ("SOC", "LAW", 0.65, "regulatory_mandate"),
    ("SEC", "PHYS", 0.55, "containment_isolation"),
    ("LAW", "ECON", 0.62, "audit_liability"),
    ("LAW", "SEC", 0.72, "nis2_enforcement"),
    ("ECON", "SOC", 0.64, "resource_equity"),
    ("PSYCH", "SYS", 0.58, "human_in_the_loop"),
]


def _normalize_pg_dsn(database_url: str) -> str:
    dsn = (database_url or "").strip()
    if dsn.startswith("postgresql+asyncpg://"):
        dsn = "postgresql://" + dsn[len("postgresql+asyncpg://"):]
    elif dsn.startswith("postgres://"):
        dsn = "postgresql://" + dsn[len("postgres://"):]
    return dsn


def _parse_agtype(raw: Any) -> Any:
    """Dekóduje hodnotu typu Apache AGE agtype (např. '\"SYS\"', '0.85::float', JSON)."""
    if raw is None:
        return None
    if isinstance(raw, (int, float, bool)):
        return raw
    text_val = str(raw).strip()
    if "::" in text_val and not text_val.startswith(("{", "[", '"')):
        text_val = text_val.split("::", 1)[0].strip()
    if (text_val.startswith('"') and text_val.endswith('"')) or text_val.startswith(("{", "[")):
        try:
            return json.loads(text_val)
        except Exception:
            return text_val.strip('"')
    try:
        if "." in text_val:
            return float(text_val)
        return int(text_val)
    except ValueError:
        return text_val


def validate_domain_code(code: str) -> str:
    cleaned = (code or "").strip().upper()
    if not _VALID_DOMAIN_CODE_RE.match(cleaned):
        raise ValueError(f"Invalid domain code '{code}': must match ^[A-Z0-9_]{{2,32}}$.")
    return cleaned


@dataclass(frozen=True)
class CsrGraphSnapshot:
    """
    Neměnný (immutable) snímek kauzálního grafu v paměti workeru.
    Aktualizace probíhá výhradně lock-free atomickou výměnou reference.
    """

    knowledge_graph: CsrKnowledgeGraph
    csr_matrix: Optional[Any]
    node_mapping: Dict[str, int]
    inverse_mapping: Dict[int, str]
    edge_mechanisms: Dict[Tuple[str, str], str] = field(default_factory=dict)
    version: int = 1
    loaded_at_epoch_ms: int = field(default_factory=lambda: int(time.time() * 1000))
    source: str = "baseline_8d"
    graph_name: str = "omnis_causal_graph"

    @property
    def node_count(self) -> int:
        return len(self.node_mapping)

    @property
    def edge_count(self) -> int:
        return len(self.knowledge_graph.indices)


class AgeCausalGraphRepository:
    """
    Repozitář kauzálního grafu nad PostgreSQL 17 + Apache AGE (`omnis_causal_graph`).
    Udržuje lock-free odkaz na aktivní `CsrGraphSnapshot` a poslouchá `LISTEN omnis_graph_invalidated`.
    """

    INVALIDATION_CHANNEL = "omnis_graph_invalidated"

    def __init__(self, graph_name: str = "omnis_causal_graph"):
        if not _VALID_GRAPH_NAME_RE.match(graph_name):
            raise ValueError(f"Invalid Apache AGE graph name: {graph_name}")
        self.graph_name = graph_name
        self._version_counter = 0
        self._snapshot: CsrGraphSnapshot = self.compile_snapshot_from_edges(
            nodes=list(DOMAINS_8D),
            edges=DEFAULT_AGE_EDGES,
            source="baseline_8d",
        )
        self._database_url: Optional[str] = None
        self._listener_conn: Optional[Any] = None
        self._listener_task: Optional[asyncio.Task] = None
        self._stop_event = asyncio.Event()
        self._reload_lock = asyncio.Lock()

    def get_snapshot(self) -> CsrGraphSnapshot:
        """Lock-free čtení aktuální neměnné instance `CsrGraphSnapshot`."""
        return self._snapshot

    def compile_snapshot_from_edges(
        self,
        nodes: List[str],
        edges: List[Tuple[str, str, float, str]],
        source: str = "apache_age",
    ) -> CsrGraphSnapshot:
        ordered_nodes: List[str] = []
        seen_nodes = set()
        for n in list(DOMAINS_8D) + list(nodes):
            code = str(n).strip().upper()
            if code and code not in seen_nodes:
                seen_nodes.add(code)
                ordered_nodes.append(code)

        for src, tgt, _, _ in edges:
            for endpoint in (str(src).strip().upper(), str(tgt).strip().upper()):
                if endpoint and endpoint not in seen_nodes:
                    seen_nodes.add(endpoint)
                    ordered_nodes.append(endpoint)

        node_mapping = {code: idx for idx, code in enumerate(ordered_nodes)}
        inverse_mapping = {idx: code for code, idx in node_mapping.items()}

        adjacency: Dict[int, Dict[int, float]] = {i: {} for i in range(len(ordered_nodes))}
        mechanisms: Dict[Tuple[str, str], str] = {}

        for src, tgt, weight, mech in edges:
            src_code = str(src).strip().upper()
            tgt_code = str(tgt).strip().upper()
            if src_code not in node_mapping or tgt_code not in node_mapping or src_code == tgt_code:
                continue
            u = node_mapping[src_code]
            v = node_mapping[tgt_code]
            w = round(min(1.0, max(0.01, float(weight))), 4)
            adjacency[u][v] = w
            mechanisms[(src_code, tgt_code)] = str(mech or "causal_link")

        row_pointers = [0]
        column_indices: List[int] = []
        data: List[float] = []
        for u in range(len(ordered_nodes)):
            targets = sorted(adjacency[u].items(), key=lambda kv: kv[0])
            for v, w in targets:
                column_indices.append(v)
                data.append(w)
            row_pointers.append(len(column_indices))

        kg = CsrKnowledgeGraph(
            row_pointers=row_pointers,
            column_indices=column_indices,
            data=data,
            node_mapping=node_mapping,
        )

        scipy_mat = None
        if scipy_csr_matrix is not None and np is not None:
            n = len(ordered_nodes)
            scipy_mat = scipy_csr_matrix(
                (
                    np.asarray(data, dtype=np.float32),
                    np.asarray(column_indices, dtype=np.int32),
                    np.asarray(row_pointers, dtype=np.int32),
                ),
                shape=(n, n),
            )

        self._version_counter += 1
        return CsrGraphSnapshot(
            knowledge_graph=kg,
            csr_matrix=scipy_mat,
            node_mapping=node_mapping,
            inverse_mapping=inverse_mapping,
            edge_mechanisms=mechanisms,
            version=self._version_counter,
            loaded_at_epoch_ms=int(time.time() * 1000),
            source=source,
            graph_name=self.graph_name,
        )

    def swap_snapshot(self, new_snapshot: CsrGraphSnapshot) -> CsrGraphSnapshot:
        """Atomická výměna reference `self._snapshot` bez uzamykání čtenářů."""
        self._snapshot = new_snapshot
        return self._snapshot

    async def load_from_postgres(self, database_url: str) -> CsrGraphSnapshot:
        """
        Načte uzly a hrany z Apache AGE (`omnis_causal_graph`) pomocí openCypher dotazu,
        zkopiluje `CsrGraphSnapshot` a provede lock-free atomickou výměnu.
        """
        dsn = _normalize_pg_dsn(database_url)
        if not dsn.startswith("postgresql://") or asyncpg is None:
            return self._snapshot

        self._database_url = dsn
        async with self._reload_lock:
            conn = await asyncpg.connect(dsn, timeout=5.0)
            try:
                await conn.execute("LOAD 'age';")
                await conn.execute('SET search_path = ag_catalog, "$user", public;')

                nodes_rows = await conn.fetch(
                    f"""
                    SELECT code
                    FROM ag_catalog.cypher('{self.graph_name}', $$
                        MATCH (n:Domain)
                        RETURN n.code
                    $$) AS (code agtype);
                    """
                )
                edges_rows = await conn.fetch(
                    f"""
                    SELECT src, tgt, weight, mechanism
                    FROM ag_catalog.cypher('{self.graph_name}', $$
                        MATCH (a:Domain)-[r:CAUSES]->(b:Domain)
                        RETURN a.code, b.code, r.weight, r.mechanism
                    $$) AS (src agtype, tgt agtype, weight agtype, mechanism agtype);
                    """
                )

                nodes: List[str] = []
                for row in nodes_rows:
                    parsed = _parse_agtype(row["code"])
                    if parsed:
                        nodes.append(str(parsed))

                edges: List[Tuple[str, str, float, str]] = []
                for row in edges_rows:
                    src = _parse_agtype(row["src"])
                    tgt = _parse_agtype(row["tgt"])
                    weight = _parse_agtype(row["weight"])
                    mech = _parse_agtype(row["mechanism"])
                    if src and tgt:
                        edges.append(
                            (
                                str(src),
                                str(tgt),
                                float(weight if weight is not None else 0.5),
                                str(mech or "causal_link"),
                            )
                        )

                if not edges:
                    edges = list(DEFAULT_AGE_EDGES)

                snapshot = self.compile_snapshot_from_edges(
                    nodes=nodes or list(DOMAINS_8D),
                    edges=edges,
                    source="apache_age",
                )
                self.swap_snapshot(snapshot)
                logger.info(
                    "Compiled Apache AGE CsrGraphSnapshot v%d (%d nodes, %d edges).",
                    snapshot.version,
                    snapshot.node_count,
                    snapshot.edge_count,
                )
                return snapshot
            finally:
                await conn.close()

    async def upsert_causal_edge(
        self,
        src_code: str,
        tgt_code: str,
        weight: float,
        mechanism: str = "causal_link",
        database_url: Optional[str] = None,
    ) -> CsrGraphSnapshot:
        """
        Zapíše/aktualizuje kauzální hranu v Apache AGE a vyšle `NOTIFY omnis_graph_invalidated`.
        Pokud není připojena PostgreSQL databáze, aktualizuje lokální snímek atomicky v paměti.
        """
        src = validate_domain_code(src_code)
        tgt = validate_domain_code(tgt_code)
        w = round(min(1.0, max(0.01, float(weight))), 4)
        safe_mech = re.sub(r"[^a-zA-Z0-9_\- ]", "", str(mechanism or "causal_link"))[:64]

        dsn = _normalize_pg_dsn(database_url or self._database_url or "")
        if dsn.startswith("postgresql://") and asyncpg is not None:
            conn = await asyncpg.connect(dsn, timeout=5.0)
            try:
                await conn.execute("LOAD 'age';")
                await conn.execute('SET search_path = ag_catalog, "$user", public;')
                cypher_sql = f"""
                SELECT * FROM ag_catalog.cypher('{self.graph_name}', $$
                    MERGE (a:Domain {{code: '{src}'}})
                    MERGE (b:Domain {{code: '{tgt}'}})
                    MERGE (a)-[r:CAUSES]->(b)
                    SET r.weight = {w}, r.mechanism = '{safe_mech}'
                    RETURN a.code, b.code
                $$) AS (src agtype, tgt agtype);
                """
                await conn.execute(cypher_sql)
                await conn.execute(f"NOTIFY {self.INVALIDATION_CHANNEL}, 'edge_upsert';")
            finally:
                await conn.close()
            return await self.load_from_postgres(dsn)

        # In-memory / SQLite fallback s atomickou výměnou CsrGraphSnapshot
        curr = self.get_snapshot()
        edges_dict: Dict[Tuple[str, str], Tuple[float, str]] = {}
        for u_code, u_idx in curr.node_mapping.items():
            for v_code, edge_w in curr.knowledge_graph.get_neighbors(u_code):
                edges_dict[(u_code, v_code)] = (
                    float(edge_w),
                    curr.edge_mechanisms.get((u_code, v_code), "causal_link"),
                )
        edges_dict[(src, tgt)] = (w, safe_mech)
        new_edges = [(s, t, val[0], val[1]) for (s, t), val in edges_dict.items()]
        new_snap = self.compile_snapshot_from_edges(
            nodes=list(curr.node_mapping.keys()),
            edges=new_edges,
            source=curr.source,
        )
        return self.swap_snapshot(new_snap)

    async def start_invalidation_listener(self, database_url: str) -> None:
        """Spustí na pozadí `LISTEN omnis_graph_invalidated` pro automatickou rekompilaci grafu."""
        dsn = _normalize_pg_dsn(database_url)
        if not dsn.startswith("postgresql://") or asyncpg is None:
            logger.info("Apache AGE LISTEN skipped (PostgreSQL asyncpg DSN not active).")
            return
        if self._listener_task is not None and not self._listener_task.done():
            return

        self._database_url = dsn
        self._stop_event.clear()

        try:
            await self.load_from_postgres(dsn)
        except Exception as exc:
            logger.warning("Initial Apache AGE graph load deferred: %s", exc)

        self._listener_task = asyncio.create_task(self._listen_loop(dsn), name="age_graph_listener")

    async def _listen_loop(self, dsn: str) -> None:
        backoff = 1.0
        while not self._stop_event.is_set():
            conn = None
            try:
                conn = await asyncpg.connect(dsn, timeout=5.0)
                self._listener_conn = conn

                def _on_notify(_conn, _pid, _channel, payload):
                    logger.info("Received %s notification (%s); scheduling atomic CSR reload.", _channel, payload)
                    asyncio.create_task(self._safe_reload(dsn))

                await conn.add_listener(self.INVALIDATION_CHANNEL, _on_notify)
                logger.info("Listening on PostgreSQL channel '%s'.", self.INVALIDATION_CHANNEL)
                backoff = 1.0

                while not self._stop_event.is_set():
                    try:
                        await asyncio.wait_for(self._stop_event.wait(), timeout=30.0)
                    except asyncio.TimeoutError:
                        await conn.execute("SELECT 1;")
            except asyncio.CancelledError:
                break
            except Exception as exc:
                logger.warning("Apache AGE listener reconnecting in %.1fs: %s", backoff, exc)
                try:
                    await asyncio.wait_for(self._stop_event.wait(), timeout=backoff)
                except asyncio.TimeoutError:
                    pass
                backoff = min(30.0, backoff * 2.0)
            finally:
                if conn is not None:
                    try:
                        await conn.close()
                    except Exception:
                        pass
                self._listener_conn = None

    async def _safe_reload(self, dsn: str) -> None:
        try:
            await self.load_from_postgres(dsn)
        except Exception as exc:
            logger.warning("Failed to reload Apache AGE graph after NOTIFY: %s", exc)

    async def stop_listener(self) -> None:
        self._stop_event.set()
        if self._listener_conn is not None:
            try:
                await self._listener_conn.close()
            except Exception:
                pass
            self._listener_conn = None
        if self._listener_task is not None:
            self._listener_task.cancel()
            try:
                await self._listener_task
            except (asyncio.CancelledError, Exception):
                pass
            self._listener_task = None

    def evaluate_g3_do_calculus(
        self,
        intervention_domain: str,
        target_domain: str,
        intervention_value: float = 0.95,
        current_values: Optional[Dict[str, float]] = None,
    ) -> Dict[str, Any]:
        """
        Brána G3 (Causal Graph Path + Judea Pearl Do-Calculus P(Y | do(X=x))).
        Provádí chirurgický řez příchozích hran do intervenovaného uzlu X (`subgraph_slice`)
        a deterministicky ověřuje existenci orientované kauzální cesty X -> ... -> Y.
        """
        snapshot = self.get_snapshot()
        src = (intervention_domain or "").strip().upper()
        tgt = (target_domain or "").strip().upper()
        baseline_values = current_values or {d: 0.5 for d in DOMAINS_8D}

        if src not in snapshot.node_mapping or tgt not in snapshot.node_mapping:
            return {
                "g3_passed": False,
                "intervention": f"do({src}={intervention_value:.2f})",
                "target": tgt,
                "shortest_causal_path": [],
                "causal_effect_p_y_do_x": 0.0,
                "post_intervention_p_y": float(baseline_values.get(tgt, 0.5)),
                "mechanisms": [],
                "snapshot_version": snapshot.version,
                "graph_source": snapshot.source,
                "graph_name": snapshot.graph_name,
                "reason": "Unknown causal domain node in Apache AGE graph.",
            }

        # Chirurgický řez Do-Calculus: zachováme cestu bez cyklů skrze aktivní uzly
        causal_path = snapshot.knowledge_graph.find_shortest_causal_path(src, tgt)
        g3_passed = len(causal_path) > 0

        post_intervention_y = snapshot.knowledge_graph.compute_do_calculus_intervention(
            intervention_domain=src,
            intervention_value=intervention_value,
            target_domain=tgt,
            current_values=baseline_values,
        )
        do_eval = snapshot.knowledge_graph.evaluate_do_calculus(src, tgt)
        mechanisms = [
            snapshot.edge_mechanisms.get((causal_path[i], causal_path[i + 1]), "causal_link")
            for i in range(max(0, len(causal_path) - 1))
        ]

        return {
            "g3_passed": g3_passed,
            "intervention": f"do({src}={intervention_value:.2f})",
            "target": tgt,
            "shortest_causal_path": causal_path,
            "direct_weight": do_eval["direct_weight"],
            "causal_effect_p_y_do_x": do_eval["causal_effect_p_y_do_x"],
            "post_intervention_p_y": post_intervention_y,
            "correlation_vs_causation_gap": do_eval["correlation_vs_causation_gap"],
            "mechanisms": mechanisms,
            "snapshot_version": snapshot.version,
            "graph_source": snapshot.source,
            "graph_name": snapshot.graph_name,
            "scipy_csr_shape": list(snapshot.csr_matrix.shape) if snapshot.csr_matrix is not None else [snapshot.node_count, snapshot.node_count],
        }


_age_graph_repository: Optional[AgeCausalGraphRepository] = None


def get_age_graph_repository() -> AgeCausalGraphRepository:
    global _age_graph_repository
    if _age_graph_repository is None:
        _age_graph_repository = AgeCausalGraphRepository()
    return _age_graph_repository
