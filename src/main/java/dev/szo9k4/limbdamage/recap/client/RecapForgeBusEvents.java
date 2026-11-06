package dev.szo9k4.limbdamage.recap.client;

import dev.szo9k4.limbdamage.LimbDamageMod;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = LimbDamageMod.MOD_ID, value = Dist.CLIENT)
public final class RecapForgeBusEvents {

    private RecapForgeBusEvents() {
    }

    /** The vanilla death screen draws a red overlay over the HUD, so draw on top of it. */
    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (event.getScreen() instanceof DeathScreen screen) {
            RecapClient.render(event.getGuiGraphics(), screen.width, screen.height);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        RecapClient.clear();
    }
}
