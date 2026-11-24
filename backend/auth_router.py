from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, func
from google.oauth2 import id_token
from google.auth.transport import requests
import os
import uuid

from datetime import datetime, timezone
from .database import get_db, is_mock_session
from .models import User, UserProfile
from .schemas import UserCreate, UserLogin, Token, GoogleAuthRequest, UserProfileResponse, UserProfileUpdate
from .security import get_password_hash, verify_password, create_access_token, decode_access_token
from .security.in_memory_store import fs_find_one, fs_get_doc, fs_list_collection, fs_set_doc
from fastapi.security import OAuth2PasswordBearer

from backend.security.env_secrets import get_secret

router = APIRouter(prefix="/api/auth", tags=["Authentication"])
user_router = APIRouter(prefix="/api/user", tags=["User Profile"])

oauth2_scheme = OAuth2PasswordBearer(tokenUrl="/api/auth/login")

ROOT_ADMIN_EMAIL = "susetofu@gmail.com"
GOOGLE_CLIENT_ID = (get_secret("GOOGLE_CLIENT_ID") or "").strip() or None
FIREBASE_PROJECT_ID = os.getenv("FIREBASE_PROJECT_ID", "").strip()


def _fs_dict_to_user(data: dict) -> User:
    created_raw = data.get("created_at")
    created_dt = (
        datetime.fromisoformat(created_raw)
        if isinstance(created_raw, str)
        else (created_raw if isinstance(created_raw, datetime) else datetime.now(timezone.utc))
    )
    return User(
        id=str(data.get("id") or data.get("username") or uuid.uuid4()),
        username=str(data.get("username") or ""),
        email=str(data.get("email") or "").lower(),
        google_id=data.get("google_id"),
        hashed_password=data.get("hashed_password"),
        role=str(data.get("role") or "STANDARD_USER"),
        is_active=bool(data.get("is_active", True)),
        is_approved=bool(data.get("is_approved", False)),
        created_at=created_dt,
    )


def _fs_save_user(user: User) -> None:
    fs_set_doc(
        "users",
        str(user.id),
        {
            "id": str(user.id),
            "username": user.username,
            "email": (user.email or "").lower(),
            "google_id": user.google_id,
            "hashed_password": user.hashed_password,
            "role": user.role,
            "is_active": bool(user.is_active),
            "is_approved": bool(user.is_approved),
            "created_at": (user.created_at or datetime.now(timezone.utc)).isoformat(),
        },
        merge=True,
    )


def _verify_google_or_firebase_token(raw_token: str) -> dict:
    """Verify either a Google OAuth2 ID token (Android) or Firebase Auth ID token (Web)."""
    transport_req = requests.Request()
    if GOOGLE_CLIENT_ID:
        try:
            return id_token.verify_oauth2_token(raw_token, transport_req, GOOGLE_CLIENT_ID)
        except ValueError as oauth_err:
            try:
                return id_token.verify_firebase_token(raw_token, transport_req, audience=FIREBASE_PROJECT_ID)
            except ValueError:
                raise oauth_err
    return id_token.verify_firebase_token(raw_token, transport_req, audience=FIREBASE_PROJECT_ID)


def _is_root_admin_email(email: str | None) -> bool:
    return (email or "").strip().lower() == ROOT_ADMIN_EMAIL


async def get_current_user(token: str = Depends(oauth2_scheme), db: AsyncSession = Depends(get_db)):
    payload = decode_access_token(token)
    if not payload:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid token")

    username: str = payload.get("sub")
    if not username:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid token payload")

    if is_mock_session(db):
        fs_user = fs_find_one("users", username=username)
        user = _fs_dict_to_user(fs_user) if fs_user else None
    else:
        result = await db.execute(select(User).where(User.username == username))
        user = result.scalar_one_or_none()
    if not user:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="User not found")
    if not user.is_active:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Uživatelský účet byl deaktivován.")
    if not user.is_approved and not _is_root_admin_email(user.email):
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Váš účet čeká na schválení administrátorem.")
    return user


