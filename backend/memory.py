import uuid
import logging
from datetime import datetime, timezone
from typing import List, Optional
from fastapi import APIRouter, Depends, HTTPException, Query, status
from pydantic import BaseModel
from sqlalchemy import desc, select
from sqlalchemy.ext.asyncio import AsyncSession

from backend.auth_router import get_current_user
from backend.database import get_db, async_session_factory, set_pg_rls_context
from backend.gemini_service import cognitive_service
from backend.models import Conversation, Message, VectorMemory, User
from backend.schemas import ConversationDetail, MemoryItem

logger = logging.getLogger("omnis.api")
memory_router = APIRouter(prefix="/api", tags=["Memory & Conversations"])


@memory_router.get("/conversations", response_model=List[ConversationDetail])
async def list_conversations(
    limit: int = Query(20, ge=1, le=100),
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> List[ConversationDetail]:
    """Return the most recently updated conversations owned by the caller."""
    await set_pg_rls_context(db, str(current_user.id), current_user.role)
    stmt = select(Conversation)
    if current_user.role != "ADMIN_OPERATOR":
        stmt = stmt.where(Conversation.user_id == str(current_user.id))
    stmt = stmt.order_by(desc(Conversation.updated_at)).limit(limit)
    result = await db.execute(stmt)
    records = result.scalars().all()
    return [ConversationDetail.model_validate(record) for record in records]


async def background_record_vector_memory(
    conversation_id: Optional[uuid.UUID],
    content: str,
    memory_type: str = "semantic",
    metadata_json: Optional[dict] = None,
) -> None:
    """Background task to asynchronously generate embedding and save vector imprint."""
    try:
        embedding = await cognitive_service.generate_embedding(content)
        async with async_session_factory() as session:
            memory_entry = VectorMemory(
                conversation_id=conversation_id,
                content=content,
                embedding=embedding,
                memory_type=memory_type,
                metadata_json=metadata_json or {},
                importance_score=1.0,
            )
            session.add(memory_entry)
            await session.commit()
            logger.info(f"Background vector memory stored for conversation {conversation_id}")
    except Exception as exc:
        logger.exception("Failed to record background vector memory: %s", exc)


@memory_router.get("/memory", response_model=List[MemoryItem])
async def list_memories(
    limit: int = Query(20, ge=1, le=100),
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> List[MemoryItem]:
    await set_pg_rls_context(db, str(current_user.id), current_user.role)
    stmt = select(VectorMemory)
    if current_user.role != "ADMIN_OPERATOR":
        stmt = stmt.join(
            Conversation, VectorMemory.conversation_id == Conversation.id
        ).where(Conversation.user_id == str(current_user.id))
    stmt = stmt.order_by(desc(VectorMemory.created_at)).limit(limit)
    result = await db.execute(stmt)
    records = result.scalars().all()
    return [MemoryItem.model_validate(r) for r in records]


class SyncThreadsRequest(BaseModel):
    threads: List[dict]


@memory_router.post("/memory/sync")
async def sync_threads(
    payload: SyncThreadsRequest,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """
    Asynchronní synchronizace konverzačních vláken z lokální databáze do PostgreSQL 17.
    """
    await set_pg_rls_context(db, str(current_user.id), current_user.role)
    synced_count = 0
    try:
        for t in payload.threads:
            t_id = t.get("id")
            if not t_id:
                continue

            stmt = select(Conversation).where(Conversation.id == str(t_id))
            result = await db.execute(stmt)
            existing_conv = result.scalar_one_or_none()

            if not existing_conv:
                new_conv = Conversation(
                    id=str(t_id),
                    user_id=str(current_user.id),
                    title=t.get("title", "Synchronizované Vlákno"),
                    ontology_domain=t.get("ontologyDomain", "SYSTEMS_INTELLIGENCE"),
                    created_at=datetime.now(timezone.utc),
                    updated_at=datetime.now(timezone.utc),
                )
                db.add(new_conv)
                synced_count += 1
            else:
                if existing_conv.user_id != str(current_user.id) and current_user.role != "ADMIN_OPERATOR":
                    raise HTTPException(
                        status_code=status.HTTP_403_FORBIDDEN,
                        detail="Přístup k synchronizaci cizího vlákna byl odepřen.",
                    )
                existing_conv.title = t.get("title", existing_conv.title)
                existing_conv.updated_at = datetime.now(timezone.utc)
                db.add(existing_conv)

            messages_list = t.get("messages", [])
            for m in messages_list:
                m_id = m.get("id")
                if not m_id:
                    continue
                m_stmt = select(Message).where(Message.id == str(m_id))
                m_res = await db.execute(m_stmt)
                existing_msg = m_res.scalar_one_or_none()
                if not existing_msg:
                    new_msg = Message(
                        id=str(m_id),
                        conversation_id=str(t_id),
                        role=m.get("role", "user"),
                        content=m.get("content") or m.get("text", ""),
                        cognitive_thoughts=m.get("cognitive_process") or m.get("cognitive_thoughts", ""),
                        follow_up_questions=m.get("follow_up_questions") or [],
                        created_at=datetime.now(timezone.utc),
                    )
                    db.add(new_msg)

        await db.commit()
        return {
            "status": "success",
            "message": f"Úspěšně synchronizováno {synced_count} nově vytvořených vláken z IndexedDB.",
            "synced_count": synced_count,
        }
    except HTTPException:
        await db.rollback()
        raise
    except Exception as exc:
        await db.rollback()
        logger.error("Chyba při asynchronní synchronizaci z IndexedDB: %s", exc)
        return {
            "status": "partial_success",
            "message": "Chyba synchronizace (uloženo lokálně).",
            "synced_count": 0,
        }
