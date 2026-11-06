package dev.szo9k4.limbdamage;

/** Fine-grained hit location used by the death recap: like {@link BodyPart} but with left/right limbs. */
public enum Limb {
    HEAD, CHEST, ABDOMEN, LEFT_ARM, RIGHT_ARM, LEFT_LEG, RIGHT_LEG, LEFT_FOOT, RIGHT_FOOT;

    /** @param lateral signed offset from the victim's center line, positive = the victim's LEFT side. */
    public static Limb of(BodyPart part, double lateral) {
        boolean left = lateral >= 0.0;
        return switch (part) {
            case HEAD -> HEAD;
            case BODY -> CHEST;
            case ABDOMEN -> ABDOMEN;
            case ARMS -> left ? LEFT_ARM : RIGHT_ARM;
            case LEGS -> left ? LEFT_LEG : RIGHT_LEG;
            case FEET -> left ? LEFT_FOOT : RIGHT_FOOT;
        };
    }

    /** Human-readable name for report rows. */
    public String label() {
        return switch (this) {
            case HEAD -> "Head";
            case CHEST -> "Chest";
            case ABDOMEN -> "Abdomen";
            case LEFT_ARM -> "Left arm";
            case RIGHT_ARM -> "Right arm";
            case LEFT_LEG -> "Left leg";
            case RIGHT_LEG -> "Right leg";
            case LEFT_FOOT -> "Left foot";
            case RIGHT_FOOT -> "Right foot";
        };
    }

    public BodyPart bodyPart() {
        return switch (this) {
            case HEAD -> BodyPart.HEAD;
            case CHEST -> BodyPart.BODY;
            case ABDOMEN -> BodyPart.ABDOMEN;
            case LEFT_ARM, RIGHT_ARM -> BodyPart.ARMS;
            case LEFT_LEG, RIGHT_LEG -> BodyPart.LEGS;
            case LEFT_FOOT, RIGHT_FOOT -> BodyPart.FEET;
        };
    }
}
