package dev.szo9k4.limbdamage.config;

import dev.isxander.yacl3.config.v2.api.SerialEntry;
import dev.szo9k4.limbdamage.recap.client.RecapClientConfig;

/** The HUD settings file (config/limbdamage-client.json5), as plain data. See {@link RecapClientConfig} for the facade. */
public final class ClientSettings {

    public static final class General {
        @SerialEntry(comment = "Show the damage feedback (death recap, damage done, dummy report).")
        public boolean enabled = true;
    }

    public static final class Layout {
        @SerialEntry(comment = "Screen corner the panels attach to: TOP_RIGHT, TOP_LEFT, BOTTOM_RIGHT, BOTTOM_LEFT.")
        public RecapClientConfig.Anchor anchor = RecapClientConfig.Anchor.TOP_RIGHT;
        @SerialEntry(comment = "Distance from the left/right screen edge in GUI pixels.")
        public int marginX = 8;
        @SerialEntry(comment = "Distance from the top/bottom screen edge in GUI pixels.")
        public int marginY = 8;
        @SerialEntry(comment = "Size of the whole HUD. With pixelSnap on, the real size is rounded to whole screen pixels so edges and text stay razor sharp.")
        public double scale = 1.0;
        @SerialEntry(comment = "Opacity of everything (0.1 - 1).")
        public double uiOpacity = 1.0;
        @SerialEntry(comment = "Extra opacity of the dark strips behind the text (0 = none).")
        public double backgroundOpacity = 0.62;
        @SerialEntry(comment = "Most attacker rows in the death recap.")
        public int maxRows = 4;
        @SerialEntry(comment = "Most hit rows in a dummy report (the rest collapse into '+N more').")
        public int reportMaxRows = 8;
        @SerialEntry(comment = "Widest the name column may grow, in design pixels.")
        public int nameMaxWidth = 72;
        @SerialEntry(comment = "true: a long name slowly scrolls. false: it is cut with an ellipsis.")
        public boolean nameMarquee = true;
    }

    public static final class Content {
        @SerialEntry public boolean showHeader = true;
        @SerialEntry public boolean showWeaponIcon = true;
        @SerialEntry public boolean showHitsColumn = true;
        @SerialEntry public boolean showBodyIcon = true;
        @SerialEntry(comment = "Show the second strip with the players you had just damaged.")
        public boolean showDealtToast = true;
        @SerialEntry public boolean showProgressBar = true;
        @SerialEntry(comment = "false: the left of the figure is YOUR left. true: mirrored.")
        public boolean mirrorBody = false;
        @SerialEntry(comment = "Decimals in damage numbers (trailing zeros are dropped).")
        public int damageDecimals = 1;
    }

    public static final class BodyLevels {
        @SerialEntry(comment = "true: limbs are coloured by how much damage they took. false: one 'hit' colour.")
        public boolean colorByLevel = true;
        @SerialEntry(comment = "Limb damage / max health where LOW turns MEDIUM.")
        public double mediumAt = 0.20;
        @SerialEntry(comment = "... MEDIUM turns HIGH.")
        public double highAt = 0.40;
        @SerialEntry(comment = "... HIGH turns CRITICAL.")
        public double criticalAt = 0.70;
    }

    public static final class Animation {
        @SerialEntry public double holdSeconds = 4.0;
        @SerialEntry public double reportHoldSeconds = 6.0;
        @SerialEntry public int slideInMs = 350;
        @SerialEntry public int slideOutMs = 400;
        @SerialEntry public int rowStaggerMs = 70;
        @SerialEntry(comment = "Numbers count up from 0 over this time. 0 = off.")
        public int countUpMs = 500;
        @SerialEntry(comment = "Hit limbs fade from grey to their colour over this time.")
        public int bodyFlashMs = 350;
    }

