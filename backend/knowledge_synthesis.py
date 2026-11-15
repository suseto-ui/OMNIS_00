"""
O.M.N.I.S. Autonomous Knowledge Synthesis & Persistence Module
Handles extraction, storage, and retrieval of successful cognitive fragments (KnowledgeNodes).
"""

from __future__ import annotations
import logging
from typing import Dict, Any, List, Optional
from sqlalchemy.orm import Session
from .models import KnowledgeNode

logger = logging.getLogger("omnis.knowledge")

class KnowledgeSynthesisEngine:
    """
    Engine pro autonomní extrakci a syntézu nových znalostních uzlů.
    """

    @classmethod
    def extract_and_persist_fragment(
        cls,
        db: Session,
        query: str,
        answer: str,
        matrix: Dict[str, float],
        composite_score: float,
        domain: str
    ) -> Optional[KnowledgeNode]:
        """
        Pokud je syntéza vysoce kvalitní (>0.85), uložíme její jádro jako znalostní uzel.
        """
        if composite_score < 0.85:
            return None

        # Extrakce sémantického jádra (klíčové závěry z odpovědi)
        semantic_core = answer.strip()[:500] 
        
        try:
            node = KnowledgeNode(
                domain=domain,
                semantic_core=semantic_core,
                tensor_snapshot=matrix,
                confidence_score=composite_score,
                usage_count=0
            )
            db.add(node)
            db.commit()
            db.refresh(node)
            logger.info(f"Syntetizován nový znalostní uzel: {node.id} pro doménu {domain}")
            return node
        except Exception as e:
            db.rollback()
            logger.error(f"Selhala syntéza znalostního uzlu: {e}")
            return None

    @classmethod
    def get_relevant_nodes(cls, db: Session, domain: str, limit: int = 5) -> List[KnowledgeNode]:
        """Načte historicky úspěšné znalostní uzly pro danou doménu."""
        return db.query(KnowledgeNode).filter(KnowledgeNode.domain == domain).order_by(KnowledgeNode.confidence_score.desc()).limit(limit).all()
