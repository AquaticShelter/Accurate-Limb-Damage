package dev.szo9k4.limbdamage.compat;

import dev.szo9k4.limbdamage.HitLocationTracker;
import dev.szo9k4.limbdamage.LimbDamageMod;
import dev.szo9k4.limbdamage.config.LimbConfig;
import dev.szo9k4.limbdamage.config.CategoryConfig;
import dev.szo9k4.limbdamage.util.Safe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Everything that needs to know about TACZ, with no compile-time dependency (pure reflection; each
 * piece degrades on its own and logs once). Reflection handles are resolved ONCE and cached: a hit
 * must never pay for Class.forName / getMethod.
 *
 *  1. Weapon category: which TACZ gun type (pistol / rifle / sniper / rpg / smg / shotgun / mg) a hit
 *     was made with. Resolution order:
 *       a) the gun TACZ itself reported for this victim in EntityHurtByGunEvent.Pre (exact),
 *       b) the bullet entity's own gun id,
 *       c) the gun in the shooter's hand (assumes they didn't switch while the bullet flew).
 *     The gun's type comes from TACZ's gun index; gunTypeOverrides in the config wins over it.
 *  2. Headshot override: in EntityHurtByGunEvent.Pre, a headshot's per-gun damage multiplier is
 *     cancelled (set to 1.0) so only this mod's multiplier applies.
 */
public final class TaczCompat {

    private record Ctx(ResourceLocation gunId, long tick) {
    }

    /** TimelessAPI / IGun entry points, resolved once. Absent members are null. */
    private record Api(Method getIGunOrNull, Method getGunId, Method getCommonIndex, Method getClientIndex) {
    }

    private static final Map<UUID, Ctx> CTX = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, String> TYPE_CACHE = new ConcurrentHashMap<>();
    private static final Set<ResourceLocation> WARNED = ConcurrentHashMap.newKeySet();
    /** bullet class -> its getGunId() (Optional.empty = this class has none). */
    private static final Map<Class<?>, Optional<Method>> BULLET_ID_METHODS = new ConcurrentHashMap<>();

    private static volatile Api api;
    private static volatile boolean apiResolved;

    // EntityHurtByGunEvent.Pre accessors (any may be null if this TACZ build names them differently)
    private static Method mHurt;
    private static Method mGunId;
    private static Method mIsHead;
    private static Method mSetHeadshot;
    private static Method mGetHeadMult;
    private static Method mSetHeadMult;
    private static Method mGetBase;
    private static Method mSetBase;
    private static volatile boolean overrideCapable;

    // gunTypeOverrides, parsed once per config reload (compared by list identity)
    private static volatile List<? extends String> overridesSource;
    private static volatile Map<String, String> overridesParsed = Map.of();

    private TaczCompat() {
    }

    /** True when TACZ's pre-damage event lets this mod set the headshot flag (headshotGuard can then work both ways). */
    public static boolean headshotFlagSyncAvailable() {
        return mIsHead != null && mSetHeadshot != null;
    }

    /** True when this TACZ build exposes what the headshot override needs. */
    public static boolean headshotOverrideAvailable() {
        return overrideCapable;
    }

