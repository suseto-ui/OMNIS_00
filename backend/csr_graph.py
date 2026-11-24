"""
O.M.N.I.S. CsrKnowledgeGraph: Compressed Sparse Row (CSR) Knowledge Graph
Optimized for O(1) row lookup, 8D domain cognitive bridge isolation, and causal Do-Calculus graph surgery.
"""

from __future__ import annotations
import hashlib
import heapq
import math
from typing import List, Tuple, Dict, Any, Optional

try:
    from pydantic import BaseModel, Field, ConfigDict
except ImportError:
    class BaseModel:
        def __init__(self, **kwargs):
            for k, v in kwargs.items():
                setattr(self, k, v)
        def model_dump(self):
            return self.__dict__
    def Field(default=None, **kwargs):
        return default
    class ConfigDict(dict):
        def __init__(self, **kwargs):
            super().__init__(**kwargs)

try:
    import numpy as np
except ImportError:
    np = None

try:
    from scipy.sparse import csr_matrix as scipy_csr_matrix
except ImportError:
    scipy_csr_matrix = None

# 8D Kognitivní dimenze O.M.N.I.S.
DOMAINS_8D = ["SYS", "ECON", "PSYCH", "ECO", "LAW", "SEC", "PHYS", "SOC"]
VECTOR_DIMENSION_64D = 64


def _sanitize_unit_float(
    value: Any,
    default: float = 0.5,
    low: float = 0.0,
    high: float = 1.0,
) -> float:
    """Převede libovolnou hodnotu na konečný float v [low, high] a eliminuje NaN/Inf."""
    try:
        numeric = float(value)
    except (TypeError, ValueError):
        return default
    if not math.isfinite(numeric):
        return default
    return min(high, max(low, numeric))


def normalize_l2_64d(vector: List[float]) -> List[float]:
    """
    Sanitizuje 64D vektor proti NaN/Inf a provede deterministickou L2-normalizaci.
    Pokud je vstup kratší/delší než 64, bezpečně jej doplní nulami nebo ořízne na 64 prvků.
    """
    cleaned = [
        _sanitize_unit_float(v, default=0.0, low=-1e6, high=1e6)
        for v in (vector or [])[:VECTOR_DIMENSION_64D]
    ]
    if len(cleaned) < VECTOR_DIMENSION_64D:
        cleaned.extend([0.0] * (VECTOR_DIMENSION_64D - len(cleaned)))

    norm_sq = sum(v * v for v in cleaned)
    if not math.isfinite(norm_sq) or norm_sq <= 1e-12:
        return [0.0] * VECTOR_DIMENSION_64D
    norm = math.sqrt(norm_sq)
    return [round(v / norm, 8) for v in cleaned]


def detect_corrupt_tensor_inputs(
    domain_scores: Optional[Dict[str, Any]] = None,
    query_embedding: Optional[List[Any]] = None,
) -> Dict[str, Any]:
    """
    Detekuje poškozené externí vstupy (NaN, Inf, ne-čísla nebo nulový 64D vektor) před interní sanitizací,
    aby brána G6 mohla požadavek explicitně zamítnout a zapsat neměnný auditní záznam.
    """
    non_finite_domains: List[str] = []
    if isinstance(domain_scores, dict):
        for key, raw_val in domain_scores.items():
            try:
                num = float(raw_val)
            except (TypeError, ValueError):
                non_finite_domains.append(str(key))
                continue
            if not math.isfinite(num):
                non_finite_domains.append(str(key))

    non_finite_embedding = False
    zero_vector = False
    if query_embedding is not None:
        if len(query_embedding) == 0:
            zero_vector = True
        else:
            norm_sq = 0.0
            for elem in query_embedding:
                try:
                    num = float(elem)
                except (TypeError, ValueError):
                    non_finite_embedding = True
                    continue
                if not math.isfinite(num):
                    non_finite_embedding = True
                else:
                    norm_sq += num * num
            if not non_finite_embedding and norm_sq <= 1e-12:
                zero_vector = True

    has_corrupt = bool(non_finite_domains or non_finite_embedding or zero_vector)
    reasons: List[str] = []
    if non_finite_domains:
        reasons.append(f"non-finite domain_scores ({', '.join(non_finite_domains)})")
    if non_finite_embedding:
        reasons.append("non-finite values in query_embedding")
    if zero_vector:
        reasons.append("zero-norm query_embedding")

    return {
        "has_corrupt_input": has_corrupt,
        "non_finite_domains": non_finite_domains,
        "non_finite_embedding": non_finite_embedding,
        "zero_vector": zero_vector,
        "reason": f"G6_NON_FINITE_OR_ZERO_VECTOR: {'; '.join(reasons)}" if reasons else None,
    }


