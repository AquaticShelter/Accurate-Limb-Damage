package dev.szo9k4.limbdamage.config;

import dev.szo9k4.limbdamage.BodyPart;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Per-weapon-category limb multipliers for TACZ guns. Each category has its OWN value for every body part;
 * -1 means "not set: use the global value". The category of a gun is TACZ's own gun type; gunTypeOverrides
 * can remap individual guns ("tacz:m700=sniper"). The data lives in LimbSettings.weaponCategories.
 */
public final class CategoryConfig {

    /** TACZ's gun types. pistol / rifle / sniper / rpg are the main four; smg, shotgun and mg exist too. */
    public static final List<String> CATEGORIES = List.of("pistol", "rifle", "sniper", "rpg", "smg", "shotgun", "mg");
    /** Keys used inside each category block, in BodyPart order. */
    public static final List<String> PART_KEYS = List.of("head", "body", "abdomen", "arms", "legs", "feet");

    public static final Val<Boolean> ENABLED = new Val<>(() -> LimbConfig.settings().weaponCategories.enabled);
    public static final Val<Boolean> HEADSHOT_OVERRIDE = new Val<>(() -> LimbConfig.settings().weaponCategories.headshotOverride);
    public static final Val<Boolean> HEADSHOT_GUARD = new Val<>(() -> LimbConfig.settings().weaponCategories.headshotGuard);
    public static final Val<List<? extends String>> OVERRIDES = new Val<>(() -> LimbConfig.settings().weaponCategories.gunTypeOverrides);

    private CategoryConfig() {
    }

    public static String key(BodyPart part) {
        return part.name().toLowerCase(Locale.ROOT);
    }

    public static boolean exists(String category) {
        return CATEGORIES.contains(category);
    }

    /** The category's own multiplier for a part, or -1 when not set / category unknown / feature off. */
    public static double get(String category, BodyPart part) {
        if (category == null || !ENABLED.get()) {
            return -1.0;
        }
        return raw(category, part);
    }

    /** Like {@link #get} but ignores the master switch (used by the config screen and /limbdamage multipliers). */
    public static double raw(String category, BodyPart part) {
        Map<String, Map<String, Double>> all = LimbConfig.settings().weaponCategories.values;
        Map<String, Double> parts = all == null ? null : all.get(category);
        Double v = parts == null ? null : parts.get(key(part));
        return v == null ? -1.0 : v;
    }

    /** @return false when the category is unknown */
    public static boolean set(String category, BodyPart part, double value) {
        if (!exists(category)) {
            return false;
        }
        Map<String, Map<String, Double>> all = LimbConfig.settings().weaponCategories.values;
        all.computeIfAbsent(category, k -> new java.util.LinkedHashMap<>()).put(key(part), value < 0.0 ? -1.0 : Math.min(10.0, value));
        return true;
    }

    /** Short label for report rows ("SNP", "RFL"...). */
    public static String code(String category) {
        return switch (category) {
            case "pistol" -> "PST";
            case "rifle" -> "RFL";
            case "sniper" -> "SNP";
            case "rpg" -> "RPG";
            case "smg" -> "SMG";
            case "shotgun" -> "SHT";
            case "mg" -> "MG";
            default -> category.toUpperCase(Locale.ROOT);
        };
    }
}
