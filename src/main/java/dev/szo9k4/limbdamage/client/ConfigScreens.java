package dev.szo9k4.limbdamage.client;

import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.LabelOption;
import dev.isxander.yacl3.api.ListOption;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.ColorControllerBuilder;
import dev.isxander.yacl3.api.controller.DoubleSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import dev.szo9k4.limbdamage.config.CategoryConfig;
import dev.szo9k4.limbdamage.config.ClientSettings;
import dev.szo9k4.limbdamage.config.LimbConfig;
import dev.szo9k4.limbdamage.config.LimbSettings;
import dev.szo9k4.limbdamage.config.Multipliers;
import dev.szo9k4.limbdamage.BodyPart;
import dev.szo9k4.limbdamage.recap.client.RecapClientConfig;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The YACL config screen (client only; never loaded on a dedicated server). It edits this machine's files:
 * on a dedicated server, change the server's own config/limbdamage-common.json5 (or use /limbdamage setmult
 * and /limbdamage reload) - singleplayer and LAN use these files directly.
 */
public final class ConfigScreens {

    /** Slider position meaning "inherit the global value" in the weapon category sliders. */
    private static final double INHERIT = -310.0;

    private ConfigScreens() {
    }

    public static Screen create(Screen parent) {
        LimbSettings d = LimbConfig.HANDLER.defaults();
        LimbSettings s = LimbConfig.settings();
        ClientSettings cd = RecapClientConfig.HANDLER.defaults();
        ClientSettings cs = RecapClientConfig.settings();

        return YetAnotherConfigLib.createBuilder()
                .title(Component.literal("Limb Damage"))
                .category(damage(d, s))
                .category(weapons(d, s))
                .category(zones(d, s))
                .category(entities(d, s))
                .category(dummy(d, s))
                .category(feedback(d, s))
                .category(hud(cd, cs))
                .category(look(cd, cs))
                .save(() -> {
                    s.sanitize();
                    cs.sanitize();
                    LimbConfig.save();
                    RecapClientConfig.save();
                })
                .build()
                .generateScreen(parent);
    }

    // ------------------------------------------------------------------ categories

    private static ConfigCategory damage(LimbSettings d, LimbSettings s) {
        var m = s.multipliers;
        var dm = d.multipliers;
        return ConfigCategory.createBuilder()
                .name(Component.literal("Damage"))
                .tooltip(Component.literal("Damage per body part"))
                .option(LabelOption.create(Component.literal("Percent of damage taken off. 0 = normal, 20 = takes 80%, below 0 = takes extra.")))
                .option(reduction("Head", "TACZ already adds its own headshot bonus, so this is usually 0.", dm.head, () -> m.head, v -> m.head = v))
                .option(reduction("Chest", "Upper torso.", dm.body, () -> m.body, v -> m.body = v))
                .option(reduction("Abdomen", "Lower torso.", dm.abdomen, () -> m.abdomen, v -> m.abdomen = v))
                .option(reduction("Arms", "Both arms.", dm.arms, () -> m.arms, v -> m.arms = v))
                .option(reduction("Legs", "Both legs.", dm.legs, () -> m.legs, v -> m.legs = v))
                .option(reduction("Feet", "Bottom of the legs. Height is set in Zones.", dm.feet, () -> m.feet, v -> m.feet = v))
                .option(bool("Also apply to melee", "Work out where melee hits land from where the attacker is aiming.",
                        d.applyToMelee, () -> s.applyToMelee, v -> s.applyToMelee = v))
                .build();
    }

