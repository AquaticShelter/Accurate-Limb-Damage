package dev.szo9k4.limbdamage.config;

import dev.isxander.yacl3.config.v2.api.ConfigClassHandler;
import dev.isxander.yacl3.config.v2.api.serializer.GsonConfigSerializerBuilder;
import dev.szo9k4.limbdamage.LimbDamageMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Gameplay config (config/limbdamage-common.json5), managed by YACL. Every field below is a live view:
 * {@code LimbConfig.HEAD_MULTIPLIER.get()} reads the current value, so /limbdamage reload and the in-game
 * config screen take effect immediately and nothing needs to be re-registered.
 *
 * Damage is computed on the server, so the server's file is the one that matters; the same file also exists
 * on clients for singleplayer / LAN.
 */
public final class LimbConfig {

    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("limbdamage-common.json5");

    public static final ConfigClassHandler<LimbSettings> HANDLER = ConfigClassHandler.createBuilder(LimbSettings.class)
            .id(new ResourceLocation(LimbDamageMod.MOD_ID, "common"))
            .serializer(config -> GsonConfigSerializerBuilder.create(config)
                    .setPath(FILE)
                    .setJson5(true)
                    .build())
            .build();

    private static LimbSettings s() {
        return HANDLER.instance();
    }

    // Per-part damage multipliers
    public static final Val<Double> HEAD_MULTIPLIER = new Val<>(() -> s().multipliers.head);
    public static final Val<Double> BODY_MULTIPLIER = new Val<>(() -> s().multipliers.body);
    public static final Val<Double> ABDOMEN_MULTIPLIER = new Val<>(() -> s().multipliers.abdomen);
    public static final Val<Double> ARMS_MULTIPLIER = new Val<>(() -> s().multipliers.arms);
    public static final Val<Double> LEGS_MULTIPLIER = new Val<>(() -> s().multipliers.legs);
    public static final Val<Double> FEET_MULTIPLIER = new Val<>(() -> s().multipliers.feet);

    // Which entities get locational damage
    public static final Val<Boolean> AFFECT_PLAYERS = new Val<>(() -> s().entities.affectPlayers);
    public static final Val<Boolean> AFFECT_MOBS = new Val<>(() -> s().entities.affectMobs);
    public static final Val<Boolean> AUTO_DETECT_HUMANOID = new Val<>(() -> s().entities.autoDetectHumanoid);
    public static final Val<List<? extends String>> EXTRA_ENTITY_IDS = new Val<>(() -> s().entities.extraEntityIds);
    public static final Val<List<? extends String>> BLACKLIST_ENTITY_IDS = new Val<>(() -> s().entities.blacklistEntityIds);

    // Zone geometry
    public static final Val<Double> LEGS_TOP_FRACTION = new Val<>(() -> s().geometry.legsTopFraction);
    public static final Val<Double> HEAD_BOTTOM_FRACTION = new Val<>(() -> s().geometry.headBottomFraction);
    public static final Val<Double> ARM_MIN_LATERAL = new Val<>(() -> s().geometry.armMinLateral);
    public static final Val<Double> ARM_MIN_FORWARD = new Val<>(() -> s().geometry.armMinForward);
    public static final Val<Double> ARM_TOP_FRACTION = new Val<>(() -> s().geometry.armTopFraction);
    public static final Val<Double> FEET_HEIGHT = new Val<>(() -> s().geometry.feetHeight);
    public static final Val<Boolean> RAY_CLASSIFICATION = new Val<>(() -> s().geometry.rayClassification);
    public static final Val<Boolean> PLAYER_PROPORTIONS = new Val<>(() -> s().geometry.playerProportions);
    public static final Val<Boolean> SANITY_GUARDS = new Val<>(() -> s().geometry.sanityGuards);

    // Accurate Hitboxes per-bone classification
    public static final Val<Boolean> USE_OBB_CLASSIFICATION = new Val<>(() -> s().obbClassification.useObbClassification);
    public static final Val<Double> OBB_HEAD_Y_TOLERANCE = new Val<>(() -> s().obbClassification.obbHeadYTolerance);
    public static final Val<Double> OBB_LEG_HEIGHT_FRACTION = new Val<>(() -> s().obbClassification.obbLegHeightFraction);
    public static final Val<Double> OBB_ARM_OFFSET = new Val<>(() -> s().obbClassification.obbArmOffset);

