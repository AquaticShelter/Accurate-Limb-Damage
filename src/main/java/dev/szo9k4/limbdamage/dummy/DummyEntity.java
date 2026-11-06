package dev.szo9k4.limbdamage.dummy;

import dev.szo9k4.limbdamage.Limb;
import dev.szo9k4.limbdamage.config.LimbConfig;
import dev.szo9k4.limbdamage.config.Multipliers;
import dev.szo9k4.limbdamage.recap.RecapNetwork;
import dev.szo9k4.limbdamage.recap.RecapPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraftforge.network.NetworkHooks;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Training dummy: a player-sized (0.6 x 1.8) humanoid that stands still, never moves or gets
 * knocked back, wears the skin of the player who spawned it, and has configurable health. It always
 * takes locational damage (BodyPartClassifier#isEligible) and tells whoever hits it which limb they
 * hit, the multiplier and the damage. Spawn with /limbrecap dummy [health].
 */
public class DummyEntity extends Mob {

    private static final EntityDataAccessor<Optional<UUID>> OWNER =
            SynchedEntityData.defineId(DummyEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    /** One recorded hit, kept until the report is sent. limb == null: no hit location was recorded. */
    private static final class HitLog {
        final UUID attacker;
        final ItemStack weapon;
        final Limb limb;
        final String tag;
        final boolean special;
        float damage; // grows when the armor-piercing part of the SAME bullet is folded in
        final UUID bulletId;
        final int tick;

        HitLog(UUID attacker, ItemStack weapon, Limb limb, String tag, boolean special, float damage, UUID bulletId, int tick) {
            this.attacker = attacker;
            this.weapon = weapon;
            this.limb = limb;
            this.tag = tag;
            this.special = special;
            this.damage = damage;
            this.bulletId = bulletId;
            this.tick = tick;
        }

        UUID attacker() { return attacker; }
        ItemStack weapon() { return weapon; }
        Limb limb() { return limb; }
        String tag() { return tag; }
        boolean special() { return special; }
        float damage() { return damage; }
    }

    /** A hit seen by DamageHandler, waiting for the FINAL damage from LivingDamageEvent. limb/resolved null = no location. */
    private record Pending(ServerPlayer attacker, ItemStack weapon, Limb limb, Multipliers.Resolved resolved,
                           float raw, float afterMultiplier, UUID bulletId, DamageSource source) {
    }

    /** What a dummy does when its health runs out. DEFAULT follows the config's dummy section. */
    public enum DeathMode {
        DEFAULT("Default"), RESTORE("Restore"), RESPAWN("Respawn"), DIE("Die");

        public final String label;

        DeathMode(String label) {
            this.label = label;
        }

        public DeathMode next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    /** Damage reduction in percent (0.1 steps), applied after armor and every other reduction. */
    private float damageReduction = LimbConfig.DUMMY_DAMAGE_REDUCTION.get().floatValue();
    /** Seconds without a hit before it heals; negative = use the config value. */
    private float autoHealSeconds = -1f;
    private DeathMode deathMode = DeathMode.DEFAULT;

    private Pending pending;

    private int lastHitTick;
    private int lastReportHitTick;
    private final List<HitLog> hitLog = new ArrayList<>();

    public DummyEntity(EntityType<? extends DummyEntity> type, Level level) {
        super(type, level);
        this.setNoAi(true);
        this.setPersistenceRequired();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            this.setDropChance(slot, 0f); // whatever is put on a dummy never drops
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(OWNER, Optional.empty());
    }

    /** Sets max health and fills it. */
    public void configureHealth(float health) {
        AttributeInstance max = this.getAttribute(Attributes.MAX_HEALTH);
        if (max != null) {
            max.setBaseValue(health);
        }
        this.setHealth(health);
    }

    /**
     * Called by DamageHandler for every locational hit a player lands on this dummy (server side).
     * The hits are listed one by one, never stacked, in the report sent once the attacker has stopped
     * for reportDelaySeconds.
     */
    public void stashHit(ServerPlayer attacker, ItemStack weapon, Limb limb, Multipliers.Resolved resolved,
                         float raw, float afterMultiplier, UUID bulletId, DamageSource source) {
        this.pending = new Pending(attacker, weapon, limb, resolved, raw, afterMultiplier, bulletId, source);
    }

    /** The dummy's own damage-reduction stat. */
    public float applyDamageReduction(float amount) {
        return damageReduction <= 0f ? amount : amount * (1f - damageReduction / 100f);
    }

    /**
     * Called from LivingDamageEvent (LOWEST) with the damage that was ACTUALLY dealt: after this mod's
     * multiplier AND everything else that reduces damage (armor, effects, other mods). That final number is
     * what the action bar and the report show, not the raw hit.
     */
    public void completeHit(DamageSource damageSource, float dealt) {
        Pending hit = this.pending;
        this.pending = null;
        if (hit == null || hit.source() != damageSource) {
            return; // the stashed hit never became damage (or belongs to another source): never report it for this one
        }
        boolean located = hit.limb() != null && hit.resolved() != null;
        float hpAfter = Math.max(0f, this.getHealth() - dealt);

        // TACZ hurts a target up to TWICE per bullet (a normal part and an armor-piercing part). Both belong to
        // ONE hit: fold the second into the first row instead of listing a phantom extra hit.
        boolean merged = false;
        float totalForBullet = dealt;
        if (LimbConfig.DUMMY_REPORT_ENABLED.get()) {
            this.lastReportHitTick = this.tickCount;
            HitLog last = hitLog.isEmpty() ? null : hitLog.get(hitLog.size() - 1);
            if (last != null && hit.bulletId() != null && hit.bulletId().equals(last.bulletId)
                    && last.tick == this.tickCount && last.attacker.equals(hit.attacker().getUUID())) {
                last.damage += dealt;
                totalForBullet = last.damage;
                merged = true;
            } else if (hitLog.size() < LimbConfig.DUMMY_REPORT_MAX_HITS.get()) {
                hitLog.add(new HitLog(hit.attacker().getUUID(), hit.weapon(), hit.limb(),
                        located ? hit.resolved().tag() : "x1.00 ??", located && hit.resolved().special(), dealt,
                        hit.bulletId(), this.tickCount));
            }
        }

        if (LimbConfig.DUMMY_SHOW_HIT_INFO.get()) {
            String label = located ? hit.limb().label() : "No hit location";
            String source = located ? hit.resolved().describe() : "multiplier not applied";
            String text;
            if (merged) {
                text = String.format(Locale.ROOT, "%s  DEALT %s in total (+%s armor-piercing part)  [%s]  hp %s/%s",
                        label, trim(totalForBullet), trim(dealt), source, trim(hpAfter), trim(this.getMaxHealth()));
            } else {
                String chain = located
                        ? String.format(Locale.ROOT, "%s x%.2f = %s", trim(hit.raw()), hit.resolved().value(), trim(hit.afterMultiplier()))
                        : trim(hit.raw()) + " (no multiplier)";
                String reduced = Math.abs(dealt - hit.afterMultiplier()) > 0.005f ? "  reduced to " + trim(dealt) : "";
                text = String.format(Locale.ROOT, "%s  %s%s  DEALT %s  [%s]  hp %s/%s",
                        label, chain, reduced, trim(dealt), source, trim(hpAfter), trim(this.getMaxHealth()));
            }
            hit.attacker().displayClientMessage(Component.literal(text), true);
        }
    }

    /** 2 decimals, trailing zeros dropped (9.690 -> 9.69, 10.00 -> 10). */
    private static String trim(float v) {
        String s = String.format(Locale.ROOT, "%.2f", v);
        if (s.contains(".")) {
            s = s.replaceAll("0+$", "");
            if (s.endsWith(".")) {
                s = s.substring(0, s.length() - 1);
            }
        }
        return s;
    }

    /** Sends each attacker THEIR OWN hits (in order) as a report toast, then forgets them. */
    private void flushReport() {
        if (hitLog.isEmpty()) {
            return;
        }
        List<HitLog> snapshot = new ArrayList<>(hitLog);
        hitLog.clear();
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Map<UUID, List<HitLog>> byAttacker = new LinkedHashMap<>();
        for (HitLog hit : snapshot) {
            byAttacker.computeIfAbsent(hit.attacker(), k -> new ArrayList<>()).add(hit);
        }
        for (Map.Entry<UUID, List<HitLog>> entry : byAttacker.entrySet()) {
            ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                continue; // logged off since
            }
            float[] parts = new float[Limb.values().length];
            List<RecapPacket.RowData> rows = new ArrayList<>();
            for (HitLog hit : entry.getValue()) {
                if (hit.limb() != null) {
                    parts[hit.limb().ordinal()] += hit.damage();
                }
                rows.add(new RecapPacket.RowData(hit.limb() != null ? hit.limb().label() : "No location", false, false,
                        hit.weapon(), hit.damage(), 1, hit.tag(), hit.special()));
            }
            RecapNetwork.sendTo(player, new RecapPacket(this.getMaxHealth(), parts, rows.size(), rows, true));
        }
    }

    public void setOwnerUuid(UUID id) {
        this.entityData.set(OWNER, Optional.ofNullable(id));
    }

    public Optional<UUID> getOwnerUuid() {
        return this.entityData.get(OWNER);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        getOwnerUuid().ifPresent(id -> tag.putUUID("Owner", id));
        tag.putFloat("DamageReduction", damageReduction);
        tag.putFloat("AutoHealSeconds", autoHealSeconds);
        tag.putString("DeathMode", deathMode.name());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("Owner")) {
            setOwnerUuid(tag.getUUID("Owner"));
        }
        if (tag.contains("DamageReduction")) {
            damageReduction = tag.getFloat("DamageReduction");
        }
        if (tag.contains("AutoHealSeconds")) {
            autoHealSeconds = tag.getFloat("AutoHealSeconds");
        }
        if (tag.contains("DeathMode")) {
            try {
                deathMode = DeathMode.valueOf(tag.getString("DeathMode"));
            } catch (IllegalArgumentException ignored) {
                deathMode = DeathMode.DEFAULT;
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !this.level().isClientSide) {
            this.lastHitTick = this.tickCount;
        }
        return hurt;
    }

    @Override
    public void die(DamageSource source) {
        DeathMode mode = effectiveDeathMode();
        if (mode == DeathMode.RESPAWN) {
            super.die(source);
            if (!this.level().isClientSide) {
                flushReport();                   // the report goes out now, with the killing blow in it
                DummyRespawns.schedule(this);    // ...and the dummy comes back in the same spot
            }
            return;
        }
        if (mode == DeathMode.RESTORE) {
            this.setHealth(this.getMaxHealth()); // would have died: reset instead
            return;
        }
        super.die(source);
    }

    public DeathMode effectiveDeathMode() {
        if (deathMode != DeathMode.DEFAULT) {
            return deathMode;
        }
        if (LimbConfig.DUMMY_DIE_AND_RESPAWN.get()) {
            return DeathMode.RESPAWN;
        }
        return LimbConfig.DUMMY_CANNOT_DIE.get() ? DeathMode.RESTORE : DeathMode.DIE;
    }

    public float effectiveAutoHealSeconds() {
        return autoHealSeconds >= 0f ? autoHealSeconds : LimbConfig.DUMMY_AUTO_HEAL_SECONDS.get().floatValue();
    }

    public float getDamageReduction() {
        return damageReduction;
    }

    public DeathMode getDeathMode() {
        return deathMode;
    }

    /** Applies the stats from the dummy screen, clamped; one decimal for the reduction. */
    public void applySettings(float maxHealth, float reductionPercent, float autoHeal, int mode) {
        configureHealthKeepingRatio(Math.max(1f, Math.min(2048f, maxHealth)));
        this.damageReduction = Math.round(Math.max(0f, Math.min(100f, reductionPercent)) * 10f) / 10f;
        this.autoHealSeconds = Math.max(0f, Math.min(600f, autoHeal));
        DeathMode[] modes = DeathMode.values();
        this.deathMode = modes[Math.max(0, Math.min(modes.length - 1, mode))];
    }

    private void configureHealthKeepingRatio(float newMax) {
        float ratio = this.getMaxHealth() <= 0f ? 1f : this.getHealth() / this.getMaxHealth();
        AttributeInstance max = this.getAttribute(Attributes.MAX_HEALTH);
        if (max != null) {
            max.setBaseValue(newMax);
        }
        this.setHealth(Math.max(1f, newMax * ratio));
    }

    // ------------------------------------------------------------------ interaction: equipment and stats

    public boolean isOwnedBy(Player player) {
        Optional<UUID> owner = getOwnerUuid();
        return owner.isPresent() && owner.get().equals(player.getUUID());
    }

    /** Owner and operators always; everybody when dummy.publicEquipment is on. */
    public boolean canEquip(Player player) {
        return LimbConfig.DUMMY_PUBLIC_EQUIPMENT.get() || isOwnedBy(player) || player.hasPermissions(2);
    }

    public boolean canEditSettings(Player player) {
        return isOwnedBy(player) || player.hasPermissions(2) || getOwnerUuid().isEmpty();
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);
        boolean gun = !held.isEmpty() && "tacz".equals(BuiltInRegistries.ITEM.getKey(held.getItem()).getNamespace());
        if (gun && !player.isSecondaryUseActive()) {
            return InteractionResult.PASS; // aiming a TACZ gun at the dummy must not pop a menu; crouch + click does
        }
        if (this.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        if (player.isSecondaryUseActive() && !held.isEmpty() && canEquip(player)) {
            // crouch + click with an item: put it straight on (armor goes to its slot, anything else to the hand)
            EquipmentSlot slot = Mob.getEquipmentSlotForItem(held);
            ItemStack worn = held.copy();
            worn.setCount(1);
            ItemStack old = this.getItemBySlot(slot);
            this.setItemSlot(slot, worn);
            if (!player.getAbilities().instabuild) {
                held.shrink(1);
            }
            if (!old.isEmpty()) {
                player.getInventory().placeItemBackInInventory(old);
            }
            return InteractionResult.CONSUME;
        }
        DummyMenu.Stats stats = DummyMenu.Stats.of(this, player);
        NetworkHooks.openScreen(serverPlayer,
                new SimpleMenuProvider((id, inv, p) -> new DummyMenu(id, inv, this, stats), this.getName()),
                buf -> stats.write(buf, this.getId()));
        return InteractionResult.CONSUME;
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide) {
            if (!hitLog.isEmpty()
                    && this.tickCount - this.lastReportHitTick >= (int) (LimbConfig.DUMMY_REPORT_DELAY_SECONDS.get() * 20.0)) {
                flushReport();
            }
            int healAfter = (int) (effectiveAutoHealSeconds() * 20.0);
            if (healAfter > 0 && this.getHealth() < this.getMaxHealth() && this.tickCount - this.lastHitTick > healAfter) {
                this.setHealth(this.getMaxHealth());
            }
        }
    }

    /** Stands perfectly still. */
    @Override
    public void knockback(double strength, double x, double z) {
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }

    /** Same eye height as a player, so anything keyed to the eyes behaves like it does on a player. */
    @Override
    protected float getStandingEyeHeight(Pose pose, EntityDimensions dimensions) {
        return 1.62F;
    }
}
