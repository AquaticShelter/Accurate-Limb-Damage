package dev.szo9k4.limbdamage.event;

import dev.szo9k4.limbdamage.BodyPart;
import dev.szo9k4.limbdamage.BodyPartClassifier;
import dev.szo9k4.limbdamage.CombatLog;
import dev.szo9k4.limbdamage.HitLocationTracker;
import dev.szo9k4.limbdamage.Limb;
import dev.szo9k4.limbdamage.LimbDamageMod;
import dev.szo9k4.limbdamage.compat.TaczCompat;
import dev.szo9k4.limbdamage.config.CategoryConfig;
import dev.szo9k4.limbdamage.config.LimbConfig;
import dev.szo9k4.limbdamage.config.Multipliers;
import dev.szo9k4.limbdamage.dummy.DummyEntity;
import dev.szo9k4.limbdamage.effect.ModEffects;
import dev.szo9k4.limbdamage.recap.DamageLog;
import dev.szo9k4.limbdamage.util.Safe;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;
import java.util.Optional;

/**
 * Applies the locational multiplier. The hit location comes from, in order of preference:
 *
 *  1. an exact recorded location (HitLocationTracker): TACZ bullets via the getHitResult mixin
 *     (Accurate Hitboxes' bone verdict when available, otherwise the bullet's path), vanilla
 *     projectiles via ProjectileImpactEvent;
 *  2. the projectile's own trajectory clipped against the target's box;
 *  3. melee: the attacker's aim ray clipped against the target's box (applyToMelee).
 *
 * Where a path is known it is cast against the player model (BodyPartClassifier#diagnoseRay);
 * otherwise the single position is matched to the nearest model box. Location-less damage
 * (explosions, magic, poison, fall...) passes through untouched.
 *
 * The multiplier is global per part, or the weapon category's own value for TACZ guns
 * (CategoryConfig), and arms/legs/feet are forced to x1.0 while the Fragile effect is active.
 */
@Mod.EventBusSubscriber(modid = LimbDamageMod.MOD_ID)
public final class DamageHandler {

    private DamageHandler() {
    }

    /** LOWEST priority: runs after other mods have made their own adjustments to the same event,
     *  so our multiplier applies to the final agreed-upon number. (TACZ's headshot multiplier is
     *  applied even earlier — before hurt() is ever called — and is cancelled in TaczCompat when the
     *  headshot override is on.) */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHurt(LivingHurtEvent event) {
        Safe.run("LivingHurtEvent", () -> handleHurt(event));
    }

    private static void handleHurt(LivingHurtEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) {
            return;
        }
        // Death recap: every damage instance a player takes is logged per attacker, whether or not
        // it gets a hit location / multiplier below (fall, fire, explosions still show up).
        ServerPlayer recapVictim = target instanceof ServerPlayer sp
                && (LimbConfig.ENABLE_DEATH_RECAP.get() || LimbConfig.DEALT_ENABLED.get()) ? sp : null;

        if (!BodyPartClassifier.isEligible(target)) {
            return;
        }

        HitInfo hitInfo = resolveHitPosition(event, target);
        if (hitInfo == null) {
            if (target instanceof DummyEntity dummy && event.getSource().getEntity() instanceof ServerPlayer shooter) {
                // Say so instead of staying silent: a hit with no location gets NO multiplier.
                dummy.stashHit(shooter, DamageLog.weaponOf(event.getSource()), null, null, event.getAmount(), event.getAmount(),
                        bulletIdOf(event.getSource()), event.getSource());
                if (LimbConfig.LOG_HITS.get()) {
                    LimbDamageMod.LOGGER.info("[LimbDamage] hit on {} had NO recorded location (direct={}, source={}): multiplier not applied",
                            target.getName().getString(), event.getSource().getDirectEntity(), event.getSource().getMsgId());
                }
            }
            return;
        }

        BodyPart part;
        BodyPartClassifier.Diagnosis diagnosis = null;
        if (hitInfo.preclassifiedPart() != null) {
            part = hitInfo.preclassifiedPart();
        } else {
            diagnosis = hitInfo.rayStart() != null
                    ? BodyPartClassifier.diagnoseRay(target, hitInfo.position(), hitInfo.rayStart(), hitInfo.rayEnd())
                    : BodyPartClassifier.diagnose(target, hitInfo.position());
            part = diagnosis.part();
        }
        if (diagnosis == null) {
            // Verdict came from Accurate Hitboxes' bones: cross-check it against the real hit height.
            BodyPart guarded = BodyPartClassifier.sanityCheck(target, hitInfo.position(), part);
            if (guarded != part) {
                if (LimbConfig.LOG_HITS.get()) {
                    LimbDamageMod.LOGGER.info("[LimbDamage] sanity guard: {} -> {} at {}", part, guarded, hitInfo.position());
                }
                part = guarded;
            }
        }

