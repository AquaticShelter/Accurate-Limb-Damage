package dev.szo9k4.limbdamage.geometry;

import dev.szo9k4.limbdamage.BodyPart;

/**
 * The vanilla player model as six boxes, plus the two queries the hit classifier needs. PURE MATH:
 * no Minecraft classes, so it can be read, reasoned about and unit-tested on its own.
 *
 * <p>Frame: every coordinate is (lateral, height, forward) relative to the victim's feet-center and
 * body yaw, normalised to a 1.8 tall x 0.6 wide player. Lateral is positive towards the player's
 * LEFT. One model pixel = {@link #PX} blocks (the model is drawn at 0.9375 scale).
 *
 * <pre>
 *   head   8x8x8 px   x +-0.234  y 1.406..1.875  z +-0.234
 *   torso  8x12x4 px  x +-0.234  y 0.703..1.406  z +-0.117   upper half = chest, lower half = abdomen
 *   arms   4x12x4 px  x 0.234..0.469 each side, same height as the torso
 *   legs   4x12x4 px  x 0..0.234 each side, y 0..0.703        lowest feetHeight = feet
 * </pre>
 */
public final class PlayerModel {

    public static final double REFERENCE_WIDTH = 0.6;
    public static final double REFERENCE_HEIGHT = 1.8;
    public static final double PX = 0.9375 / 16.0;
    /** Height splitting the torso into chest (above) and abdomen (below). */
    public static final double TORSO_MID_Y = 18 * PX;
    /** Where the head starts (top of the torso / shoulders). Nothing below this line can be a head hit. */
    public static final double NECK_Y = 24 * PX;

    /** Tolerance around model boxes when casting a ray (bullet radius / rounding). */
    private static final double RAY_EPS = 0.01;

    // Box layout {minX, maxX, minY, maxY, minZ, maxZ}; X = lateral, Y = height, Z = forward.
    private static final double[] HEAD = {-4 * PX, 4 * PX, 24 * PX, 32 * PX, -4 * PX, 4 * PX};
    private static final double[] TORSO = {-4 * PX, 4 * PX, 12 * PX, 24 * PX, -2 * PX, 2 * PX};
    private static final double[] ARM_L = {4 * PX, 8 * PX, 12 * PX, 24 * PX, -2 * PX, 2 * PX};
    private static final double[] ARM_R = {-8 * PX, -4 * PX, 12 * PX, 24 * PX, -2 * PX, 2 * PX};
    private static final double[] LEG_L = {0.0, 4 * PX, 0.0, 12 * PX, -2 * PX, 2 * PX};
    private static final double[] LEG_R = {-4 * PX, 0.0, 0.0, 12 * PX, -2 * PX, 2 * PX};

    /** Box indices, in tie-break priority order (head beats arm beats torso beats leg). */
    private static final int B_HEAD = 0, B_ARM_L = 1, B_ARM_R = 2, B_TORSO = 3, B_LEG_L = 4, B_LEG_R = 5;
    private static final double[][] MODEL = {HEAD, ARM_L, ARM_R, TORSO, LEG_L, LEG_R};
    // Inflated copies are built once, not per shot.
    private static final double[][] MODEL_INFLATED = inflateAll(MODEL, RAY_EPS);
    /**
     * Region a path is examined in when it touches no model box. Deliberately much larger than the vanilla
     * hitbox: TACZ grows the box it tests (hits show up at +-0.38 lateral and ~0.4 forward), so rim hits
     * land well outside 0.3 and must still be classified.
     */
    private static final double[] REGION = {-0.55, 0.55, -0.1, 2.1, -0.55, 0.55};
    /** Tie-break order when a point is equally close to two boxes: arms before head, so a doubtful
     *  shoulder-top hit is a shoulder, never a headshot. */
    private static final int[] FALLBACK_ORDER = {B_ARM_L, B_ARM_R, B_HEAD, B_TORSO, B_LEG_L, B_LEG_R};

