package dev.szo9k4.limbdamage.dummy;

import dev.szo9k4.limbdamage.LimbDamageMod;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, LimbDamageMod.MOD_ID);

    /** Same hitbox as a standing player UNDER TACZ: 0.6 wide, 1.8 tall + the 0.0625 TACZ grows it upward (so the top pixel of the head is hittable). */
    public static final RegistryObject<EntityType<DummyEntity>> DUMMY = ENTITIES.register("dummy",
            () -> EntityType.Builder.<DummyEntity>of(DummyEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8625F)
                    .clientTrackingRange(10)
                    .build(LimbDamageMod.MOD_ID + ":dummy"));

    private ModEntities() {
    }
}
