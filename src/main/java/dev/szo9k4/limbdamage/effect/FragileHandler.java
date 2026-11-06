package dev.szo9k4.limbdamage.effect;

import dev.szo9k4.limbdamage.LimbDamageMod;
import dev.szo9k4.limbdamage.config.LimbConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Regeneration delay of the Fragile effect. A player is "delayed" while they have Fragile and for
 * extraDelaySeconds after it ends. During that time:
 *  1. the vanilla Regeneration effect is held back (removed, then given back with its remaining time);
 *  2. food / natural / saturation regeneration is cancelled (LivingHealEvent, only when the heal
 *     comes from FoodData or the Regeneration tweak mod, so Instant Health etc. still work);
 *  3. the Regeneration tweak mod's own after-damage timer is pushed forward (see RegenModCompat),
 *     which delays its HP *and* Absorption regen.
 */
@Mod.EventBusSubscriber(modid = LimbDamageMod.MOD_ID)
public final class FragileHandler {

    /** player -> game tick until which regeneration stays delayed. */
    private static final Map<UUID, Long> DELAYED_UNTIL = new ConcurrentHashMap<>();
    /** player -> Regeneration effect that was taken off and will be given back. */
    private static final Map<UUID, MobEffectInstance> HELD = new ConcurrentHashMap<>();

    private FragileHandler() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        dev.szo9k4.limbdamage.util.Safe.run("onPlayerTick", () -> impl_onPlayerTick(event));
    }

    private static void impl_onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        // Fast path: runs for every player every tick, so bail before any config read when there is nothing to do.
        if (DELAYED_UNTIL.isEmpty() && HELD.isEmpty() && player.getActiveEffectsMap().isEmpty()) {
            return;
        }
        UUID id = player.getUUID();
        long now = player.level().getGameTime();
        long extra = (long) (LimbConfig.FRAGILE_EXTRA_DELAY_SECONDS.get() * 20.0);

        if (!LimbConfig.FRAGILE_PAUSE_REGEN_EFFECT.get() && HELD.containsKey(id)) {
            release(player);
        }

        if (player.hasEffect(ModEffects.FRAGILE.get())) {
            DELAYED_UNTIL.put(id, now + extra);
            if (LimbConfig.FRAGILE_PAUSE_REGEN_EFFECT.get()) {
                hold(player);
            }
            if (LimbConfig.FRAGILE_SYNC_REGEN_MOD.get()) {
                RegenModCompat.pushLastDamage(id, now + extra);
            }
            return;
        }

        Long until = DELAYED_UNTIL.get(id);
        if (until == null) {
            return;
        }
        if (now >= until) {
            DELAYED_UNTIL.remove(id);
            release(player);
        } else {
            // still inside the extra delay: keep Regeneration held and the regen mod's timer from being lowered
            if (LimbConfig.FRAGILE_PAUSE_REGEN_EFFECT.get()) {
                hold(player);
            }
            if (LimbConfig.FRAGILE_SYNC_REGEN_MOD.get()) {
                RegenModCompat.pushLastDamage(id, until);
            }
        }
    }

    public static void clearAll() {
        DELAYED_UNTIL.clear();
        HELD.clear();
    }

    private static void hold(ServerPlayer player) {
        MobEffectInstance regen = player.getEffect(MobEffects.REGENERATION);
        if (regen == null) {
            return;
        }
        MobEffectInstance copy = new MobEffectInstance(regen);
        HELD.merge(player.getUUID(), copy, (a, b) -> b.getDuration() > a.getDuration() ? b : a);
        player.removeEffect(MobEffects.REGENERATION);
    }

    private static void release(ServerPlayer player) {
        MobEffectInstance held = HELD.remove(player.getUUID());
        if (held != null) {
            player.addEffect(held);
        }
    }

    private static boolean isDelayed(ServerPlayer player) {
        if (player.hasEffect(ModEffects.FRAGILE.get())) {
            return true;
        }
        Long until = DELAYED_UNTIL.get(player.getUUID());
        return until != null && player.level().getGameTime() < until;
    }

    /** HIGH priority so we cancel before anything else reacts to the heal. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHeal(LivingHealEvent event) {
        dev.szo9k4.limbdamage.util.Safe.run("onHeal", () -> impl_onHeal(event));
    }

    private static void impl_onHeal(LivingHealEvent event) {
        if (!LimbConfig.FRAGILE_BLOCK_NATURAL_REGEN.get() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (isDelayed(player) && isPassiveHealSource()) {
            event.setCanceled(true);
        }
    }

    /** LivingHealEvent carries no source, so look at who is calling heal(): food/natural regen or the regen mod. */
    private static boolean isPassiveHealSource() {
        return StackWalker.getInstance().walk(frames -> frames.limit(48).anyMatch(frame -> {
            String cls = frame.getClassName();
            return cls.equals("net.minecraft.world.food.FoodData") || cls.startsWith("net.mcreator.regen.");
        }));
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        dev.szo9k4.limbdamage.util.Safe.run("onDeath", () -> impl_onDeath(event));
    }

    private static void impl_onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DELAYED_UNTIL.remove(player.getUUID());
            HELD.remove(player.getUUID()); // all effects are cleared on death; don't resurrect Regeneration
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        dev.szo9k4.limbdamage.util.Safe.run("onLogout", () -> impl_onLogout(event));
    }

    private static void impl_onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        DELAYED_UNTIL.remove(event.getEntity().getUUID());
        HELD.remove(event.getEntity().getUUID());
    }
}
