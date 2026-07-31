# RescueLink — Closed-Loop Disaster Response System

RescueLink is a **victim-to-rescuer disaster-response system**, not a chat app. A trapped
victim's SOS hops across an **offline Bluetooth/Wi-Fi mesh** until *any* device regains
connectivity, then it surfaces on a **live rescuer dashboard** with location, medical info,
and an explainable triage priority — and the responder's status **hops back** to the victim.

```
 Victim phone            Offline mesh (multi-hop, TTL + UUID dedup)         Bridge          Backend            Rescuer
 (airplane mode)   A ───▶ B ───▶ C ───▶ D  (store-carry-forward)     D gets signal ─▶  FastAPI + SQLite ─▶  Leaflet dashboard
      ▲                                                                    │                │                    │
      │                                                                    │  POST /api/ingest (idempotent)      │ Acknowledge / En route / Resolved
      │        RESPONDER_UPDATE hops back through the offline mesh  ◀───────┴──  GET /api/alerts/updates  ◀───────┘
      └──────────────────────  "A responder is on the way"  ◀──────────────────────────────────────────────────
```

## The wedge (honest prior-art comparison)
| System | What it does | The gap RescueLink fills |
|---|---|---|
| **Bitchat / Bridgefy / Fernweh** | Peer-to-peer offline **chat** | Stop at device-to-device messaging. No rescuer view, no triage, no loop back. |
| **SACHET (India)** | One-way **government** cell broadcast alerts | Authority → citizen only; a victim can't call for help. |
| **SoS India / 112** | Emergency dispatch | Needs **cellular**; useless when towers are down. |
| **RescueLink** | Victim → offline mesh → opportunistic bridge → rescuer map → status back to victim | **Closes the loop** to the people who do the rescuing. |

We do **not** claim to out-encrypt or out-scale the incumbents. We build only the closed loop.

## Offline guarantees (non-negotiable, by design)
- The victim app is **100% functional in airplane mode**: SOS, mesh discovery, multi-hop
  relay, store-carry-forward, siren, saved locations, cached-tile map, chat, profile.
- The internet bridge is **opportunistic and optional**. It never blocks, delays, or gates
  any victim action. If the internet never comes, the mesh works exactly as before.
- The **mesh broadcast fires first**; sync/siren/UI come after and are never on the SOS
  critical path.
- No network call runs on the main thread or crashes when offline — the bridge fails
  silently and WorkManager retries with exponential backoff.
- Triage is **rule-based and explainable** (`backend/triage.py`, mirrored in
  `TriageScorer.java`). It is **not** AI/ML. Nothing in the app or docs claims otherwise.

## Components
- **`/` (Android app)** — existing Java/MVVM/Room/Nearby/osmdroid mesh app.
  New this project: `ConnectivityBridgeManager`, `BackendClient` (OkHttp), `TriageScorer`,
  `MessageEntity.syncedToServer`, real `MessageRelayWorker` upload job, RESPONDER_UPDATE
  injection + victim banner.
- **`/backend`** — FastAPI + SQLite. Idempotent ingest (UPSERT by UUID), rescuer API
  (GeoJSON, token auth), status updates feed, simulation mode.
- **`/dashboard`** — static Leaflet SPA served by FastAPI at `/dashboard`: priority-coloured
  clustered markers, sortable list, status actions, need-density heatmap.

## Setup & run
```bash
# Backend + dashboard
cd backend
pip install -r requirements.txt
uvicorn main:app --host 0.0.0.0 --port 8000
#   Dashboard: http://localhost:8000/dashboard
#   Rescuer API token (default): demo-token   (override with env RESCUELINK_TOKEN)

# Backend tests (no phones needed)
python test_dedup.py
python test_e2e.py

# Android: set BACKEND_URL in app/build.gradle to the machine's LAN IP:8000 for
# on-device testing (emulator default 10.0.2.2:8000 already set). The app MUST still
# build and run with BACKEND_URL unreachable.

# Populate the dashboard with no phones:
curl -X POST "http://localhost:8000/api/simulate?n=8" -H "X-Auth-Token: demo-token"
```

## Demo script
1. Four phones in **airplane mode**. Phone A (trapped) sends SOS, medical note
   "diabetic, low insulin" → shows **QUEUED**.
2. SOS hops A → B → C → D across the offline mesh (hop count climbs).
3. Phone D regains Wi-Fi at the zone edge → auto-bridges → SOS uploads → A shows
   **Reached responders**.
4. Rescuer dashboard: victim appears, flagged **CRITICAL** — reason
   "medical note + battery 11% + unresolved".
5. Responder clicks **En route** → Phone A (still airplane mode) shows
   **"A responder has seen your SOS and is on the way"**, hopped back through the mesh.
6. *"Bitchat and Bridgefy stop at step 2. RescueLink closes the loop to the people who do
   the rescuing."*

## Verification status (honest)
The backend halves are covered by runnable tests (`test_dedup.py`, `test_e2e.py`). The
end-to-end **physical** steps (four phones, airplane mode, on-device mesh relay + bridge)
require real hardware and an Android SDK build; they are **not** automatable here and must
be run on devices. See `DEVICE_CHECKLIST.md`.
