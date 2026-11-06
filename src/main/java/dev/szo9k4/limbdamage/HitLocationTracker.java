package dev.szo9k4.limbdamage;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Short-lived map of "the exact position where <target> was just hit" plus, when the source knew
 * them, the path the shot travelled (rayStart -> rayEnd), which BodyPart Accurate Hitboxes already
 * decided it was, and the signed lateral offset of the hit bone (for left/right limbs).
 * Written by whichever precise source fires first (vanilla ProjectileImpactEvent, or the TACZ
 * getHitResult mixin) and consumed moments later — same server tick — by DamageHandler.
 *
 * Keyed by target UUID rather than by projectile: TACZ applies damage from inside its own bullet
 * tick where the bullet is still mid-flight, so matching "damage source entity == projectile at
 * position X" doesn't hold there; the target is the one stable identity across both pipelines.
 *
 * Entries expire after a couple of ticks and are consumed destructively on read, so one recorded
 * location can never be reused for a later, unrelated damage event. On top of that, an entry made
 * by a bullet is NOT spent on a damage event that has no attacker at all (fire, poison, bleeding
 * from an incendiary round...): that event is skipped and the entry stays for the bullet's own damage.
 */
public final class HitLocationTracker {

    private static final long MAX_AGE_TICKS = 2;

    /** lateralHint: signed lateral offset (+ = victim's left) of the hit bone from the victim's center line,
     *  or NaN when the source had none. rayStart/rayEnd: the shot's path, or null when unknown. */
    public record Consumed(Vec3 position, BodyPart preclassifiedPart, double lateralHint,
                           Vec3 rayStart, Vec3 rayEnd) {
    }

    /** bulletId: the projectile that made the hit (null for position-only sources); uses: how many damage
     *  events have already read it. */
    private record Entry(Vec3 position, BodyPart preclassifiedPart, double lateralHint,
                         Vec3 rayStart, Vec3 rayEnd, UUID bulletId, Boolean headVerdict, int uses, long gameTime) {
    }

    /** TACZ hurts the target up to TWICE per bullet (a normal part and an armor-piercing part), so a bullet's
     *  location must serve this many damage events before it is spent. */
    private static final int MAX_USES_PER_BULLET = 2;

    private static final Map<UUID, Entry> RECENT_HITS = new ConcurrentHashMap<>();

    private HitLocationTracker() {
    }

    /** Position-only source (vanilla projectiles). */
    public static void record(Entity target, Vec3 hitPosition) {
        record(target, hitPosition, null);
    }

    public static void record(Entity target, Vec3 hitPosition, BodyPart preclassifiedPart) {
        record(target, hitPosition, preclassifiedPart, Double.NaN);
    }

    public static void record(Entity target, Vec3 hitPosition, BodyPart preclassifiedPart, double lateralHint) {
        record(target, hitPosition, preclassifiedPart, lateralHint, null, null, null);
    }

    public static void record(Entity target, Vec3 hitPosition, BodyPart preclassifiedPart, double lateralHint,
                              Vec3 rayStart, Vec3 rayEnd, UUID bulletId) {
        if (target == null || hitPosition == null || target.level().isClientSide()) {
            return;
        }
        RECENT_HITS.put(target.getUUID(), new Entry(hitPosition, preclassifiedPart, lateralHint,
                rayStart, rayEnd, bulletId, null, 0, target.level().getGameTime()));
    }

    /** The mixin's final answer to "was this bullet hit on the head?" (after Accurate Hitboxes, the
     *  sanity guards and the player model). Read by the TACZ pre-damage hook to veto a wrong headshot bonus. */
    public static void markHeadVerdict(Entity target, boolean head) {
        RECENT_HITS.computeIfPresent(target.getUUID(), (id, e) -> new Entry(e.position(), e.preclassifiedPart(),
                e.lateralHint(), e.rayStart(), e.rayEnd(), e.bulletId(), head, e.uses(), e.gameTime()));
    }

    /** Non-destructive: the head verdict recorded for this target within the last couple of ticks, or null. */
    public static Boolean peekHeadVerdict(Entity target) {
        Entry e = RECENT_HITS.get(target.getUUID());
        if (e == null) {
            return null;
        }
        long age = target.level().getGameTime() - e.gameTime();
        return age >= 0 && age <= MAX_AGE_TICKS ? e.headVerdict() : null;
    }

    public static Consumed consume(Entity target) {
        return consume(target, null);
    }

    /**
     * Returns the hit recorded for this target within the last couple of ticks, or null.
     *
     *  - Position-only records (vanilla projectiles) are consumed on the first read.
     *  - A bullet's record belongs to THAT bullet: it is only returned for damage whose direct entity is the
     *    same bullet (so fire/poison ticks and other bullets never borrow it), and it is kept for the bullet's
     *    second damage event (TACZ's armor-piercing part) before being spent.
     */
    public static Consumed consume(Entity target, DamageSource source) {
        UUID id = target.getUUID();
        Entry entry = RECENT_HITS.get(id);
        if (entry == null) {
            return null;
        }
        long age = target.level().getGameTime() - entry.gameTime();
        if (age < 0 || age > MAX_AGE_TICKS) {
            RECENT_HITS.remove(id, entry);
            return null;
        }
        if (entry.bulletId() != null) {
            Entity direct = source != null ? source.getDirectEntity() : null;
            if (direct == null || !entry.bulletId().equals(direct.getUUID())) {
                return null; // not this bullet's damage: leave the record for its owner
            }
            if (entry.uses() + 1 >= MAX_USES_PER_BULLET) {
                RECENT_HITS.remove(id, entry);
            } else {
                RECENT_HITS.replace(id, entry, new Entry(entry.position(), entry.preclassifiedPart(), entry.lateralHint(),
                        entry.rayStart(), entry.rayEnd(), entry.bulletId(), entry.headVerdict(), entry.uses() + 1,
                        entry.gameTime()));
            }
        } else {
            RECENT_HITS.remove(id, entry);
        }
        return new Consumed(entry.position(), entry.preclassifiedPart(), entry.lateralHint(),
                entry.rayStart(), entry.rayEnd());
    }

    /** Cheap periodic sweep so entries for never-damaged targets don't accumulate forever. */
    public static void sweep(long currentGameTime) {
        if (RECENT_HITS.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Entry>> iterator = RECENT_HITS.entrySet().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next().getValue();
            if (currentGameTime - entry.gameTime() > MAX_AGE_TICKS) {
                iterator.remove();
            }
        }
    }

    /** Server start/stop: game time restarts per world, so stale entries must not survive. */
    public static void clearAll() {
        RECENT_HITS.clear();
    }
}
