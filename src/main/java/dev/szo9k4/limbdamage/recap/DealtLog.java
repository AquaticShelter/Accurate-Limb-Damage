package dev.szo9k4.limbdamage.recap;

import dev.szo9k4.limbdamage.Limb;
import dev.szo9k4.limbdamage.config.LimbConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "What I just did to other players": for every player, the damage they dealt to each OTHER player, kept for
 * dealtToast.trackSeconds after their last hit on that player. When the owner dies, up to maxPlayers of those
 * players go into the second ("you dealt") toast: the one who killed them first (if they had damaged that
 * player), then whoever they hit most recently. Attacking someone else naturally pushes older targets out.
 */
public final class DealtLog {

    /** Damage one player has dealt to one other player. */
    public static final class Entry {
        final UUID victim;
        String name = "?";
        float damage;
        int hits;
        long lastTick;
        ItemStack weapon = ItemStack.EMPTY;
        final float[] parts = new float[Limb.values().length];
        float maxHealth = 20f;
        boolean dead;
        boolean killedByMe;

        Entry(UUID victim) {
            this.victim = victim;
        }

        void reset() {
            damage = 0f;
            hits = 0;
            java.util.Arrays.fill(parts, 0f);
            dead = false;
            killedByMe = false;
        }
    }

    private static final Map<UUID, Map<UUID, Entry>> BY_ATTACKER = new ConcurrentHashMap<>();

    private DealtLog() {
    }

    private static long windowTicks() {
        return (long) (LimbConfig.DEALT_TRACK_SECONDS.get() * 20.0);
    }

    /** @param damage the damage actually dealt (final, already capped like the recap); limb may be null. */
    public static void record(ServerPlayer attacker, ServerPlayer victim, float damage, Limb limb, ItemStack weapon) {
        if (damage <= 0f) {
            return;
        }
        long now = attacker.level().getGameTime();
        Entry e = BY_ATTACKER.computeIfAbsent(attacker.getUUID(), k -> new ConcurrentHashMap<>())
                .computeIfAbsent(victim.getUUID(), Entry::new);
        if (e.dead || (e.hits > 0 && now - e.lastTick > windowTicks())) {
            // a new fight, or a new life of the same player: never mix it with the old one (a stale KILL flag or
            // the old life's damage must not leak into this one)
            e.reset();
        }
        String raw = ChatFormatting.stripFormatting(victim.getGameProfile().getName());
        e.name = raw == null || raw.isBlank() ? "?" : raw.trim();
        e.damage += damage;
        e.hits++;
        e.lastTick = now;
        e.maxHealth = victim.getMaxHealth();
        if (!weapon.isEmpty()) {
            e.weapon = weapon;
        }
        if (limb != null) {
            e.parts[limb.ordinal()] += damage;
        }
    }

    /** A death waiting one tick to be confirmed (see {@link #confirmDeaths}). */
    private record PendingDeath(ServerPlayer victim, UUID killer, long tick) {
    }

    private static final List<PendingDeath> PENDING = new ArrayList<>();

    /**
     * A player's death was announced. It is NOT applied yet: mods that revive / "down" players cancel or undo the
     * death right after this event, and a player who is in fact alive must never be shown as DEAD or KILL. The
     * mark is confirmed a tick later, and only if the victim really is dead then.
     */
    public static void markDeath(ServerPlayer victim, Entity killer) {
        synchronized (PENDING) {
            PENDING.add(new PendingDeath(victim, killer != null ? killer.getUUID() : null, victim.level().getGameTime()));
        }
    }

    /** Server tick: applies the deaths that are still true. */
    public static void confirmDeaths(long gameTime) {
        List<PendingDeath> due = null;
        synchronized (PENDING) {
            if (PENDING.isEmpty()) {
                return;
            }
            for (java.util.Iterator<PendingDeath> it = PENDING.iterator(); it.hasNext(); ) {
                PendingDeath p = it.next();
                if (p.tick() < gameTime) {
                    if (due == null) {
                        due = new ArrayList<>();
                    }
                    due.add(p);
                    it.remove();
                }
            }
        }
        if (due == null) {
            return;
        }
        for (PendingDeath p : due) {
            if (p.victim().isAlive()) {
                continue; // revived / cancelled by another mod: not a death
            }
            for (Map.Entry<UUID, Map<UUID, Entry>> attacker : BY_ATTACKER.entrySet()) {
                Entry e = attacker.getValue().get(p.victim().getUUID());
                if (e != null && e.hits > 0 && !e.dead) {
                    e.dead = true;
                    e.killedByMe = p.killer() != null && p.killer().equals(attacker.getKey());
                }
            }
        }
    }

    /** Up to maxPlayers live entries for `me`: the player who killed them first, then the most recently hit. */
    public static List<Entry> snapshot(ServerPlayer me, UUID killerId) {
        Map<UUID, Entry> targets = BY_ATTACKER.get(me.getUUID());
        if (targets == null || targets.isEmpty()) {
            return List.of();
        }
        long now = me.level().getGameTime();
        long window = windowTicks();
        List<Entry> live = new ArrayList<>();
        for (Entry e : targets.values()) {
            if (e.hits > 0 && now - e.lastTick <= window) {
                live.add(e);
            }
        }
        live.sort(Comparator.comparing((Entry e) -> !e.victim.equals(killerId)).thenComparingLong(e -> -e.lastTick));
        int max = LimbConfig.DEALT_MAX_PLAYERS.get();
        return live.size() > max ? new ArrayList<>(live.subList(0, max)) : live;
    }

    /** The packet for the "you dealt" toast (entries must not be empty). */
    public static RecapPacket packet(List<Entry> entries) {
        float[] parts = new float[Limb.values().length];
        List<RecapPacket.RowData> rows = new ArrayList<>();
        for (Entry e : entries) {
            for (int i = 0; i < parts.length; i++) {
                parts[i] += e.parts[i];
            }
            String tag = e.killedByMe ? "KILL" : e.dead ? "DEAD" : "";
            rows.add(new RecapPacket.RowData(e.name, true, false, e.weapon, e.damage, e.hits, tag, e.killedByMe));
        }
        return new RecapPacket(entries.get(0).maxHealth, parts, rows.size(), rows, false, true);
    }

    public static void clear(UUID attacker) {
        BY_ATTACKER.remove(attacker);
    }

    public static void clearAll() {
        BY_ATTACKER.clear();
        synchronized (PENDING) {
            PENDING.clear();
        }
    }

    /** Drops expired entries so a long session doesn't accumulate every player ever hit. */
    public static void sweep(long gameTime) {
        long window = windowTicks();
        BY_ATTACKER.values().removeIf(targets -> {
            targets.values().removeIf(e -> gameTime - e.lastTick > window);
            return targets.isEmpty();
        });
    }
}