    public static final Val<Boolean> APPLY_TO_MELEE = new Val<>(() -> s().applyToMelee);

    // Death recap
    public static final Val<Boolean> ENABLE_DEATH_RECAP = new Val<>(() -> s().recap.enableDeathRecap);
    public static final Val<Double> RECAP_FIGHT_TIMEOUT_SECONDS = new Val<>(() -> s().recap.fightTimeoutSeconds);
    public static final Val<Boolean> RECAP_CAP_OVERKILL = new Val<>(() -> s().recap.capToRemainingHealth);

    // Fragile effect
    public static final Val<Boolean> FRAGILE_PAUSE_REGEN_EFFECT = new Val<>(() -> s().fragile.pauseRegenerationEffect);
    public static final Val<Boolean> FRAGILE_BLOCK_NATURAL_REGEN = new Val<>(() -> s().fragile.blockNaturalRegen);
    public static final Val<Boolean> FRAGILE_SYNC_REGEN_MOD = new Val<>(() -> s().fragile.syncRegenTweakMod);
    public static final Val<Double> FRAGILE_EXTRA_DELAY_SECONDS = new Val<>(() -> s().fragile.extraDelaySeconds);

    // Training dummy
    public static final Val<Double> DUMMY_HEALTH = new Val<>(() -> s().dummy.health);
    public static final Val<Double> DUMMY_DAMAGE_REDUCTION = new Val<>(() -> s().dummy.damageReductionPercent);
    public static final Val<Boolean> DUMMY_CANNOT_DIE = new Val<>(() -> s().dummy.cannotDie);
    public static final Val<Double> DUMMY_AUTO_HEAL_SECONDS = new Val<>(() -> s().dummy.autoHealSeconds);
    public static final Val<Boolean> DUMMY_SHOW_HIT_INFO = new Val<>(() -> s().dummy.showHitInfo);
    public static final Val<Boolean> DUMMY_REPORT_ENABLED = new Val<>(() -> s().dummy.reportEnabled);
    public static final Val<Double> DUMMY_REPORT_DELAY_SECONDS = new Val<>(() -> s().dummy.reportDelaySeconds);
    public static final Val<Integer> DUMMY_REPORT_MAX_HITS = new Val<>(() -> s().dummy.reportMaxHits);
    public static final Val<Boolean> DUMMY_DIE_AND_RESPAWN = new Val<>(() -> s().dummy.dieAndRespawn);
    public static final Val<Double> DUMMY_RESPAWN_DELAY_SECONDS = new Val<>(() -> s().dummy.respawnDelaySeconds);
    public static final Val<Boolean> DUMMY_PUBLIC_EQUIPMENT = new Val<>(() -> s().dummy.publicEquipment);

    // "Damage done" strip
    public static final Val<Boolean> DEALT_ENABLED = new Val<>(() -> s().dealtToast.enabled);
    public static final Val<Double> DEALT_TRACK_SECONDS = new Val<>(() -> s().dealtToast.trackSeconds);
    public static final Val<Integer> DEALT_MAX_PLAYERS = new Val<>(() -> s().dealtToast.maxPlayers);

    // Debug
    public static final Val<Boolean> LOG_HITS = new Val<>(() -> s().debug.logHits);
    public static final Val<Boolean> ANNOUNCE_KILLS = new Val<>(() -> s().debug.announceKills);

    private LimbConfig() {
    }

    /**
     * Loads the file (importing the old limbdamage-common.toml once if there is no JSON file yet), repairs
     * anything out of range and writes the result back so new keys appear in the file.
     */
    public static void load() {
        boolean fresh = !Files.exists(FILE);
        try {
            HANDLER.load();
        } catch (Throwable t) {
            LimbDamageMod.LOGGER.error("[LimbDamage] Could not read {}; using defaults for this session.", FILE, t);
        }
        if (fresh) {
            LegacyConfigImport.importCommon(s());
        }
        s().sanitize();
        save();
    }

    public static void save() {
        try {
            HANDLER.save();
        } catch (Throwable t) {
            LimbDamageMod.LOGGER.error("[LimbDamage] Could not write {}", FILE, t);
        }
    }

    public static LimbSettings settings() {
        return s();
    }
}
