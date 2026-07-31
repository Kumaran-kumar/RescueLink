package com.rescuelink.app.util;

import com.rescuelink.app.R;
import com.rescuelink.app.data.entity.SavedLocationEntity;

/**
 * FEAT-LOC-01: single source of truth mapping a saved-location category to its icon
 * drawable, tint color, and a human label. Used by both the map and the list screen.
 */
public final class CategoryMeta {

    private CategoryMeta() {}

    public static int iconRes(String category) {
        if (category == null) return R.drawable.ic_cat_custom;
        switch (category) {
            case SavedLocationEntity.CAT_HOME: return R.drawable.ic_cat_home;
            case SavedLocationEntity.CAT_HOSPITAL: return R.drawable.ic_cat_hospital;
            case SavedLocationEntity.CAT_CARE_CENTER: return R.drawable.ic_cat_care;
            case SavedLocationEntity.CAT_SHELTER: return R.drawable.ic_cat_shelter;
            case SavedLocationEntity.CAT_POLICE: return R.drawable.ic_cat_police;
            case SavedLocationEntity.CAT_WATER: return R.drawable.ic_cat_water;
            default: return R.drawable.ic_cat_custom;
        }
    }

    public static int colorRes(String category) {
        if (category == null) return R.color.text_secondary;
        switch (category) {
            case SavedLocationEntity.CAT_HOME: return R.color.status_safe;
            case SavedLocationEntity.CAT_HOSPITAL: return R.color.medical_color;
            case SavedLocationEntity.CAT_CARE_CENTER: return R.color.primary_variant;
            case SavedLocationEntity.CAT_SHELTER: return R.color.status_warning;
            case SavedLocationEntity.CAT_POLICE: return R.color.secondary_variant;
            case SavedLocationEntity.CAT_WATER: return R.color.flood_color;
            default: return R.color.text_secondary;
        }
    }

    /** Ordered categories for the picker chips. */
    public static String[] all() {
        return new String[]{
                SavedLocationEntity.CAT_HOME,
                SavedLocationEntity.CAT_HOSPITAL,
                SavedLocationEntity.CAT_CARE_CENTER,
                SavedLocationEntity.CAT_SHELTER,
                SavedLocationEntity.CAT_POLICE,
                SavedLocationEntity.CAT_WATER,
                SavedLocationEntity.CAT_CUSTOM
        };
    }

    public static int labelRes(String category) {
        if (category == null) return R.string.cat_custom;
        switch (category) {
            case SavedLocationEntity.CAT_HOME: return R.string.cat_home;
            case SavedLocationEntity.CAT_HOSPITAL: return R.string.cat_hospital;
            case SavedLocationEntity.CAT_CARE_CENTER: return R.string.cat_care_center;
            case SavedLocationEntity.CAT_SHELTER: return R.string.cat_shelter;
            case SavedLocationEntity.CAT_POLICE: return R.string.cat_police;
            case SavedLocationEntity.CAT_WATER: return R.string.cat_water;
            default: return R.string.cat_custom;
        }
    }
}
