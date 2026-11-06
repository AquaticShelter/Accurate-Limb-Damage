package dev.szo9k4.limbdamage.recap.client;

import dev.szo9k4.limbdamage.Limb;
import dev.szo9k4.limbdamage.recap.RecapPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static dev.szo9k4.limbdamage.recap.client.UiDraw.argb;

/**
 * The damage HUD, drawn as strips: a header strip, one strip per attacker (name, weapon, DMG // HITS) and a
 * card with the body figure, like the reference layout:
 *
 * <pre>
 *   :: ATTACKERS: 2            DMG // HITS     | YOU
 *   <> Benni   [weapon]        331 |  2       +------+
 *   <> JD072   [grenade]        69 |  1       | body |
 * </pre>
 *
 * How it stays sharp:
 *  - The whole HUD is laid out in real screen pixels (the pose is scaled by 1 / guiScale). Sizes are whole
 *    multiples of a "design pixel"; with pixelSnap on that multiple is an integer, so every edge lands on a
 *    pixel boundary.
 *  - Panels, strips and the body figure are vector shapes batched into one draw call (UiDraw), with a feathered
 *    rim only on curved edges.
 *  - Text is always drawn at a whole-number multiple of the font, never a fractional scale.
 *  - Everything that depends on config, text widths or the screen is computed once per toast (and again only
 *    if the GUI scale changes); a frame just replays that layout.
 *
 * The death recap shows damage taken; the "damage done" strip shows what you did to others. KILL / DEAD are
 * badges next to the name: the hits column ALWAYS shows the number of hits.
 */
public final class RecapClient {

    private enum Kind { RECAP, DEALT, REPORT }

    // design units ("du"): one du = u real pixels
    private static final int RH = 14;        // row strip height
    private static final int GAP = 2;        // gap between strips
    private static final int HH = 12;        // header strip height
    private static final int PAD = 5;
    private static final int ICON_W = 32;
    private static final int ICON_H = 8;
    private static final int CARD_W = 30;
    private static final int CARD_H = 52;
    private static final int FIG_W = 18;
    private static final int FIG_H = 44;
    private static final int COL_GAP = 4;
    private static final int NAME_MIN = 28;
    private static final String SELF_TAG = " (YOU)";

    /** Every config value the HUD uses, captured when the toast is created. */
    private record HudStyle(boolean showHeader, boolean showWeapon, boolean showHits, boolean showBody,
                            boolean showProgress, boolean mirror, boolean colorByLevel, boolean marquee,
                            boolean snap, boolean smoothIcons, int decimals, int nameMax,
                            int accent, int hitColor, int base, int selfColor, int[] levelColors,
                            float levelMid, float levelHigh, float levelCritical,
                            float scale, RecapClientConfig.Anchor anchor, int marginX, int marginY,
                            float uiOpacity, float bgOpacity,
                            long inMs, long holdMs, long outMs, long stagger, long countMs, long flashMs,
                            Style font) {

        static HudStyle read(boolean report, boolean dealt) {
            Style font = Style.EMPTY;
            String id = RecapClientConfig.FONT.get();
            if (id != null && !id.isBlank()) {
                ResourceLocation rl = ResourceLocation.tryParse(id.trim());
                if (rl != null) {
                    font = Style.EMPTY.withFont(rl);
                }
            }
            return new HudStyle(
                    RecapClientConfig.SHOW_HEADER.get(), RecapClientConfig.SHOW_WEAPON.get(),
                    RecapClientConfig.SHOW_HITS.get(), RecapClientConfig.SHOW_BODY.get(),
                    RecapClientConfig.SHOW_PROGRESS_BAR.get(), RecapClientConfig.MIRROR_BODY.get(),
                    RecapClientConfig.COLOR_BY_LEVEL.get(), RecapClientConfig.NAME_MARQUEE.get(),
                    RecapClientConfig.PIXEL_SNAP.get(), RecapClientConfig.SMOOTH_WEAPON_ICONS.get(),
                    RecapClientConfig.DAMAGE_DECIMALS.get(), RecapClientConfig.NAME_MAX_WIDTH.get(),
                    dealt ? parseColor(RecapClientConfig.DEALT_ACCENT_COLOR.get(), 0xFF9F1C)
                            : parseColor(RecapClientConfig.ACCENT_COLOR.get(), 0xFF3B30),
                    parseColor(RecapClientConfig.HIT_COLOR.get(), 0xFF3B30),
                    parseColor(RecapClientConfig.BASE_COLOR.get(), 0x7C828A),
                    parseColor(RecapClientConfig.SELF_COLOR.get(), 0xFFC857),
                    new int[]{parseColor(RecapClientConfig.LEVEL_LOW_COLOR.get(), 0xFFFFFF),
                            parseColor(RecapClientConfig.LEVEL_MEDIUM_COLOR.get(), 0xFFD93D),
                            parseColor(RecapClientConfig.LEVEL_HIGH_COLOR.get(), 0xFF9F1C),
                            parseColor(RecapClientConfig.LEVEL_CRITICAL_COLOR.get(), 0xFF3B30)},
                    RecapClientConfig.LEVEL_MEDIUM.get().floatValue(),
                    RecapClientConfig.LEVEL_HIGH.get().floatValue(),
                    RecapClientConfig.LEVEL_CRITICAL.get().floatValue(),
                    RecapClientConfig.SCALE.get().floatValue(), RecapClientConfig.ANCHOR.get(),
                    RecapClientConfig.MARGIN_X.get(), RecapClientConfig.MARGIN_Y.get(),
                    RecapClientConfig.UI_OPACITY.get().floatValue(), RecapClientConfig.BACKGROUND_OPACITY.get().floatValue(),
                    RecapClientConfig.SLIDE_IN_MS.get(),
                    (long) ((report ? RecapClientConfig.REPORT_HOLD_SECONDS : RecapClientConfig.HOLD_SECONDS).get() * 1000.0),
                    RecapClientConfig.SLIDE_OUT_MS.get(), RecapClientConfig.ROW_STAGGER_MS.get(),
                    RecapClientConfig.COUNT_UP_MS.get(), RecapClientConfig.BODY_FLASH_MS.get(), font);
        }
    }

