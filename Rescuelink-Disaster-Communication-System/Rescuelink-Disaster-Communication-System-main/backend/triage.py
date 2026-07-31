"""Explainable rule-based priority triage (Phase 3).

NOT AI. A transparent weighted score over signals a coordinator would actually use.
The exact same rules are mirrored on the Android client (TriageScorer.java) so the
victim-side app can order/colour alerts identically while fully offline.

Signals:
  - emergency-type severity
  - presence of a medical note (and diabetic/insulin/bleeding keywords bump it)
  - battery level (lower = more urgent; the victim may go dark)
  - minutes since first seen (unresolved alerts escalate)
  - hop count (far from a bridge = harder to reach)

Output: tier CRITICAL | HIGH | MODERATE + a human-readable reason string.
"""

TYPE_SEVERITY = {
    "Medical": 40,
    "Fire": 35,
    "Earthquake": 30,
    "Flood": 30,
    "Cyclone": 25,
    "Other": 10,
}


def score(*, emergency_type=None, medical_note=None, battery_level=None,
          minutes_unresolved=0, hop_count=0):
    """Return (points:int, tier:str, reason:str)."""
    pts = 0
    reasons = []

    sev = TYPE_SEVERITY.get(emergency_type or "Other", 10)
    pts += sev
    if sev >= 30:
        reasons.append((emergency_type or "emergency").lower())

    note = (medical_note or "").strip()
    if note:
        pts += 20
        low = note.lower()
        # High-acuity keywords worth an extra bump.
        if any(k in low for k in ("insulin", "diabetic", "bleed", "cardiac", "heart", "breath", "asthma")):
            pts += 15
        reasons.append("medical note")

    if battery_level is not None and battery_level >= 0:
        if battery_level <= 10:
            pts += 25
            reasons.append(f"battery {battery_level}%")
        elif battery_level <= 25:
            pts += 12
            reasons.append(f"battery {battery_level}%")

    if minutes_unresolved >= 30:
        pts += 20
        reasons.append(f"unresolved {int(minutes_unresolved)} min")
    elif minutes_unresolved >= 10:
        pts += 8
        reasons.append(f"unresolved {int(minutes_unresolved)} min")

    if hop_count >= 4:
        pts += 10
        reasons.append(f"{hop_count} hops from bridge")

    if pts >= 70:
        tier = "CRITICAL"
    elif pts >= 40:
        tier = "HIGH"
    else:
        tier = "MODERATE"

    reason = tier + ": " + (" + ".join(reasons) if reasons else "baseline")
    return pts, tier, reason