        double lateral = !Double.isNaN(hitInfo.lateralHint()) ? hitInfo.lateralHint()
                : diagnosis != null && !Double.isNaN(diagnosis.lateral()) ? diagnosis.lateral()
                : BodyPartClassifier.lateral(target, hitInfo.position());

        // TACZ weapon category (null when this wasn't a TACZ gun hit): its own per-part multiplier.
        String category = CategoryConfig.ENABLED.get() ? TaczCompat.categoryOf(target, event.getSource()) : null;
        Multipliers.Resolved resolved = Multipliers.resolve(part, category);
        // Fragile effect: arms, legs and feet always take full (x1.0) damage, whatever the config says.
        if ((part == BodyPart.ARMS || part == BodyPart.LEGS || part == BodyPart.FEET)
                && target.hasEffect(ModEffects.FRAGILE.get())) {
            resolved = Multipliers.fragile();
        }
        double multiplier = resolved.value();

        float before = event.getAmount();
        float after = (float) (before * multiplier);

        if (recapVictim != null) {
            // The recap logs the FINAL damage (after armor/enchant reduction) in LivingDamageEvent;
            // this just tells it which limb the hit landed on.
            DamageLog.stashLimb(recapVictim, Limb.of(part, lateral), event.getSource());
        }

        if (target instanceof DummyEntity dummy && event.getSource().getEntity() instanceof ServerPlayer shooter) {
            // The dummy reports the damage actually DEALT: completed in LivingDamageEvent (see RecapHandler).
            dummy.stashHit(shooter, DamageLog.weaponOf(event.getSource()), Limb.of(part, lateral), resolved, before, after,
                    bulletIdOf(event.getSource()), event.getSource());
        }

        // Logged before the no-op early-return below on purpose: when tuning zone boundaries you
        // need to see HEAD/BODY classifications too, and those are x1.0 by default.
        if (LimbConfig.LOG_HITS.get()) {
            String cat = resolved.describe();
            if (diagnosis != null) {
                LimbDamageMod.LOGGER.info(
                        "[LimbDamage] {} hit on {} at {} (x{}, source: {}): {} -> {} "
                                + "[heightFraction={}, boxY=[{},{}], sideways={}/{}, forward={}/{}, ray={}]",
                        part, target.getName().getString(), hitInfo.position(), multiplier, cat, before, after,
                        diagnosis.heightFraction(), diagnosis.boxMinY(), diagnosis.boxMaxY(),
                        diagnosis.sidewaysOffset(), diagnosis.sidewaysThreshold(),
                        diagnosis.forwardOffset(), diagnosis.forwardThreshold(), hitInfo.rayStart() != null);
            } else {
                LimbDamageMod.LOGGER.info(
                        "[LimbDamage] {} hit on {} at {} (x{}, source: {}): {} -> {} [source=AH-OBB]",
                        part, target.getName().getString(), hitInfo.position(), multiplier, cat, before, after);
            }
        }

        if (LimbConfig.ANNOUNCE_KILLS.get()) {
            Entity attacker = event.getSource().getEntity();
            String attackerName = attacker != null ? attacker.getName().getString() : null;
            String weaponName = attacker instanceof LivingEntity livingAttacker
                    && !livingAttacker.getMainHandItem().isEmpty()
                    ? livingAttacker.getMainHandItem().getHoverName().getString()
                    : null;
            CombatLog.record(target, new CombatLog.Entry(part, multiplier, after, attackerName,
                    weaponName, target.level().getGameTime(), event.getSource()));
        }

