"""Phase 1 verify: idempotent ingest + triage sanity. No FastAPI needed.

Run:  cd backend && python test_dedup.py
Exits non-zero on failure.
"""
import os
import tempfile
import db
import triage


def test_ingest_same_uuid_once():
    _, tier, reason = triage.score(emergency_type="Medical", medical_note="diabetic",
                                   battery_level=11, minutes_unresolved=0, hop_count=3)
    msg = {"uuid": "abc-123", "senderName": "A", "senderId": "dev-a",
           "latitude": 12.9, "longitude": 77.6, "emergencyType": "Medical",
           "medicalNote": "diabetic, low insulin", "bloodGroup": "O+",
           "batteryLevel": 11, "hopCount": 3, "timestamp": 1000}
    actions = [db.upsert_alert(msg, tier, reason) for _ in range(5)]
    rows = db.all_alerts()
    assert len(rows) == 1, f"expected 1 alert, got {len(rows)}"
    assert actions[0] == "inserted" and actions[1:] == ["updated"] * 4, actions
    print("PASS: 5x same uuid -> exactly 1 alert stored")


def test_keeps_earliest_timestamp():
    db.upsert_alert({"uuid": "t-1", "latitude": 1, "longitude": 1,
                     "emergencyType": "Fire", "timestamp": 5000, "hopCount": 1,
                     "batteryLevel": 50, "isSOSAlert": True}, "HIGH", "x")
    db.upsert_alert({"uuid": "t-1", "latitude": 1, "longitude": 1,
                     "emergencyType": "Fire", "timestamp": 2000, "hopCount": 2,
                     "batteryLevel": 40, "isSOSAlert": True}, "HIGH", "x")
    row = db.get_alert("t-1")
    assert row["original_timestamp"] == 2000, row["original_timestamp"]
    assert row["hop_count"] == 2, "newer hop_count should update"
    print("PASS: keeps earliest original_timestamp, updates volatile fields")


def test_triage_tiers():
    _, tier, reason = triage.score(emergency_type="Medical", medical_note="diabetic, low insulin",
                                   battery_level=8, minutes_unresolved=40, hop_count=4)
    assert tier == "CRITICAL", (tier, reason)
    assert "medical note" in reason and "battery 8%" in reason and "unresolved" in reason
    _, tier2, _ = triage.score(emergency_type="Other", medical_note=None,
                               battery_level=90, minutes_unresolved=0, hop_count=0)
    assert tier2 == "MODERATE", tier2
    print(f"PASS: triage tiers ok  ({reason})")


if __name__ == "__main__":
    db.DB_PATH = os.path.join(tempfile.gettempdir(), "rescuelink_test.db")
    if os.path.exists(db.DB_PATH):
        os.remove(db.DB_PATH)
    db.init_db()
    test_ingest_same_uuid_once()
    test_keeps_earliest_timestamp()
    test_triage_tiers()
    print("\nAll Phase 1 backend tests passed.")
