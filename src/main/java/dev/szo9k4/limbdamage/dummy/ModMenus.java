package dev.szo9k4.limbdamage.dummy;

import dev.szo9k4.limbdamage.LimbDamageMod;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, LimbDamageMod.MOD_ID);

    public static final RegistryObject<MenuType<DummyMenu>> DUMMY =
            MENUS.register("dummy", () -> IForgeMenuType.create(DummyMenu::fromNetwork));

    private ModMenus() {
    }
}