    public static final class Rendering {
        @SerialEntry(comment = "Snap the HUD to whole screen pixels (recommended). Text is always drawn at whole-pixel multiples of the font.")
        public boolean pixelSnap = true;
        @SerialEntry(comment = "Font id used for the HUD text, e.g. \"minecraft:default\" or a TTF font from a resource pack like \"mypack:hud\". Empty = default.")
        public String font = "";
        @SerialEntry(comment = "Filter TACZ weapon icons smoothly when they are drawn smaller than their texture. Slightly softens that texture elsewhere too.")
        public boolean smoothWeaponIcons = true;
    }

    public static final class Colors {
        @SerialEntry(comment = "Accent: markers, header bar, timer.") public String accent = "#FF3B30";
        @SerialEntry(comment = "Hit limb colour when colorByLevel = false.") public String hit = "#FF3B30";
        @SerialEntry(comment = "Limbs that were not hit.") public String untouched = "#7C828A";
        @SerialEntry(comment = "Name colour for self-inflicted damage.") public String self = "#FFC857";
        @SerialEntry(comment = "Accent of the 'damage done' strip.") public String dealt = "#FF9F1C";
        @SerialEntry public String lowColor = "#FFFFFF";
        @SerialEntry public String mediumColor = "#FFD93D";
        @SerialEntry public String highColor = "#FF9F1C";
        @SerialEntry public String criticalColor = "#FF3B30";
    }

    @SerialEntry public General general = new General();
    @SerialEntry public Layout layout = new Layout();
    @SerialEntry public Content content = new Content();
    @SerialEntry public BodyLevels bodyLevels = new BodyLevels();
    @SerialEntry public Animation animation = new Animation();
    @SerialEntry public Rendering rendering = new Rendering();
    @SerialEntry public Colors colors = new Colors();

    public void sanitize() {
        if (general == null) general = new General();
        if (layout == null) layout = new Layout();
        if (content == null) content = new Content();
        if (bodyLevels == null) bodyLevels = new BodyLevels();
        if (animation == null) animation = new Animation();
        if (rendering == null) rendering = new Rendering();
        if (colors == null) colors = new Colors();
        if (layout.anchor == null) layout.anchor = RecapClientConfig.Anchor.TOP_RIGHT;
        if (rendering.font == null) rendering.font = "";

        layout.marginX = Math.max(0, Math.min(400, layout.marginX));
        layout.marginY = Math.max(0, Math.min(400, layout.marginY));
        layout.scale = LimbSettings.clamp(layout.scale, 0.5, 4.0);
        layout.uiOpacity = LimbSettings.clamp(layout.uiOpacity, 0.1, 1.0);
        layout.backgroundOpacity = LimbSettings.clamp(layout.backgroundOpacity, 0.0, 1.0);
        layout.maxRows = Math.max(1, Math.min(6, layout.maxRows));
        layout.reportMaxRows = Math.max(1, Math.min(16, layout.reportMaxRows));
        layout.nameMaxWidth = Math.max(40, Math.min(200, layout.nameMaxWidth));
        content.damageDecimals = Math.max(0, Math.min(2, content.damageDecimals));
        bodyLevels.mediumAt = LimbSettings.clamp(bodyLevels.mediumAt, 0.0, 10.0);
        bodyLevels.highAt = LimbSettings.clamp(bodyLevels.highAt, 0.0, 10.0);
        bodyLevels.criticalAt = LimbSettings.clamp(bodyLevels.criticalAt, 0.0, 10.0);
        animation.holdSeconds = LimbSettings.clamp(animation.holdSeconds, 0.5, 30.0);
        animation.reportHoldSeconds = LimbSettings.clamp(animation.reportHoldSeconds, 0.5, 30.0);
        animation.slideInMs = Math.max(0, Math.min(3000, animation.slideInMs));
        animation.slideOutMs = Math.max(0, Math.min(3000, animation.slideOutMs));
        animation.rowStaggerMs = Math.max(0, Math.min(1000, animation.rowStaggerMs));
        animation.countUpMs = Math.max(0, Math.min(3000, animation.countUpMs));
        animation.bodyFlashMs = Math.max(0, Math.min(3000, animation.bodyFlashMs));
    }
}