    /** 1 px: how far beside the head still counts as a head graze when the path misses the head itself. */
    private static final double HEAD_SIDE_MARGIN = 0.0625;
    /**
     * 2 px. Accurate Hitboxes inflates its bone boxes by up to 0.125 (and TACZ's own box can be bigger than
     * the model), so a shot can be registered up to ~2 px ABOVE a shoulder or ABOVE the top of the head
     * without touching the real model. For those misses the arms get 2 px of extra height (a shot that skims
     * the top of the shoulder is a shoulder hit, NOT a headshot) and the head gets 2 px of extra height
     * (a shot at the very top edge of the head is still a headshot). Only used when the path touches no
     * real box; a path that really enters the head box is always a head hit.
     */
    private static final double SHOULDER_MARGIN = 0.125;
    /** Same box order as MODEL, with the margins above applied. Used only by the silhouette fallback. */
    private static final double[][] SIL = {
            {HEAD[0] - HEAD_SIDE_MARGIN, HEAD[1] + HEAD_SIDE_MARGIN, HEAD[2], HEAD[3] + SHOULDER_MARGIN,
                    HEAD[4] - HEAD_SIDE_MARGIN, HEAD[5] + HEAD_SIDE_MARGIN},
            {ARM_L[0], ARM_L[1], ARM_L[2], ARM_L[3] + SHOULDER_MARGIN, ARM_L[4], ARM_L[5]},
            {ARM_R[0], ARM_R[1], ARM_R[2], ARM_R[3] + SHOULDER_MARGIN, ARM_R[4], ARM_R[5]},
            TORSO, LEG_L, LEG_R};

    /** A classified hit. lateral is signed (+ = left); its sign is what picks the left/right limb. */
    public record Hit(BodyPart part, double lateral) {
    }

    private PlayerModel() {
    }

    /**
     * Classifies a single hit POINT on the hitbox surface (no shot path known).
     *
     * The point is treated as a shot arriving STRAIGHT through the face it lies on (front/back along the
     * forward axis, left/right along the lateral axis) and the first model box that shot enters wins.
     * (Plain "nearest box" is wrong here: a surface point is ~0.18 in front of the torso but only ~0.07
     * in front of the 8 px deep head, so everything up to ~2.7 px below the neck used to read as head.)
     * A straight shot that touches nothing - the air above a shoulder, the strip beside the head - is
     * decided by the silhouette seen from that side, see {@link #silhouette}.
     */
    public static Hit nearest(double h, double l, double f, double feetHeight) {
        double dl = 0.0;
        double df = 0.0;
        if (Math.abs(f) >= Math.abs(l)) {
            df = f > 0.0 ? -1.0 : 1.0;
        } else {
            dl = l > 0.0 ? -1.0 : 1.0;
        }
        Hit viaRay = cast(new double[]{l, h, f}, new double[]{l + dl, h, f + df}, feetHeight);
        return viaRay != null ? viaRay : silhouette(h, l, f, feetHeight);
    }

    /**
     * Casts the path a -> b (each {lateral, height, forward}) against the model and returns the FIRST
     * box it enters: this is what makes the rim right (top of the head, shoulders, feet). A path that
     * touches no model box (it grazes the air above a shoulder, or passes beside the head) is decided by
     * the SILHOUETTE: the box closest to the path as seen from the side the shot comes from. Returns
     * null only when the path never gets near the player at all.
     */
    public static Hit cast(double[] a, double[] b, double feetHeight) {
        double dl = b[0] - a[0];
        double dh = b[1] - a[1];
        double df = b[2] - a[2];
        double len = Math.sqrt(dl * dl + dh * dh + df * df);
        if (len < 1.0E-9) {
            return null;
        }
        double[] d = {dl, dh, df};
        // The reported end may stop right at the hitbox surface: look 1.5 blocks beyond it.
        double tMax = 1.0 + 1.5 / len;
        double[] range = new double[2];

        int bestBox = -1;
        double bestT = Double.MAX_VALUE;
        for (int i = 0; i < MODEL.length; i++) {
            if (slab(MODEL_INFLATED[i], a, d, tMax, range) && range[0] < bestT - 1.0E-9) {
                bestT = range[0];
                bestBox = i;
            }
        }
        if (bestBox >= 0) {
            return hitFor(bestBox, a[1] + bestT * dh, a[0] + bestT * dl, feetHeight);
        }

        // Touched no box: find where the path comes closest to the silhouette.
        if (!slab(REGION, a, d, tMax, range)) {
            return null;
        }
        final int samples = 24;
        int sampleBox = -1;
        double sampleD = Double.MAX_VALUE;
        double sh = 0.0;
        double sl = 0.0;
        for (int k = 0; k <= samples; k++) {
            double t = range[0] + (range[1] - range[0]) * k / samples;
            double pl = a[0] + dl * t;
            double ph = a[1] + dh * t;
            double pf = a[2] + df * t;
            for (int i : FALLBACK_ORDER) {
                double dist = silhouetteDistance(i, pl, ph, pf);
                if (dist < sampleD - 1.0E-9) {
                    sampleD = dist;
                    sampleBox = i;
                    sh = ph;
                    sl = pl;
                }
            }
        }
        return sampleBox < 0 ? null : hitFor(sampleBox, sh, sl, feetHeight);
    }

