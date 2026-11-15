from __future__ import annotations
from datetime import datetime, timezone
from typing import List, Optional, Dict, Any
from pydantic import BaseModel, Field, EmailStr

# ==========================================================
# User & Auth Schémata
# ==========================================================

class UserBase(BaseModel):
    username: str
    email: EmailStr

class UserCreate(UserBase):
    password: str

class UserLogin(BaseModel):
    username: str
    password: str

class GoogleAuthRequest(BaseModel):
    id_token: str

class Token(BaseModel):
    access_token: str
    token_type: str = "bearer"
    username: str
    role: str

class UserProfileUpdate(BaseModel):
    avatar_url: Optional[str] = None
    birth_date: Optional[datetime] = None
    bio: Optional[str] = None
    preferences_json: Optional[Dict[str, Any]] = None

class UserProfileResponse(BaseModel):
    id: str
    user_id: str
    username: str
    email: str
    avatar_url: Optional[str]
    birth_date: Optional[datetime]
    bio: Optional[str]
    preferences_json: Dict[str, Any]
    role: str
    created_at: datetime

# ==========================================================
# Mobilní Synchronizační Schémata (Mobile API Gateway Sync)
# ==========================================================

class SyncMessageItem(BaseModel):
    id: int
    role: str
    content: str
    cognitive_process: Optional[str] = ""
    follow_up_questions: Optional[str] = ""
    val_sys: float = 0.5
    val_econ: float = 0.5
    val_psych: float = 0.5
    val_eco: float = 0.5
    val_law: float = 0.5
    val_sec: float = 0.5
    val_phys: float = 0.5
    val_soc: float = 0.5
    composite_score: float = 0.5
    domain: str = "SYSTEMS_INTELLIGENCE"
    attached_image_path: Optional[str] = None
    timestamp: int
    defense_tier: str = "APPROVED"
    defense_notes: Optional[str] = ""
    thread_id: str = "thread_main"
    thread_title: str = "Hlavní vlákno"
    user_name: str = "operator"

class SyncMessagesRequest(BaseModel):
    messages: List[SyncMessageItem]
    client_version: Optional[str] = "4.5"
    device_id: Optional[str] = None

class SyncMessagesResponse(BaseModel):
    synced_count: int
    status: str = "SUCCESS"
    message: str
    timestamp: int = Field(default_factory=lambda: int(datetime.now().timestamp() * 1000))

class SyncMemoryItem(BaseModel):
    id: int
    content: str
    domain: str = "SYSTEMS_INTELLIGENCE"
    val_sys: float = 0.5
    val_econ: float = 0.5
    val_psych: float = 0.5
    val_eco: float = 0.5
    val_law: float = 0.5
    val_sec: float = 0.5
    val_phys: float = 0.5
    val_soc: float = 0.5
    importance_score: float = 1.0
    timestamp: int
    user_name: str = "operator"

class SyncMemoryRequest(BaseModel):
    fragments: List[SyncMemoryItem]

class SyncMemoryResponse(BaseModel):
    synced_count: int
    status: str = "SUCCESS"
    message: str

class SyncTelemetryItem(BaseModel):
    level: str
    tag: str
    message: str
    metadata_json: Optional[str] = ""
    timestamp: int

class SyncTelemetryRequest(BaseModel):
    logs: List[SyncTelemetryItem]

class SyncTelemetryResponse(BaseModel):
    synced_count: int
    status: str = "SUCCESS"

class HealthCheckResponse(BaseModel):
    status: str
    database: Dict[str, Any]
    service: str = "O.M.N.I.S. Core Gateway"
    version: str = "4.5"
    timestamp: int = Field(default_factory=lambda: int(datetime.now().timestamp() * 1000))

# ==========================================================
# Self-Healing & Prompt Logic Korekční Schémata
# ==========================================================

class PromptCorrectionRecord(BaseModel):
    original_prompt: str
    detected_inconsistencies: List[str] = Field(default_factory=list)
    corrected_prompt: str
    confidence_score: float = Field(default=0.95, ge=0.0, le=1.0)
    prompt_health_index: float = Field(default=98.5, ge=0.0, le=100.0)
    healing_actions_applied: List[str] = Field(default_factory=list)
    requires_user_authorization: bool = False

class SelfHealingRequest(BaseModel):
    prompt: str
    domain: Optional[str] = "SYSTEMS_INTELLIGENCE"
    user_role: Optional[str] = "ADMIN_OPERATOR"