    /** Common setup: hook TACZ's pre-damage event if TACZ is installed. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void init() {
        try {
            Class<?> pre = Class.forName("com.tacz.guns.api.event.common.EntityHurtByGunEvent$Pre");
            mHurt = find(pre, new Class<?>[0], "getHurtEntity");
            mGunId = find(pre, new Class<?>[0], "getGunId");
            mIsHead = find(pre, new Class<?>[0], "isHeadShot", "isHeadshot");
            mSetHeadshot = find(pre, new Class<?>[]{boolean.class}, "setHeadshot", "setHeadShot");
            mGetHeadMult = find(pre, new Class<?>[0], "getHeadshotMultiplier", "getHeadShotMultiplier");
            mSetHeadMult = find(pre, new Class<?>[]{float.class}, "setHeadshotMultiplier", "setHeadShotMultiplier");
            mGetBase = find(pre, new Class<?>[0], "getBaseAmount");
            mSetBase = find(pre, new Class<?>[]{float.class}, "setBaseAmount");
            overrideCapable = mIsHead != null
                    && (mSetHeadMult != null || (mGetHeadMult != null && mGetBase != null && mSetBase != null));

            Consumer listener = TaczCompat::onGunPre;
            IEventBus bus = MinecraftForge.EVENT_BUS;
            bus.addListener(EventPriority.HIGH, false, (Class) pre, listener);
            LimbDamageMod.LOGGER.info("[LimbDamage] TACZ detected: weapon categories on; headshot override {}.",
                    overrideCapable ? "available" : "NOT available (this TACZ build's event has no usable headshot accessors)");
        } catch (ClassNotFoundException e) {
            LimbDamageMod.LOGGER.info("[LimbDamage] TACZ pre-damage event not found: weapon categories fall back to the gun in hand, headshot override unavailable.");
        } catch (Throwable t) {
            Safe.report("TaczCompat.init", t);
        }
    }

    /** Listener for EntityHurtByGunEvent.Pre (fired on the logical server before TACZ hurts the target). */
    private static void onGunPre(Object event) {
        try {
            Object hurt = mHurt != null ? mHurt.invoke(event) : null;
            LivingEntity victim = hurt instanceof LivingEntity living ? living : null;
            if (victim == null || victim.level().isClientSide()) {
                return;
            }
            Object id = mGunId != null ? mGunId.invoke(event) : null;
            if (id instanceof ResourceLocation gunId) {
                CTX.put(victim.getUUID(), new Ctx(gunId, victim.level().getGameTime()));
            }

            boolean flagged = mIsHead != null && Boolean.TRUE.equals(mIsHead.invoke(event));

            // Sync: TACZ decides "headshot" from a vertical band around the eyes (eye +-0.25) and knows nothing
            // about the real model, so it calls the top of the shoulders a headshot and misses the top pixels of
            // the head. Make its flag follow THIS mod's verdict (player-model geometry) in both directions.
            if (CategoryConfig.HEADSHOT_GUARD.get()) {
                Boolean verdict = HitLocationTracker.peekHeadVerdict(victim);
                if (verdict != null && verdict.booleanValue() != flagged) {
                    if (mSetHeadshot != null) {
                        mSetHeadshot.invoke(event, verdict.booleanValue());
                        flagged = verdict;
                    } else if (!verdict && mIsHead != null) {
                        cancelHeadshotBonus(event); // can't flip the flag, but the bonus can still be removed
                        flagged = false;
                    }
                    if (LimbConfig.LOG_HITS.get()) {
                        LimbDamageMod.LOGGER.info("[LimbDamage] headshot sync on {}: TACZ said {} -> this mod says {}",
                                victim.getName().getString(), !verdict, verdict);
                    }
                }
            }
            // Override: the gun's own headshot multiplier never applies; this mod's head multiplier does.
            if (flagged && CategoryConfig.HEADSHOT_OVERRIDE.get()) {
                cancelHeadshotBonus(event);
            }
        } catch (Throwable t) {
            Safe.report("TaczCompat.onGunPre", t);
        }
    }

    /** Neutralises the gun's own headshot damage multiplier for this shot (needs the accessors found in init). */
    private static void cancelHeadshotBonus(Object event) throws ReflectiveOperationException {
        if (mSetHeadMult != null) {
            mSetHeadMult.invoke(event, 1.0f);
        } else if (mGetHeadMult != null && mGetBase != null && mSetBase != null) {
            float mult = ((Number) mGetHeadMult.invoke(event)).floatValue();
            float base = ((Number) mGetBase.invoke(event)).floatValue();
            if (mult > 0f) {
                mSetBase.invoke(event, base / mult); // base * mult becomes the un-multiplied base
            }
        }
    }

    /** Category of the TACZ gun that caused this damage, or null (not a TACZ gun hit / unknown type). */
    public static String categoryOf(LivingEntity victim, DamageSource source) {
        ResourceLocation gunId = null;

        Entity direct = source.getDirectEntity();
        Entity attacker = source.getEntity();
        Ctx ctx = CTX.get(victim.getUUID());
        // only damage dealt BY a bullet may use the gun TACZ just reported: melee / fire in the same 2 ticks must not
        if (ctx != null && direct != null && direct != attacker && victim.level().getGameTime() - ctx.tick() <= 2) {
            gunId = ctx.gunId();
        }
        if (gunId == null && direct != null && direct != attacker) {
            gunId = bulletGunId(direct);
            if (gunId == null && attacker instanceof LivingEntity shooter) {
                gunId = heldGunId(shooter.getMainHandItem());
            }
        }
        return gunId == null ? null : typeOf(gunId);
    }

    // ------------------------------------------------------------------ reflection (cached)

    private static Api api() {
        if (!apiResolved) {
            synchronized (TaczCompat.class) {
                if (!apiResolved) {
                    Api resolved = null;
                    try {
                        Class<?> iGun = Class.forName("com.tacz.guns.api.item.IGun");
                        Class<?> timeless = Class.forName("com.tacz.guns.api.TimelessAPI");
                        resolved = new Api(
                                iGun.getMethod("getIGunOrNull", ItemStack.class),
                                iGun.getMethod("getGunId", ItemStack.class),
                                optionalMethod(timeless, "getCommonGunIndex"),
                                optionalMethod(timeless, "getClientGunIndex"));
                    } catch (Throwable ignored) {
                        // TACZ not installed or API changed: category detection stays off
                    }
                    api = resolved;
                    apiResolved = true;
                }
            }
        }
        return api;
    }

