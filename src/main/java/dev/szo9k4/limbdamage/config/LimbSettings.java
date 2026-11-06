package dev.szo9k4.limbdamage.config;

import dev.isxander.yacl3.config.v2.api.SerialEntry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The gameplay settings file (config/limbdamage-common.json5), as plain data. YACL reads and writes it;
 * {@link LimbConfig} is the facade the rest of the mod reads through.
 *
 * Damage multipliers are stored as multipliers (0.8 = 20 % less damage), exactly like the old TOML file, so
 * nothing about existing setups changes. The config screen shows them as a damage-reduction percentage.
 */
public final class LimbSettings {

    public static final class Mult {
        @SerialEntry(comment = "Head. 1.0 = normal. TACZ already adds a headshot bonus, so leave it unless you want both.")
        public double head = 1.0;
        @SerialEntry(comment = "Chest.")
        public double body = 1.0;
        @SerialEntry(comment = "Abdomen.")
        public double abdomen = 1.0;
        @SerialEntry(comment = "Arms.")
        public double arms = 0.8;
        @SerialEntry(comment = "Legs.")
        public double legs = 0.8;
        @SerialEntry(comment = "Feet (bottom of the legs, height in geometry.feetHeight).")
        public double feet = 0.8;
    }

    public static final class Entities {
        @SerialEntry(comment = "Players get limb damage.")
        public boolean affectPlayers = true;
        @SerialEntry(comment = "Mobs get limb damage (see autoDetectHumanoid / extraEntityIds).")
        public boolean affectMobs = true;
        @SerialEntry(comment = "Mobs shaped like a player count as humanoid (width 0.45-0.75, height 1.5-2.2).")
        public boolean autoDetectHumanoid = true;
        @SerialEntry(comment = "Always humanoid, e.g. \"minecraft:iron_golem\".")
        public List<String> extraEntityIds = new ArrayList<>();
        @SerialEntry(comment = "Never get limb damage. Beats everything else.")
        public List<String> blacklistEntityIds = new ArrayList<>();
    }

    public static final class Geometry {
        @SerialEntry(comment = "Hits at or below this fraction of the box height are LEGS (only when playerProportions = false).")
        public double legsTopFraction = 0.42;
        @SerialEntry(comment = "Hits at or above this fraction of the box height are HEAD (only when playerProportions = false).")
        public double headBottomFraction = 0.78;
        @SerialEntry(comment = "Sideways distance from the body axis (blocks, scaled by width/0.6) beyond which a hit counts as ARMS.")
        public double armMinLateral = 0.24;
        @SerialEntry(comment = "Same idea for the front/back axis. Deliberately looser; tune armMinLateral first.")
        public double armMinForward = 0.55;
        @SerialEntry(comment = "The arms rule only applies below this fraction of the box height.")
        public double armTopFraction = 0.86;
        @SerialEntry(comment = "Height of the FEET zone above the ground (blocks of a 1.8 tall player).")
        public double feetHeight = 0.2;
        @SerialEntry(comment = "Check the shot's whole path against the player model, not one point.")
        public boolean rayClassification = true;
        @SerialEntry(comment = "Use the real player model for the borders.")
        public boolean playerProportions = true;
        @SerialEntry(comment = "Fix impossible Accurate Hitboxes results by checking the real hit height.")
        public boolean sanityGuards = true;
    }

    public static final class Obb {
        @SerialEntry(comment = "Use Accurate Hitboxes' per-bone geometry when available.")
        public boolean useObbClassification = true;
        @SerialEntry(comment = "How close (blocks) to the highest cube-shaped bone a bone must be to count as HEAD.")
        public double obbHeadYTolerance = 0.35;
        @SerialEntry(comment = "Bones in the bottom fraction of the skeleton's own height count as LEGS.")
        public double obbLegHeightFraction = 0.35;
        @SerialEntry(comment = "Horizontal distance (blocks) from the head bone beyond which a bone counts as ARMS.")
        public double obbArmOffset = 0.2;
    }

    public static final class Recap {
        @SerialEntry(comment = "Send the recap when a player dies.")
        public boolean enableDeathRecap = true;
        @SerialEntry(comment = "Fight ends after this many seconds without damage.")
        public double fightTimeoutSeconds = 30.0;
        @SerialEntry(comment = "Cap each hit to the health the player had left.")
        public boolean capToRemainingHealth = false;
    }

