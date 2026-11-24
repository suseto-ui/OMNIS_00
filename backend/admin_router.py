from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession
from backend.database import get_db, is_mock_session
from backend.models import User, AuditLog
from backend.auth_router import get_current_user
from backend.security.in_memory_store import fs_delete_doc, fs_list_collection, fs_set_doc
from sqlalchemy import select, func
import logging
import uuid
from datetime import datetime, timezone

router = APIRouter(prefix="/api/admin", tags=["Administration"])
logger = logging.getLogger("omnis.admin")

ALLOWED_ROLES = {"STANDARD_USER", "ADMIN_OPERATOR", "AUDITOR"}
ROOT_ADMIN_EMAIL = "susetofu@gmail.com"


async def get_current_admin(
    current_user: User = Depends(get_current_user),
) -> User:
    if not current_user.is_active or current_user.role != "ADMIN_OPERATOR":
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Vyžadována oprávnění ADMIN_OPERATOR."
        )
    return current_user


def _normalize_target_email(target_email: str) -> str:
    clean = (target_email or "").strip().lower()
    if not clean or "@" not in clean:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Neplatná e-mailová adresa uživatele."
        )
    return clean


@router.post("/set-role")
async def set_user_role(
    target_email: str,
    new_role: str,
    current_admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    if new_role not in ALLOWED_ROLES:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Neplatná role."
        )

    clean_email = _normalize_target_email(target_email)
    if clean_email == ROOT_ADMIN_EMAIL and new_role != "ADMIN_OPERATOR":
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Nelze odebrat roli ADMIN_OPERATOR hlavnímu vlastníkovi systému."
        )

    if is_mock_session(db):
        users = fs_list_collection("users")
        matched = next((u for u in users if str(u.get("email", "")).lower() == clean_email), None)
        if not matched:
            raise HTTPException(status_code=404, detail="Uživatel nebyl nalezen.")
        old_role = matched.get("role", "STANDARD_USER")
        matched["role"] = new_role
        fs_set_doc("users", str(matched["id"]), matched, merge=True)
        fs_set_doc(
            "audit_logs",
            str(uuid.uuid4()),
            {
                "actor_email": current_admin.email,
                "target_email": matched.get("email"),
                "action": "UPDATE_ROLE",
                "details": {"old_role": old_role, "new_role": new_role},
                "timestamp": datetime.now(timezone.utc).isoformat(),
            },
        )
        return {"status": "success", "message": f"Role uživatele {matched.get('email')} byla změněna na {new_role}."}

    result = await db.execute(select(User).where(func.lower(User.email) == clean_email))
    user = result.scalar_one_or_none()

    if not user:
        raise HTTPException(status_code=404, detail="Uživatel nebyl nalezen.")

    old_role = user.role
    user.role = new_role

    audit = AuditLog(
        actor_email=current_admin.email,
        target_email=user.email,
        action="UPDATE_ROLE",
        details={"old_role": old_role, "new_role": new_role}
    )
    db.add(audit)

    await db.commit()
    logger.info("Role uživatele %s změněna z %s na %s administrátorem %s", user.email, old_role, new_role, current_admin.email)

    return {"status": "success", "message": f"Role uživatele {user.email} byla změněna na {new_role}."}


@router.get("/pending-users")
async def get_pending_users(
    current_admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    if is_mock_session(db):
        pending = [u for u in fs_list_collection("users") if not bool(u.get("is_approved", False))]
        pending.sort(key=lambda u: str(u.get("created_at") or ""), reverse=True)
        return [
            {
                "id": u.get("id"),
                "username": u.get("username"),
                "email": u.get("email"),
                "created_at": u.get("created_at"),
                "role": u.get("role", "STANDARD_USER"),
            }
            for u in pending
        ]

    result = await db.execute(
        select(User)
        .where(User.is_approved == False)  # noqa: E712
        .order_by(User.created_at.desc())
    )
    users = result.scalars().all()
    return [
        {
            "id": u.id,
            "username": u.username,
            "email": u.email,
            "created_at": u.created_at.isoformat() if u.created_at else None,
            "role": u.role
        } for u in users
    ]


@router.post("/approve-user")
async def approve_user(
    target_email: str,
    current_admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    clean_email = _normalize_target_email(target_email)
    if is_mock_session(db):
        users = fs_list_collection("users")
        matched = next((u for u in users if str(u.get("email", "")).lower() == clean_email), None)
        if not matched:
            raise HTTPException(status_code=404, detail="Uživatel nebyl nalezen.")
        matched["is_approved"] = True
        matched["is_active"] = True
        fs_set_doc("users", str(matched["id"]), matched, merge=True)
        fs_set_doc(
            "audit_logs",
            str(uuid.uuid4()),
            {
                "actor_email": current_admin.email,
                "target_email": matched.get("email"),
                "action": "APPROVE_USER",
                "details": {"approved_by": current_admin.email},
                "timestamp": datetime.now(timezone.utc).isoformat(),
            },
        )
        return {"status": "success", "message": f"Uživatel {matched.get('email')} byl schválen a aktivován."}

    result = await db.execute(select(User).where(func.lower(User.email) == clean_email))
    user = result.scalar_one_or_none()
    if not user:
        raise HTTPException(status_code=404, detail="Uživatel nebyl nalezen.")

    user.is_approved = True
    user.is_active = True

    audit = AuditLog(
        actor_email=current_admin.email,
        target_email=user.email,
        action="APPROVE_USER",
        details={"approved_by": current_admin.email}
    )
    db.add(audit)
    await db.commit()
    logger.info("Uživatel %s schválen administrátorem %s", user.email, current_admin.email)
    return {"status": "success", "message": f"Uživatel {user.email} byl schválen a aktivován."}


@router.post("/reject-user")
async def reject_user(
    target_email: str,
    current_admin: User = Depends(get_current_admin),
    db: AsyncSession = Depends(get_db)
):
    clean_email = _normalize_target_email(target_email)
    if clean_email == ROOT_ADMIN_EMAIL or clean_email == (current_admin.email or "").strip().lower():
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Nelze zamítnout nebo smazat účet aktuálního či hlavního administrátora."
        )

    if is_mock_session(db):
        users = fs_list_collection("users")
        matched = next((u for u in users if str(u.get("email", "")).lower() == clean_email), None)
        if not matched:
            raise HTTPException(status_code=404, detail="Uživatel nebyl nalezen.")
        fs_set_doc(
            "audit_logs",
            str(uuid.uuid4()),
            {
                "actor_email": current_admin.email,
                "target_email": matched.get("email"),
                "action": "REJECT_USER",
                "details": {"rejected_by": current_admin.email},
                "timestamp": datetime.now(timezone.utc).isoformat(),
            },
        )
        fs_delete_doc("users", str(matched["id"]))
        return {"status": "success", "message": f"Žádost uživatele {matched.get('email')} byla zamítnuta."}

    result = await db.execute(select(User).where(func.lower(User.email) == clean_email))
    user = result.scalar_one_or_none()
    if not user:
        raise HTTPException(status_code=404, detail="Uživatel nebyl nalezen.")

    audit = AuditLog(
        actor_email=current_admin.email,
        target_email=user.email,
        action="REJECT_USER",
        details={"rejected_by": current_admin.email}
    )
    db.add(audit)
    await db.delete(user)
    await db.commit()
    logger.info("Uživatel %s zamítnut a smazán administrátorem %s", user.email, current_admin.email)
    return {"status": "success", "message": f"Žádost uživatele {user.email} byla zamítnuta."}