        if (Math.abs(multiplier - 1.0) < 1.0E-6) {
            return;
        }
        event.setAmount(after);
    }

    /** Supplements (doesn't replace) vanilla's own death message — this mod never touches that
     *  message directly, it just sends its own extra line right alongside it. */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        Safe.run("LivingDeathEvent", () -> handleDeath(event));
    }

    private static void handleDeath(LivingDeathEvent event) {
        if (!LimbConfig.ANNOUNCE_KILLS.get()) {
            return;
        }
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide() || !(victim.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        CombatLog.Entry lastHit = CombatLog.consume(victim, event.getSource());
        if (lastHit == null) {
            return; // the killing blow wasn't a located hit (fire, explosion, fall...): don't blame an older hit
        }

        StringBuilder line = new StringBuilder("[LimbDamage] ")
                .append(victim.getName().getString())
                .append(" died \u2014 ")
                .append(lastHit.part().name().toLowerCase(Locale.ROOT))
                .append(" hit");
        if (lastHit.attackerName() != null) {
            line.append(" from ").append(lastHit.attackerName());
        }
        if (lastHit.weaponName() != null) {
            line.append(" (").append(lastHit.weaponName()).append(')');
        }
        line.append(String.format(Locale.ROOT, " for %.1f dmg (x%.2f)", lastHit.damage(), lastHit.multiplier()));

        serverLevel.getServer().getPlayerList().broadcastSystemMessage(Component.literal(line.toString()), false);
    }

    /** UUID of the projectile that caused this damage (null for melee / environmental damage). */
    private static java.util.UUID bulletIdOf(net.minecraft.world.damagesource.DamageSource source) {
        Entity direct = source.getDirectEntity();
        return direct != null && direct != source.getEntity() ? direct.getUUID() : null;
    }

    /** A resolved hit position plus, when known, the shot's path (rayStart -> rayEnd), the BodyPart
     *  Accurate Hitboxes already decided, and the hit bone's signed lateral offset. */
    private record HitInfo(Vec3 position, BodyPart preclassifiedPart, double lateralHint,
                           Vec3 rayStart, Vec3 rayEnd) {
    }

    private static HitInfo resolveHitPosition(LivingHurtEvent event, LivingEntity target) {
        // Tier 1: exact recorded location (vanilla projectile impact / TACZ bullet raycast).
        HitLocationTracker.Consumed recorded = HitLocationTracker.consume(target, event.getSource());
        if (recorded != null) {
            return new HitInfo(recorded.position(), recorded.preclassifiedPart(), recorded.lateralHint(),
                    recorded.rayStart(), recorded.rayEnd());
        }

        Entity direct = event.getSource().getDirectEntity();

        // Tier 2: projectile trajectory clip.
        if (direct instanceof Projectile projectile) {
            Vec3 start = projectile.position().subtract(projectile.getDeltaMovement());
            Vec3 end = projectile.position().add(projectile.getDeltaMovement().scale(2.0));
            Vec3 clipped = clipAgainstBox(target, start, end, 0.1);
            if (clipped != null) {
                return new HitInfo(clipped, null, Double.NaN, start, end);
            }
            // TACZ bullets are moved by TACZ itself, not through deltaMovement, so the trajectory above usually
            // misses them. Fall back to where the shooter was aiming (generous box: TACZ / Accurate Hitboxes
            // register hits up to ~0.3 outside the vanilla box).
            Entity owner = event.getSource().getEntity();
            if (owner instanceof LivingEntity shooter && owner != target) {
                Vec3 eye = shooter.getEyePosition();
                Vec3 aimEnd = eye.add(shooter.getViewVector(1.0f).scale(shooter.distanceTo(target) + 3.0));
                Vec3 aimHit = clipAgainstBox(target, eye, aimEnd, 0.3);
                if (aimHit != null) {
                    return new HitInfo(aimHit, null, Double.NaN, eye, aimEnd);
                }
            }
            return null;
        }

        // Tier 3: melee aim ray.
        if (LimbConfig.APPLY_TO_MELEE.get() && direct instanceof LivingEntity attacker && direct == event.getSource().getEntity()) {
            double reach = attacker.distanceTo(target) + 3.0;
            Vec3 eye = attacker.getEyePosition();
            Vec3 end = eye.add(attacker.getViewVector(1.0f).scale(reach));
            Vec3 clipped = clipAgainstBox(target, eye, end, 0.1);
            return clipped != null ? new HitInfo(clipped, null, Double.NaN, eye, end) : null;
        }

        return null;
    }

    /** Entry point of the segment into the target's (slightly inflated) bounding box, or the
     *  segment start if it begins inside, or null on a clean miss. */
    private static Vec3 clipAgainstBox(LivingEntity target, Vec3 start, Vec3 end, double inflate) {
        AABB box = target.getBoundingBox().inflate(inflate);
        if (box.contains(start)) {
            return start;
        }
        Optional<Vec3> clip = box.clip(start, end);
        return clip.orElse(null);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        Safe.run("ServerTick sweep", () -> {
            if (event.phase == TickEvent.Phase.END) {
                long gameTime = event.getServer().overworld().getGameTime();
                HitLocationTracker.sweep(gameTime);
                CombatLog.sweep(gameTime);
                TaczCompat.sweep(gameTime);
            }
        });
    }
}