    private static ConfigCategory weapons(LimbSettings d, LimbSettings s) {
        var w = s.weaponCategories;
        ConfigCategory.Builder b = ConfigCategory.createBuilder()
                .name(Component.literal("Weapons (TACZ)"))
                .tooltip(Component.literal("Different numbers per gun type"))
                .option(LabelOption.create(Component.literal("Far left = use the number from the Damage tab. Anything else overrides it for that gun type.")))
                .option(bool("Per-gun-type values", "Off = every gun uses the Damage tab.",
                        d.weaponCategories.enabled, () -> w.enabled, v -> w.enabled = v))
                .option(bool("Headshot override", "Ignore TACZ's own headshot multiplier, only use the head value here.",
                        d.weaponCategories.headshotOverride, () -> w.headshotOverride, v -> w.headshotOverride = v))
                .option(bool("Headshot guard", "Make TACZ's headshot flag match the real player model.",
                        d.weaponCategories.headshotGuard, () -> w.headshotGuard, v -> w.headshotGuard = v))
                .group(stringList("Gun type overrides", "Put a gun in a type by hand, like tacz:ak47=rifle.",
                        () -> w.gunTypeOverrides, v -> w.gunTypeOverrides = v, "tacz:gun=rifle"));
        for (String category : CategoryConfig.CATEGORIES) {
            OptionGroup.Builder g = OptionGroup.createBuilder()
                    .name(Component.literal(category.substring(0, 1).toUpperCase(Locale.ROOT) + category.substring(1)))
                    .description(OptionDescription.of(Component.literal("Numbers for " + category + " hits.")))
                    .collapsed(true);
            for (BodyPart part : BodyPart.values()) {
                g.option(categorySlider(category, part));
            }
            b.group(g.build());
        }
        return b.build();
    }