    /**
     * The box nearest to a point in the SILHOUETTE (with the margins above), taking the better of the front
     * view (lateral x height) and the side view (forward x height). Depth is ignored on purpose: a hit point
     * sits on the outside of the hitbox, so depth carries no information and only biases the choice toward
     * the deepest box (the head). Both views are used because at a hitbox corner it isn't known which face the
     * shot came through; ties go to the arms, so a doubtful shoulder hit is never promoted to a headshot.
     */
    private static Hit silhouette(double h, double l, double f, double feetHeight) {
        int best = -1;
        double bestD = Double.MAX_VALUE;
        for (int i : FALLBACK_ORDER) {
            double d = silhouetteDistance(i, l, h, f);
            if (d < bestD - 1.0E-9) { // strict: a tie keeps the earlier box in FALLBACK_ORDER
                bestD = d;
                best = i;
            }
        }
        return hitFor(best, h, l, feetHeight);
    }

    private static Hit hitFor(int box, double h, double l, double feetHeight) {
        double magnitude = Math.abs(l) + 1.0E-6;
        return switch (box) {
            case B_HEAD -> new Hit(BodyPart.HEAD, l);
            case B_ARM_L -> new Hit(BodyPart.ARMS, magnitude);
            case B_ARM_R -> new Hit(BodyPart.ARMS, -magnitude);
            case B_TORSO -> new Hit(h < TORSO_MID_Y ? BodyPart.ABDOMEN : BodyPart.BODY, l);
            case B_LEG_L -> new Hit(h < feetHeight ? BodyPart.FEET : BodyPart.LEGS, magnitude);
            default -> new Hit(h < feetHeight ? BodyPart.FEET : BodyPart.LEGS, -magnitude);
        };
    }

    /** Ray / box slab test over t in [0, tMax]. On a hit, out = {tEnter, tExit} and true is returned. */
    private static boolean slab(double[] box, double[] o, double[] d, double tMax, double[] out) {
        double t0 = 0.0;
        double t1 = tMax;
        for (int axis = 0; axis < 3; axis++) {
            double min = box[axis * 2];
            double max = box[axis * 2 + 1];
            if (Math.abs(d[axis]) < 1.0E-12) {
                if (o[axis] < min || o[axis] > max) {
                    return false;
                }
            } else {
                double ta = (min - o[axis]) / d[axis];
                double tb = (max - o[axis]) / d[axis];
                if (ta > tb) {
                    double swap = ta;
                    ta = tb;
                    tb = swap;
                }
                t0 = Math.max(t0, ta);
                t1 = Math.min(t1, tb);
                if (t0 > t1) {
                    return false;
                }
            }
        }
        out[0] = t0;
        out[1] = t1;
        return true;
    }

    private static double silhouetteDistance(int box, double l, double h, double f) {
        return Math.min(distance2D(SIL[box], false, l, h, f), distance2D(SIL[box], true, l, h, f));
    }

    private static double distance2D(double[] b, boolean sideView, double l, double h, double f) {
        double across = sideView ? f : l;
        double lo = sideView ? b[4] : b[0];
        double hi = sideView ? b[5] : b[1];
        double dx = Math.max(Math.max(lo - across, 0.0), across - hi);
        double dy = Math.max(Math.max(b[2] - h, 0.0), h - b[3]);
        return dx * dx + dy * dy;
    }

    private static double[] inflate(double[] b, double e) {
        return new double[]{b[0] - e, b[1] + e, b[2] - e, b[3] + e, b[4] - e, b[5] + e};
    }

    private static double[][] inflateAll(double[][] boxes, double e) {
        double[][] out = new double[boxes.length][];
        for (int i = 0; i < boxes.length; i++) {
            out[i] = inflate(boxes[i], e);
        }
        return out;
    }
}
