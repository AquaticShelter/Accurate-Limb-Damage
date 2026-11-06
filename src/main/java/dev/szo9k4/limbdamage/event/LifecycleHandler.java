package dev.szo9k4.limbdamage.event;

import dev.szo9k4.limbdamage.CombatLog;
import dev.szo9k4.limbdamage.HitLocationTracker;
import dev.szo9k4.limbdamage.LimbDamageMod;
import dev.szo9k4.limbdamage.compat.TaczCompat;
import dev.szo9k4.limbdamage.dummy.DummyRespawns;
import dev.szo9k4.limbdamage.effect.FragileHandler;
import dev.szo9k4.limbdamage.recap.DamageLog;
import dev.szo9k4.limbdamage.util.Safe;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * All of this mod's per-world state is static and keyed by game tick. Game time restarts with every
 * world, so state left over from a previous world in the same session (singleplayer: quit, load
 * another) would otherwise leak into the next one (e.g. a regeneration lock that "ends" at a tick
 * the new world never reaches). Cleared on server start AND stop.
 */
@Mod.EventBusSubscriber(modid = LimbDamageMod.MOD_ID)
public final class LifecycleHandler {

    private LifecycleHandler() {
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        Safe.run("clear state (start)", LifecycleHandler::clearAll);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        Safe.run("clear state (stop)", LifecycleHandler::clearAll);
    }

    private static void clearAll() {
        HitLocationTracker.clearAll();
        CombatLog.clearAll();
        DamageLog.clearAll();
        FragileHandler.clearAll();
        TaczCompat.clearAll();
        DummyRespawns.clearAll();
        dev.szo9k4.limbdamage.recap.DealtLog.clearAll();
    }
}
