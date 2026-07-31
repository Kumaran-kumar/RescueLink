package com.rescuelink.app.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * SMART-TRIAGE (Phase 3): explainable rule-based priority triage — NOT AI.
 *
 * A faithful mirror of the backend scorer (backend/triage.py) so the victim-side app can
 * order and colour alerts identically while 100% offline. Keep the two in sync.
 *
 * Signals: emergency-type severity, medical note (+ high-acuity keywords), battery level
 * (lower = more urgent), minutes unresolved (escalates), hop count (far from a bridge).
 */
public final class TriageScorer {

    public static final String CRITICAL = "CRITICAL";
    public static final String HIGH = "HIGH";
    public static final String MODERATE = "MODERATE";

    public static class Result {
        public final int points;
        public final String tier;
        public final String reason;
        Result(int points, String tier, String reason) {
            this.points = points; this.tier = tier; this.reason = reason;
        }
    }

    private TriageScorer() {}

    private static int typeSeverity(String type) {
        if (type == null) return 10;
        switch (type) {
            case "Medical": return 40;
            case "Fire": return 35;
            case "Earthquake": return 30;
            case "Flood": return 30;
            case "Cyclone": return 25;
            default: return 10;
        }
    }

    public static Result score(String emergencyType, String medicalNote,
                               int batteryLevel, double minutesUnresolved, int hopCount) {
        int pts = 0;
        List<String> reasons = new ArrayList<>();

        int sev = typeSeverity(emergencyType);
        pts += sev;
        if (sev >= 30 && emergencyType != null) reasons.add(emergencyType.toLowerCase(Locale.US));

        String note = medicalNote == null ? "" : medicalNote.trim();
        if (!note.isEmpty()) {
            pts += 20;
            String low = note.toLowerCase(Locale.US);
            if (low.contains("insulin") || low.contains("diabetic") || low.contains("bleed")
                    || low.contains("cardiac") || low.contains("heart")
                    || low.contains("breath") || low.contains("asthma")) {
                pts += 15;
            }
            reasons.add("medical note");
        }

        if (batteryLevel >= 0) {
            if (batteryLevel <= 10) { pts += 25; reasons.add("battery " + batteryLevel + "%"); }
            else if (batteryLevel <= 25) { pts += 12; reasons.add("battery " + batteryLevel + "%"); }
        }

        if (minutesUnresolved >= 30) { pts += 20; reasons.add("unresolved " + (int) minutesUnresolved + " min"); }
        else if (minutesUnresolved >= 10) { pts += 8; reasons.add("unresolved " + (int) minutesUnresolved + " min"); }

        if (hopCount >= 4) { pts += 10; reasons.add(hopCount + " hops from bridge"); }

        String tier = pts >= 70 ? CRITICAL : (pts >= 40 ? HIGH : MODERATE);
        String reason = tier + ": " + (reasons.isEmpty() ? "baseline" : String.join(" + ", reasons));
        return new Result(pts, tier, reason);
    }
}
