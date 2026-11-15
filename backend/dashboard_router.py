
from fastapi import APIRouter, WebSocket, WebSocketDisconnect
import asyncio
import random
import logging
from typing import List, Optional

router = APIRouter(prefix="/api/dashboard", tags=["Dashboard Metrics"])
logger = logging.getLogger("omnis.dashboard")


class ConnectionManager:
    def __init__(self):
        self.active_connections: List[WebSocket] = []
        self._broadcaster_task: Optional[asyncio.Task] = None

    async def connect(self, websocket: WebSocket):
        await websocket.accept()
        self.active_connections.append(websocket)
        if self._broadcaster_task is None or self._broadcaster_task.done():
            self._broadcaster_task = asyncio.create_task(metric_generator())

    def disconnect(self, websocket: WebSocket):
        if websocket in self.active_connections:
            self.active_connections.remove(websocket)

    async def broadcast(self, message: dict):
        stale: List[WebSocket] = []
        for connection in list(self.active_connections):
            try:
                await connection.send_json(message)
            except Exception:
                stale.append(connection)
        for conn in stale:
            self.disconnect(conn)


manager = ConnectionManager()


async def metric_generator():
    """Streamuje živé metriky pouze dokud jsou připojeni aktivní klienti."""
    while manager.active_connections:
        metrics = {
            "token_usage": random.randint(100, 5000),
            "sync_status": random.choice(["SYNCED", "SYNCING", "ERROR"]),
            "threats_detected": random.randint(0, 5),
        }
        await manager.broadcast(metrics)
        await asyncio.sleep(2)


@router.websocket("/ws/metrics")
async def websocket_endpoint(websocket: WebSocket):
    await manager.connect(websocket)
    try:
        while True:
            await websocket.receive_text()
    except WebSocketDisconnect:
        manager.disconnect(websocket)
