package dev.szo9k4.limbdamage.recap;

import dev.szo9k4.limbdamage.LimbDamageMod;
import dev.szo9k4.limbdamage.config.LimbConfig;
import dev.szo9k4.limbdamage.BodyPartClassifier;
import dev.szo9k4.limbdamage.Limb;
import dev.szo9k4.limbdamage.dummy.DummyEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = LimbDamageMod.MOD_ID)
public final class RecapHandler {

    private RecapHandler() {
    }

    /** LOWEST so every other mod has already adjusted the amount. LivingDamageEvent carries the damage
     *  AFTER armor / enchantment / potion reduction (and after Limb Damage's multiplier, applied in
     *  the earlier LivingHurtEvent) — the real number, not the raw hit. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent event) {
        dev.szo9k4.limbdamage.util.Safe.run("onDamage", () -> impl_onDamage(event));
    }

    private static void impl_onDamage(LivingDamageEvent event) {
        if (event.getEntity() instanceof DummyEntity dummy) {
            // the dummy's own damage-reduction stat comes last, after armor and every other reduction
            float dealt = dummy.applyDamageReduction(event.getAmount());
            event.setAmount(dealt);
            dummy.completeHit(event.getSource(), dealt);
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        boolean recapOn = LimbConfig.ENABLE_DEATH_RECAP.get();
        boolean dealtOn = LimbConfig.DEALT_ENABLED.get();
        if (!recapOn && !dealtOn) {
            return;
        }
        Limb part = DamageLog.takeLimb(player, event.getSource());
        if (part == null) {
            // No hit location was recorded (e.g. melee with applyToMelee off, a bullet whose location
            // was lost): estimate the limb from the attacker's aim ray / projectile path for the
            // recap's body icon only. Damage multipliers are never affected by this estimate.
            part = estimatePart(player, event.getSource());
        }
        dev.szo9k4.limbdamage.CombatLog.setFinalDamage(player, event.getSource(), event.getAmount());
        if (recapOn) {
            DamageLog.record(player, event.getSource(), event.getAmount(), part);
        }
        // "what I just did to other players": feeds the second toast shown when the attacker dies
        if (dealtOn && event.getSource().getEntity() instanceof ServerPlayer attacker && attacker != player) {
            DealtLog.record(attacker, player, DamageLog.capped(player, event.getAmount()), part,
                    DamageLog.weaponOf(event.getSource()));
        }
    }

    private static Limb estimatePart(ServerPlayer victim, DamageSource source) {
        Entity attacker = source.getEntity();
        Entity direct = source.getDirectEntity();
        if (!(attacker instanceof LivingEntity shooter) || attacker == victim) {
            return null;
        }
        Vec3 start;
        Vec3 end;
        if (direct instanceof Projectile projectile) {
            start = projectile.position().subtract(projectile.getDeltaMovement());
            end = projectile.position().add(projectile.getDeltaMovement().scale(2.0));
        } else if (direct == attacker) { // melee
            start = shooter.getEyePosition();
            end = start.add(shooter.getViewVector(1.0f).scale(shooter.distanceTo(victim) + 3.0));
        } else {
            return null; // explosions, fire, magic...: no meaningful hit location
        }
        AABB box = victim.getBoundingBox().inflate(0.1);
        Vec3 hit = box.contains(start) ? start : box.clip(start, end).orElse(null);
        return hit == null ? null : BodyPartClassifier.classifyLimb(victim, hit);
    }

    /** LOWEST: cancelled deaths (totem of undying, other mods) are not delivered here, so a
     *  saved player doesn't get a recap. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        dev.szo9k4.limbdamage.util.Safe.run("onDeath", () -> impl_onDeath(event));
    }

    private static void impl_onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Entity killer = event.getSource().getEntity();
        // everybody this player had damaged keeps them on THEIR "you dealt" toast, now tagged DEAD / KILL
        DealtLog.markDeath(player, killer);

        if (LimbConfig.ENABLE_DEATH_RECAP.get()) {
            DamageLog.Snapshot snapshot = DamageLog.consume(player);
            if (snapshot != null) {
                List<RecapPacket.RowData> rows = new ArrayList<>();
                for (DamageLog.Row row : snapshot.rows()) {
                    if (rows.size() >= RecapPacket.MAX_ROWS) {
                        break;
                    }
                    rows.add(new RecapPacket.RowData(row.name, row.player, row.self, row.weapon, row.damage, row.hits, "", false));
                }
                RecapNetwork.sendTo(player, new RecapPacket(player.getMaxHealth(), snapshot.parts(),
                        snapshot.rows().size(), rows, false));
            }
        }

        if (LimbConfig.DEALT_ENABLED.get()) {
            // second toast under the recap: the players THIS player had just damaged (killer first), if any
            List<DealtLog.Entry> dealt = DealtLog.snapshot(player, killer != null ? killer.getUUID() : null);
            if (!dealt.isEmpty()) {
                RecapNetwork.sendTo(player, DealtLog.packet(dealt));
            }
        }
        DealtLog.clear(player.getUUID()); // a new life starts with a clean slate
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        dev.szo9k4.limbdamage.util.Safe.run("onLogout", () -> impl_onLogout(event));
    }

    private static void impl_onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        DamageLog.clear(event.getEntity().getUUID());
        DealtLog.clear(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        dev.szo9k4.limbdamage.util.Safe.run("onServerTick", () -> impl_onServerTick(event));
    }

    private static void impl_onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            long gameTime = event.getServer().overworld().getGameTime();
            DealtLog.confirmDeaths(gameTime);
            if (gameTime % 200L == 0L) {
                DamageLog.sweep(gameTime);
                DealtLog.sweep(gameTime);
            }
        }
    }
}