@router.post("/register")
async def register(user_in: UserCreate, db: AsyncSession = Depends(get_db)):
    clean_username = user_in.username.strip()
    clean_email = user_in.email.strip().lower()
    if len(clean_username) < 3:
        raise HTTPException(status_code=400, detail="Uživatelské jméno musí mít alespoň 3 znaky.")
    if len(user_in.password) < 6:
        raise HTTPException(status_code=400, detail="Heslo musí mít alespoň 6 znaků.")

    is_admin = _is_root_admin_email(clean_email)
    hashed_pw = get_password_hash(user_in.password)

    if is_mock_session(db):
        existing_all = fs_list_collection("users")
        if any(
            str(u.get("username", "")).lower() == clean_username.lower()
            or str(u.get("email", "")).lower() == clean_email
            for u in existing_all
        ):
            raise HTTPException(status_code=400, detail="Uživatelské jméno nebo e-mail je již registrován.")
        uid = str(uuid.uuid4())
        now_utc = datetime.now(timezone.utc)
        user = User(
            id=uid,
            username=clean_username,
            email=clean_email,
            hashed_password=hashed_pw,
            role="ADMIN_OPERATOR" if is_admin else "STANDARD_USER",
            is_active=True,
            is_approved=True if is_admin else False,
            created_at=now_utc,
        )
        _fs_save_user(user)
        fs_set_doc("user_profiles", uid, {"id": str(uuid.uuid4()), "user_id": uid, "preferences_json": {}}, merge=True)
    else:
        result = await db.execute(
            select(User).where(
                (func.lower(User.username) == clean_username.lower()) | (func.lower(User.email) == clean_email)
            )
        )
        if result.scalar_one_or_none():
            raise HTTPException(status_code=400, detail="Uživatelské jméno nebo e-mail je již registrován.")

        user = User(
            username=clean_username,
            email=clean_email,
            hashed_password=hashed_pw,
            role="ADMIN_OPERATOR" if is_admin else "STANDARD_USER",
            is_active=True,
            is_approved=True if is_admin else False,
        )
        db.add(user)
        await db.flush()

        profile = UserProfile(user_id=user.id)
        db.add(profile)

        await db.commit()
        await db.refresh(user)

    if not user.is_approved:
        return {
            "access_token": "",
            "token_type": "bearer",
            "username": user.username,
            "role": user.role,
            "status": "pending",
            "message": "Registrace byla úspěšně odeslána a čeká na schválení administrátorem.",
        }

    access_token = create_access_token(data={"sub": user.username, "role": user.role})
    return {
        "access_token": access_token,
        "token_type": "bearer",
        "username": user.username,
        "role": user.role,
        "status": "approved",
    }


@router.post("/login", response_model=Token)
async def login(user_in: UserLogin, db: AsyncSession = Depends(get_db)):
    identifier = user_in.username.strip()
    if not identifier or not user_in.password:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Jméno a heslo nesmí být prázdné.")

    if is_mock_session(db):
        users = fs_list_collection("users")
        matched = next(
            (
                u
                for u in users
                if u.get("username") == identifier or str(u.get("email", "")).lower() == identifier.lower()
            ),
            None,
        )
        user = _fs_dict_to_user(matched) if matched else None
    else:
        result = await db.execute(
            select(User).where(
                (User.username == identifier) | (func.lower(User.email) == identifier.lower())
            )
        )
        user = result.scalar_one_or_none()

    if not user or not user.hashed_password or not verify_password(user_in.password, user.hashed_password):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Nesprávné uživatelské jméno nebo heslo.")

    if not user.is_active:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Uživatelský účet byl deaktivován.")

    if not user.is_approved and not _is_root_admin_email(user.email):
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Váš účet čeká na schválení administrátorem.")

    if _is_root_admin_email(user.email) and (user.role != "ADMIN_OPERATOR" or not user.is_approved):
        user.role = "ADMIN_OPERATOR"
        user.is_approved = True
        if is_mock_session(db):
            _fs_save_user(user)
        else:
            await db.commit()
            await db.refresh(user)

    access_token = create_access_token(data={"sub": user.username, "role": user.role})
    return {"access_token": access_token, "token_type": "bearer", "username": user.username, "role": user.role}