    private static ConfigCategory zones(LimbSettings d, LimbSettings s) {
        var g = s.geometry;
        var dg = d.geometry;
        var o = s.obbClassification;
        var dobb = d.obbClassification;
        return ConfigCategory.createBuilder()
                .name(Component.literal("Zones"))
                .tooltip(Component.literal("Where body parts start and end"))
                .option(bool("Real player proportions", "Use the real player model for the borders.",
                        dg.playerProportions, () -> g.playerProportions, v -> g.playerProportions = v))
                .option(bool("Whole-path classification", "Check the shot's whole path, not just one point.",
                        dg.rayClassification, () -> g.rayClassification, v -> g.rayClassification = v))
                .option(bool("Sanity guards", "Fix impossible Accurate Hitboxes results.",
                        dg.sanityGuards, () -> g.sanityGuards, v -> g.sanityGuards = v))
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("Manual borders"))
                        .description(OptionDescription.of(Component.literal("Only used when real proportions is off. Fractions of the hitbox height.")))
                        .collapsed(true)
                        .option(dbl("Legs top", "Below this = legs.", dg.legsTopFraction, 0, 1, 0.01, () -> g.legsTopFraction, v -> g.legsTopFraction = v))
                        .option(dbl("Head bottom", "Above this = head.", dg.headBottomFraction, 0, 1, 0.01, () -> g.headBottomFraction, v -> g.headBottomFraction = v))
                        .option(dbl("Arm sideways distance", "Sideways distance from the body that counts as arm.", dg.armMinLateral, 0, 2, 0.01, () -> g.armMinLateral, v -> g.armMinLateral = v))
                        .option(dbl("Arm forward distance", "Same, front/back.", dg.armMinForward, 0, 2, 0.01, () -> g.armMinForward, v -> g.armMinForward = v))
                        .option(dbl("Arm top", "Arms only count below this height.", dg.armTopFraction, 0, 1, 0.01, () -> g.armTopFraction, v -> g.armTopFraction = v))
                        .build())
                .option(dbl("Feet height", "Height of the feet, in blocks.", dg.feetHeight, 0, 0.7, 0.01, () -> g.feetHeight, v -> g.feetHeight = v))
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("Accurate Hitboxes"))
                        .description(OptionDescription.of(Component.literal("Only if Accurate Hitboxes is installed.")))
                        .collapsed(true)
                        .option(bool("Use bone data", "Read the part from the bone that was hit.", dobb.useObbClassification, () -> o.useObbClassification, v -> o.useObbClassification = v))
                        .option(dbl("Head height tolerance", "How close to the top bone still counts as head.", dobb.obbHeadYTolerance, 0, 2, 0.01, () -> o.obbHeadYTolerance, v -> o.obbHeadYTolerance = v))
                        .option(dbl("Leg height fraction", "Bottom part of the skeleton counts as legs.", dobb.obbLegHeightFraction, 0, 1, 0.01, () -> o.obbLegHeightFraction, v -> o.obbLegHeightFraction = v))
                        .option(dbl("Arm offset", "Distance from the head bone that counts as arm.", dobb.obbArmOffset, 0, 2, 0.01, () -> o.obbArmOffset, v -> o.obbArmOffset = v))
                        .build())
                .build();
    }

    private static ConfigCategory entities(LimbSettings d, LimbSettings s) {
        var e = s.entities;
        var de = d.entities;
        return ConfigCategory.createBuilder()
                .name(Component.literal("Entities"))
                .tooltip(Component.literal("Who gets limb damage"))
                .option(bool("Players", "Players get limb damage.", de.affectPlayers, () -> e.affectPlayers, v -> e.affectPlayers = v))
                .option(bool("Mobs", "Mobs get limb damage (see below).", de.affectMobs, () -> e.affectMobs, v -> e.affectMobs = v))
                .option(bool("Detect humanoids", "Mobs shaped like a player count (zombies, skeletons, villagers...).", de.autoDetectHumanoid, () -> e.autoDetectHumanoid, v -> e.autoDetectHumanoid = v))
                .group(stringList("Always humanoid", "Always get limb damage, e.g. minecraft:iron_golem.", () -> e.extraEntityIds, v -> e.extraEntityIds = v, "minecraft:"))
                .group(stringList("Never humanoid", "Never get limb damage. Beats everything else.", () -> e.blacklistEntityIds, v -> e.blacklistEntityIds = v, "minecraft:"))
                .build();
    }

    private static ConfigCategory dummy(LimbSettings d, LimbSettings s) {
        var m = s.dummy;
        var dm = d.dummy;
        return ConfigCategory.createBuilder()
                .name(Component.literal("Training dummy"))
                .tooltip(Component.literal("Defaults for new dummies. Right-click one to change its own."))
                .option(dbl("Max health", "Health of a new dummy.", dm.health, 1, 1024, 1, () -> m.health, v -> m.health = v))
                .option(percent("Damage reduction", "Damage reduction of a new dummy.", dm.damageReductionPercent, () -> m.damageReductionPercent, v -> m.damageReductionPercent = v))
                .option(dbl("Auto-heal after", "Seconds without a hit before it heals. 0 = never.", dm.autoHealSeconds, 0, 60, 0.5, () -> m.autoHealSeconds, v -> m.autoHealSeconds = v))
                .option(bool("Cannot die", "Heals back instead of dying.", dm.cannotDie, () -> m.cannotDie, v -> m.cannotDie = v))
                .option(bool("Die and respawn", "Dies, then comes back with its gear.", dm.dieAndRespawn, () -> m.dieAndRespawn, v -> m.dieAndRespawn = v))
                .option(dbl("Respawn delay", "Seconds until it comes back.", dm.respawnDelaySeconds, 0.5, 60, 0.5, () -> m.respawnDelaySeconds, v -> m.respawnDelaySeconds = v))
                .option(bool("Hit info in action bar", "Show the limb, multiplier and damage above the hotbar.", dm.showHitInfo, () -> m.showHitInfo, v -> m.showHitInfo = v))
                .option(bool("Hit report", "List every hit once you stop shooting.", dm.reportEnabled, () -> m.reportEnabled, v -> m.reportEnabled = v))
                .option(dbl("Report delay", "Seconds after the last hit.", dm.reportDelaySeconds, 0.5, 60, 0.5, () -> m.reportDelaySeconds, v -> m.reportDelaySeconds = v))
                .option(integer("Report max hits", "Most hits in one report.", dm.reportMaxHits, 1, 512, () -> m.reportMaxHits, v -> m.reportMaxHits = v))
                .option(bool("Anyone can equip", "Off = only the owner and ops can change gear.", dm.publicEquipment, () -> m.publicEquipment, v -> m.publicEquipment = v))
                .build();
    }

    private static ConfigCategory feedback(LimbSettings d, LimbSettings s) {
        var r = s.recap;
        var dr = d.recap;
        var t = s.dealtToast;
        var dt = d.dealtToast;
        var f = s.fragile;
        var df = d.fragile;
        var g = s.debug;
        var dg = d.debug;
        return ConfigCategory.createBuilder()
                .name(Component.literal("Recap & effects"))
                .tooltip(Component.literal("Recap, damage done, Fragile, debug"))
                .group(OptionGroup.createBuilder().name(Component.literal("Death recap"))
                        .description(OptionDescription.of(Component.literal("Shown when you die.")))
                        .option(bool("Enable death recap", "Send the recap on death.", dr.enableDeathRecap, () -> r.enableDeathRecap, v -> r.enableDeathRecap = v))
                        .option(dbl("Fight timeout", "Fight ends after this long without damage (seconds).", dr.fightTimeoutSeconds, 1, 600, 1, () -> r.fightTimeoutSeconds, v -> r.fightTimeoutSeconds = v))
                        .option(bool("Cap to remaining health", "Don't count damage past the health left.", dr.capToRemainingHealth, () -> r.capToRemainingHealth, v -> r.capToRemainingHealth = v))
                        .build())
                .group(OptionGroup.createBuilder().name(Component.literal("Damage done"))
                        .description(OptionDescription.of(Component.literal("Second strip: who you had hit before you died.")))
                        .option(bool("Enable", "Track damage you do to players.", dt.enabled, () -> t.enabled, v -> t.enabled = v))
                        .option(dbl("Track time", "How long a player stays listed after your last hit (seconds).", dt.trackSeconds, 5, 300, 1, () -> t.trackSeconds, v -> t.trackSeconds = v))
                        .option(integer("Players listed", "Most players listed.", dt.maxPlayers, 1, 4, () -> t.maxPlayers, v -> t.maxPlayers = v))
                        .build())
                .group(OptionGroup.createBuilder().name(Component.literal("Fragile effect"))
                        .description(OptionDescription.of(Component.literal("Limbs take full damage, regen waits.")))
                        .collapsed(true)
                        .option(bool("Pause Regeneration effect", "Pause vanilla Regeneration while Fragile.", df.pauseRegenerationEffect, () -> f.pauseRegenerationEffect, v -> f.pauseRegenerationEffect = v))
                        .option(bool("Block natural regeneration", "Block natural regen while Fragile.", df.blockNaturalRegen, () -> f.blockNaturalRegen, v -> f.blockNaturalRegen = v))
                        .option(bool("Sync 'Regeneration tweak' mod", "Push that mod's timer back while Fragile.", df.syncRegenTweakMod, () -> f.syncRegenTweakMod, v -> f.syncRegenTweakMod = v))
                        .option(dbl("Extra delay after Fragile", "Seconds before regen starts again.", df.extraDelaySeconds, 0, 600, 0.5, () -> f.extraDelaySeconds, v -> f.extraDelaySeconds = v))
                        .build())
                .group(OptionGroup.createBuilder().name(Component.literal("Debug"))
                        .description(OptionDescription.of(Component.literal("For testing zones.")))
                        .collapsed(true)
                        .option(bool("Log every hit", "Spammy. Logs part, multiplier and damage.", dg.logHits, () -> g.logHits, v -> g.logHits = v))
                        .option(bool("Announce killing blows", "Chat line with part, attacker, weapon and damage on a kill.", dg.announceKills, () -> g.announceKills, v -> g.announceKills = v))
                        .build())
                .build();
    }

    private static ConfigCategory hud(ClientSettings cd, ClientSettings cs) {
        var l = cs.layout;
        var dl = cd.layout;
        var n = cs.content;
        var dn = cd.content;
        var a = cs.animation;
        var da = cd.animation;
        var r = cs.rendering;
        var dr = cd.rendering;
        return ConfigCategory.createBuilder()
                .name(Component.literal("HUD"))
                .tooltip(Component.literal("How the damage panels look"))
                .option(bool("Show damage feedback", "Recap, damage done and dummy report.", cd.general.enabled, () -> cs.general.enabled, v -> cs.general.enabled = v))
                .group(OptionGroup.createBuilder().name(Component.literal("Layout"))
                        .description(OptionDescription.of(Component.literal("Position and size.")))
                        .option(Option.<RecapClientConfig.Anchor>createBuilder()
                                .name(Component.literal("Corner"))
                                .description(OptionDescription.of(Component.literal("Panels slide in from here.")))
                                .binding(dl.anchor, () -> l.anchor, v -> l.anchor = v)
                                .controller(o -> EnumControllerBuilder.create(o).enumClass(RecapClientConfig.Anchor.class)
                                        .formatValue(v -> Component.literal(prettify(v.name()))))
                                .build())
                        .option(integer("Margin X", "Distance from the side edge.", dl.marginX, 0, 200, () -> l.marginX, v -> l.marginX = v))
                        .option(integer("Margin Y", "Distance from the top/bottom edge.", dl.marginY, 0, 200, () -> l.marginY, v -> l.marginY = v))
                        .option(dbl("Size", "Overall size. Rounded to whole pixels when pixel snapping is on.", dl.scale, 0.5, 4, 0.05, () -> l.scale, v -> l.scale = v))
                        .option(dbl("Opacity", "Overall opacity.", dl.uiOpacity, 0.1, 1, 0.05, () -> l.uiOpacity, v -> l.uiOpacity = v))
                        .option(dbl("Strip opacity", "Opacity of the dark strips.", dl.backgroundOpacity, 0, 1, 0.05, () -> l.backgroundOpacity, v -> l.backgroundOpacity = v))
                        .option(integer("Attacker rows", "Most rows in the recap.", dl.maxRows, 1, 6, () -> l.maxRows, v -> l.maxRows = v))
                        .option(integer("Report rows", "Most rows in a dummy report.", dl.reportMaxRows, 1, 16, () -> l.reportMaxRows, v -> l.reportMaxRows = v))
                        .option(integer("Name width", "Widest the name column gets.", dl.nameMaxWidth, 40, 200, () -> l.nameMaxWidth, v -> l.nameMaxWidth = v))
                        .option(bool("Scroll long names", "Off = cut off with ...", dl.nameMarquee, () -> l.nameMarquee, v -> l.nameMarquee = v))
                        .build())
                .group(OptionGroup.createBuilder().name(Component.literal("Content"))
                        .description(OptionDescription.of(Component.literal("What is shown.")))
                        .option(bool("Header", "Header strip.", dn.showHeader, () -> n.showHeader, v -> n.showHeader = v))
                        .option(bool("Weapon icon", "Weapon of each attacker.", dn.showWeaponIcon, () -> n.showWeaponIcon, v -> n.showWeaponIcon = v))
                        .option(bool("Hits column", "Number of hits.", dn.showHitsColumn, () -> n.showHitsColumn, v -> n.showHitsColumn = v))
                        .option(bool("Body figure", "Body with the hit limbs.", dn.showBodyIcon, () -> n.showBodyIcon, v -> n.showBodyIcon = v))
                        .option(bool("Damage done strip", "Strip with the players you had hit.", dn.showDealtToast, () -> n.showDealtToast, v -> n.showDealtToast = v))
                        .option(bool("Timer bar", "Thin bar with the time left.", dn.showProgressBar, () -> n.showProgressBar, v -> n.showProgressBar = v))
                        .option(bool("Mirror figure", "Flip the body left/right.", dn.mirrorBody, () -> n.mirrorBody, v -> n.mirrorBody = v))
                        .option(integer("Damage decimals", "Decimals in damage.", dn.damageDecimals, 0, 2, () -> n.damageDecimals, v -> n.damageDecimals = v))
                        .build())
                .group(OptionGroup.createBuilder().name(Component.literal("Animation"))
                        .description(OptionDescription.of(Component.literal("Timings.")))
                        .collapsed(true)
                        .option(dbl("Hold (recap)", "How long the recap stays (seconds).", da.holdSeconds, 0.5, 30, 0.5, () -> a.holdSeconds, v -> a.holdSeconds = v))
                        .option(dbl("Hold (report)", "How long a dummy report stays (seconds).", da.reportHoldSeconds, 0.5, 30, 0.5, () -> a.reportHoldSeconds, v -> a.reportHoldSeconds = v))
                        .option(integer("Slide in (ms)", "0 = instant.", da.slideInMs, 0, 2000, () -> a.slideInMs, v -> a.slideInMs = v))
                        .option(integer("Slide out (ms)", "0 = instant.", da.slideOutMs, 0, 2000, () -> a.slideOutMs, v -> a.slideOutMs = v))
                        .option(integer("Row delay (ms)", "Delay between rows.", da.rowStaggerMs, 0, 500, () -> a.rowStaggerMs, v -> a.rowStaggerMs = v))
                        .option(integer("Count-up (ms)", "Numbers count up from 0. 0 = off.", da.countUpMs, 0, 2000, () -> a.countUpMs, v -> a.countUpMs = v))
                        .option(integer("Body fade (ms)", "Hit limbs fade from grey to colour.", da.bodyFlashMs, 0, 2000, () -> a.bodyFlashMs, v -> a.bodyFlashMs = v))
                        .build())
                .group(OptionGroup.createBuilder().name(Component.literal("Rendering"))
                        .description(OptionDescription.of(Component.literal("Sharpness.")))
                        .collapsed(true)
                        .option(bool("Pixel snapping", "Keep everything on whole pixels so it stays sharp.", dr.pixelSnap, () -> r.pixelSnap, v -> r.pixelSnap = v))
                        .option(bool("Smooth weapon icons", "Smooth TACZ weapon icons when shrunk. Also softens that texture in TACZ.", dr.smoothWeaponIcons, () -> r.smoothWeaponIcons, v -> r.smoothWeaponIcons = v))
                        .option(Option.<String>createBuilder()
                                .name(Component.literal("Font"))
                                .description(OptionDescription.of(Component.literal("Font id, e.g. a resource pack font like mypack:hud. Empty = default.")))
                                .binding(dr.font, () -> r.font, v -> r.font = v)
                                .controller(StringControllerBuilder::create)
                                .build())
                        .build())
                .build();
    }

    private static ConfigCategory look(ClientSettings cd, ClientSettings cs) {
        var c = cs.colors;
        var dc = cd.colors;
        var b = cs.bodyLevels;
        var db = cd.bodyLevels;
        return ConfigCategory.createBuilder()
                .name(Component.literal("HUD colours"))
                .tooltip(Component.literal("Colours"))
                .option(color("Accent", "Markers, header bar and timer.", dc.accent, () -> c.accent, v -> c.accent = v))
                .option(color("Damage done accent", "Same, for the damage done strip.", dc.dealt, () -> c.dealt, v -> c.dealt = v))
                .option(color("Self-inflicted", "Your name when you hurt yourself.", dc.self, () -> c.self, v -> c.self = v))
                .option(color("Untouched limb", "Limbs that were not hit.", dc.untouched, () -> c.untouched, v -> c.untouched = v))
                .option(bool("Colour limbs by damage", "Off = every hit limb gets the one colour below.", db.colorByLevel, () -> b.colorByLevel, v -> b.colorByLevel = v))
                .option(color("Hit limb (single colour)", "Only when colouring by damage is off.", dc.hit, () -> c.hit, v -> c.hit = v))
                .option(color("Low damage", "Below the medium level.", dc.lowColor, () -> c.lowColor, v -> c.lowColor = v))
                .option(color("Medium damage", "", dc.mediumColor, () -> c.mediumColor, v -> c.mediumColor = v))
                .option(color("High damage", "", dc.highColor, () -> c.highColor, v -> c.highColor = v))
                .option(color("Critical damage", "", dc.criticalColor, () -> c.criticalColor, v -> c.criticalColor = v))
                .option(dbl("Medium at", "Limb damage / max health where low becomes medium.", db.mediumAt, 0, 2, 0.01, () -> b.mediumAt, v -> b.mediumAt = v))
                .option(dbl("High at", "... medium becomes high.", db.highAt, 0, 2, 0.01, () -> b.highAt, v -> b.highAt = v))
                .option(dbl("Critical at", "... high becomes critical.", db.criticalAt, 0, 2, 0.01, () -> b.criticalAt, v -> b.criticalAt = v))
                .build();
    }

    // ------------------------------------------------------------------ option helpers

    private static Option<Boolean> bool(String name, String desc, boolean def, Supplier<Boolean> get, Consumer<Boolean> set) {
        return Option.<Boolean>createBuilder()
                .name(Component.literal(name))
                .description(OptionDescription.of(Component.literal(desc)))
                .binding(def, get, set)
                .controller(TickBoxControllerBuilder::create)
                .build();
    }

    private static Option<Double> dbl(String name, String desc, double def, double min, double max, double step,
                                      Supplier<Double> get, Consumer<Double> set) {
        return Option.<Double>createBuilder()
                .name(Component.literal(name))
                .description(OptionDescription.of(Component.literal(desc)))
                .binding(def, get, set)
                .controller(o -> DoubleSliderControllerBuilder.create(o).range(min, max).step(step)
                        .formatValue(v -> Component.literal(trim(v))))
                .build();
    }

    private static Option<Integer> integer(String name, String desc, int def, int min, int max,
                                           Supplier<Integer> get, Consumer<Integer> set) {
        return Option.<Integer>createBuilder()
                .name(Component.literal(name))
                .description(OptionDescription.of(Component.literal(desc)))
                .binding(def, get, set)
                .controller(o -> IntegerSliderControllerBuilder.create(o).range(min, max).step(1))
                .build();
    }

    /** A percentage slider, 0.1 steps, 0 - 100 (the dummy's damage reduction). */
    private static Option<Double> percent(String name, String desc, double def, Supplier<Double> get, Consumer<Double> set) {
        return Option.<Double>createBuilder()
                .name(Component.literal(name))
                .description(OptionDescription.of(Component.literal(desc)))
                .binding(def, get, v -> set.accept(Math.round(v * 10.0) / 10.0))
                .controller(o -> DoubleSliderControllerBuilder.create(o).range(0.0, 100.0).step(0.1)
                        .formatValue(v -> Component.literal(String.format(Locale.ROOT, "%.1f%%", v))))
                .build();
    }

    /** Damage reduction slider over a stored multiplier: 55.5 % reduction <-> x0.445. Negative = extra damage. */
    private static Option<Double> reduction(String name, String desc, double defMultiplier,
                                            Supplier<Double> getMultiplier, Consumer<Double> setMultiplier) {
        return Option.<Double>createBuilder()
                .name(Component.literal(name))
                .description(OptionDescription.of(Component.literal(desc)))
                .binding(Multipliers.toReductionPercent(defMultiplier),
                        () -> Multipliers.toReductionPercent(getMultiplier.get()),
                        v -> setMultiplier.accept(Multipliers.fromReductionPercent(v)))
                .controller(o -> DoubleSliderControllerBuilder.create(o).range(-300.0, 100.0).step(0.1)
                        .formatValue(ConfigScreens::reductionText))
                .build();
    }

    private static Option<Double> categorySlider(String category, BodyPart part) {
        double defaultSlider = INHERIT;
        return Option.<Double>createBuilder()
                .name(Component.literal(prettify(part.name())))
                .description(OptionDescription.of(Component.literal("Damage reduction of " + part.name().toLowerCase(Locale.ROOT)
                        + " hits with a " + category + ". Far left = use the Damage tab.")))
                .binding(defaultSlider,
                        () -> {
                            double raw = CategoryConfig.raw(category, part);
                            return raw < 0.0 ? INHERIT : Multipliers.toReductionPercent(raw);
                        },
                        v -> CategoryConfig.set(category, part, v < -300.0 ? -1.0 : Multipliers.fromReductionPercent(v)))
                .controller(o -> DoubleSliderControllerBuilder.create(o).range(INHERIT, 100.0).step(0.1)
                        .formatValue(v -> v < -300.0 ? Component.literal("use Damage tab") : reductionText(v)))
                .build();
    }

    private static Component reductionText(double percent) {
        if (Math.abs(percent) < 0.05) {
            return Component.literal("normal");
        }
        return Component.literal(String.format(Locale.ROOT, "%s%.1f%% damage", percent > 0 ? "-" : "+", Math.abs(percent)));
    }

    private static Option<Color> color(String name, String desc, String def, Supplier<String> get, Consumer<String> set) {
        return Option.<Color>createBuilder()
                .name(Component.literal(name))
                .description(OptionDescription.of(Component.literal(desc.isEmpty() ? name : desc)))
                .binding(parse(def), () -> parse(get.get()), c -> set.accept(hex(c)))
                .controller(o -> ColorControllerBuilder.create(o).allowAlpha(false))
                .build();
    }

    private static ListOption<String> stringList(String name, String desc, Supplier<List<String>> get,
                                                 Consumer<List<String>> set, String initial) {
        return ListOption.<String>createBuilder()
                .name(Component.literal(name))
                .description(OptionDescription.of(Component.literal(desc)))
                .binding(new ArrayList<String>(), () -> new ArrayList<String>(get.get()), v -> set.accept(new ArrayList<String>(v)))
                .controller(StringControllerBuilder::create)
                .initial(initial)
                .build();
    }

    private static String trim(double v) {
        String s = String.format(Locale.ROOT, "%.2f", v);
        s = s.replaceAll("0+$", "");
        return s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
    }

    private static String prettify(String enumName) {
        String[] words = enumName.toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    private static Color parse(String text) {
        try {
            String t = text.trim();
            if (t.startsWith("#")) t = t.substring(1);
            return new Color((int) (Long.parseLong(t, 16) & 0xFFFFFF));
        } catch (Exception e) {
            return Color.WHITE;
        }
    }

    private static String hex(Color c) {
        return String.format(Locale.ROOT, "#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }
}
