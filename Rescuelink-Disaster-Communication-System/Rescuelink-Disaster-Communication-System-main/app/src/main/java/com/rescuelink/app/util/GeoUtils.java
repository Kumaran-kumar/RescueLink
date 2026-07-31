package com.rescuelink.app.util;

import java.util.Locale;

/**
 * FEAT-LOC-01: offline geo math. No network, no Geocoder — pure trig so it works with
 * cached map tiles and zero connectivity.
 */
public final class GeoUtils {

    private static final double EARTH_RADIUS_M = 6_371_000.0;

    private static final String[] COMPASS_16 = {
            "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
            "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"
    };

    private GeoUtils() {}

    /** Great-circle (Haversine) distance in metres between two lat/lng points. */
    public static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_M * c;
    }

    /** Initial bearing in degrees [0,360) from point 1 to point 2. */
    public static double bearingDegrees(double lat1, double lon1, double lat2, double lon2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double dLon = Math.toRadians(lon2 - lon1);
        double y = Math.sin(dLon) * Math.cos(phi2);
        double x = Math.cos(phi1) * Math.sin(phi2)
                - Math.sin(phi1) * Math.cos(phi2) * Math.cos(dLon);
        double theta = Math.atan2(y, x);
        return (Math.toDegrees(theta) + 360.0) % 360.0;
    }

    /** 16-point compass label for a bearing (e.g. 42° -> "NE"). */
    public static String compass16(double bearing) {
        int idx = (int) Math.round(((bearing % 360.0) / 22.5)) % 16;
        if (idx < 0) idx += 16;
        return COMPASS_16[idx];
    }

    /** e.g. "820 m" under 1 km, else "3.4 km". */
    public static String formatDistance(double meters) {
        if (meters < 1000) {
            return String.format(Locale.US, "%.0f m", meters);
        }
        return String.format(Locale.US, "%.1f km", meters / 1000.0);
    }

    /** e.g. "NE (42°)". */
    public static String formatBearing(double bearing) {
        return String.format(Locale.US, "%s (%.0f°)", compass16(bearing), bearing);
    }
}