@router.post("/google", response_model=Token)
async def google_auth(req: GoogleAuthRequest, db: AsyncSession = Depends(get_db)):
    try:
        idinfo = _verify_google_or_firebase_token(req.id_token)

        raw_email = idinfo.get("email")
        if not raw_email:
            raise HTTPException(status_code=400, detail="Google token neobsahuje e-mailovou adresu.")
        email = raw_email.strip().lower()
        email_verified = bool(idinfo.get("email_verified", False))
        if not email_verified:
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="E-mail u Google účtu není ověřen.")

        google_id = idinfo["sub"]
        base_name = (idinfo.get("name") or email.split("@")[0]).strip()[:80] or email.split("@")[0]
        picture = idinfo.get("picture")
        is_admin_email = _is_root_admin_email(email)

        if is_mock_session(db):
            users = fs_list_collection("users")
            matched = next((u for u in users if u.get("google_id") == google_id), None)
            if not matched:
                matched = next((u for u in users if str(u.get("email", "")).lower() == email), None)
            if matched:
                user = _fs_dict_to_user(matched)
                needs_commit = False
                if not user.google_id:
                    user.google_id = google_id
                    needs_commit = True
            else:
                candidate_username = base_name
                if any(str(u.get("username", "")).lower() == candidate_username.lower() for u in users):
                    candidate_username = f"{base_name[:70]}_{uuid.uuid4().hex[:6]}"
                uid = str(uuid.uuid4())
                role = "ADMIN_OPERATOR" if is_admin_email else "STANDARD_USER"
                user = User(
                    id=uid,
                    username=candidate_username,
                    email=email,
                    google_id=google_id,
                    role=role,
                    is_active=True,
                    is_approved=True if is_admin_email else False,
                    created_at=datetime.now(timezone.utc),
                )
                fs_set_doc(
                    "user_profiles",
                    uid,
                    {"id": str(uuid.uuid4()), "user_id": uid, "avatar_url": picture, "preferences_json": {}},
                    merge=True,
                )
                needs_commit = True

            user_role = "ADMIN_OPERATOR" if is_admin_email else user.role
            user_approved = True if is_admin_email else user.is_approved
            if user.role != user_role or user.is_approved != user_approved:
                user.role = user_role
                user.is_approved = user_approved
                needs_commit = True
            if needs_commit:
                _fs_save_user(user)
        else:
            result = await db.execute(select(User).where(User.google_id == google_id))
            user = result.scalar_one_or_none()
            needs_commit = False

            if not user:
                result = await db.execute(select(User).where(func.lower(User.email) == email))
                user = result.scalar_one_or_none()

                if user:
                    user.google_id = google_id
                    needs_commit = True
                else:
                    candidate_username = base_name
                    existing_name = await db.execute(
                        select(User).where(func.lower(User.username) == candidate_username.lower())
                    )
                    if existing_name.scalar_one_or_none():
                        candidate_username = f"{base_name[:70]}_{uuid.uuid4().hex[:6]}"

                    role = "ADMIN_OPERATOR" if is_admin_email else "STANDARD_USER"
                    user = User(
                        username=candidate_username,
                        email=email,
                        google_id=google_id,
                        role=role,
                        is_active=True,
                        is_approved=True if is_admin_email else False,
                    )
                    db.add(user)
                    await db.flush()
                    profile = UserProfile(user_id=user.id, avatar_url=picture)
                    db.add(profile)
                    needs_commit = True

            user_role = "ADMIN_OPERATOR" if is_admin_email else user.role
            user_approved = True if is_admin_email else user.is_approved

            if user.role != user_role or user.is_approved != user_approved:
                user.role = user_role
                user.is_approved = user_approved
                needs_commit = True

            if needs_commit:
                await db.commit()
                await db.refresh(user)

        if not user.is_active:
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Uživatelský účet byl deaktivován.")

        if not user.is_approved and not is_admin_email:
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Váš účet čeká na schválení administrátorem.")

        access_token = create_access_token(data={"sub": user.username, "role": user.role, "email": user.email})
        return {"access_token": access_token, "token_type": "bearer", "username": user.username, "role": user.role}
    except ValueError:
        raise HTTPException(status_code=400, detail="Invalid Google ID token")


