"""Phase 5 golden-path integration test (server half of the closed loop).

Exercises: ingest -> alert appears in GeoJSON with triage -> status update ->
/api/alerts/updates reflects it (the data a bridge hops back to the victim).

Run:  cd backend && pip install -r requirements.txt && python test_e2e.py
Uses FastAPI's TestClient (no live server / no phones needed).
"""
import os
import tempfile

import db
# Point the DB at a temp file BEFORE importing the app so startup uses it.
db.DB_PATH = os.path.join(tempfile.gettempdir(), "rescuelink_e2e.db")
if os.path.exists(db.DB_PATH):
    os.remove(db.DB_PATH)

from fastapi.testclient import TestClient  # noqa: E402
import main  # noqa: E402

client = TestClient(main.app)
AUTH = {"X-Auth-Token": "demo-token"}


def run():
    # 1) Victim SOS bridged in (same schema the mesh serializes; 'id' alias for uuid).
    sos = {"id": "e2e-1", "senderName": "Trapped A", "senderId": "dev-a",
           "latitude": 12.97, "longitude": 77.59, "emergencyType": "Medical",
           "medicalNote": "diabetic, low insulin", "bloodGroup": "O+",
           "batteryLevel": 11, "hopCount": 3, "timestamp": 1000, "isSOSAlert": True}
    r = client.post("/api/ingest", json={"messages": [sos, sos, sos]})  # 3x -> dedup
    assert r.status_code == 200, r.text
    assert r.json()["inserted"] == 1 and r.json()["updated"] == 2, r.json()

    # 2) Appears once on the rescuer map, flagged CRITICAL with a human reason.
    r = client.get("/api/alerts", headers=AUTH)
    assert r.status_code == 200, r.text
    feats = r.json()["features"]
    assert len(feats) == 1, f"expected 1 feature, got {len(feats)}"
    props = feats[0]["properties"]
    assert props["priorityTier"] == "CRITICAL", props
    assert "medical note" in props["priorityReason"] and "battery 11%" in props["priorityReason"]
    assert props["medicalNote"] == "diabetic, low insulin"
    print(f"PASS: ingest+dedup+triage  ({props['priorityReason']})")

    # 3) Responder marks EN_ROUTE.
    r = client.post("/api/alerts/e2e-1/status", headers=AUTH, json={"status": "EN_ROUTE"})
    assert r.status_code == 200, r.text

    # 4) The updates feed (what a bridge hops back to the victim) reflects it.
    r = client.get("/api/alerts/updates?since=0", headers=AUTH)
    assert r.status_code == 200, r.text
    ups = r.json()["updates"]
    assert any(u["uuid"] == "e2e-1" and u["senderId"] == "dev-a" and u["status"] == "EN_ROUTE"
               for u in ups), ups
    print("PASS: status update surfaces in /api/alerts/updates for hop-back")

    # 5) Auth is enforced.
    assert client.get("/api/alerts").status_code == 401
    print("PASS: rescuer endpoints require token")

    print("\nGolden-path (server half) e2e passed.")


if __name__ == "__main__":
    run()
