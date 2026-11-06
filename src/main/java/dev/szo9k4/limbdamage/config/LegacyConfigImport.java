package dev.szo9k4.limbdamage.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import dev.szo9k4.limbdamage.LimbDamageMod;
import dev.szo9k4.limbdamage.recap.client.RecapClientConfig;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * One-time import of the pre-YACL TOML files, so nobody has to redo their tuning. It only runs when the new
 * JSON file does not exist yet, never overwrites anything afterwards, and leaves the old TOML in place.
 */
public final class LegacyConfigImport {

    private LegacyConfigImport() {
    }

    // ------------------------------------------------------------------ gameplay (limbdamage-common.toml)

    public static void importCommon(LimbSettings s) {
        Path file = FMLPaths.CONFIGDIR.get().resolve("limbdamage-common.toml");
        if (!Files.isRegularFile(file)) {
            return;
        }
        try (CommentedFileConfig c = CommentedFileConfig.builder(file).build()) {
            c.load();
            var m = s.multipliers;
            m.head = num(c, "multipliers.head", m.head);
            m.body = num(c, "multipliers.body", m.body);
            m.abdomen = num(c, "multipliers.abdomen", m.abdomen);
            m.arms = num(c, "multipliers.arms", m.arms);
            m.legs = num(c, "multipliers.legs", m.legs);
            m.feet = num(c, "multipliers.feet", m.feet);

            var e = s.entities;
            e.affectPlayers = bool(c, "entities.affectPlayers", e.affectPlayers);
            e.affectMobs = bool(c, "entities.affectMobs", e.affectMobs);
            e.autoDetectHumanoid = bool(c, "entities.autoDetectHumanoid", e.autoDetectHumanoid);
            e.extraEntityIds = strings(c, "entities.extraEntityIds", e.extraEntityIds);
            e.blacklistEntityIds = strings(c, "entities.blacklistEntityIds", e.blacklistEntityIds);

            var g = s.geometry;
            g.legsTopFraction = num(c, "geometry.legsTopFraction", g.legsTopFraction);
            g.headBottomFraction = num(c, "geometry.headBottomFraction", g.headBottomFraction);
            g.armMinLateral = num(c, "geometry.armMinLateral", g.armMinLateral);
            g.armMinForward = num(c, "geometry.armMinForward", g.armMinForward);
            g.armTopFraction = num(c, "geometry.armTopFraction", g.armTopFraction);
            g.feetHeight = num(c, "geometry.feetHeight", g.feetHeight);
            g.rayClassification = bool(c, "geometry.rayClassification", g.rayClassification);
            g.playerProportions = bool(c, "geometry.playerProportions", g.playerProportions);
            g.sanityGuards = bool(c, "geometry.sanityGuards", g.sanityGuards);

            var o = s.obbClassification;
            o.useObbClassification = bool(c, "obbClassification.useObbClassification", o.useObbClassification);
            o.obbHeadYTolerance = num(c, "obbClassification.obbHeadYTolerance", o.obbHeadYTolerance);
            o.obbLegHeightFraction = num(c, "obbClassification.obbLegHeightFraction", o.obbLegHeightFraction);
            o.obbArmOffset = num(c, "obbClassification.obbArmOffset", o.obbArmOffset);

            s.applyToMelee = bool(c, "damageKinds.applyToMelee", s.applyToMelee);

            var r = s.recap;
            r.enableDeathRecap = bool(c, "recap.enableDeathRecap", r.enableDeathRecap);
            r.fightTimeoutSeconds = num(c, "recap.fightTimeoutSeconds", r.fightTimeoutSeconds);
            r.capToRemainingHealth = bool(c, "recap.capToRemainingHealth", r.capToRemainingHealth);

            var f = s.fragile;
            f.pauseRegenerationEffect = bool(c, "fragile.pauseRegenerationEffect", f.pauseRegenerationEffect);
            f.blockNaturalRegen = bool(c, "fragile.blockNaturalRegen", f.blockNaturalRegen);
            f.syncRegenTweakMod = bool(c, "fragile.syncRegenTweakMod", f.syncRegenTweakMod);
            f.extraDelaySeconds = num(c, "fragile.extraDelaySeconds", f.extraDelaySeconds);

            var d = s.dummy;
            d.health = num(c, "dummy.health", d.health);
            d.cannotDie = bool(c, "dummy.cannotDie", d.cannotDie);
            d.autoHealSeconds = num(c, "dummy.autoHealSeconds", d.autoHealSeconds);
            d.showHitInfo = bool(c, "dummy.showHitInfo", d.showHitInfo);
            d.reportEnabled = bool(c, "dummy.reportEnabled", d.reportEnabled);
            d.reportDelaySeconds = num(c, "dummy.reportDelaySeconds", d.reportDelaySeconds);
            d.reportMaxHits = (int) num(c, "dummy.reportMaxHits", d.reportMaxHits);
            d.dieAndRespawn = bool(c, "dummy.dieAndRespawn", d.dieAndRespawn);
            d.respawnDelaySeconds = num(c, "dummy.respawnDelaySeconds", d.respawnDelaySeconds);

            var w = s.weaponCategories;
            w.enabled = bool(c, "weaponCategories.enabled", w.enabled);
            w.headshotOverride = bool(c, "weaponCategories.headshotOverride", w.headshotOverride);
            w.headshotGuard = bool(c, "weaponCategories.headshotGuard", w.headshotGuard);
            w.gunTypeOverrides = strings(c, "weaponCategories.gunTypeOverrides", w.gunTypeOverrides);
            for (String category : CategoryConfig.CATEGORIES) {
                for (String part : CategoryConfig.PART_KEYS) {
                    double v = num(c, "weaponCategories." + category + "." + part, -1.0);
                    w.values.computeIfAbsent(category, k -> new java.util.LinkedHashMap<>()).put(part, v);
                }
            }

            var t = s.dealtToast;
            t.enabled = bool(c, "dealtToast.enabled", t.enabled);
            t.trackSeconds = num(c, "dealtToast.trackSeconds", t.trackSeconds);
            t.maxPlayers = (int) num(c, "dealtToast.maxPlayers", t.maxPlayers);

            s.debug.logHits = bool(c, "debug.logHits", s.debug.logHits);
            s.debug.announceKills = bool(c, "debug.announceKills", s.debug.announceKills);
            LimbDamageMod.LOGGER.info("[LimbDamage] Imported your old settings from {}", file.getFileName());
        } catch (Throwable t) {
            LimbDamageMod.LOGGER.warn("[LimbDamage] Could not import {} ({}); starting from defaults.", file.getFileName(), t.toString());
        }
    }