@user_router.get("/profile", response_model=UserProfileResponse)
async def get_profile(current_user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)):
    if is_mock_session(db):
        profile_doc = fs_get_doc("user_profiles", str(current_user.id)) or {}
        if not profile_doc:
            profile_doc = {"id": str(uuid.uuid4()), "user_id": str(current_user.id), "preferences_json": {}}
            fs_set_doc("user_profiles", str(current_user.id), profile_doc, merge=True)
        return {
            "id": str(profile_doc.get("id") or current_user.id),
            "user_id": current_user.id,
            "username": current_user.username,
            "email": current_user.email,
            "avatar_url": profile_doc.get("avatar_url"),
            "birth_date": profile_doc.get("birth_date"),
            "bio": profile_doc.get("bio"),
            "preferences_json": profile_doc.get("preferences_json") or {},
            "role": current_user.role,
            "created_at": current_user.created_at,
        }

    result = await db.execute(select(UserProfile).where(UserProfile.user_id == current_user.id))
    profile = result.scalar_one_or_none()
    if profile is None:
        profile = UserProfile(user_id=current_user.id)
        db.add(profile)
        await db.commit()
        await db.refresh(profile)

    return {
        "id": profile.id,
        "user_id": current_user.id,
        "username": current_user.username,
        "email": current_user.email,
        "avatar_url": profile.avatar_url,
        "birth_date": profile.birth_date,
        "bio": profile.bio,
        "preferences_json": profile.preferences_json or {},
        "role": current_user.role,
        "created_at": current_user.created_at
    }


@user_router.put("/profile", response_model=UserProfileResponse)
async def update_profile(
    profile_in: UserProfileUpdate,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    if is_mock_session(db):
        profile_doc = fs_get_doc("user_profiles", str(current_user.id)) or {
            "id": str(uuid.uuid4()),
            "user_id": str(current_user.id),
            "preferences_json": {},
        }
        if profile_in.avatar_url is not None:
            profile_doc["avatar_url"] = profile_in.avatar_url
        if profile_in.birth_date is not None:
            profile_doc["birth_date"] = profile_in.birth_date.isoformat() if hasattr(profile_in.birth_date, "isoformat") else profile_in.birth_date
        if profile_in.bio is not None:
            profile_doc["bio"] = profile_in.bio
        if profile_in.preferences_json is not None:
            profile_doc["preferences_json"] = profile_in.preferences_json
        fs_set_doc("user_profiles", str(current_user.id), profile_doc, merge=True)
        return await get_profile(current_user, db)

    result = await db.execute(select(UserProfile).where(UserProfile.user_id == current_user.id))
    profile = result.scalar_one_or_none()
    if profile is None:
        profile = UserProfile(user_id=current_user.id)
        db.add(profile)

    if profile_in.avatar_url is not None:
        profile.avatar_url = profile_in.avatar_url
    if profile_in.birth_date is not None:
        profile.birth_date = profile_in.birth_date
    if profile_in.bio is not None:
        profile.bio = profile_in.bio
    if profile_in.preferences_json is not None:
        profile.preferences_json = profile_in.preferences_json

    await db.commit()

    return await get_profile(current_user, db)
