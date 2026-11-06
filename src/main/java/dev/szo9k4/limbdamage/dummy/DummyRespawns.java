package dev.szo9k4.limbdamage.dummy;

import dev.szo9k4.limbdamage.LimbDamageMod;
import dev.szo9k4.limbdamage.config.LimbConfig;
import dev.szo9k4.limbdamage.util.Safe;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Brings a dummy back after it dies (dummy.dieAndRespawn): same dimension, same spot, same direction, same
 * owner (so the same skin) and the same max health, at full health. If the chunk isn't loaded yet the respawn
 * waits and retries once a second instead of being lost.
 */
@Mod.EventBusSubscriber(modid = LimbDamageMod.MOD_ID)
public final class DummyRespawns {

    private record Job(ResourceKey<Level> dimension, double x, double y, double z, float yaw, UUID owner,
                        float maxHealth, int dueTick, int tries,
                        net.minecraft.nbt.CompoundTag state) {
    }

    private static final int MAX_TRIES = 120; // ~2 minutes of waiting for the chunk
    private static final List<Job> JOBS = new ArrayList<>();

    private DummyRespawns() {
    }

    /** Called when a dummy dies; remembers where it stood. */
    static void schedule(DummyEntity dummy) {
        if (!(dummy.level() instanceof ServerLevel level)) {
            return;
        }
        int due = level.getServer().getTickCount() + (int) (LimbConfig.DUMMY_RESPAWN_DELAY_SECONDS.get() * 20.0);
        net.minecraft.nbt.CompoundTag state = new net.minecraft.nbt.CompoundTag();
        dummy.saveWithoutId(state); // equipment, owner and the dummy's own stats come back with it
        synchronized (JOBS) {
            JOBS.add(new Job(level.dimension(), dummy.getX(), dummy.getY(), dummy.getZ(), dummy.getYRot(),
                    dummy.getOwnerUuid().orElse(null), dummy.getMaxHealth(), due, 0, state));
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || JOBS.isEmpty()) {
            return;
        }
        Safe.run("dummy respawn", () -> {
            int now = event.getServer().getTickCount();
            List<Job> retry = new ArrayList<>();
            synchronized (JOBS) {
                List<Job> due = new ArrayList<>();
                JOBS.removeIf(job -> {
                    if (now >= job.dueTick()) {
                        due.add(job);
                        return true;
                    }
                    return false;
                });
                for (Job job : due) {
                    ServerLevel level = event.getServer().getLevel(job.dimension());
                    if (level == null) {
                        continue; // dimension gone
                    }
                    if (!level.isLoaded(BlockPos.containing(job.x(), job.y(), job.z()))) {
                        if (job.tries() < MAX_TRIES) {
                            retry.add(new Job(job.dimension(), job.x(), job.y(), job.z(), job.yaw(), job.owner(),
                                    job.maxHealth(), now + 20, job.tries() + 1, job.state()));
                        }
                        continue;
                    }
                    spawn(level, job);
                }
                JOBS.addAll(retry);
            }
        });
    }

    private static void spawn(ServerLevel level, Job job) {
        DummyEntity dummy = ModEntities.DUMMY.get().create(level);
        if (dummy == null) {
            return;
        }
        net.minecraft.nbt.CompoundTag state = job.state().copy();
        for (String key : new String[]{"UUID", "Health", "DeathTime", "HurtTime", "HurtByTimestamp", "Pos", "Motion", "Rotation", "Fire"}) {
            state.remove(key);
        }
        dummy.load(state);
        dummy.deathTime = 0;
        dummy.moveTo(job.x(), job.y(), job.z(), job.yaw(), 0f);
        dummy.setYBodyRot(job.yaw());
        dummy.setYHeadRot(job.yaw());
        dummy.setOwnerUuid(job.owner());
        dummy.configureHealth(job.maxHealth());
        level.addFreshEntity(dummy);
    }

    public static void clearAll() {
        synchronized (JOBS) {
            JOBS.clear();
        }
    }
}
