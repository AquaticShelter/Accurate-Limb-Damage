package dev.szo9k4.limbdamage;

/**
 * Locational-damage zones. Each has its own multiplier. ARMS, LEGS and FEET cover both sides (same
 * multiplier); which side was hit is tracked separately by {@link Limb} for the death recap.
 * BODY is the chest / upper half of the torso, ABDOMEN the lower half, FEET the bottom of the legs.
 */
public enum BodyPart {
    HEAD,
    BODY,
    ABDOMEN,
    ARMS,
    LEGS,
    FEET
}