    /** One strip with its texts prepared. */
    private static final class RowView {
        final RecapPacket.RowData data;
        final ResourceLocation hud;
        final String rawName;       // without the (YOU) tag
        final String tag;           // "" or " (YOU)"
        final String badge;         // "KILL" / "DEAD" / ""
        final boolean badgeKill;
        final String dmgText;
        final String valueText;
        FormattedCharSequence name;  // laid-out name (+tag), cut if needed
        FormattedCharSequence badgeSeq;
        FormattedCharSequence valueSeq;
        int nameFontW;               // font px width of `name`
        int badgeFontW;
        int valueFontW;
        boolean scrolls;
        int availFontPx;
        int maxStart;

        RowView(RecapPacket.RowData data, ResourceLocation hud, String dmgText, String valueText, boolean report) {
            this.data = data;
            this.hud = hud;
            this.rawName = data.name();
            this.tag = data.self() ? SELF_TAG : "";
            String t = report ? "" : data.tag();
            this.badge = "KILL".equals(t) || "DEAD".equals(t) ? t : "";
            this.badgeKill = "KILL".equals(t);
            this.dmgText = dmgText;
            this.valueText = valueText;
        }
    }

    /** Pixel layout of a toast for one GUI scale. All values are real screen pixels, origin = toast top-left. */
    private static final class Layout {
        double guiScale;
        float u;
        int t;                       // font multiple
        int w, h, fullH;
        int rowH, gap, hh, pad;
        int markerX, nameX, nameW, iconX, iconW, iconH, dmgR, sepX, valR, leftW;
        int cardX, cardW, cardY, cardH, figX, figY, figW, figH;
        int rowsTop;
        RowView[] rows;
        FormattedCharSequence header, dmgLabel, valueLabel, slashes, bodyLabel;
        int headerFontW, dmgLabelFontW, valueLabelFontW, bodyLabelFontW, slashFontW;
        int dotsW;
        int dmgW, valW;
        int badgeBlock;
        int px(float du) {
            return Math.round(du * u);
        }
    }

    private static final class Toast {
        final RecapPacket packet;
        final Kind kind;
        final HudStyle st;
        final long startMs;
        final List<RecapPacket.RowData> shown;
        final List<ResourceLocation> huds;
        final int[] limbColors;
        final String header, valueLabel, bodyLabel;
        final int heightDu;
        final float stackDu;
        Layout layout;

        Toast(RecapPacket packet, Kind kind, HudStyle st, long startMs, List<RecapPacket.RowData> shown,
              List<ResourceLocation> huds, int[] limbColors, String header, String valueLabel, String bodyLabel,
              float stackDu) {
            this.packet = packet;
            this.kind = kind;
            this.st = st;
            this.startMs = startMs;
            this.shown = shown;
            this.huds = huds;
            this.limbColors = limbColors;
            this.header = header;
            this.valueLabel = valueLabel;
            this.bodyLabel = bodyLabel;
            this.heightDu = heightDu(shown.size(), st);
            this.stackDu = stackDu;
        }
    }

    private static volatile Toast current;
    /** The "damage done" strip, drawn next to the main one. */
    private static volatile Toast currentDealt;

    private RecapClient() {
    }

    // ------------------------------------------------------------------ creation (once per toast)