    public static final class Fragile {
        @SerialEntry(comment = "Pause the vanilla Regeneration effect while Fragile.")
        public boolean pauseRegenerationEffect = true;
        @SerialEntry(comment = "Block natural/food regeneration while Fragile.")
        public boolean blockNaturalRegen = true;
        @SerialEntry(comment = "Keep the 'Regeneration tweak' mod's timer pushed back while Fragile.")
        public boolean syncRegenTweakMod = true;
        @SerialEntry(comment = "Extra seconds after Fragile ends before regeneration resumes.")
        public double extraDelaySeconds = 2.0;
    }

    public static final class Dummy {
        @SerialEntry(comment = "Default max health of a new dummy.")
        public double health = 20.0;
        @SerialEntry(comment = "Default damage reduction of a new dummy, in percent (0.1 steps, e.g. 55.5).")
        public double damageReductionPercent = 0.0;
        @SerialEntry(comment = "Dummy heals back instead of dying (each dummy can override).")
        public boolean cannotDie = true;
        @SerialEntry(comment = "Seconds without a hit before a dummy heals fully. 0 = never.")
        public double autoHealSeconds = 3.0;
        @SerialEntry(comment = "Show limb, multiplier and damage in the attacker's action bar.")
        public boolean showHitInfo = true;
        @SerialEntry(comment = "Send the attacker a report listing every hit once they stop attacking.")
        public boolean reportEnabled = true;
        @SerialEntry(comment = "Seconds without a hit before the report is sent.")
        public double reportDelaySeconds = 3.0;
        @SerialEntry(comment = "Most hits remembered for one report.")
        public int reportMaxHits = 64;
        @SerialEntry(comment = "Dummy dies and comes back (each dummy can override).")
        public boolean dieAndRespawn = false;
        @SerialEntry(comment = "Seconds between a dummy's death and its return.")
        public double respawnDelaySeconds = 2.0;
        @SerialEntry(comment = "Anyone can change a dummy's gear. false = owner and ops only.")
        public boolean publicEquipment = true;
    }

    public static final class Dealt {
        @SerialEntry(comment = "Track the damage players deal to other players and show the 'damage done' strip when they die.")
        public boolean enabled = true;
        @SerialEntry(comment = "A player stays listed this long after your last hit on them (seconds).")
        public double trackSeconds = 30.0;
        @SerialEntry(comment = "Most players listed.")
        public int maxPlayers = 2;
    }

    public static final class WeaponCategories {
        @SerialEntry(comment = "false = every gun uses the global multipliers.")
        public boolean enabled = true;
        @SerialEntry(comment = "Ignore TACZ's own headshot multiplier, only use the head value here.")
        public boolean headshotOverride = false;
        @SerialEntry(comment = "Make TACZ's headshot flag match this mod's verdict.")
        public boolean headshotGuard = true;
        @SerialEntry(comment = "Put a gun in a category, like \"tacz:ak47=rifle\".")
        public List<String> gunTypeOverrides = new ArrayList<>();
        @SerialEntry(comment = "Per category and part. -1 = use the global value, otherwise this multiplier.")
        public Map<String, Map<String, Double>> values = defaults();

        static Map<String, Map<String, Double>> defaults() {
            Map<String, Map<String, Double>> all = new LinkedHashMap<>();
            for (String category : CategoryConfig.CATEGORIES) {
                Map<String, Double> parts = new LinkedHashMap<>();
                for (String part : CategoryConfig.PART_KEYS) {
                    parts.put(part, -1.0);
                }
                all.put(category, parts);
            }
            return all;
        }
    }

    public static final class Debug {
        @SerialEntry(comment = "Log every hit to the console (spammy).")
        public boolean logHits = false;
        @SerialEntry(comment = "Chat line with part, attacker, weapon and damage on a kill.")
        public boolean announceKills = true;
    }

    @SerialEntry public Mult multipliers = new Mult();
    @SerialEntry public Entities entities = new Entities();
    @SerialEntry public Geometry geometry = new Geometry();
    @SerialEntry public Obb obbClassification = new Obb();
    @SerialEntry(comment = "Melee hits get limb damage too (from the attacker's aim).")
    public boolean applyToMelee = true;
    @SerialEntry public Recap recap = new Recap();
    @SerialEntry public Fragile fragile = new Fragile();
    @SerialEntry public Dummy dummy = new Dummy();
    @SerialEntry public WeaponCategories weaponCategories = new WeaponCategories();
    @SerialEntry public Dealt dealtToast = new Dealt();
    @SerialEntry public Debug debug = new Debug();

