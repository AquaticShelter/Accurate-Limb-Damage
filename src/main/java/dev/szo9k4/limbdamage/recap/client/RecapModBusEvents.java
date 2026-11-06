package dev.szo9k4.limbdamage.recap.client;

import dev.szo9k4.limbdamage.LimbDamageMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = LimbDamageMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class RecapModBusEvents {

    private RecapModBusEvents() {
    }

    /** HUD layer for when no death screen is open (instant-respawn setups, after clicking respawn). */
    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("death_recap", (gui, graphics, partialTick, width, height) -> {
            if (Minecraft.getInstance().screen instanceof DeathScreen) {
                return; // drawn by RecapForgeBusEvents on top of the death screen instead
            }
            RecapClient.render(graphics, width, height);
        });
    }
}
