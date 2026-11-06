package dev.szo9k4.limbdamage;

import dev.szo9k4.limbdamage.compat.TaczCompat;
import dev.szo9k4.limbdamage.config.LimbConfig;
import dev.szo9k4.limbdamage.dummy.ModEntities;
import dev.szo9k4.limbdamage.dummy.ModMenus;
import dev.szo9k4.limbdamage.effect.ModEffects;
import dev.szo9k4.limbdamage.recap.RecapNetwork;
import dev.szo9k4.limbdamage.recap.client.RecapClientConfig;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Call of Duty-style locational damage: arms and legs take less damage, every body part is tunable, for
 * players and humanoid mobs. Works with TACZ and Accurate Hitboxes (hit locations to the pixel) and falls back to
 * trajectory estimates without them. Settings live in YACL-managed files (YACL is required); the server decides
 * damage, clients draw the feedback.
 */
@Mod(LimbDamageMod.MOD_ID)
public final class LimbDamageMod {
    public static final String MOD_ID = "limbdamage";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public LimbDamageMod() {
        LimbConfig.load();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            RecapClientConfig.load();
        }
        RecapNetwork.register();
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModEffects.EFFECTS.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        ModMenus.MENUS.register(modBus);
        modBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(TaczCompat::init));
    }
}
