package work.inception.ltsmodelshare.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;
import work.inception.ltsmodelshare.server.BlueprintServer;

/** Client → server: one gzip-compressed structure chunk. */
public class MsgBlueprintChunk {
    public static final int MAX_DATA = 30_000;

    private final int transferId;
    private final int index;
    private final int total;
    private final String title;
    private final byte[] data;

    public MsgBlueprintChunk(int transferId, int index, int total, String title, byte[] data) {
        this.transferId = transferId;
        this.index = index;
        this.total = total;
        this.title = title == null ? "" : title;
        this.data = data;
    }

    public static void encode(MsgBlueprintChunk msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.transferId);
        buf.writeVarInt(msg.index);
        buf.writeVarInt(msg.total);
        buf.writeUtf(msg.title, 32767);
        buf.writeByteArray(msg.data);
    }

    public static MsgBlueprintChunk decode(FriendlyByteBuf buf) {
        int transferId = buf.readVarInt();
        int index = buf.readVarInt();
        int total = buf.readVarInt();
        String title = buf.readUtf(32767);
        byte[] data = buf.readByteArray(MAX_DATA);
        return new MsgBlueprintChunk(transferId, index, total, title, data);
    }

    public static void handle(MsgBlueprintChunk msg, CustomPayloadEvent.Context ctx) {
        ctx.enqueueWork(() -> BlueprintServer.accept(ctx.getSender(), msg));
        ctx.setPacketHandled(true);
    }

    public int transferId() { return transferId; }
    public int index() { return index; }
    public int total() { return total; }
    public String title() { return title; }
    public byte[] data() { return data; }
}