class SelfHealingResponse(BaseModel):
    status: str = "SUCCESS"
    correction: PromptCorrectionRecord
    timestamp: int = Field(default_factory=lambda: int(datetime.now().timestamp() * 1000))

# ==========================================================
# Co-Synthesis & Pre-Execution Analysis Schémata
# ==========================================================

class CoSynthesisRequest(BaseModel):
    query: str
    ontology_domain: Optional[str] = "SYSTEMS_INTELLIGENCE"
    user_role: Optional[str] = "ADMIN_OPERATOR"

class CoSynthesisResponse(BaseModel):
    status: str = "SUCCESS"
    original_query: str
    detected_gaps: List[str]
    cascade_preview: Dict[str, Any]
    completion_proposals: List[Dict[str, str]]
    timestamp: int = Field(default_factory=lambda: int(datetime.now().timestamp() * 1000))

# ==========================================================
# Epistemic & Knowledge Expansion Schémata
# ==========================================================

class KnowledgeNodeSchema(BaseModel):
    id: str
    domain: str
    semantic_core: str
    confidence_score: float
    usage_count: int
    created_at: datetime

class LatentCausalStateResponse(BaseModel):
    detected_domain_name: str
    active_persona: str
    betti_number_beta1: int
    percolation_index_pc: float
    is_percolation_critical: bool
    system_entropy: float
    learned_nodes: List[KnowledgeNodeSchema] = Field(default_factory=list)
    hidden_causal_links: List[Dict[str, Any]]
    butterfly_effect_chains: List[Dict[str, Any]]
    pon_iceberg: Dict[str, Any]

# ==========================================================
# Thread & Message Persistence Schémata (pro backend/main.py)
# ==========================================================

class ChatMessage(BaseModel):
    role: str
    content: str
    cognitive_thoughts: Optional[str] = None
    follow_up_questions: Optional[Any] = None
    timestamp: Optional[int] = None

class MessageSchema(BaseModel):
    id: str
    conversation_id: str
    role: str
    content: str
    cognitive_thoughts: Optional[str] = None
    follow_up_questions: Optional[Any] = None
    created_at: datetime


class ThreadCreateRequest(BaseModel):
    title: Optional[str] = "Bez názvu"
    ontology_domain: Optional[str] = "SYSTEMS_INTELLIGENCE"


class ThreadUpdateRequest(BaseModel):
    title: Optional[str] = None
    ontology_domain: Optional[str] = None


class ThreadDetailResponse(BaseModel):
    id: str
    title: str = "Bez názvu"
    ontology_domain: str = "SYSTEMS_INTELLIGENCE"
    messages: List[MessageSchema] = Field(default_factory=list)
    created_at: datetime
    updated_at: datetime


# ==========================================================
# Conversation & Memory Schémata (pro backend/memory.py)
# ==========================================================

class ConversationDetail(BaseModel):
    id: str
    title: str = "Bez názvu"
    ontology_domain: str = "SYSTEMS_INTELLIGENCE"
    created_at: datetime
    updated_at: datetime

    class Config:
        from_attributes = True


class MemoryItem(BaseModel):
    id: str
    conversation_id: Optional[str] = None
    content: str
    memory_type: str = "semantic"
    importance_score: float = 1.0
    created_at: datetime

    class Config:
        from_attributes = True


# ==========================================================
# Cognitive & Query Schémata (pro backend/cognitive.py, backend/engine.py)
# ==========================================================

class QueryRequest(BaseModel):
    query: str
    conversation_id: Optional[str] = None
    ontology_domain: Optional[str] = "SYSTEMS_INTELLIGENCE"
    enable_thinking: Optional[bool] = False
    user_role: Optional[str] = "STANDARD_USER"


class ImpactMatrixScores(BaseModel):
    sys: float = 0.5
    econ: float = 0.5
    psych: float = 0.5
    eco: float = 0.5
    law: float = 0.5
    sec: float = 0.5
    phys: float = 0.5
    soc: float = 0.5
    composite_score: float = 0.5
    reasoning: Optional[str] = ""


class ConsequenceForensicsSchema(BaseModel):
    horizon: str = "T+1"
    risk_index: float = 0.1
    risk_level: str = "SAFE"
    identified_vectors: List[str] = Field(default_factory=list)
    t_plus_1_systemic_drift: Optional[str] = ""
    thermodynamic_entropy_spike: Optional[str] = ""


