package dev.szo9k4.limbdamage.recap;

import dev.szo9k4.limbdamage.Limb;
import dev.szo9k4.limbdamage.recap.client.RecapClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Server -> client: everything needed to draw the damage toast. Two flavours share it:
 *  - death recap (report = false): one row per ATTACKER, the HIT column is the number of hits;
 *  - dummy report (report = true): one row per HIT, the HIT column shows `tag` (multiplier + source).
 */
public final class RecapPacket {

    public static final int MAX_ROWS = 6;
    /** Most rows a dummy report can carry. */
    public static final int MAX_REPORT_ROWS = 64;

    /**
     * @param tag       text for the right-hand column in a report ("x1.50", "x2.00 SNP"), "" in a recap
     * @param tagStrong true when the tag's multiplier did not come from the plain global config (highlighted)
     */
    public record RowData(String name, boolean player, boolean self, ItemStack weapon, float damage,
                          int hits, String tag, boolean tagStrong) {
    }

    public final float maxHealth;
    public final float[] parts;
    /** Recap: number of attackers. Report: number of hits (may exceed rows.size()). */
    public final int total;
    public final List<RowData> rows;
    public final boolean report;
    /** true = the "you dealt" toast: one row per player you damaged (shown under the death recap). */
    public final boolean dealt;

    public RecapPacket(float maxHealth, float[] parts, int total, List<RowData> rows, boolean report) {
        this(maxHealth, parts, total, rows, report, false);
    }

    public RecapPacket(float maxHealth, float[] parts, int total, List<RowData> rows, boolean report, boolean dealt) {
        this.dealt = dealt;
        this.maxHealth = maxHealth;
        this.parts = parts;
        this.total = total;
        this.rows = rows;
        this.report = report;
    }

    public static void encode(RecapPacket msg, FriendlyByteBuf buf) {
        buf.writeFloat(msg.maxHealth);
        for (int i = 0; i < Limb.values().length; i++) {
            buf.writeFloat(msg.parts[i]);
        }
        buf.writeVarInt(msg.total);
        buf.writeBoolean(msg.report);
        buf.writeBoolean(msg.dealt);
        buf.writeVarInt(msg.rows.size());
        for (RowData row : msg.rows) {
            buf.writeUtf(row.name(), 128);
            buf.writeBoolean(row.player());
            buf.writeBoolean(row.self());
            buf.writeItem(row.weapon());
            buf.writeFloat(row.damage());
            buf.writeVarInt(row.hits());
            buf.writeUtf(row.tag(), 32);
            buf.writeBoolean(row.tagStrong());
        }
    }

    public static RecapPacket decode(FriendlyByteBuf buf) {
        float maxHealth = buf.readFloat();
        float[] parts = new float[Limb.values().length];
        for (int i = 0; i < parts.length; i++) {
            parts[i] = buf.readFloat();
        }
        int total = buf.readVarInt();
        boolean report = buf.readBoolean();
        boolean dealt = buf.readBoolean();
        int n = Math.min(buf.readVarInt(), MAX_REPORT_ROWS);
        List<RowData> rows = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            rows.add(new RowData(buf.readUtf(128), buf.readBoolean(), buf.readBoolean(), buf.readItem(),
                    buf.readFloat(), buf.readVarInt(), buf.readUtf(32), buf.readBoolean()));
        }
        return new RecapPacket(maxHealth, parts, total, rows, report, dealt);
    }

    public static void handle(RecapPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> RecapClient.accept(msg)));
        ctx.setPacketHandled(true);
    }
}
