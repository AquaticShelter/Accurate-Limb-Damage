package dev.szo9k4.limbdamage.recap;

import dev.szo9k4.limbdamage.Limb;
import dev.szo9k4.limbdamage.config.LimbConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side log of everything that hurt each player during the current "fight": per attacker
 * (damage total, hit count, last weapon held) plus damage per limb (left/right arms and legs are
 * separate). Consumed once on death and sent to the dead player's client as a RecapPacket.
 *
 * A fight resets when the gap between two hits exceeds fightTimeoutSeconds, so the recap only
 * shows the damage from the fight that actually killed you.
 */
public final class DamageLog {

    public static final class Row {
        public final String name;
        public final boolean player;
        public final boolean self;
        public ItemStack weapon = ItemStack.EMPTY;
        public float damage;
        public int hits;

        Row(String name, boolean player, boolean self) {
            this.name = name;
            this.player = player;
            this.self = self;
        }
    }

    private static final class Log {
        final Map<String, Row> rows = new LinkedHashMap<>();
        final float[] parts = new float[Limb.values().length];
        long lastTick;
    }

    public record Snapshot(List<Row> rows, float[] parts) {
    }

    private static final Map<UUID, Log> LOGS = new ConcurrentHashMap<>();

    private record Stash(Limb limb, long tick, DamageSource source) {
    }

    private static final Map<UUID, Stash> STASH = new ConcurrentHashMap<>();

    private DamageLog() {
    }

    /** Called from LivingHurtEvent: remembers which limb was hit until the matching LivingDamageEvent. */
    public static void stashLimb(ServerPlayer victim, Limb limb, DamageSource source) {
        STASH.put(victim.getUUID(), new Stash(limb, victim.level().getGameTime(), source));
    }

    /** Destructive read; null if nothing fresh (same/previous tick) was stashed. */
    public static Limb takeLimb(ServerPlayer victim, DamageSource source) {
        Stash s = STASH.remove(victim.getUUID());
        if (s == null || s.source() != source) {
            return null; // left over from a hit that never turned into damage: never lend its limb to another hit
        }
        long age = victim.level().getGameTime() - s.tick();
        return age >= 0 && age <= 1 ? s.limb() : null;
    }

    /** The damage that will really be recorded: capped to the health the victim has left when capToRemainingHealth is on. */
    public static float capped(ServerPlayer victim, float damage) {
        if (LimbConfig.RECAP_CAP_OVERKILL.get()) {
            return Math.max(0f, Math.min(damage, victim.getHealth() + victim.getAbsorptionAmount()));
        }
        return damage;
    }

    /** @param limb limb that was hit, or null when the hit had no known location (fall, fire, ...). */
    public static void record(ServerPlayer victim, DamageSource source, float damage, Limb limb) {
        if (damage <= 0f) {
            return;
        }
        if (LimbConfig.RECAP_CAP_OVERKILL.get()) {
            damage = Math.min(damage, victim.getHealth() + victim.getAbsorptionAmount());
            if (damage <= 0f) {
                return;
            }
        }
        long now = victim.level().getGameTime();
        long timeout = (long) (LimbConfig.RECAP_FIGHT_TIMEOUT_SECONDS.get() * 20.0);
        Log log = LOGS.get(victim.getUUID());
        if (log == null || now - log.lastTick > timeout) {
            log = new Log();
            LOGS.put(victim.getUUID(), log);
        }
        log.lastTick = now;

        Entity attacker = source.getEntity();
        final String key;
        final String name;
        final boolean isPlayer;
        final boolean isSelf = attacker != null && attacker.getUUID().equals(victim.getUUID());
        if (attacker != null) {
            key = attacker.getUUID().toString();
            // Raw profile / entity name only: getDisplayName() adds scoreboard team prefixes, faction tags etc.
            String raw = attacker instanceof Player p ? p.getGameProfile().getName() : attacker.getName().getString();
            String clean = ChatFormatting.stripFormatting(raw);
            name = clean == null || clean.isBlank() ? "Unknown" : clean.trim();
            isPlayer = attacker instanceof Player;
        } else {
            key = "src:" + source.getMsgId();
            name = prettify(source.getMsgId());
            isPlayer = false;
        }

        Row row = log.rows.computeIfAbsent(key, k -> new Row(name, isPlayer, isSelf));
        row.damage += damage;
        row.hits++;

        ItemStack weapon = weaponOf(source);
        if (!weapon.isEmpty()) {
            row.weapon = weapon;
        }
        if (limb != null) {
            log.parts[limb.ordinal()] += damage;
        }
    }

    /**
     * The item to show as the weapon of a hit (a fresh single-item copy, or EMPTY). Thrown items
     * (grenades, snowballs, pearls...) report themselves; everything else (TACZ bullets, arrows,
     * melee) is whatever the attacker is holding.
     */
    public static ItemStack weaponOf(DamageSource source) {
        Entity attacker = source.getEntity();
        Entity direct = source.getDirectEntity();
        ItemStack weapon = ItemStack.EMPTY;
        if (direct instanceof ItemSupplier supplier && direct != attacker) {
            weapon = supplier.getItem();
        }
        if (weapon.isEmpty() && attacker instanceof LivingEntity living) {
            weapon = living.getMainHandItem();
        }
        if (weapon.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack copy = weapon.copy();
        copy.setCount(1);
        return copy;
    }

    /** Destructive read; rows sorted by damage, biggest first. */
    public static Snapshot consume(ServerPlayer victim) {
        Log log = LOGS.remove(victim.getUUID());
        if (log == null || log.rows.isEmpty()) {
            return null;
        }
        List<Row> rows = new ArrayList<>(log.rows.values());
        rows.sort(Comparator.comparingDouble((Row r) -> r.damage).reversed());
        return new Snapshot(rows, log.parts);
    }

    public static void clearAll() {
        LOGS.clear();
        STASH.clear();
    }

    public static void clear(UUID id) {
        STASH.remove(id);
        LOGS.remove(id);
    }

    /** Drops logs of players who were hurt but never died (healed, logged off...). */
    public static void sweep(long gameTime) {
        long maxAge = (long) (LimbConfig.RECAP_FIGHT_TIMEOUT_SECONDS.get() * 20.0) * 2L;
        LOGS.values().removeIf(log -> gameTime - log.lastTick > maxAge);
        STASH.values().removeIf(s -> gameTime - s.tick() > 2L);
    }

    private static String prettify(String msgId) {
        if (msgId == null || msgId.isEmpty()) {
            return "Unknown";
        }
        String s = msgId.replaceAll("(?<=[a-z])(?=[A-Z])", " ").replace('_', ' ').replace('.', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
