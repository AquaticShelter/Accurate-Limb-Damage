package dev.szo9k4.limbdamage.dummy.client;

import dev.szo9k4.limbdamage.dummy.DummyEntity;
import dev.szo9k4.limbdamage.dummy.DummyMenu;
import dev.szo9k4.limbdamage.recap.DummySettingsPacket;
import dev.szo9k4.limbdamage.recap.RecapNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.Locale;
import java.util.function.DoubleFunction;

/**
 * The dummy screen: equipment slots around a live preview of the dummy, your inventory underneath, and a stats
 * panel on the right with sliders for max health, damage reduction % (0.1 steps, so 55.5 % works), auto-heal
 * and a death-behaviour button. Stats are sent to the server when a slider is released.
 */
public final class DummyScreen extends AbstractContainerScreen<DummyMenu> {

    private static final int PANEL_X = 182;
    private static final int PANEL_W = 96;

    private float maxHealth;
    private float reduction;
    private float autoHeal;
    private int mode;

    public DummyScreen(DummyMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 282;
        this.imageHeight = 222;
        this.inventoryLabelY = 129;
        this.titleLabelY = 6;
        this.maxHealth = menu.stats.maxHealth();
        this.reduction = menu.stats.reduction();
        this.autoHeal = menu.stats.autoHeal();
        this.mode = menu.stats.mode();
    }

    @Override
    protected void init() {
        super.init();
        int x = this.leftPos + PANEL_X;
        int y = this.topPos + 30;
        boolean edit = this.menu.stats.canEdit();

        addRenderableWidget(new StatSlider(x, y, PANEL_W, 20, 1.0, 1024.0, 1.0, maxHealth, edit,
                v -> Component.literal(String.format(Locale.ROOT, "Max health: %.0f", v)),
                v -> maxHealth = (float) v));
        addRenderableWidget(new StatSlider(x, y + 24, PANEL_W, 20, 0.0, 100.0, 0.1, reduction, edit,
                v -> Component.literal(String.format(Locale.ROOT, "Damage reduction: %.1f%%", v)),
                v -> reduction = (float) v));
        addRenderableWidget(new StatSlider(x, y + 48, PANEL_W, 20, 0.0, 60.0, 0.5, Math.min(60f, autoHeal), edit,
                v -> Component.literal(v <= 0.0 ? "Auto-heal: off" : String.format(Locale.ROOT, "Auto-heal: %.1fs", v)),
                v -> autoHeal = (float) v));
        Button death = Button.builder(deathLabel(), b -> {
            mode = (mode + 1) % DummyEntity.DeathMode.values().length;
            b.setMessage(deathLabel());
            send();
        }).bounds(x, y + 72, PANEL_W, 20).build();
        death.active = edit;
        addRenderableWidget(death);
    }

    private Component deathLabel() {
        return Component.literal("On death: " + DummyEntity.DeathMode.values()[mode].label);
    }

    private void send() {
        RecapNetwork.CHANNEL.sendToServer(new DummySettingsPacket(menu.dummy.getId(), maxHealth, reduction, autoHeal, mode));
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        panel(g, x, y, 176, 222);
        panel(g, x + PANEL_X - 6, y, 102, 222 - 100);
        for (Slot slot : this.menu.slots) {
            int sx = x + slot.x - 1;
            int sy = y + slot.y - 1;
            g.fill(sx, sy, sx + 18, sy + 18, 0xFF8B8B8B);
            g.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFF2B2D31);
        }
        // preview of the dummy, turning to follow the mouse
        int cx = x + 80;
        int by = y + 88;
        g.fill(x + 30, y + 16, x + 130, y + 92, 0xFF16181C);
        g.enableScissor(x + 30, y + 16, x + 130, y + 92);
        InventoryScreen.renderEntityInInventoryFollowsMouse(g, cx, by, 34, cx - mouseX, by - 50 - mouseY, this.menu.dummy);
        g.disableScissor();
        g.drawString(this.font, Component.literal("Stats"), x + PANEL_X, y + 8, 0xFFE0E0E0, false);
        if (!this.menu.stats.canEdit()) {
            g.drawString(this.font, Component.literal("Only the owner can edit"), x + PANEL_X, y + 22, 0xFF909090, false);
        }
    }

    private static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFF9A9A9A);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF3A3C41);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(this.font, this.title, 8, 6, 0xFFE0E0E0, false);
        g.drawString(this.font, this.playerInventoryTitle, 8, this.inventoryLabelY, 0xFFE0E0E0, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    /** A slider over [min, max] snapped to `step`; the value is sent when the mouse is released. */
    private final class StatSlider extends AbstractSliderButton {
        private final double min;
        private final double max;
        private final double step;
        private final DoubleFunction<Component> label;
        private final java.util.function.DoubleConsumer onChange;

        StatSlider(int x, int y, int w, int h, double min, double max, double step, double initial, boolean enabled,
                   DoubleFunction<Component> label, java.util.function.DoubleConsumer onChange) {
            super(x, y, w, h, Component.empty(), (clamp(initial, min, max) - min) / (max - min));
            this.min = min;
            this.max = max;
            this.step = step;
            this.label = label;
            this.onChange = onChange;
            this.active = enabled;
            updateMessage();
        }

        private static double clamp(double v, double lo, double hi) {
            return Math.max(lo, Math.min(hi, v));
        }

        private double current() {
            double raw = min + this.value * (max - min);
            return Math.round(raw / step) * step;
        }

        @Override
        protected void updateMessage() {
            setMessage(label.apply(current()));
        }

        @Override
        protected void applyValue() {
            onChange.accept(current());
        }

        @Override
        public void onRelease(double mouseX, double mouseY) {
            super.onRelease(mouseX, mouseY);
            send();
        }
    }
}
