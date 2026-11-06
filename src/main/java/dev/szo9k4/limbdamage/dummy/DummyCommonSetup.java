package dev.szo9k4.limbdamage.dummy;

import dev.szo9k4.limbdamage.LimbDamageMod;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = LimbDamageMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class DummyCommonSetup {

    private DummyCommonSetup() {
    }

    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.DUMMY.get(), DummyEntity.createAttributes().build());
    }
}