    public static void accept(RecapPacket packet) {
        if (!RecapClientConfig.ENABLED.get()) {
            current = null;
            currentDealt = null;
            return;
        }
        boolean report = packet.report;
        boolean dealt = packet.dealt;
        if (dealt && !RecapClientConfig.SHOW_DEALT.get()) {
            return;
        }
        if (!dealt && !report) {
            currentDealt = null; // a new death recap starts over; its "damage done" strip (if any) arrives right after it
        }
        HudStyle st = HudStyle.read(report, dealt);
        Kind kind = report ? Kind.REPORT : (dealt ? Kind.DEALT : Kind.RECAP);

        int cap = report ? RecapClientConfig.REPORT_MAX_ROWS.get() : (dealt ? 4 : RecapClientConfig.MAX_ROWS.get());
        List<RecapPacket.RowData> shown = new ArrayList<>();
        for (int i = 0; i < Math.min(cap, packet.rows.size()); i++) {
            shown.add(packet.rows.get(i));
        }
        if (report && packet.rows.size() > shown.size()) {
            float rest = 0f;
            for (int i = shown.size(); i < packet.rows.size(); i++) {
                rest += packet.rows.get(i).damage();
            }
            shown.add(new RecapPacket.RowData("+" + (packet.rows.size() - shown.size()) + " more", false, false,
                    ItemStack.EMPTY, rest, 0, "", false));
        }

        // TACZ weapon HUD textures, looked up once per distinct weapon
        Map<String, ResourceLocation> cache = new HashMap<>();
        List<ResourceLocation> huds = new ArrayList<>();
        for (RecapPacket.RowData r : shown) {
            ResourceLocation hud = null;
            ItemStack weapon = r.weapon();
            if (!weapon.isEmpty()) {
                String key = weapon.getItem() + "|" + weapon.getTag();
                hud = cache.computeIfAbsent(key, k -> TaczIcons.hudTexture(weapon));
                if (hud != null && st.smoothIcons()) {
                    try {
                        Minecraft.getInstance().getTextureManager().getTexture(hud).setFilter(true, false);
                    } catch (Throwable ignored) {
                        // icon stays as it was
                    }
                }
            }
            huds.add(hud);
        }

        int[] limbColors = new int[Limb.values().length];
        for (int i = 0; i < limbColors.length; i++) {
            limbColors[i] = partColor(packet.parts[i], packet.maxHealth, st);
        }

        String header = switch (kind) {
            case REPORT -> "DUMMY: " + packet.total + (packet.total == 1 ? " HIT" : " HITS");
            case DEALT -> "DAMAGE DONE";
            case RECAP -> "ATTACKERS: " + packet.total;
        };
        long now = System.currentTimeMillis();
        Toast main = current;
        float shift = dealt && main != null ? main.heightDu + 4f : 0f;
        Toast toast = new Toast(packet, kind, st, dealt ? now + 150L : now, shown, huds, limbColors, header,
                report ? "MULT" : "HITS", report ? "DUMMY" : (dealt ? "THEM" : "YOU"), shift);
        if (dealt) {
            currentDealt = toast;
        } else {
            current = toast;
        }
    }

    public static void clear() {
        current = null;
        currentDealt = null;
    }

    /** Total height of a toast in design units (strips + card + timer bar). */
    private static int heightDu(int rows, HudStyle st) {
        int hh = st.showHeader() ? HH : 0;
        int rowsTop = hh + (hh > 0 ? GAP : 0);
        int rowsH = rows * RH + Math.max(0, rows - 1) * GAP;
        int h = rowsTop + rowsH;
        if (st.showBody()) {
            h = Math.max(h, rowsTop + Math.max(CARD_H, rowsH));
        }
        return h + (st.showProgress() ? 3 : 0);
    }

    // ------------------------------------------------------------------ layout (once per toast and GUI scale)

    private static FormattedCharSequence seq(String s, Style style) {
        return FormattedCharSequence.forward(s, style);
    }

    private static FormattedCharSequence fit(Font font, String s, Style style, int maxFontPx) {
        FormattedText cut = font.substrByWidth(FormattedText.of(s, style), Math.max(1, maxFontPx));
        return Language.getInstance().getVisualOrder(cut);
    }

