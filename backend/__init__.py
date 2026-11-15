try:
    from .autopoiesis import autopoiesis_router
    from .cognitive import cognitive_router
    from .dev import dev_router
    from .engine import engine_router
    from .epistemic import epistemic_router
    from .memory import memory_router
    from .multi_agent_orchestrator import multi_agent_router
    from .phase4 import phase4_router
    from .system import system_router
    from .sync_gateway import sync_router
    from .dashboard_router import router as dashboard_router
    from .admin_router import router as admin_router
    from .audit_router import router as audit_router
    from .apex_orchestrator_engine import apex_router
except ImportError as err:
    import logging
    logging.getLogger("omnis.backend").warning("Failed to import router in backend package: %s", err)