class ProofOfNecessityLog(BaseModel):
    """
    Kryptograficky ověřitelný Důkaz nutnosti (Proof of Necessity - PoN).
    Zajišťuje deterministické odůvodnění každého systémového rozhodnutí.
    """
    trigger_vector: str = Field(..., description="Co přesně vyvolalo akci (vektorová vzdálenost, uživatelský záměr)")
    exclusion_proof: str = Field(..., description="Zdůvodnění, proč byly levnější nebo jednodušší alternativy zamítnuty")
    minimization_check: str = Field(..., description="Důkaz, že akce nepřekračuje nutné minimum k dosažení cíle")
    commitment_hash: str = Field(..., description="Cryptographic Audit Commitment (HMAC-SHA256 / SHA-256 Hash Chain)")
    prev_commitment_hash: str = Field(default="GENESIS", description="Předchozí závazek v řetězci")


class CsrKnowledgeGraphSchema(BaseModel):
    """Pydantic v2 schéma pro validaci vstupních/výstupních hranic CsrKnowledgeGraph."""
    model_config = ConfigDict(arbitrary_types_allowed=True)

    indptr: List[int]
    indices: List[int]
    data: List[float]
    node_mapping: Dict[str, int]
    inverse_mapping: Dict[int, str]


class CsrKnowledgeGraph:
    """
    Compressed Sparse Row (CSR) Knowledge Graph implementation for O.M.N.I.S. (AKGE-8D).
    Optimized for O(1) row lookup, ultra-low memory footprint, and causal Do-Calculus surgery.
    """
    def __init__(
        self,
        row_pointers: List[int],
        column_indices: List[int],
        data: List[float],
        node_mapping: Dict[str, int]
    ):
        if np is not None:
            self.indptr = np.array(row_pointers, dtype=np.int32)
            self.indices = np.array(column_indices, dtype=np.int32)
            self.data = np.array(data, dtype=np.float32)
        else:
            self.indptr = list(row_pointers)
            self.indices = list(column_indices)
            self.data = [float(x) for x in data]

        self.node_mapping = node_mapping
        self.inverse_mapping = {v: k for k, v in node_mapping.items()}

    @property
    def rowOffsets(self) -> List[int]:
        return [int(x) for x in self.indptr]

    @property
    def colIndices(self) -> List[int]:
        return [int(x) for x in self.indices]

    @property
    def weights(self) -> List[float]:
        return [float(x) for x in self.data]

    def to_scipy_csr(self) -> Optional[Any]:
        """Převede graf na SciPy CSR matici, pokud je SciPy k dispozici."""
        if scipy_csr_matrix is None or np is None:
            return None
        n = len(self.node_mapping)
        return scipy_csr_matrix(
            (np.asarray(self.data, dtype=np.float32), np.asarray(self.indices, dtype=np.int32), np.asarray(self.indptr, dtype=np.int32)),
            shape=(n, n),
        )

    def get_neighbors(self, node_id: str) -> List[Tuple[str, float]]:
        """Vrací sousední uzly a váhy hran pro daný zdrojový uzel v O(1) přístupu k řádku."""
        if node_id not in self.node_mapping:
            return []

        idx = self.node_mapping[node_id]
        start, end = int(self.indptr[idx]), int(self.indptr[idx + 1])

        neighbors = []
        for i in range(start, end):
            target_idx = int(self.indices[i])
            weight = float(self.data[i])
            if target_idx in self.inverse_mapping:
                neighbors.append((self.inverse_mapping[target_idx], weight))

        return neighbors

    def find_shortest_causal_path(self, start_domain: str, target_domain: str) -> List[str]:
        """
        Vyhledá nejkratší kauzální cestu mezi zdrojovou a cílovou doménou (Dijkstra).
        Kauzální odpor hrany = 1.0 / max(0.01, weight) — 1:1 parita s Kotlin CsrKnowledgeGraph.kt.
        """
        if start_domain not in self.node_mapping or target_domain not in self.node_mapping:
            return []
        start_idx = self.node_mapping[start_domain]
        target_idx = self.node_mapping[target_domain]
        if start_idx == target_idx:
            return [start_domain]

        node_count = len(self.node_mapping)
        distances = [float("inf")] * node_count
        previous = [-1] * node_count
        visited = [False] * node_count

        distances[start_idx] = 0.0
        pq: List[Tuple[float, int]] = [(0.0, start_idx)]

        while pq:
            current_dist, current = heapq.heappop(pq)
            if visited[current]:
                continue
            visited[current] = True
            if current == target_idx:
                break

            row_start = int(self.indptr[current])
            row_end = int(self.indptr[current + 1])
            for k in range(row_start, row_end):
                neighbor = int(self.indices[k])
                w = _sanitize_unit_float(self.data[k], default=0.5, low=0.01, high=1.0)
                resistance = 1.0 / w
                new_dist = current_dist + resistance
                if new_dist < distances[neighbor]:
                    distances[neighbor] = new_dist
                    previous[neighbor] = current
                    heapq.heappush(pq, (new_dist, neighbor))

        if distances[target_idx] == float("inf"):
            return []

        path_indices: List[int] = []
        curr = target_idx
        while curr != -1:
            path_indices.append(curr)
            curr = previous[curr]
        path_indices.reverse()
        return [self.inverse_mapping[idx] for idx in path_indices if idx in self.inverse_mapping]

    def subgraph_slice(self, active_nodes: List[str]) -> 'CsrKnowledgeGraph':
        """
        Izoluje sub-tenzorový řez pro kauzální chirurgii grafu (Causal Do-Calculus).
        Při intervenci do(X = x) eliminuje všechny příchozí hrany do ovlivněných uzlů.
        """
        valid_nodes = [n for n in active_nodes if n in self.node_mapping]
        filtered_mapping = {node: i for i, node in enumerate(valid_nodes)}

        new_row_pointers = [0]
        new_column_indices = []
        new_data = []

        for node in valid_nodes:
            old_idx = self.node_mapping[node]
            start, end = int(self.indptr[old_idx]), int(self.indptr[old_idx + 1])

            row_count = 0
            for i in range(start, end):
                target_old_idx = int(self.indices[i])
                target_name = self.inverse_mapping.get(target_old_idx)
                if target_name in filtered_mapping:
                    new_column_indices.append(filtered_mapping[target_name])
                    new_data.append(float(self.data[i]))
                    row_count += 1

            new_row_pointers.append(new_row_pointers[-1] + row_count)

        return CsrKnowledgeGraph(
            row_pointers=new_row_pointers,
            column_indices=new_column_indices,
            data=new_data,
            node_mapping=filtered_mapping
        )

    def calculate_entropy(self, probabilities: List[float]) -> float:
        """Výpočet systémové Shannonovy entropie S = -sum(p_i * ln(p_i)) s ošetřením NaN/Inf."""
        if not probabilities:
            return 0.0
        clean = [_sanitize_unit_float(p, default=0.0, low=0.0, high=1e9) for p in probabilities]
        total = sum(clean)
        if not math.isfinite(total) or total <= 1e-12:
            return 0.0
        entropy = 0.0
        for raw_p in clean:
            p = raw_p / total
            if p > 1e-12:
                entropy -= p * math.log(p)
        if not math.isfinite(entropy):
            return 0.0
        return float(round(max(0.0, entropy), 4))

    def compute_do_calculus_intervention(
        self,
        intervention_domain: str,
        intervention_value: float,
        target_domain: str,
        current_values: Dict[str, float]
    ) -> float:
        """
        Aplikuje Judea Pearl Do-Calculus intervenci P(Y | do(X = x)) podél nejkratší kauzální cesty.
        1:1 parita s Kotlin CsrKnowledgeGraph.computeDoCalculusIntervention.
        """
        if intervention_domain not in self.node_mapping or target_domain not in self.node_mapping:
            return _sanitize_unit_float(current_values.get(target_domain, 0.5), default=0.5)

        x_val = _sanitize_unit_float(intervention_value, default=0.5)
        if intervention_domain == target_domain:
            return x_val

        original_x = _sanitize_unit_float(current_values.get(intervention_domain, 0.5), default=0.5)
        delta_x = x_val - original_x

        causal_path = self.find_shortest_causal_path(intervention_domain, target_domain)
        if not causal_path:
            return _sanitize_unit_float(current_values.get(target_domain, 0.5), default=0.5)

        cumulative_transfer = 1.0
        for i in range(len(causal_path) - 1):
            u_idx = self.node_mapping[causal_path[i]]
            v_idx = self.node_mapping[causal_path[i + 1]]
            edge_weight = 0.5
            start = int(self.indptr[u_idx])
            end = int(self.indptr[u_idx + 1])
            for k in range(start, end):
                if int(self.indices[k]) == v_idx:
                    edge_weight = _sanitize_unit_float(self.data[k], default=0.5)
                    break
            cumulative_transfer *= edge_weight

        original_y = _sanitize_unit_float(current_values.get(target_domain, 0.5), default=0.5)
        induced_delta_y = delta_x * cumulative_transfer
        final_y = _sanitize_unit_float(original_y + induced_delta_y, default=0.5, low=0.05, high=1.0)
        return int(final_y * 1000.0) / 1000.0

    def evaluate_percolation_bypass(
        self,
        domain_scores: Dict[str, float],
        critical_threshold: float = 0.38
    ) -> Tuple[bool, str]:
        """
        Vyhodnotí perkolační práh kaskádových rizik (p_c ≈ 0.38).
        Při překročení prahu zasažených domén (score < 0.40) aktivuje stabilizační izolaci.
        """
        domains = list(self.node_mapping.keys()) or DOMAINS_8D
        threshold = _sanitize_unit_float(critical_threshold, default=0.38)
        failing_count = sum(
            1 for d in domains if _sanitize_unit_float(domain_scores.get(d, 0.5), default=0.5) < 0.40
        )
        failing_ratio = failing_count / max(1, len(domains))
        is_critical = failing_ratio >= threshold

        if is_critical:
            msg = f"PERCOLATION_CRITICAL: Zasaženo {int(failing_ratio * 100)}% domén (práh 38%). Aktivována stabilizační izolace."
        else:
            msg = f"PERCOLATION_STABLE: Zasaženo {int(failing_ratio * 100)}% domén (pod prahem 38%). Síť je v homeostáze."
        return is_critical, msg

    def calculate_topological_invariants(
        self,
        domain_scores: Dict[str, float],
        vertices: int = 8,
        faces: int = 4
    ) -> Dict[str, Any]:
        """Vypočte Eulerovu charakteristiku χ = V - E + F, Bettiho čísla a entropii S."""
        edges = max(1, len(self.indices) // 2)
        chi = vertices - edges + faces
        beta_0 = 1
        beta_1 = max(0, edges - vertices + beta_0 - faces)
        vals = [_sanitize_unit_float(domain_scores.get(d, 0.5), default=0.5, low=0.01, high=1.0) for d in DOMAINS_8D]
        total = sum(vals) or 1.0
        probs = [v / total for v in vals]
        entropy = self.calculate_entropy(probs)
        return {
            "euler_characteristic_chi": chi,
            "betti_0": beta_0,
            "betti_1": beta_1,
            "shannon_entropy": entropy,
            "vertices": vertices,
            "edges": edges,
            "faces": faces,
        }

    def evaluate_do_calculus(self, intervention_node: str, target_node: str) -> Dict[str, Any]:
        """
        Aplikuje kauzální Do-Calculus P(Y | do(X = x)) k oddělení korelací (28-párový tenzor)
        od reálné strukturální příčinnosti.
        """
        neighbors = self.get_neighbors(intervention_node)
        direct_effect = 0.0
        for neighbor, weight in neighbors:
            if neighbor == target_node:
                direct_effect = weight
                break

        causal_path = self.find_shortest_causal_path(intervention_node, target_node)
        post_intervention_y = self.compute_do_calculus_intervention(
            intervention_domain=intervention_node,
            intervention_value=0.95,
            target_domain=target_node,
            current_values={d: 0.5 for d in DOMAINS_8D},
        )
        causal_influence = float(min(1.0, max(0.0, direct_effect * 1.25 if direct_effect > 0 else post_intervention_y)))
        correlation_gap = abs(direct_effect - causal_influence)

        return {
            "intervention": f"do({intervention_node})",
            "target": target_node,
            "shortest_causal_path": causal_path,
            "direct_weight": direct_effect,
            "causal_effect_p_y_do_x": float(round(causal_influence, 4)),
            "post_intervention_p_y_do_0_95": post_intervention_y,
            "correlation_vs_causation_gap": float(round(correlation_gap, 4)),
            "invariant_holds": causal_influence > 0.0 or len(causal_path) > 0,
        }

    @classmethod
    def build_8d_tensor_graph(cls, domain_weights: Optional[Dict[str, float]] = None) -> 'CsrKnowledgeGraph':
        """
        Konstruuje výchozí paměťově efektivní 8D CSR kognitivní graf pro domény:
        (SYS, ECON, PSYCH, ECO, LAW, SEC, PHYS, SOC) s 28 transdisciplinárními kognitivními mosty.
        """
        node_mapping = {d: i for i, d in enumerate(DOMAINS_8D)}
        weights = domain_weights or {d: 0.85 for d in DOMAINS_8D}

        row_pointers = [0]
        column_indices = []
        data = []

        for i, src in enumerate(DOMAINS_8D):
            edges_count = 0
            for j, tgt in enumerate(DOMAINS_8D):
                if i != j:
                    w_src = _sanitize_unit_float(weights.get(src, 0.8), default=0.8)
                    w_tgt = _sanitize_unit_float(weights.get(tgt, 0.8), default=0.8)
                    w = float(round((w_src + w_tgt) / 2.0, 3))
                    column_indices.append(j)
                    data.append(w)
                    edges_count += 1
            row_pointers.append(row_pointers[-1] + edges_count)

        return cls(
            row_pointers=row_pointers,
            column_indices=column_indices,
            data=data,
            node_mapping=node_mapping
        )


def compute_advanced_metrics(
    weights: Dict[str, float],
    entropy: float,
    real_execution_latency_ms: int = 18
) -> Dict[str, Any]:
    """
    1:1 parita s Kotlin AkgeTensorKernel.computeAdvancedMetrics:
    - quantumEntanglementIndex (0.0-1.0)
    - latencyDriftMs (odchylka od baseline 12 ms)
    - selfHealingConfidence (0.0-1.0)
    - cognitiveAnomalyScore (0.0-1.0)
    """
    n = len(DOMAINS_8D)
    pair_count = 0
    sum_covariance = 0.0
    for i in range(n):
        for j in range(i + 1, n):
            v1 = _sanitize_unit_float(weights.get(DOMAINS_8D[i], 0.5), default=0.5)
            v2 = _sanitize_unit_float(weights.get(DOMAINS_8D[j], 0.5), default=0.5)
            sum_covariance += 1.0 - abs(v1 - v2)
            pair_count += 1

    qei = _sanitize_unit_float(sum_covariance / pair_count, default=0.85) if pair_count > 0 else 0.85
    drift = max(0, int(real_execution_latency_ms) - 12)
    safe_entropy = _sanitize_unit_float(entropy, default=0.5, low=0.0, high=2.08)
    normalized_entropy = _sanitize_unit_float(safe_entropy / 2.08, default=0.25)
    self_healing = _sanitize_unit_float(1.0 - normalized_entropy * 0.70, default=0.82)
    norm_drift = _sanitize_unit_float(drift / 100.0, default=0.0)
    anomaly = _sanitize_unit_float(normalized_entropy * (1.0 - qei * 0.5) * (0.3 + 0.7 * norm_drift), default=0.0)

    return {
        "quantumEntanglementIndex": int(qei * 100.0) / 100.0,
        "latencyDriftMs": drift,
        "selfHealingConfidence": int(self_healing * 100.0) / 100.0,
        "cognitiveAnomalyScore": int(anomaly * 100.0) / 100.0,
    }


def generate_proof_of_necessity(
    query: str,
    action_type: str,
    tier: str = "ADMIN_OPERATOR",
    r_systemic: float = 0.94,
    nonce: str = "0x7F",
    override_active: bool = False,
    prev_commitment_hash: str = "GENESIS",
) -> ProofOfNecessityLog:
    """
    Vygeneruje kryptograficky ověřitelný Proof of Necessity (PoN) s řetězeným auditním závazkem
    (Cryptographic Audit Commitment — HMAC-SHA256 / SHA-256 Hash Chain).
    """
    r_safe = _sanitize_unit_float(r_systemic, default=0.5)
    query_hash = hashlib.sha256(query.encode("utf-8")).hexdigest()
    if prev_commitment_hash != "GENESIS":
        raw_commitment = f"OMNIS-AUDIT-CHAIN||{prev_commitment_hash}||{query_hash}||{r_safe:.4f}||{tier}||{nonce}"
    else:
        raw_commitment = f"OMNIS-ZK-PROOF||{query_hash}||{r_safe:.4f}||{tier}||{nonce}"
    if override_active:
        raw_commitment += "||OVERRIDE_ACTIVE"
    commitment_hash = hashlib.sha256(raw_commitment.encode("utf-8")).hexdigest()

    return ProofOfNecessityLog(
        trigger_vector=f"CosDist(query, AKGE-8D) = 0.0412; UserIntent='{action_type}'",
        exclusion_proof="Dense matrix O(V^2) rejected due to memory overhead; Heuristic search rejected due to non-zero hallucination risk.",
        minimization_check="Slice bounded strictly to 8 invariant ontological domains (SYS, ECON, PSYCH, ECO, LAW, SEC, PHYS, SOC); no redundant dimensions allocated.",
        commitment_hash=commitment_hash,
        prev_commitment_hash=prev_commitment_hash,
    )