    /** Repairs anything a hand edit could have broken (nulls, out-of-range numbers, missing categories). */
    public void sanitize() {
        if (multipliers == null) multipliers = new Mult();
        if (entities == null) entities = new Entities();
        if (geometry == null) geometry = new Geometry();
        if (obbClassification == null) obbClassification = new Obb();
        if (recap == null) recap = new Recap();
        if (fragile == null) fragile = new Fragile();
        if (dummy == null) dummy = new Dummy();
        if (weaponCategories == null) weaponCategories = new WeaponCategories();
        if (dealtToast == null) dealtToast = new Dealt();
        if (debug == null) debug = new Debug();
        if (entities.extraEntityIds == null) entities.extraEntityIds = new ArrayList<>();
        if (entities.blacklistEntityIds == null) entities.blacklistEntityIds = new ArrayList<>();
        if (weaponCategories.gunTypeOverrides == null) weaponCategories.gunTypeOverrides = new ArrayList<>();

        multipliers.head = clamp(multipliers.head, 0.0, 10.0);
        multipliers.body = clamp(multipliers.body, 0.0, 10.0);
        multipliers.abdomen = clamp(multipliers.abdomen, 0.0, 10.0);
        multipliers.arms = clamp(multipliers.arms, 0.0, 10.0);
        multipliers.legs = clamp(multipliers.legs, 0.0, 10.0);
        multipliers.feet = clamp(multipliers.feet, 0.0, 10.0);

        geometry.legsTopFraction = clamp(geometry.legsTopFraction, 0.0, 1.0);
        geometry.headBottomFraction = clamp(geometry.headBottomFraction, 0.0, 1.0);
        geometry.armMinLateral = clamp(geometry.armMinLateral, 0.0, 2.0);
        geometry.armMinForward = clamp(geometry.armMinForward, 0.0, 2.0);
        geometry.armTopFraction = clamp(geometry.armTopFraction, 0.0, 1.0);
        geometry.feetHeight = clamp(geometry.feetHeight, 0.0, 0.7);
        obbClassification.obbHeadYTolerance = clamp(obbClassification.obbHeadYTolerance, 0.0, 2.0);
        obbClassification.obbLegHeightFraction = clamp(obbClassification.obbLegHeightFraction, 0.0, 1.0);
        obbClassification.obbArmOffset = clamp(obbClassification.obbArmOffset, 0.0, 2.0);

        recap.fightTimeoutSeconds = clamp(recap.fightTimeoutSeconds, 1.0, 600.0);
        fragile.extraDelaySeconds = clamp(fragile.extraDelaySeconds, 0.0, 600.0);

        dummy.health = clamp(dummy.health, 1.0, 2048.0);
        dummy.damageReductionPercent = Math.round(clamp(dummy.damageReductionPercent, 0.0, 100.0) * 10.0) / 10.0;
        dummy.autoHealSeconds = clamp(dummy.autoHealSeconds, 0.0, 600.0);
        dummy.reportDelaySeconds = clamp(dummy.reportDelaySeconds, 0.5, 60.0);
        dummy.reportMaxHits = Math.max(1, Math.min(512, dummy.reportMaxHits));
        dummy.respawnDelaySeconds = clamp(dummy.respawnDelaySeconds, 0.5, 60.0);

        dealtToast.trackSeconds = clamp(dealtToast.trackSeconds, 5.0, 300.0);
        dealtToast.maxPlayers = Math.max(1, Math.min(4, dealtToast.maxPlayers));

        if (weaponCategories.values == null) {
            weaponCategories.values = WeaponCategories.defaults();
        }
        for (String category : CategoryConfig.CATEGORIES) {
            Map<String, Double> parts = weaponCategories.values.get(category);
            if (parts == null) {
                parts = new LinkedHashMap<>();
                weaponCategories.values.put(category, parts);
            }
            for (String part : CategoryConfig.PART_KEYS) {
                Double v = parts.get(part.toLowerCase(Locale.ROOT));
                if (v == null || v.isNaN()) {
                    parts.put(part, -1.0);
                } else {
                    parts.put(part, v < 0.0 ? -1.0 : Math.min(10.0, v));
                }
            }
        }
    }

    static double clamp(double v, double lo, double hi) {
        return Double.isNaN(v) ? lo : Math.max(lo, Math.min(hi, v));
    }
}
