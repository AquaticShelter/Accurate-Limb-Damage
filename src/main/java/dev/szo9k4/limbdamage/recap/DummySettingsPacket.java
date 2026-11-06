package dev.szo9k4.limbdamage.recap;

import dev.szo9k4.limbdamage.dummy.DummyEntity;
import dev.szo9k4.limbdamage.dummy.DummyMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: the stats typed into the dummy screen (max health, damage reduction %, auto-heal, death mode). */
public final class DummySettingsPacket {

    public final int entityId;
    public final float maxHealth;
    public final float reductionPercent;
    public final float autoHealSeconds;
    public final int deathMode;

    public DummySettingsPacket(int entityId, float maxHealth, float reductionPercent, float autoHealSeconds, int deathMode) {
        this.entityId = entityId;
        this.maxHealth = maxHealth;
        this.reductionPercent = reductionPercent;
        this.autoHealSeconds = autoHealSeconds;
        this.deathMode = deathMode;
    }

    public static void encode(DummySettingsPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.entityId);
        buf.writeFloat(p.maxHealth);
        buf.writeFloat(p.reductionPercent);
        buf.writeFloat(p.autoHealSeconds);
        buf.writeVarInt(p.deathMode);
    }

    public static DummySettingsPacket decode(FriendlyByteBuf buf) {
        return new DummySettingsPacket(buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readVarInt());
    }

    public static void handle(DummySettingsPacket p, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            if (sender == null) {
                return;
            }
            // only while that dummy's screen is really open for this player, and only if they may edit it
            if (sender.containerMenu instanceof DummyMenu menu && menu.dummy.getId() == p.entityId
                    && menu.dummy.canEditSettings(sender) && menu.stillValid(sender)) {
                DummyEntity dummy = menu.dummy;
                if (Float.isFinite(p.maxHealth) && Float.isFinite(p.reductionPercent) && Float.isFinite(p.autoHealSeconds)) {
                    dummy.applySettings(p.maxHealth, p.reductionPercent, p.autoHealSeconds, p.deathMode);
                }
            }
        });
        ctx.setPacketHandled(true);
    }
}
