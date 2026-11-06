package dev.szo9k4.limbdamage.recap.client;

import dev.isxander.yacl3.config.v2.api.ConfigClassHandler;
import dev.isxander.yacl3.config.v2.api.serializer.GsonConfigSerializerBuilder;
import dev.szo9k4.limbdamage.LimbDamageMod;
import dev.szo9k4.limbdamage.config.ClientSettings;
import dev.szo9k4.limbdamage.config.LegacyConfigImport;
import dev.szo9k4.limbdamage.config.Val;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * CLIENT config (config/limbdamage-client.json5, managed by YACL): everything about how the damage HUD looks
 * and moves. Same live-view shape as {@code LimbConfig}. Only ever touched on the client.
 */
public final class RecapClientConfig {

    public enum Anchor { TOP_RIGHT, TOP_LEFT, BOTTOM_RIGHT, BOTTOM_LEFT }

    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("limbdamage-client.json5");

    public static final ConfigClassHandler<ClientSettings> HANDLER = ConfigClassHandler.createBuilder(ClientSettings.class)
            .id(new ResourceLocation(LimbDamageMod.MOD_ID, "client"))
            .serializer(config -> GsonConfigSerializerBuilder.create(config)
                    .setPath(FILE)
                    .setJson5(true)
                    .build())
            .build();

    private static ClientSettings s() {
        return HANDLER.instance();
    }

    public static final Val<Boolean> ENABLED = new Val<>(() -> s().general.enabled);

    // layout
    public static final Val<Anchor> ANCHOR = new Val<>(() -> s().layout.anchor);
    public static final Val<Integer> MARGIN_X = new Val<>(() -> s().layout.marginX);
    public static final Val<Integer> MARGIN_Y = new Val<>(() -> s().layout.marginY);
    public static final Val<Double> SCALE = new Val<>(() -> s().layout.scale);
    public static final Val<Double> UI_OPACITY = new Val<>(() -> s().layout.uiOpacity);
    public static final Val<Double> BACKGROUND_OPACITY = new Val<>(() -> s().layout.backgroundOpacity);
    public static final Val<Integer> MAX_ROWS = new Val<>(() -> s().layout.maxRows);
    public static final Val<Integer> REPORT_MAX_ROWS = new Val<>(() -> s().layout.reportMaxRows);
    public static final Val<Integer> NAME_MAX_WIDTH = new Val<>(() -> s().layout.nameMaxWidth);
    public static final Val<Boolean> NAME_MARQUEE = new Val<>(() -> s().layout.nameMarquee);

    // content
    public static final Val<Boolean> SHOW_HEADER = new Val<>(() -> s().content.showHeader);
    public static final Val<Boolean> SHOW_WEAPON = new Val<>(() -> s().content.showWeaponIcon);
    public static final Val<Boolean> SHOW_HITS = new Val<>(() -> s().content.showHitsColumn);
    public static final Val<Boolean> SHOW_BODY = new Val<>(() -> s().content.showBodyIcon);
    public static final Val<Boolean> SHOW_PROGRESS_BAR = new Val<>(() -> s().content.showProgressBar);
    public static final Val<Boolean> SHOW_DEALT = new Val<>(() -> s().content.showDealtToast);
    public static final Val<Integer> DAMAGE_DECIMALS = new Val<>(() -> s().content.damageDecimals);
    public static final Val<Boolean> MIRROR_BODY = new Val<>(() -> s().content.mirrorBody);

    // body colour levels
    public static final Val<Boolean> COLOR_BY_LEVEL = new Val<>(() -> s().bodyLevels.colorByLevel);
    public static final Val<Double> LEVEL_MEDIUM = new Val<>(() -> s().bodyLevels.mediumAt);
    public static final Val<Double> LEVEL_HIGH = new Val<>(() -> s().bodyLevels.highAt);
    public static final Val<Double> LEVEL_CRITICAL = new Val<>(() -> s().bodyLevels.criticalAt);

    // animation
    public static final Val<Double> HOLD_SECONDS = new Val<>(() -> s().animation.holdSeconds);
    public static final Val<Double> REPORT_HOLD_SECONDS = new Val<>(() -> s().animation.reportHoldSeconds);
    public static final Val<Integer> SLIDE_IN_MS = new Val<>(() -> s().animation.slideInMs);
    public static final Val<Integer> SLIDE_OUT_MS = new Val<>(() -> s().animation.slideOutMs);
    public static final Val<Integer> ROW_STAGGER_MS = new Val<>(() -> s().animation.rowStaggerMs);
    public static final Val<Integer> COUNT_UP_MS = new Val<>(() -> s().animation.countUpMs);
    public static final Val<Integer> BODY_FLASH_MS = new Val<>(() -> s().animation.bodyFlashMs);

    // rendering
    public static final Val<Boolean> PIXEL_SNAP = new Val<>(() -> s().rendering.pixelSnap);
    public static final Val<String> FONT = new Val<>(() -> s().rendering.font);
    public static final Val<Boolean> SMOOTH_WEAPON_ICONS = new Val<>(() -> s().rendering.smoothWeaponIcons);

    // colours
    public static final Val<String> ACCENT_COLOR = new Val<>(() -> s().colors.accent);
    public static final Val<String> HIT_COLOR = new Val<>(() -> s().colors.hit);
    public static final Val<String> BASE_COLOR = new Val<>(() -> s().colors.untouched);
    public static final Val<String> SELF_COLOR = new Val<>(() -> s().colors.self);
    public static final Val<String> DEALT_ACCENT_COLOR = new Val<>(() -> s().colors.dealt);
    public static final Val<String> LEVEL_LOW_COLOR = new Val<>(() -> s().colors.lowColor);
    public static final Val<String> LEVEL_MEDIUM_COLOR = new Val<>(() -> s().colors.mediumColor);
    public static final Val<String> LEVEL_HIGH_COLOR = new Val<>(() -> s().colors.highColor);
    public static final Val<String> LEVEL_CRITICAL_COLOR = new Val<>(() -> s().colors.criticalColor);

    private RecapClientConfig() {
    }

    public static ClientSettings settings() {
        return s();
    }

    public static void load() {
        boolean fresh = !Files.exists(FILE);
        try {
            HANDLER.load();
        } catch (Throwable t) {
            LimbDamageMod.LOGGER.error("[LimbDamage] Could not read {}; using defaults for this session.", FILE, t);
        }
        if (fresh) {
            LegacyConfigImport.importClient(s());
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
}