    private static Layout buildLayout(Toast tt, Font font, double gs) {
        HudStyle st = tt.st;
        Layout L = new Layout();
        L.guiScale = gs;
        float u = (float) (st.scale() * gs);
        L.u = st.snap() ? Math.max(1f, Math.round(u)) : Math.max(1f, u);
        L.t = Math.max(1, Math.round(L.u));
        Style fs = st.font();
        boolean report = tt.kind == Kind.REPORT;

        L.rowH = L.px(RH);
        L.gap = L.px(GAP);
        L.hh = st.showHeader() ? L.px(HH) : 0;
        L.pad = L.px(PAD);

        L.header = seq(tt.header, fs);
        L.headerFontW = font.width(L.header);
        L.dmgLabel = seq("DMG", fs);
        L.dmgLabelFontW = font.width(L.dmgLabel);
        L.valueLabel = seq(tt.valueLabel, fs);
        L.valueLabelFontW = font.width(L.valueLabel);
        L.slashes = seq("//", fs);
        L.slashFontW = font.width(L.slashes);
        L.bodyLabel = seq(tt.bodyLabel, fs);
        L.bodyLabelFontW = font.width(L.bodyLabel);

        // rows and column widths
        int n = tt.shown.size();
        L.rows = new RowView[n];
        int nameBlock = L.px(NAME_MIN);
        int dmgW = Math.max(L.dmgLabelFontW * L.t, L.px(14));
        int valW = Math.max(L.valueLabelFontW * L.t, L.px(10));
        int badgeExtra = 0;
        for (int i = 0; i < n; i++) {
            RecapPacket.RowData r = tt.shown.get(i);
            String valueText = report ? r.tag() : (r.hits() > 0 ? Integer.toString(r.hits()) : "");
            RowView rv = new RowView(r, tt.huds.get(i), fmtDamage(r.damage(), st.decimals()), valueText, report);
            rv.valueSeq = seq(valueText, fs);
            rv.valueFontW = font.width(rv.valueSeq);
            if (!rv.badge.isEmpty()) {
                rv.badgeSeq = seq(rv.badge, fs);
                rv.badgeFontW = font.width(rv.badgeSeq);
            }
            L.rows[i] = rv;
            int nameW = font.width(seq(rv.rawName + rv.tag, fs)) * L.t;
            int badgeW = rv.badgeSeq != null ? rv.badgeFontW * L.t + L.px(6) + L.px(3) : 0;
            badgeExtra = Math.max(badgeExtra, badgeW);
            nameBlock = Math.max(nameBlock, nameW + badgeW);
            dmgW = Math.max(dmgW, font.width(seq(rv.dmgText, fs)) * L.t);
            valW = Math.max(valW, rv.valueFontW * L.t);
        }
        L.badgeBlock = badgeExtra;
        L.nameW = Math.min(nameBlock, L.px(st.nameMax()));
        L.dmgW = dmgW;
        L.valW = valW;

        // name text of each row, fitted into the space left of the badge
        for (RowView rv : L.rows) {
            int badgeW = rv.badgeSeq != null ? rv.badgeFontW * L.t + L.px(6) + L.px(3) : 0;
            int availPx = L.nameW - badgeW;
            int availFont = Math.max(8, availPx / L.t - font.width(seq(rv.tag, fs)));
            rv.availFontPx = availFont;
            if (font.width(seq(rv.rawName, fs)) <= availFont) {
                rv.name = seq(rv.rawName + rv.tag, fs);
            } else if (st.marquee()) {
                int maxStart = rv.rawName.length() - 1;
                for (int k = 0; k < rv.rawName.length(); k++) {
                    if (font.width(seq(rv.rawName.substring(k), fs)) <= availFont) {
                        maxStart = k;
                        break;
                    }
                }
                rv.scrolls = true;
                rv.maxStart = maxStart;
                rv.name = joinTag(fit(font, rv.rawName, fs, availFont), rv.tag, fs);
            } else {
                int dots = font.width(seq("...", fs));
                FormattedCharSequence cut = fit(font, rv.rawName, fs, Math.max(1, availFont - dots));
                rv.name = joinTag(cut, "..." + rv.tag, fs);
            }
            rv.nameFontW = font.width(rv.name);
        }

        // columns, left to right
        int x = L.pad;
        L.markerX = x;
        x += L.px(5) + L.px(4);
        L.nameX = x;
        x += L.nameW;
        if (st.showWeapon()) {
            x += L.px(6);
            L.iconX = x;
            L.iconW = L.px(ICON_W);
            L.iconH = L.px(ICON_H);
            x += L.iconW;
        }
        x += L.px(6);
        L.dmgR = x + L.dmgW;
        x = L.dmgR;
        if (st.showHits()) {
            x += L.px(5);
            L.sepX = x;
            x += L.px(5);
            L.valR = x + L.valW;
            x = L.valR;
        }
        L.leftW = x + L.pad;

        // the header must fit next to the DMG // HITS labels: widen the name column if it doesn't
        if (st.showHeader()) {
            L.dotsW = L.px(5);
            int headerRight = L.pad + L.dotsW + L.px(4) + L.headerFontW * L.t;
            int labelsLeft = L.dmgR - L.dmgLabelFontW * L.t;
            int need = headerRight + L.px(8) - labelsLeft;
            if (need > 0) {
                L.nameW += need;
                if (st.showWeapon()) L.iconX += need;
                L.dmgR += need;
                if (st.showHits()) {
                    L.sepX += need;
                    L.valR += need;
                }
                L.leftW += need;
            }
        }

        // vertical layout, from the same design-unit arithmetic as heightDu()
        L.rowsTop = L.px(st.showHeader() ? HH + GAP : 0);
        int rowsH = L.px(n * RH + Math.max(0, n - 1) * GAP);
        L.h = L.rowsTop + rowsH;
        if (st.showBody()) {
            L.cardX = L.leftW + L.px(COL_GAP);
            L.cardW = L.px(CARD_W);
            L.cardY = L.rowsTop;
            L.cardH = Math.max(L.px(CARD_H), rowsH);
            L.figW = L.px(FIG_W);
            L.figH = L.px(FIG_H);
            L.figX = L.cardX + (L.cardW - L.figW) / 2;
            L.figY = L.cardY + (L.cardH - L.figH) / 2;
            L.w = L.cardX + L.cardW;
            L.h = Math.max(L.h, L.cardY + L.cardH);
        } else {
            L.w = L.leftW;
        }
        L.fullH = L.h + (st.showProgress() ? L.px(3) : 0);
        return L;
    }

