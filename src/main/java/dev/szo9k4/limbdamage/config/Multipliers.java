package dev.szo9k4.limbdamage.config;

import dev.szo9k4.limbdamage.BodyPart;
import java.util.Locale;

/**
 * THE place that decides which multiplier a hit gets, and says where it came from.
 *
 * <pre>
 *   1. Fragile effect on the victim, part is arms/legs/feet   -> x1.0                (Source.FRAGILE)
 *   2. TACZ gun hit AND its category has a value (>= 0) set   -> that value          (Source.CATEGORY)
 *      -- it REPLACES the global value; the two are never multiplied together --
 *   3. TACZ gun hit, category value is -1 (inherit)           -> the global value    (Source.CATEGORY_INHERITED)
 *   4. anything else (melee, arrows, non-TACZ, ...)          -> the global value    (Source.GLOBAL)
 * </pre>
 *
 * Step 1 is applied by the caller (DamageHandler) via {@link #fragile()}.
 */
public final class Multipliers {

    public enum Source { GLOBAL, CATEGORY, CATEGORY_INHERITED, FRAGILE }

    /** @param category the weapon category involved (null for GLOBAL / FRAGILE). */
    public record Resolved(double value, Source source, String category) {

        /** Short human description for logs and the dummy's action bar. */
        public String describe() {
            return switch (source) {
                case GLOBAL -> "global";
                case CATEGORY -> category + " override";
                case CATEGORY_INHERITED -> category + ": inherits global";
                case FRAGILE -> "Fragile effect";
            };
        }

        /** Compact label for a report row: "x1.50", "x2.00 SNP" (category value), "x1.00 FRG" (Fragile). */
        public String tag() {
            String x = String.format(Locale.ROOT, "x%.2f", value);
            return switch (source) {
                case CATEGORY -> x + " " + CategoryConfig.code(category);
                case FRAGILE -> x + " FRG";
                default -> x;
            };
        }

        /** True when this value did not come from the plain global config (shown highlighted). */
        public boolean special() {
            return source == Source.CATEGORY || source == Source.FRAGILE;
        }
    }

    private Multipliers() {
    }

    /** The global multiplier of a part. */
    public static double global(BodyPart part) {
        return globalValue(part).get();
    }

    private static Val<Double> globalValue(BodyPart part) {
        return switch (part) {
            case HEAD -> LimbConfig.HEAD_MULTIPLIER;
            case BODY -> LimbConfig.BODY_MULTIPLIER;
            case ABDOMEN -> LimbConfig.ABDOMEN_MULTIPLIER;
            case ARMS -> LimbConfig.ARMS_MULTIPLIER;
            case LEGS -> LimbConfig.LEGS_MULTIPLIER;
            case FEET -> LimbConfig.FEET_MULTIPLIER;
        };
    }

    /** Sets and saves a global multiplier (used by /limbdamage setmult). */
    public static void setGlobal(BodyPart part, double value) {
        var m = LimbConfig.settings().multipliers;
        double v = Math.max(0.0, Math.min(10.0, value));
        switch (part) {
            case HEAD -> m.head = v;
            case BODY -> m.body = v;
            case ABDOMEN -> m.abdomen = v;
            case ARMS -> m.arms = v;
            case LEGS -> m.legs = v;
            case FEET -> m.feet = v;
        }
        LimbConfig.save();
    }

    /** Multiplier -> "damage reduction %" shown by the config screen: 0.8 -> 20.0, 1.0 -> 0.0, 1.5 -> -50.0. One decimal. */
    public static double toReductionPercent(double multiplier) {
        return Math.round((1.0 - multiplier) * 1000.0) / 10.0;
    }

    /** "Damage reduction %" -> multiplier: 55.5 -> 0.445. Rounded to 4 decimals so the file stays readable. */
    public static double fromReductionPercent(double percent) {
        return Math.round((1.0 - percent / 100.0) * 10000.0) / 10000.0;
    }

    /** Steps 2-4. category may be null (not a TACZ gun hit / unknown type). */
    public static Resolved resolve(BodyPart part, String category) {
        if (category == null) {
            return new Resolved(global(part), Source.GLOBAL, null);
        }
        double own = CategoryConfig.get(category, part);
        return own >= 0.0
                ? new Resolved(own, Source.CATEGORY, category)
                : new Resolved(global(part), Source.CATEGORY_INHERITED, category);
    }

    public static Resolved fragile() {
        return new Resolved(1.0, Source.FRAGILE, null);
    }
}
