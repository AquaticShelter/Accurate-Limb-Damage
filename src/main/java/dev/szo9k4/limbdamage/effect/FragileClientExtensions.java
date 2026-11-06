package dev.szo9k4.limbdamage.effect;

import dev.szo9k4.limbdamage.LimbDamageMod;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.client.extensions.common.IClientMobEffectExtensions;

/**
 * Draws the Fragile icon from a plain GUI texture (textures/gui/fragile_effect.png) in BOTH the HUD
 * effect row and the inventory effect list, instead of relying on the mob_effects texture atlas,
 * which showed a missing-texture square for this mod's effect.
 *
 * The overrides intentionally have no @Override: if a Forge build ever changes these signatures the
 * mod still compiles and the vanilla atlas icon is used instead.
 */
public class FragileClientExtensions implements IClientMobEffectExtensions {

    private static final ResourceLocation ICON =
            new ResourceLocation(LimbDamageMod.MOD_ID, "textures/gui/fragile_effect.png");

    /** Inventory effect list: (x, y) is where the 18x18 icon goes. */
    public boolean renderInventoryIcon(MobEffectInstance instance, EffectRenderingInventoryScreen<?> screen,
                                       GuiGraphics graphics, int x, int y, int blitOffset) {
        graphics.blit(ICON, x, y, blitOffset, 0f, 0f, 18, 18, 18, 18);
        return true;
    }

    /** HUD effect row: (x, y) is the top-left of the 24x24 slot; the icon sits 3px inside it. */
    public boolean renderGuiIcon(MobEffectInstance instance, Gui gui, GuiGraphics graphics,
                                 int x, int y, float z, float alpha) {
        graphics.setColor(1f, 1f, 1f, alpha);
        graphics.blit(ICON, x + 3, y + 3, 0, 0f, 0f, 18, 18, 18, 18);
        graphics.setColor(1f, 1f, 1f, 1f);
        return true;
    }
}
