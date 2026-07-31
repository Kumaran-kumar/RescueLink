# RescueLink — device verification checklist

I (the assistant) could not run these: they need an Android SDK build and multiple
physical phones. Each maps to a phase's `verify` gate. Run in order; don't mark a phase
done until its box passes.

## Prereqs
- Build the app with a real Android SDK (`./gradlew assembleDebug`) — this environment had
  no SDK/`gradlew`, so the app was verified only statically.
- Start the backend: `cd backend && uvicorn main:app --host 0.0.0.0 --port 8000`.
- Set `BACKEND_URL` in `app/build.gradle` to the dev machine's **LAN IP:8000** (not
  `10.0.2.2`) for real devices. Rebuild.

## Phase 1 — BRIDGE-CORE
- [ ] Backend unit tests pass: `cd backend && python test_dedup.py && python test_e2e.py`.
- [ ] 3 phones airplane mode, send SOS on each; relay to a 4th; enable Wi-Fi on the 4th
      ONLY → all 3 appear **once** on the backend with location + medical + hop count.
- [ ] Keep the 4th offline too → mesh still relays/stores exactly as before (bridge waits).
- [ ] Kill the app after an offline SOS, reopen with Wi-Fi → WorkManager uploads it.
- [ ] Build with `BACKEND_URL` pointing at a dead host → app still builds, runs, sends SOS,
      no crash, no UI block.

## Phase 2 — RESCUER-DASHBOARD
- [ ] Open `http://<host>:8000/dashboard`; alerts from Phase 1 appear live (≤5s).
- [ ] Mark one **En route** → list + marker update.
- [ ] Toggle **Heatmap** → density layer renders.
- [ ] `curl -X POST ".../api/simulate?n=8" -H "X-Auth-Token: demo-token"` → 8 victims appear.

## Phase 3 — SMART-TRIAGE
- [ ] Inject several SOS with different battery/medical/age (or use `/api/simulate`) →
      the medically-urgent, low-battery, long-unresolved victim ranks **top** with a
      human-readable reason, on BOTH the dashboard and the victim app's alert list.
- [ ] Confirm no "AI"/"NLP" wording anywhere (grep already clean in repo).

## Phase 4 — TWO-WAY
- [ ] Rescuer marks **En route**; exactly one bridge device online → the status hops back
      through airplane-mode phones → the original victim's SOS screen shows
      **"A responder has seen your SOS and is on the way."**

## Phase 5 — HARDEN + DEMO
- [ ] Restart a phone mid-mesh → no re-broadcast storm (seen-set seeded from DB).
- [ ] 3+ devices in range → connections stabilize (tie-breaker), no connect/disconnect churn.
- [ ] Run the full `SYSTEM_README.md` demo script **twice**, victim device NEVER leaving
      airplane mode.
