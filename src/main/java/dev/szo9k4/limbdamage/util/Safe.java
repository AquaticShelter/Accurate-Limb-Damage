package dev.szo9k4.limbdamage.util;

import dev.szo9k4.limbdamage.LimbDamageMod;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs mod logic so that a bug in one hit can never crash the server or the game: the exception is
 * logged (at most once per 30 s per location, to keep logs readable) and the event carries on
 * untouched, i.e. as if Limb Damage weren't there for that one hit.
 */
public final class Safe {

    private static final Map<String, Long> LAST_REPORT = new ConcurrentHashMap<>();

    private Safe() {
    }

    public static void run(String where, Runnable task) {
        try {
            task.run();
        } catch (Throwable t) {
            report(where, t);
        }
    }

    public static void report(String where, Throwable t) {
        long now = System.currentTimeMillis();
        Long last = LAST_REPORT.get(where);
        if (last == null || now - last > 30_000L) {
            LAST_REPORT.put(where, now);
            LimbDamageMod.LOGGER.error("[LimbDamage] {} failed; skipped for this event (repeats suppressed for 30s)", where, t);
        }
    }
}