class CognitiveNodeMetricsSchema(BaseModel):
    density_index: float = 0.5
    coherence_score: float = 0.9
    epistemic_depth: int = 3
    dialectical_entropy: float = 0.1
    active_nodes: List[str] = Field(default_factory=list)


class TokenUsageStats(BaseModel):
    prompt_tokens: int = 0
    completion_tokens: int = 0
    total_tokens: int = 0
    cost_usd: float = 0.0


class QueryResponse(BaseModel):
    conversation_id: str
    message_id: str
    answer: str
    cognitive_process: Optional[str] = None
    follow_up_questions: List[str] = Field(default_factory=list)
    impact_matrix: ImpactMatrixScores = Field(default_factory=ImpactMatrixScores)
    consequence_forensics: Optional[ConsequenceForensicsSchema] = None
    cognitive_node_metrics: CognitiveNodeMetricsSchema = Field(default_factory=CognitiveNodeMetricsSchema)
    token_usage: TokenUsageStats = Field(default_factory=TokenUsageStats)
    related_memories_count: int = 0
    created_at: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))


class AdversarialReviewRequest(BaseModel):
    answer: str
    query: Optional[str] = ""
    ontology_domain: Optional[str] = "SYSTEMS_INTELLIGENCE"


class AdversarialReviewResponse(BaseModel):
    critique: str
    risk_score: float = 0.1
    suggested_revisions: List[str] = Field(default_factory=list)
    passes_audit: bool = True


# ==========================================================
# Phase 4 Convergence Schémata (pro backend/phase4.py)
# ==========================================================

class ConvergenceScoreItem(BaseModel):
    economic_viability: float = 0.5
    tech_elegance: float = 0.5
    social_ecological_impact: float = 0.5
    psychological_acceptance: float = 0.5


class ConvergenceCandidateItem(BaseModel):
    candidate_id: str
    title: str
    scores: ConvergenceScoreItem = Field(default_factory=ConvergenceScoreItem)
    adversarial_vulnerabilities: List[str] = Field(default_factory=list)
    description: Optional[str] = None


class ConvergenceEvaluationRequest(BaseModel):
    candidates: List[ConvergenceCandidateItem] = Field(default_factory=list)
    minimum_threshold: float = 0.5


class ConvergenceEvaluationResponse(BaseModel):
    selected_optimal_candidate: Optional[ConvergenceCandidateItem] = None
    weighted_rankings: List[Dict[str, Any]] = Field(default_factory=list)
    status: str = "SUCCESS"


# ==========================================================
# Multi-Agent Deliberation Schémata (pro backend/multi_agent_orchestrator.py)
# ==========================================================

class AgentPerspective(BaseModel):
    agent_id: str
    agent_name: str
    perspective: str
    confidence: float = 0.85
    identified_risks: List[str] = Field(default_factory=list)
    recommendation: str = ""


class MultiAgentDeliberationRequest(BaseModel):
    query: str
    current_answer: Optional[str] = ""
    ontology_domain: Optional[str] = "SYSTEMS_INTELLIGENCE"


class MultiAgentDeliberationResponse(BaseModel):
    consensus_score: float
    consensus_status: str
    perspectives: List[AgentPerspective] = Field(default_factory=list)
    synthesis_action: str


# ==========================================================
# Autopoiesis Schémata (pro backend/autopoiesis.py)
# ==========================================================

class AutopoieticFeedbackRequest(BaseModel):
    message_id: str
    conversation_id: Optional[str] = None
    user_rating: int = 5
    feedback_text: Optional[str] = ""
    adjusted_matrix: Optional[ImpactMatrixScores] = None


class AutopoieticFeedbackResponse(BaseModel):
    status: str = "success"
    adaptation_delta: float = 0.0
    message: str = "Autopoietická smyčka byla aktualizována."


# ==========================================================
# Epistemic Ingestion Schémata (pro backend/epistemic.py)
# ==========================================================

class HardDataLayer(BaseModel):
    parameters: Dict[str, Any] = Field(default_factory=dict)


class SoftDataLayer(BaseModel):
    parameters: Dict[str, Any] = Field(default_factory=dict)


class EpistemicDataLayer(BaseModel):
    hard_data: HardDataLayer = Field(default_factory=HardDataLayer)
    soft_data: SoftDataLayer = Field(default_factory=SoftDataLayer)


class OmnisEntityIngestion(BaseModel):
    omnis_entity_id: str
    fundamental_purpose: str
    epistemic_data_layer: EpistemicDataLayer = Field(default_factory=EpistemicDataLayer)



