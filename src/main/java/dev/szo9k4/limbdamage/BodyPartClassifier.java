package dev.szo9k4.limbdamage;

import dev.szo9k4.limbdamage.config.LimbConfig;
import dev.szo9k4.limbdamage.config.Multipliers;
import dev.szo9k4.limbdamage.dummy.DummyEntity;
import dev.szo9k4.limbdamage.geometry.PlayerModel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Minecraft-facing side of hit classification: turns world-space hits on a humanoid-proportioned
 * entity into a {@link BodyPart} (and, via {@link Limb}, a left/right limb). The geometry itself
 * lives in {@link PlayerModel}; this class converts world coordinates into the victim's frame,
 * decides eligibility, and keeps the legacy threshold model for playerProportions = false.
 *
 * <ul>
 *   <li>{@link #diagnoseRay}: the shot's whole path against the player model (preferred);</li>
 *   <li>{@link #diagnose}: nearest model box to a single point (no path known);</li>
 *   <li>{@link #sanityCheck}: cross-check of an Accurate Hitboxes bone verdict.</li>
 * </ul>
 */
public final class BodyPartClassifier {

    /** Full breakdown behind a classification, for debug logging and /limbdamage probe. All numeric
     *  fields other than boxMinY/boxMaxY are NaN when the pose was non-upright (BODY by decline).
     *  lateral is signed: positive = the victim's LEFT side. */
    public record Diagnosis(BodyPart part, double heightFraction, double sidewaysOffset,
                             double forwardOffset, double sidewaysThreshold, double forwardThreshold,
                             double boxMinY, double boxMaxY, double lateral) {
    }

    /** The victim's frame: world -> (lateral, height, forward) normalised to a 1.8 x 0.6 player. */
    private record Frame(double cx, double minY, double cz, double lx, double lz, double fx, double fz,
                         double ws, double hs) {
        double[] local(Vec3 p) {
            double dx = p.x - cx;
            double dz = p.z - cz;
            return new double[]{(dx * lx + dz * lz) / ws, (p.y - minY) * hs, (dx * fx + dz * fz) / ws};
        }
    }

    private BodyPartClassifier() {
    }

    // ------------------------------------------------------------------ eligibility

    /** Whether this entity should get locational damage at all, per config. */
    public static boolean isEligible(LivingEntity entity) {
        if (entity instanceof DummyEntity) {
            return true; // the test dummy always takes locational damage
        }
        String idString = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();

        if (LimbConfig.BLACKLIST_ENTITY_IDS.get().contains(idString)) {
            return false;
        }
        if (entity instanceof Player) {
            return LimbConfig.AFFECT_PLAYERS.get();
        }
        if (!LimbConfig.AFFECT_MOBS.get()) {
            return false;
        }
        if (LimbConfig.EXTRA_ENTITY_IDS.get().contains(idString)) {
            return true;
        }
        if (!LimbConfig.AUTO_DETECT_HUMANOID.get()) {
            return false;
        }
        // "Player-like proportions": the type's REGISTERED base dimensions, not the current
        // bounding box — a crouching or swimming zombie villager is still a humanoid.
        var dims = entity.getType().getDimensions();
        return dims.width >= 0.45f && dims.width <= 0.75f
                && dims.height >= 1.5f && dims.height <= 2.2f;
    }

    // ------------------------------------------------------------------ convenience wrappers

    /** Classifies a world-space hit position. Returns BODY when the pose makes height zones
     *  meaningless (swimming/crawling/sleeping): guessing there would be worse than declining. */
    public static BodyPart classify(LivingEntity target, Vec3 hitPos) {
        return diagnose(target, hitPos).part();
    }

    /** Like {@link #classify} but with left/right limbs, for the recap. */
    public static Limb classifyLimb(LivingEntity target, Vec3 hitPos) {
        return limbOf(diagnose(target, hitPos));
    }

    /** Ray version of {@link #classifyLimb}. */
    public static Limb classifyLimbRay(LivingEntity target, Vec3 hitPos, Vec3 rayStart, Vec3 rayEnd) {
        return limbOf(diagnoseRay(target, hitPos, rayStart, rayEnd));
    }

    private static Limb limbOf(Diagnosis d) {
        return Limb.of(d.part(), Double.isNaN(d.lateral()) ? 0.0 : d.lateral());
    }

    /** Signed offset of a world position from the victim's center line; positive = the victim's LEFT. */
    public static double lateral(LivingEntity target, Vec3 pos) {
        AABB box = target.getBoundingBox();
        double yaw = Math.toRadians(target.yBodyRot);
        return (pos.x - (box.minX + box.maxX) * 0.5) * Math.cos(yaw)
                + (pos.z - (box.minZ + box.maxZ) * 0.5) * Math.sin(yaw);
    }

    /** True when a position on the torso is in its lower half (abdomen) rather than the chest. */
    public static boolean isLowerTorso(LivingEntity target, Vec3 pos) {
        return normalizedHeight(target, pos) < PlayerModel.TORSO_MID_Y;
    }

    /** True when a position is low enough on the legs to count as the FEET zone. */
    public static boolean isFoot(LivingEntity target, Vec3 pos) {
        return normalizedHeight(target, pos) < LimbConfig.FEET_HEIGHT.get();
    }

    /**
     * Height of the player model this victim stands for. The training dummy's hitbox is 1 px taller than
     * 1.8 (like a standing player under TACZ, which grows the box 0.0625 upward) but its model is still
     * a 1.8 player, so heights must be normalised against 1.8, not against the box.
     */
    private static double modelHeight(LivingEntity target, AABB box) {
        return target instanceof DummyEntity ? PlayerModel.REFERENCE_HEIGHT : box.maxY - box.minY;
    }

    private static double normalizedHeight(LivingEntity target, Vec3 pos) {
        AABB box = target.getBoundingBox();
        double height = modelHeight(target, box);
        if (height <= 1.0E-4) {
            return Double.MAX_VALUE;
        }
        return (pos.y - box.minY) * (PlayerModel.REFERENCE_HEIGHT / height);
    }

    // ------------------------------------------------------------------ classification

    /** Point classification with the intermediate numbers exposed for debugging. */
    public static Diagnosis diagnose(LivingEntity target, Vec3 hitPos) {
        Pose pose = target.getPose();
        AABB box = target.getBoundingBox();
        double height = modelHeight(target, box);
        if (!(pose == Pose.STANDING || pose == Pose.CROUCHING) || height <= 1.0E-4) {
            return new Diagnosis(BodyPart.BODY, Double.NaN, Double.NaN, Double.NaN,
                    Double.NaN, Double.NaN, box.minY, box.maxY, Double.NaN);
        }

        boolean model = LimbConfig.PLAYER_PROPORTIONS.get();
        double feetHeight = LimbConfig.FEET_HEIGHT.get();
        double heightFraction = Math.max(0.0, Math.min(1.0, (hitPos.y - box.minY) / height));

        double relX = hitPos.x - (box.minX + box.maxX) * 0.5;
        double relZ = hitPos.z - (box.minZ + box.maxZ) * 0.5;

        // Body yaw (yBodyRot). In Minecraft yaw 0 faces +Z, so the entity's LEFT is (cos, sin) and
        // its forward is (-sin, cos).
        double yawRadians = Math.toRadians(target.yBodyRot);
        double leftX = Math.cos(yawRadians);
        double leftZ = Math.sin(yawRadians);
        double lateralSigned = relX * leftX + relZ * leftZ;
        double forwardSigned = relX * -leftZ + relZ * leftX;
        double sidewaysOffset = Math.abs(lateralSigned);
        double forwardOffset = Math.abs(forwardSigned);

        double widthScale = (box.maxX - box.minX) / PlayerModel.REFERENCE_WIDTH;
        double sidewaysThreshold = (model ? 0.234 : LimbConfig.ARM_MIN_LATERAL.get()) * widthScale;
        double forwardThreshold = LimbConfig.ARM_MIN_FORWARD.get() * widthScale;

        BodyPart part;
        double lateral = lateralSigned;
        if (model && widthScale > 1.0E-4) {
            PlayerModel.Hit m = PlayerModel.nearest((hitPos.y - box.minY) * (PlayerModel.REFERENCE_HEIGHT / height),
                    lateralSigned / widthScale, forwardSigned / widthScale, feetHeight);
            part = m.part();
            lateral = m.lateral();
        } else {
            part = legacyPart(heightFraction, sidewaysOffset > sidewaysThreshold || forwardOffset > forwardThreshold, feetHeight);
        }
        return new Diagnosis(part, heightFraction, sidewaysOffset, forwardOffset,
                sidewaysThreshold, forwardThreshold, box.minY, box.maxY, lateral);
    }

    /**
     * Path classification: casts the shot's path (rayStart -> rayEnd, extended past its end) against the
     * player model and takes the first box entered. Falls back to {@link #diagnose} when no path is
     * known, the pose isn't upright, or the feature is off.
     */
    public static Diagnosis diagnoseRay(LivingEntity target, Vec3 hitPos, Vec3 rayStart, Vec3 rayEnd) {
        Diagnosis base = diagnose(target, hitPos);
        if (rayStart == null || rayEnd == null || Double.isNaN(base.heightFraction())
                || !LimbConfig.PLAYER_PROPORTIONS.get() || !LimbConfig.RAY_CLASSIFICATION.get()) {
            return base;
        }
        AABB box = target.getBoundingBox();
        double ws = (box.maxX - box.minX) / PlayerModel.REFERENCE_WIDTH;
        if (ws <= 1.0E-4) {
            return base;
        }
        double yaw = Math.toRadians(target.yBodyRot);
        double lx = Math.cos(yaw);
        double lz = Math.sin(yaw);
        Frame frame = new Frame((box.minX + box.maxX) * 0.5, box.minY, (box.minZ + box.maxZ) * 0.5,
                lx, lz, -lz, lx, ws, PlayerModel.REFERENCE_HEIGHT / modelHeight(target, box));

        PlayerModel.Hit m = PlayerModel.cast(frame.local(rayStart), frame.local(rayEnd), LimbConfig.FEET_HEIGHT.get());
        if (m == null) {
            return base;
        }
        return new Diagnosis(m.part(), base.heightFraction(), base.sidewaysOffset(), base.forwardOffset(),
                base.sidewaysThreshold(), base.forwardThreshold(), base.boxMinY(), base.boxMaxY(), m.lateral());
    }

    /** The old fixed fraction/threshold model (playerProportions = false). */
    private static BodyPart legacyPart(double heightFraction, boolean offCenter, double feetHeight) {
        double legsTop = LimbConfig.LEGS_TOP_FRACTION.get();
        double headBottom = LimbConfig.HEAD_BOTTOM_FRACTION.get();
        if (offCenter && heightFraction < LimbConfig.ARM_TOP_FRACTION.get() && heightFraction > legsTop) {
            return BodyPart.ARMS;
        }
        if (heightFraction >= headBottom) {
            return BodyPart.HEAD;
        }
        if (heightFraction <= legsTop) {
            return heightFraction * PlayerModel.REFERENCE_HEIGHT < feetHeight ? BodyPart.FEET : BodyPart.LEGS;
        }
        return heightFraction < (legsTop + headBottom) * 0.5 ? BodyPart.ABDOMEN : BodyPart.BODY;
    }

    // ------------------------------------------------------------------ Accurate Hitboxes cross-check

    /**
     * Cross-check for a verdict that did NOT come from the model above (Accurate Hitboxes bone data).
     * Returns a corrected part when the verdict is physically impossible at the real hit position,
     * otherwise the part unchanged. Chest vs abdomen is left to the bone data; legs vs feet is decided
     * by the real hit height.
     */
    public static BodyPart sanityCheck(LivingEntity target, Vec3 hitPos, BodyPart part) {
        if (!LimbConfig.SANITY_GUARDS.get()) {
            return part;
        }
        Diagnosis d = diagnose(target, hitPos);
        if (Double.isNaN(d.heightFraction())) {
            return part; // non-upright pose: no opinion
        }
        boolean model = LimbConfig.PLAYER_PROPORTIONS.get();
        double hf = d.heightFraction();
        double legsTop = model ? 0.39 : LimbConfig.LEGS_TOP_FRACTION.get();
        double headBottom = model ? 0.78 : LimbConfig.HEAD_BOTTOM_FRACTION.get();
        BodyPart legZone = hf * PlayerModel.REFERENCE_HEIGHT < LimbConfig.FEET_HEIGHT.get() ? BodyPart.FEET : BodyPart.LEGS;
        BodyPart torso = hf < (legsTop + headBottom) * 0.5 ? BodyPart.ABDOMEN : BodyPart.BODY;

        if (hf <= legsTop - 0.03 && part != BodyPart.LEGS && part != BodyPart.FEET) {
            return legZone;
        }
        if (part == BodyPart.LEGS || part == BodyPart.FEET) {
            return hf <= legsTop + 0.03 ? legZone : part;
        }
        // Accurate Hitboxes inflates its bone boxes (up to 2 px), so its HEAD box reaches 2 px down over the
        // shoulders and 2 px out to the sides: the "top 2 px of the shoulders are headshots" problem. Where the
        // real player model says the hit is not on the head, within that 2 px band around the neck line, trust
        // the model over the inflated bone.
        if (part == BodyPart.HEAD) {
            boolean nearNeck = model ? normalizedHeight(target, hitPos) < PlayerModel.NECK_Y + 0.14
                    : hf < headBottom + 0.07;
            if (nearNeck && d.part() != BodyPart.HEAD) {
                return d.part();
            }
        }
        if (part == BodyPart.ARMS && hf > legsTop && hf < headBottom
                && d.sidewaysOffset() < 0.5 * d.sidewaysThreshold()) {
            return torso;
        }
        return part;
    }

    /** Global multiplier of a part (see {@link Multipliers} for the full resolution incl. weapon categories). */
    public static double multiplier(BodyPart part) {
        return Multipliers.global(part);
    }
}
