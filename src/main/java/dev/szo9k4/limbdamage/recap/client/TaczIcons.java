package dev.szo9k4.limbdamage.recap.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Finds TACZ's flat weapon HUD texture for a gun (the same texture the killfeed mod shows via
 * SuperbWarfare's TACZ bridge, which ends in GunDisplayInstance#getHUDTexture). Pure reflection,
 * no compile-time dependency; every failure returns null so the caller falls back to the item icon.
 * Non-TACZ items always return null.
 *
 * Lookup order:
 *  1. TimelessAPI.getGunDisplay(ItemStack) -> Optional&lt;GunDisplayInstance&gt; -> getHUDTexture()
 *  2. TimelessAPI.getClientGunIndex(gunId) -> [getDefaultDisplay()] -> getHUDTexture()   (older/newer layouts)
 *  3. SuperbWarfare's own TACZ bridge (exactly what killfeed calls), if SBW is installed
 */
final class TaczIcons {

    private TaczIcons() {
    }

    static ResourceLocation hudTexture(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        ResourceLocation tex = viaGunDisplay(stack);
        if (tex == null) {
            tex = viaGunIndex(stack);
        }
        if (tex == null) {
            tex = viaSuperbWarfare(stack);
        }
        return tex;
    }

    private static ResourceLocation viaGunDisplay(ItemStack stack) {
        try {
            Class<?> api = Class.forName("com.tacz.guns.api.TimelessAPI");
            Object opt = api.getMethod("getGunDisplay", ItemStack.class).invoke(null, stack);
            if (opt instanceof Optional<?> o && o.isPresent()) {
                return textureOf(o.get());
            }
        } catch (Throwable ignored) {
            // API shape differs / TACZ absent
        }
        return null;
    }

    private static ResourceLocation viaGunIndex(ItemStack stack) {
        try {
            Class<?> iGun = Class.forName("com.tacz.guns.api.item.IGun");
            Object gun = iGun.getMethod("getIGunOrNull", ItemStack.class).invoke(null, stack);
            if (gun == null) {
                return null; // not a TACZ gun
            }
            Object id = iGun.getMethod("getGunId", ItemStack.class).invoke(gun, stack);
            if (!(id instanceof ResourceLocation rl)) {
                return null;
            }
            Class<?> api = Class.forName("com.tacz.guns.api.TimelessAPI");
            Object opt = api.getMethod("getClientGunIndex", ResourceLocation.class).invoke(null, rl);
            if (opt instanceof Optional<?> o && o.isPresent()) {
                Object index = o.get();
                ResourceLocation direct = textureOf(index);
                if (direct != null) {
                    return direct;
                }
                Object display = call(index, "getDefaultDisplay");
                return display != null ? textureOf(display) : null;
            }
        } catch (Throwable ignored) {
            // fall through
        }
        return null;
    }

    private static ResourceLocation viaSuperbWarfare(ItemStack stack) {
        try {
            Class<?> handler = Class.forName("com.atsuishio.superbwarfare.compat.tacz.TACZGunEventHandler");
            Object instance = handler.getField("INSTANCE").get(null);
            Object r = handler.getMethod("getTaczCompatIcon", ItemStack.class).invoke(instance, stack);
            return r instanceof ResourceLocation rl ? rl : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static ResourceLocation textureOf(Object holder) {
        Object r = call(holder, "getHUDTexture");
        if (r == null) {
            r = call(holder, "getHudTexture");
        }
        return r instanceof ResourceLocation rl ? rl : null;
    }

    private static Object call(Object target, String method) {
        try {
            Method m = target.getClass().getMethod(method);
            return m.invoke(target);
        } catch (Throwable t) {
            return null;
        }
    }
}