    private static Method optionalMethod(Class<?> cls, String name) {
        try {
            return cls.getMethod(name, ResourceLocation.class);
        } catch (Throwable t) {
            return null;
        }
    }

    private static ResourceLocation bulletGunId(Entity bullet) {
        Class<?> cls = bullet.getClass();
        Optional<Method> method = BULLET_ID_METHODS.computeIfAbsent(cls, c -> {
            if (!c.getName().contains("tacz")) {
                return Optional.empty();
            }
            try {
                return Optional.of(c.getMethod("getGunId"));
            } catch (Throwable t) {
                return Optional.empty();
            }
        });
        if (method.isEmpty()) {
            return null;
        }
        try {
            return method.get().invoke(bullet) instanceof ResourceLocation rl ? rl : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static ResourceLocation heldGunId(ItemStack stack) {
        Api a = api();
        if (a == null || stack == null || stack.isEmpty()) {
            return null;
        }
        try {
            Object gun = a.getIGunOrNull().invoke(null, stack);
            if (gun == null) {
                return null;
            }
            return a.getGunId().invoke(gun, stack) instanceof ResourceLocation rl ? rl : null;
        } catch (Throwable t) {
            return null;
        }
    }

    // ------------------------------------------------------------------ gun type lookup

    private static String typeOf(ResourceLocation gunId) {
        String forced = overrideFor(gunId);
        if (forced != null) {
            return forced;
        }
        String cached = TYPE_CACHE.get(gunId);
        if (cached != null) {
            return cached;
        }
        String type = normalize(fromIndex(true, gunId));
        if (type == null) {
            type = normalize(fromIndex(false, gunId));
        }
        if (type != null) {
            TYPE_CACHE.put(gunId, type);
        } else if (WARNED.add(gunId)) {
            LimbDamageMod.LOGGER.warn("[LimbDamage] Couldn't read the TACZ gun type of {}; it uses the global multipliers. "
                    + "Add \"{}=<category>\" to weaponCategories.gunTypeOverrides to set it by hand.", gunId, gunId);
        }
        return type;
    }

    /** gunTypeOverrides as a lowercase id -> category map, rebuilt only when the config list changes. */
    private static String overrideFor(ResourceLocation gunId) {
        List<? extends String> list = CategoryConfig.OVERRIDES.get();
        if (list != overridesSource) {
            Map<String, String> parsed = new HashMap<>();
            for (String entry : list) {
                int eq = entry.indexOf('=');
                String category = eq > 0 ? normalize(entry.substring(eq + 1)) : null;
                if (category != null) {
                    parsed.put(entry.substring(0, eq).trim().toLowerCase(Locale.ROOT), category);
                }
            }
            overridesParsed = parsed;
            overridesSource = list;
        }
        Map<String, String> map = overridesParsed;
        return map.isEmpty() ? null : map.get(gunId.toString().toLowerCase(Locale.ROOT));
    }

    private static String fromIndex(boolean common, ResourceLocation gunId) {
        Api a = api();
        Method lookup = a == null ? null : (common ? a.getCommonIndex() : a.getClientIndex());
        if (lookup == null) {
            return null;
        }
        try {
            if (!(lookup.invoke(null, gunId) instanceof Optional<?> opt) || opt.isEmpty()) {
                return null;
            }
            Object index = opt.get();
            Object type = call(index, "getType");
            if (type == null) {
                Object data = call(index, "getGunData");
                type = data != null ? call(data, "getType") : null;
            }
            return type == null ? null : String.valueOf(type);
        } catch (Throwable t) {
            return null;
        }
    }

    private static String normalize(String type) {
        if (type == null) {
            return null;
        }
        String t = type.trim().toLowerCase(Locale.ROOT);
        return CategoryConfig.CATEGORIES.contains(t) ? t : null;
    }

    private static Object call(Object target, String method) {
        try {
            return target.getClass().getMethod(method).invoke(target);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Method find(Class<?> cls, Class<?>[] params, String... names) {
        for (String name : names) {
            try {
                return cls.getMethod(name, params);
            } catch (NoSuchMethodException ignored) {
                // try the next spelling
            }
        }
        return null;
    }

    public static void sweep(long gameTime) {
        if (!CTX.isEmpty()) {
            CTX.values().removeIf(c -> gameTime - c.tick() > 3);
        }
    }

    public static void clearAll() {
        CTX.clear();
    }
}
