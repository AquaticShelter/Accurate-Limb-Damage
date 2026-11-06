package dev.szo9k4.limbdamage.effect;

import dev.szo9k4.limbdamage.LimbDamageMod;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

/**
 * Compatibility with the "Regeneration tweak" mod (modid "regen"). That mod keeps a static
 * Map&lt;UUID, Long&gt; (net.mcreator.regen.ServerEvents#lastDamageTick) of the game tick each player
 * last took damage, and only lets its HP and Absorption regen run once delaySeconds have passed
 * since that tick. Pushing a player's entry forward therefore delays both, using the mod's own
 * rules instead of fighting them. Pure reflection; if the mod is absent or changes, this does nothing.
 */
final class RegenModCompat {

    private static boolean resolved;
    private static Map<UUID, Long> lastDamageTick;

    private RegenModCompat() {
    }

    @SuppressWarnings("unchecked")
    private static synchronized Map<UUID, Long> map() {
        if (!resolved) {
            resolved = true;
            try {
                Class<?> events = Class.forName("net.mcreator.regen.ServerEvents");
                Field field = events.getDeclaredField("lastDamageTick");
                field.setAccessible(true);
                lastDamageTick = (Map<UUID, Long>) field.get(null);
                LimbDamageMod.LOGGER.info("[LimbDamage] Regeneration tweak mod found: Fragile will delay its regen.");
            } catch (Throwable t) {
                lastDamageTick = null; // mod not installed (or layout changed)
            }
        }
        return lastDamageTick;
    }

    /** Makes the regen mod treat `gameTick` (may be in the future) as the player's last damage; never lowers it. */
    static void pushLastDamage(UUID player, long gameTick) {
        Map<UUID, Long> map = map();
        if (map != null) {
            map.merge(player, gameTick, (old, now) -> Math.max(old, now));
        }
    }
}
