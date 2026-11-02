package work.inception.ltsmodelshare.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import work.inception.ltsmodelshare.LtsModelShare;
import work.inception.ltsmodelshare.server.BlueprintServer;

/**
 * Client → server: one gzip-compressed structure chunk.
 *
 * <p>NeoForge 1.21 payload (replaces the Forge {@code SimpleChannel} message).</p>
 */
public record MsgBlueprintChunk(int transferId, int index, int total, String title, byte[] data)
        implements CustomPacketPayload {
    public static final int MAX_DATA = 30_000;

    public static final Type<MsgBlueprintChunk> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(LtsModelShare.MODID, "blueprint_chunk"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MsgBlueprintChunk> STREAM_CODEC = StreamCodec.of(
            (buf, msg) -> {
                buf.writeVarInt(msg.transferId());
                buf.writeVarInt(msg.index());
                buf.writeVarInt(msg.total());
                buf.writeUtf(msg.title(), 32767);
                buf.writeByteArray(msg.data());
            },
            buf -> new MsgBlueprintChunk(
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readUtf(32767),
                    buf.readByteArray(MAX_DATA)));

    public MsgBlueprintChunk {
        title = title == null ? "" : title;
        data = data == null ? new byte[0] : data;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(MsgBlueprintChunk msg, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                BlueprintServer.accept(player, msg);
            }
        });
    }
}