    // ------------------------------------------------------------------ HUD (limbdamage-client.toml)

    public static void importClient(ClientSettings s) {
        Path file = FMLPaths.CONFIGDIR.get().resolve("limbdamage-client.toml");
        if (!Files.isRegularFile(file)) {
            return;
        }
        try (CommentedFileConfig c = CommentedFileConfig.builder(file).build()) {
            c.load();
            s.general.enabled = bool(c, "general.enabled", s.general.enabled);

            var l = s.layout;
            Object anchor = c.get("layout.anchor");
            if (anchor != null) {
                try {
                    l.anchor = RecapClientConfig.Anchor.valueOf(String.valueOf(anchor).trim().toUpperCase(java.util.Locale.ROOT));
                } catch (IllegalArgumentException ignored) {
                    // keep the default
                }
            }
            l.marginX = (int) num(c, "layout.marginX", l.marginX);
            l.marginY = (int) num(c, "layout.marginY", l.marginY);
            l.scale = num(c, "layout.scale", l.scale);
            l.uiOpacity = num(c, "layout.uiOpacity", l.uiOpacity);
            l.backgroundOpacity = num(c, "layout.backgroundOpacity", l.backgroundOpacity);
            l.maxRows = (int) num(c, "layout.maxRows", l.maxRows);
            l.reportMaxRows = (int) num(c, "layout.reportMaxRows", l.reportMaxRows);
            l.nameMaxWidth = (int) num(c, "layout.nameMaxWidth", l.nameMaxWidth);
            l.nameMarquee = bool(c, "layout.nameMarquee", l.nameMarquee);

            var n = s.content;
            n.showHeader = bool(c, "content.showHeader", n.showHeader);
            n.showWeaponIcon = bool(c, "content.showWeaponIcon", n.showWeaponIcon);
            n.showHitsColumn = bool(c, "content.showHitsColumn", n.showHitsColumn);
            n.showBodyIcon = bool(c, "content.showBodyIcon", n.showBodyIcon);
            n.showDealtToast = bool(c, "content.showDealtToast", n.showDealtToast);
            n.showProgressBar = bool(c, "content.showProgressBar", n.showProgressBar);
            n.mirrorBody = bool(c, "content.mirrorBody", n.mirrorBody);
            n.damageDecimals = (int) num(c, "content.damageDecimals", n.damageDecimals);

            var b = s.bodyLevels;
            b.colorByLevel = bool(c, "bodyLevels.colorByLevel", b.colorByLevel);
            b.mediumAt = num(c, "bodyLevels.mediumAt", b.mediumAt);
            b.highAt = num(c, "bodyLevels.highAt", b.highAt);
            b.criticalAt = num(c, "bodyLevels.criticalAt", b.criticalAt);

            var a = s.animation;
            a.holdSeconds = num(c, "animation.holdSeconds", a.holdSeconds);
            a.reportHoldSeconds = num(c, "animation.reportHoldSeconds", a.reportHoldSeconds);
            a.slideInMs = (int) num(c, "animation.slideInMs", a.slideInMs);
            a.slideOutMs = (int) num(c, "animation.slideOutMs", a.slideOutMs);
            a.rowStaggerMs = (int) num(c, "animation.rowStaggerMs", a.rowStaggerMs);
            a.countUpMs = (int) num(c, "animation.countUpMs", a.countUpMs);
            a.bodyFlashMs = (int) num(c, "animation.bodyFlashMs", a.bodyFlashMs);

            var k = s.colors;
            k.accent = str(c, "colors.accent", k.accent);
            k.hit = str(c, "colors.hit", k.hit);
            k.untouched = str(c, "colors.untouched", k.untouched);
            k.self = str(c, "colors.self", k.self);
            k.dealt = str(c, "colors.dealt", k.dealt);
            k.lowColor = str(c, "colors.lowColor", k.lowColor);
            k.mediumColor = str(c, "colors.mediumColor", k.mediumColor);
            k.highColor = str(c, "colors.highColor", k.highColor);
            k.criticalColor = str(c, "colors.criticalColor", k.criticalColor);
            LimbDamageMod.LOGGER.info("[LimbDamage] Imported your old HUD settings from {}", file.getFileName());
        } catch (Throwable t) {
            LimbDamageMod.LOGGER.warn("[LimbDamage] Could not import {} ({}); starting from defaults.", file.getFileName(), t.toString());
        }
    }

    // ------------------------------------------------------------------ helpers

    private static double num(CommentedFileConfig c, String path, double fallback) {
        Object v = c.get(path);
        return v instanceof Number n ? n.doubleValue() : fallback;
    }

    private static boolean bool(CommentedFileConfig c, String path, boolean fallback) {
        Object v = c.get(path);
        return v instanceof Boolean b ? b : fallback;
    }

    private static String str(CommentedFileConfig c, String path, String fallback) {
        Object v = c.get(path);
        return v instanceof String s && !s.isBlank() ? s : fallback;
    }

    private static List<String> strings(CommentedFileConfig c, String path, List<String> fallback) {
        Object v = c.get(path);
        if (!(v instanceof List<?> list)) {
            return fallback;
        }
        List<String> out = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof String s) {
                out.add(s);
            }
        }
        return out;
    }
}
