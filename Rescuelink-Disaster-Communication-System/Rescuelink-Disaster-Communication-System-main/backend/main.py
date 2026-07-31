"""RescueLink backend — opportunistic bridge sink + rescuer API + dashboard host.

Run: uvicorn main:app --host 0.0.0.0 --port 8000
Dashboard: http://localhost:8000/dashboard

Auth: rescuer endpoints require header `X-Auth-Token: <RESCUELINK_TOKEN>` (default 'demo-token').
Ingest is intentionally UNauthenticated but rate-limited: any bridging victim device must be
able to POST without provisioning a token in a disaster.
"""
import os
import time
from collections import defaultdict, deque

from fastapi import FastAPI, Header, HTTPException, Request
from fastapi.responses import JSONResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel, Field
from typing import List, Optional

import db
import triage

RESCUELINK_TOKEN = os.environ.get("RESCUELINK_TOKEN", "demo-token")
INGEST_MAX_PER_MIN = 120          # per client IP
INGEST_MAX_BATCH = 100            # messages per POST

app = FastAPI(title="RescueLink Backend", version="1.0")


@app.on_event("startup")
def _startup():
    db.init_db()


# ---------- rate limiting (simple in-memory sliding window per IP) ----------
_hits = defaultdict(deque)


def _rate_ok(ip: str) -> bool:
    now = time.time()
    q = _hits[ip]
    while q and now - q[0] > 60:
        q.popleft()
    if len(q) >= INGEST_MAX_PER_MIN:
        return False
    q.append(now)
    return True


# ---------- schemas ----------
class MeshMessage(BaseModel):
    # Mirrors the Gson MessageEntity fields the mesh already serializes.
    uuid: str = Field(..., alias="id")
    senderName: Optional[str] = None
    senderId: Optional[str] = None
    latitude: Optional[float] = None
    longitude: Optional[float] = None
    emergencyType: Optional[str] = None
    medicalNote: Optional[str] = None
    bloodGroup: Optional[str] = None
    batteryLevel: Optional[int] = None
    hopCount: Optional[int] = 0
    timestamp: Optional[int] = None
    isSOSAlert: Optional[bool] = True

    class Config:
        populate_by_name = True   # accept either 'id' or 'uuid'


class IngestBatch(BaseModel):
    messages: List[MeshMessage]


class StatusUpdate(BaseModel):
    status: str


VALID_STATUSES = {"ACTIVE", "ACKNOWLEDGED", "EN_ROUTE", "RESOLVED"}


def _require_token(token: Optional[str]):
    if token != RESCUELINK_TOKEN:
        raise HTTPException(status_code=401, detail="invalid token")


def _minutes_unresolved(row) -> float:
    first = row["first_bridged_at"] or db.now_ms()
    return max(0.0, (db.now_ms() - first) / 60000.0)


def _retriage(row) -> tuple:
    return triage.score(
        emergency_type=row["emergency_type"],
        medical_note=row["medical_note"],
        battery_level=row["battery_level"],
        minutes_unresolved=_minutes_unresolved(row),
        hop_count=row["hop_count"] or 0,
    )


# ---------- ingest (opportunistic bridge sink) ----------
@app.post("/api/ingest")
async def ingest(batch: IngestBatch, request: Request):
    ip = request.client.host if request.client else "unknown"
    if not _rate_ok(ip):
        raise HTTPException(status_code=429, detail="rate limited")
    if len(batch.messages) > INGEST_MAX_BATCH:
        raise HTTPException(status_code=413, detail="batch too large")

    results = {"inserted": 0, "updated": 0, "skipped": 0}
    for m in batch.messages:
        if not m.uuid or not m.isSOSAlert:
            results["skipped"] += 1
            continue
        _, tier, reason = triage.score(
            emergency_type=m.emergencyType,
            medical_note=m.medicalNote,
            battery_level=m.batteryLevel,
            minutes_unresolved=0,
            hop_count=m.hopCount or 0,
        )
        action = db.upsert_alert(m.model_dump(by_alias=False), tier, reason)
        results[action] += 1
    return {"ok": True, **results}


