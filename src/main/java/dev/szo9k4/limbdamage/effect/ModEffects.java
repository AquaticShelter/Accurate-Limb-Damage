package dev.szo9k4.limbdamage.effect;

import dev.szo9k4.limbdamage.LimbDamageMod;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEffects {

    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, LimbDamageMod.MOD_ID);

    /**
     * Fragile. While active:
     *  - ARMS and LEGS hits use x1.0 instead of the configured arms/legs multipliers (DamageHandler);
     *  - regeneration is delayed (FragileHandler): vanilla Regeneration effect paused, natural/food
     *    regen blocked, and the "Regeneration tweak" mod's after-damage timer pushed forward.
     * Try it with: /effect give @s limbdamage:fragile 10
     */
    public static final RegistryObject<MobEffect> FRAGILE = EFFECTS.register("fragile", FragileEffect::new);

    private ModEffects() {
    }

    public static final class FragileEffect extends MobEffect {
        public FragileEffect() {
            super(MobEffectCategory.HARMFUL, 0xC8CCD2);
        }

        /** Registers the client-side icon renderer (see FragileClientExtensions). Only ever called on the client. */
        public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientMobEffectExtensions> consumer) {
            consumer.accept(new FragileClientExtensions());
        }
    }
}
