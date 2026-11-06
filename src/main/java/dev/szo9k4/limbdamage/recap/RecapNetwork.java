package dev.szo9k4.limbdamage.recap;

import dev.szo9k4.limbdamage.LimbDamageMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class RecapNetwork {

    private static final String PROTOCOL = "6";

    // acceptMissingOr: a client without the mod can still join (it just won't see the recap),
    // same "harmless on clients" behaviour Limb Damage already had.
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(LimbDamageMod.MOD_ID, "recap"),
            () -> PROTOCOL,
            NetworkRegistry.acceptMissingOr(PROTOCOL),
            NetworkRegistry.acceptMissingOr(PROTOCOL));

    private RecapNetwork() {
    }

    public static void register() {
        CHANNEL.registerMessage(0, RecapPacket.class, RecapPacket::encode, RecapPacket::decode, RecapPacket::handle);
        CHANNEL.registerMessage(1, DummySettingsPacket.class, DummySettingsPacket::encode, DummySettingsPacket::decode,
                DummySettingsPacket::handle);
    }

    public static void sendTo(ServerPlayer player, RecapPacket packet) {
        if (player.connection != null && CHANNEL.isRemotePresent(player.connection.connection)) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
        }
    }
}