# ---------- rescuer API ----------
def _row_to_feature(row) -> dict:
    # Recompute tier live so time-since-first-seen escalation is reflected.
    _, tier, reason = _retriage(row)
    return {
        "type": "Feature",
        "geometry": {"type": "Point", "coordinates": [row["lng"], row["lat"]]},
        "properties": {
            "uuid": row["uuid"],
            "senderName": row["sender_name"],
            "senderId": row["sender_id"],
            "emergencyType": row["emergency_type"],
            "medicalNote": row["medical_note"],
            "bloodGroup": row["blood_group"],
            "batteryLevel": row["battery_level"],
            "hopCount": row["hop_count"],
            "originalTimestamp": row["original_timestamp"],
            "firstBridgedAt": row["first_bridged_at"],
            "status": row["status"],
            "priorityTier": tier,
            "priorityReason": reason,
        },
    }


@app.get("/api/alerts")
async def get_alerts(x_auth_token: Optional[str] = Header(default=None)):
    _require_token(x_auth_token)
    features = [_row_to_feature(r) for r in db.all_alerts() if r["lat"] is not None]
    return {"type": "FeatureCollection", "features": features}


@app.post("/api/alerts/{uuid}/status")
async def update_status(uuid: str, body: StatusUpdate,
                        x_auth_token: Optional[str] = Header(default=None)):
    _require_token(x_auth_token)
    status = body.status.upper()
    if status not in VALID_STATUSES:
        raise HTTPException(status_code=400, detail="invalid status")
    if not db.set_status(uuid, status):
        raise HTTPException(status_code=404, detail="unknown uuid")
    return {"ok": True, "uuid": uuid, "status": status}


@app.get("/api/alerts/updates")
async def alert_updates(since: int = 0, x_auth_token: Optional[str] = Header(default=None)):
    """Phase 4: status changes since `since` (ms epoch), for bridges to hop back to victims."""
    _require_token(x_auth_token)
    rows = db.status_updates_since(since)
    return {
        "serverTime": db.now_ms(),
        "updates": [
            {"uuid": r["uuid"], "senderId": r["sender_id"], "status": r["status"],
             "statusUpdatedAt": r["status_updated_at"]}
            for r in rows
        ],
    }


# ---------- simulation mode (Phase 5) ----------
import random

_SIM_TYPES = ["Medical", "Fire", "Flood", "Earthquake", "Cyclone", "Other"]
_SIM_NOTES = ["diabetic, low insulin", "broken leg", "", "chest pain", "", "asthma, no inhaler"]


@app.post("/api/simulate")
async def simulate(n: int = 8, lat: float = 20.5937, lng: float = 78.9629,
                   x_auth_token: Optional[str] = Header(default=None)):
    """Inject N synthetic victims around (lat,lng) so the dashboard looks populated with
    only a few (or zero) real phones. Uses the SAME ingest+triage path as real alerts."""
    _require_token(x_auth_token)
    made = 0
    for i in range(max(1, min(n, 50))):
        etype = random.choice(_SIM_TYPES)
        note = random.choice(_SIM_NOTES)
        battery = random.choice([8, 12, 20, 45, 70, 90])
        hops = random.randint(0, 6)
        msg = {
            "uuid": f"sim-{db.now_ms()}-{i}",
            "senderName": f"Victim {i+1}",
            "senderId": f"sim-dev-{i}",
            "latitude": lat + random.uniform(-0.05, 0.05),
            "longitude": lng + random.uniform(-0.05, 0.05),
            "emergencyType": etype, "medicalNote": note, "bloodGroup": "O+",
            "batteryLevel": battery, "hopCount": hops,
            "timestamp": db.now_ms() - random.randint(0, 45) * 60000,
            "isSOSAlert": True,
        }
        _, tier, reason = triage.score(emergency_type=etype, medical_note=note,
                                       battery_level=battery,
                                       minutes_unresolved=random.randint(0, 45), hop_count=hops)
        db.upsert_alert(msg, tier, reason)
        made += 1
    return {"ok": True, "created": made}


@app.exception_handler(Exception)
async def _catch_all(request: Request, exc: Exception):
    # Never leak stack traces to a field device; keep the bridge resilient.
    return JSONResponse(status_code=500, content={"ok": False, "error": "server error"})


# ---------- dashboard (Phase 2) served statically ----------
_dash = os.path.join(os.path.dirname(__file__), "..", "dashboard")
if os.path.isdir(_dash):
    app.mount("/dashboard", StaticFiles(directory=_dash, html=True), name="dashboard")