    private static FormattedCharSequence joinTag(FormattedCharSequence base, String tail, Style style) {
        if (tail.isEmpty()) {
            return base;
        }
        FormattedCharSequence suffix = FormattedCharSequence.forward(tail, style);
        return FormattedCharSequence.composite(base, suffix);
    }

    // ------------------------------------------------------------------ drawing (every frame)

    public static void render(GuiGraphics g, int sw, int sh) {
        Toast a = current;
        Toast b = currentDealt;
        if (a == null && b == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        double gs = mc.getWindow().getGuiScale();
        int fbW = mc.getWindow().getWidth();
        int fbH = mc.getWindow().getHeight();
        g.pose().pushPose();
        g.pose().scale((float) (1.0 / gs), (float) (1.0 / gs), 1f); // 1 unit = 1 real pixel from here on
        renderToast(g, mc.font, a, fbW, fbH, gs);
        renderToast(g, mc.font, b, fbW, fbH, gs);
        g.pose().popPose();
    }

    private static void renderToast(GuiGraphics g, Font font, Toast tt, int fbW, int fbH, double gs) {
        if (tt == null) {
            return;
        }
        HudStyle s = tt.st;
        long el = System.currentTimeMillis() - tt.startMs;
        if (el < 0) {
            return; // not started yet (the "damage done" strip waits a moment behind the recap)
        }
        if (el >= s.inMs() + s.holdMs() + s.outMs()) {
            if (current == tt) current = null;
            if (currentDealt == tt) currentDealt = null;
            return;
        }
        float slide;
        if (el < s.inMs()) {
            slide = easeOutCubic(el / (float) s.inMs());
        } else if (el < s.inMs() + s.holdMs()) {
            slide = 1f;
        } else {
            slide = 1f - easeInCubic((el - s.inMs() - s.holdMs()) / (float) s.outMs());
        }
        float alpha = Math.min(1f, slide * 2f) * s.uiOpacity();
        if (alpha < 0.03f) {
            return;
        }
        Layout L = tt.layout;
        if (L == null || L.guiScale != gs) {
            L = buildLayout(tt, font, gs);
            tt.layout = L;
        }

        boolean right = s.anchor() == RecapClientConfig.Anchor.TOP_RIGHT || s.anchor() == RecapClientConfig.Anchor.BOTTOM_RIGHT;
        boolean bottom = s.anchor() == RecapClientConfig.Anchor.BOTTOM_RIGHT || s.anchor() == RecapClientConfig.Anchor.BOTTOM_LEFT;
        int mx = (int) Math.round(s.marginX() * gs);
        int my = (int) Math.round(s.marginY() * gs);
        int stack = Math.round(tt.stackDu * L.u);
        int fx = right ? fbW - mx - L.w : mx;
        int fy = bottom ? fbH - my - L.fullH - stack : my + stack;
        int travel = Math.round((L.w + mx) * (1f - slide)) * (right ? 1 : -1);

        g.pose().pushPose();
        g.pose().translate(fx + travel, fy, 0);

        UiDraw d = UiDraw.begin(g.pose().last().pose());
        drawShapes(d, tt, L, s, alpha, el);
        d.end();

        final Layout laid = L;
        g.drawManaged(() -> drawTexts(g, font, tt, laid, s, alpha, el));
        g.setColor(1f, 1f, 1f, 1f);
        g.pose().popPose();
    }

    private static float rowAlpha(Toast tt, int i, float alpha, long el) {
        HudStyle s = tt.st;
        long rowStart = (long) (s.inMs() * 0.4f) + i * s.stagger();
        return alpha * easeOutCubic(Mth.clamp((el - rowStart) / 220f, 0f, 1f));
    }

    private static int rowShift(Toast tt, Layout L, int i, long el) {
        HudStyle s = tt.st;
        long rowStart = (long) (s.inMs() * 0.4f) + i * s.stagger();
        float re = easeOutCubic(Mth.clamp((el - rowStart) / 220f, 0f, 1f));
        return Math.round((1f - re) * 8f * L.u);
    }

    // ---- shapes

    private static void drawShapes(UiDraw d, Toast tt, Layout L, HudStyle s, float alpha, long el) {
        int hair = Math.max(1, Math.round(L.u * 0.5f));
        float bg = s.bgOpacity();

        if (s.showHeader()) {
            strip(d, 0, 0, L.leftW, L.hh, alpha, bg, hair);
            d.rect(0, 0, Math.max(1, L.px(2)), L.hh, argb(alpha, s.accent()));
            // 2x2 dot grid, like the reference's header glyph
            int dot = Math.max(1, L.px(2));
            int gx = L.pad;
            int gy = (L.hh - (dot * 2 + L.px(1))) / 2;
            for (int ix = 0; ix < 2; ix++) {
                for (int iy = 0; iy < 2; iy++) {
                    int x0 = gx + ix * (dot + L.px(1));
                    int y0 = gy + iy * (dot + L.px(1));
                    d.rect(x0, y0, x0 + dot, y0 + dot, argb(alpha, s.accent()));
                }
            }
            if (s.showBody()) {
                strip(d, L.cardX, 0, L.cardW, L.hh, alpha, bg, hair);
                int barW = Math.max(1, L.px(1.5f));
                d.rect(L.cardX + L.px(4), L.px(3), L.cardX + L.px(4) + barW, L.hh - L.px(3), argb(alpha, s.accent()));
            }
        }

        for (int i = 0; i < L.rows.length; i++) {
            RowView rv = L.rows[i];
            float ra = rowAlpha(tt, i, alpha, el);
            if (ra < 0.03f) {
                continue;
            }
            int sh = rowShift(tt, L, i, el);
            int y = L.rowsTop + L.px(i * (RH + GAP));
            int x0 = sh;
            strip(d, x0, y, L.leftW, L.rowH, ra, bg, hair);
            int accent = rv.data.self() ? s.selfColor() : s.accent();
            d.rect(x0, y, x0 + Math.max(1, L.px(2)), y + L.rowH, argb(ra, accent));
            d.diamond(x0 + L.markerX + L.px(2.5f), y + L.rowH / 2f, L.px(2.2f), argb(ra, accent));
            if (s.showHits() && !rv.valueText.isEmpty()) {
                d.rect(x0 + L.sepX, y + L.px(3), x0 + L.sepX + hair, y + L.rowH - L.px(3), argb(ra * 0.25f, 0xFFFFFF));
            }
            if (rv.badgeSeq != null) {
                int bw = rv.badgeFontW * L.t + L.px(6);
                int bx = x0 + L.nameX + Math.min(rv.nameFontW * L.t, L.nameW - L.badgeBlock + L.px(3)) + L.px(3);
                int bh = Math.round(7f * L.t) + L.px(2);
                int by = y + (L.rowH - bh) / 2;
                if (rv.badgeKill) {
                    d.roundRect(bx, by, bw, bh, L.px(1.5f), argb(ra, s.accent()));
                } else {
                    d.frame(bx, by, bx + bw, by + bh, hair, argb(ra * 0.55f, 0xF2F4F7));
                }
            }
        }

        if (s.showBody()) {
            d.roundRect(L.cardX, L.cardY, L.cardW, L.cardH, L.px(1.5f), argb(alpha * bg, 0x0B0D11));
            d.frame(L.cardX, L.cardY, L.cardX + L.cardW, L.cardY + L.cardH, hair, argb(alpha * 0.10f, 0xFFFFFF));
            float anim = s.flashMs() <= 0 ? 1f : Mth.clamp((el - s.inMs() * 0.6f) / s.flashMs(), 0f, 1f);
            drawFigure(d, tt, L, s, anim, alpha);
        }

        if (s.showProgress()) {
            float remaining = el < s.inMs() ? 1f
                    : (el < s.inMs() + s.holdMs() ? 1f - (el - s.inMs()) / (float) s.holdMs() : 0f);
            if (remaining > 0f) {
                int y = L.h + L.px(2);
                d.rect(0, y, Math.round(L.w * remaining), y + Math.max(1, L.px(1)), argb(alpha * 0.9f, s.accent()));
            }
        }
    }

    private static void strip(UiDraw d, int x, int y, int w, int h, float alpha, float bg, int hair) {
        d.gradientV(x, y, x + w, y + h, argb(alpha * bg, 0x15181E), argb(alpha * bg * 0.85f, 0x0A0B0E));
        d.frame(x, y, x + w, y + h, hair, argb(alpha * 0.10f, 0xFFFFFF));
    }

    /** Segmented front-view figure (design units, 18 x 44): square head, lightly chamfered blocks, gaps between parts. */
    private static void drawFigure(UiDraw d, Toast tt, Layout L, HudStyle s, float anim, float alpha) {
        boolean m = s.mirror();
        int[] c = tt.limbColors;
        figPart(d, L, s, c[Limb.HEAD.ordinal()], 5f, 0f, 8f, 8f, 0.6f, anim, alpha);
        figPart(d, L, s, c[Limb.CHEST.ordinal()], 4f, 9.6f, 10f, 9f, 0.8f, anim, alpha);
        figPart(d, L, s, c[Limb.ABDOMEN.ordinal()], 4.5f, 19.6f, 9f, 7.4f, 0.8f, anim, alpha);
        figPart(d, L, s, c[(m ? Limb.RIGHT_ARM : Limb.LEFT_ARM).ordinal()], 0.2f, 9.6f, 3.2f, 17f, 0.8f, anim, alpha);
        figPart(d, L, s, c[(m ? Limb.LEFT_ARM : Limb.RIGHT_ARM).ordinal()], 14.6f, 9.6f, 3.2f, 17f, 0.8f, anim, alpha);
        figPart(d, L, s, c[(m ? Limb.RIGHT_LEG : Limb.LEFT_LEG).ordinal()], 4.5f, 28f, 4.2f, 10f, 0.8f, anim, alpha);
        figPart(d, L, s, c[(m ? Limb.LEFT_LEG : Limb.RIGHT_LEG).ordinal()], 9.3f, 28f, 4.2f, 10f, 0.8f, anim, alpha);
        figPart(d, L, s, c[(m ? Limb.RIGHT_FOOT : Limb.LEFT_FOOT).ordinal()], 4.5f, 39f, 4.2f, 4.8f, 0.8f, anim, alpha);
        figPart(d, L, s, c[(m ? Limb.LEFT_FOOT : Limb.RIGHT_FOOT).ordinal()], 9.3f, 39f, 4.2f, 4.8f, 0.8f, anim, alpha);
    }

    private static void figPart(UiDraw d, Layout L, HudStyle s, int levelColor, float rx, float ry, float rw, float rh,
                                float radius, float anim, float alpha) {
        boolean hit = levelColor >= 0;
        float strength = hit ? anim : 0f;
        int color = hit ? lerpRgb(s.base(), levelColor, strength) : s.base();
        float x = L.figX + Math.round(rx * L.u);
        float y = L.figY + Math.round(ry * L.u);
        float w = Math.round(rw * L.u);
        float h = Math.round(rh * L.u);
        if (hit && strength > 0.02f) {
            float g = L.u;
            d.roundRect(x - g, y - g, w + 2 * g, h + 2 * g, radius * L.u + g, argb(alpha * 0.20f * strength, levelColor));
        }
        d.roundRect(x, y, w, h, radius * L.u, argb(alpha * (0.62f + 0.38f * strength), color));
    }

    // ---- text and icons

    private static void text(GuiGraphics g, Font font, FormattedCharSequence s, float x, float cy, int t, int color) {
        g.pose().pushPose();
        g.pose().translate(Math.round(x), Math.round(cy) - 4 * t, 0);
        g.pose().scale(t, t, 1f);
        g.drawString(font, s, 0, 0, color, false);
        g.pose().popPose();
    }

    private static void drawTexts(GuiGraphics g, Font font, Toast tt, Layout L, HudStyle s, float alpha, long el) {
        int t = L.t;
        Style fs = s.font();
        int white = argb(alpha, 0xF2F4F7);
        int dim = argb(alpha * 0.55f, 0xF2F4F7);
        if (s.showHeader()) {
            float cy = L.hh / 2f;
            text(g, font, L.header, L.pad + L.dotsW + L.px(4), cy, t, white);
            text(g, font, L.dmgLabel, L.dmgR - L.dmgLabelFontW * t, cy, t, dim);
            if (s.showHits()) {
                text(g, font, L.slashes, L.sepX + (L.px(5) / 2f) - L.slashFontW * t / 2f + 0.5f, cy, t, argb(alpha * 0.35f, 0xF2F4F7));
                text(g, font, L.valueLabel, L.valR - L.valueLabelFontW * t, cy, t, dim);
            }
            if (s.showBody()) {
                text(g, font, L.bodyLabel, L.cardX + L.px(4) + Math.max(1, L.px(1.5f)) + L.px(3), cy, t, white);
            }
        }
        for (int i = 0; i < L.rows.length; i++) {
            RowView rv = L.rows[i];
            float ra = rowAlpha(tt, i, alpha, el);
            if (ra < 0.03f) {
                continue;
            }
            int sh = rowShift(tt, L, i, el);
            int y = L.rowsTop + L.px(i * (RH + GAP));
            float cy = y + L.rowH / 2f;
            long rowStart = (long) (s.inMs() * 0.4f) + i * s.stagger();

            FormattedCharSequence name = rv.name;
            if (rv.scrolls) {
                long scroll = el - rowStart - 800; // short pause on the first letters
                int step = scroll <= 0 ? 0 : (int) Math.min(rv.maxStart, scroll / 150);
                name = joinTag(fit(font, rv.rawName.substring(step), fs, rv.availFontPx), rv.tag, fs);
            }
            text(g, font, name, sh + L.nameX, cy, t, argb(ra, rv.data.self() ? s.selfColor() : 0xF2F4F7));

            if (rv.badgeSeq != null) {
                int bw = rv.badgeFontW * t + L.px(6);
                int bx = sh + L.nameX + Math.min(rv.nameFontW * t, L.nameW - L.badgeBlock + L.px(3)) + L.px(3);
                int color = rv.badgeKill ? argb(ra, 0x0B0D11) : argb(ra * 0.8f, 0xF2F4F7);
                text(g, font, rv.badgeSeq, bx + (bw - rv.badgeFontW * t) / 2f, cy, t, color);
            }
            if (s.showWeapon()) {
                drawWeapon(g, rv, sh + L.iconX, y, L, ra);
            }

            // numbers count up from 0; once finished the precomputed strings are used
            float ce = s.countMs() <= 0 ? 1f : easeOutCubic(Mth.clamp((el - rowStart) / (float) s.countMs(), 0f, 1f));
            String dmg = ce >= 1f ? rv.dmgText : fmtDamage(rv.data.damage() * ce, s.decimals());
            FormattedCharSequence dmgSeq = ce >= 1f ? seq(rv.dmgText, fs) : seq(dmg, fs);
            text(g, font, dmgSeq, sh + L.dmgR - font.width(dmgSeq) * t, cy, t, argb(ra, 0xFFFFFF));
            if (s.showHits() && !rv.valueText.isEmpty()) {
                boolean tagged = tt.kind == Kind.REPORT;
                FormattedCharSequence value = rv.valueSeq;
                if (!tagged && ce < 1f) {
                    value = seq(Integer.toString(Math.round(rv.data.hits() * ce)), fs);
                }
                int color = rv.data.tagStrong() && tagged ? argb(ra, s.accent()) : argb(ra * 0.85f, 0xFFFFFF);
                text(g, font, value, sh + L.valR - font.width(value) * t, cy, t, color);
            }
        }
    }

    private static void drawWeapon(GuiGraphics g, RowView rv, int x, int rowY, Layout L, float alpha) {
        ItemStack stack = rv.data.weapon();
        if (stack.isEmpty()) {
            return;
        }
        if (rv.hud != null) {
            // The whole texture stretched into a 4:1 box at whole pixels (the box killfeed uses for TACZ HUD icons).
            g.setColor(1f, 1f, 1f, alpha);
            g.blit(rv.hud, x, rowY + (L.rowH - L.iconH) / 2, L.iconW, L.iconH, 0f, 0f, 1, 1, 1, 1);
            g.setColor(1f, 1f, 1f, 1f);
        } else if (alpha > 0.6f) {
            // Items are 16x16 models: a whole multiple keeps them crisp.
            int k = Math.max(1, (L.rowH - L.px(2)) / 16);
            g.pose().pushPose();
            g.pose().translate(x + (L.iconW - 16 * k) / 2f, rowY + (L.rowH - 16 * k) / 2f, 0);
            g.pose().scale(k, k, 1f);
            g.renderItem(stack, 0, 0);
            g.pose().popPose();
        }
    }

    // ------------------------------------------------------------------ colour / math helpers

    /** -1 = limb untouched, otherwise the RGB for its damage level (damage / max health). */
    private static int partColor(float damage, float maxHealth, HudStyle s) {
        if (damage <= 0f) {
            return -1;
        }
        if (!s.colorByLevel()) {
            return s.hitColor();
        }
        float f = damage / Math.max(1f, maxHealth);
        if (f >= s.levelCritical()) return s.levelColors()[3];
        if (f >= s.levelHigh()) return s.levelColors()[2];
        if (f >= s.levelMid()) return s.levelColors()[1];
        return s.levelColors()[0];
    }

    private static float easeOutCubic(float x) {
        float k = 1f - Mth.clamp(x, 0f, 1f);
        return 1f - k * k * k;
    }

    private static float easeInCubic(float x) {
        float k = Mth.clamp(x, 0f, 1f);
        return k * k * k;
    }

    /** Exact damage with up to `decimals` decimals, trailing zeros dropped (58.0 -> 58, 58.50 -> 58.5). */
    private static String fmtDamage(float v, int decimals) {
        String s = String.format(Locale.ROOT, "%." + decimals + "f", v);
        if (decimals > 0 && s.contains(".")) {
            s = s.replaceAll("0+$", "");
            if (s.endsWith(".")) {
                s = s.substring(0, s.length() - 1);
            }
        }
        return s;
    }

    private static int lerpRgb(int a, int b, float t) {
        t = Mth.clamp(t, 0f, 1f);
        int r = (int) Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
        int gr = (int) Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
        int bl = (int) Mth.lerp(t, a & 0xFF, b & 0xFF);
        return (r << 16) | (gr << 8) | bl;
    }

    private static int parseColor(String text, int fallback) {
        try {
            String s = text.trim();
            if (s.startsWith("#")) {
                s = s.substring(1);
            }
            return (int) (Long.parseLong(s, 16) & 0xFFFFFF);
        } catch (Exception e) {
            return fallback;
        }
    }
}
