package work.inception.ltsmodelshare.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import work.inception.ltsmodelshare.LtsModelShare;
import work.inception.ltsmodelshare.client.BlueprintClient;

/** Server → client: blueprint creation result (component sent as-is, no JSON round trip). */
public record MsgBlueprintResult(boolean ok, Component message) implements CustomPacketPayload {

    public static final Type<MsgBlueprintResult> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(LtsModelShare.MODID, "blueprint_result"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MsgBlueprintResult> STREAM_CODEC = StreamCodec.of(
            (buf, msg) -> {
                buf.writeBoolean(msg.ok());
                ComponentSerialization.STREAM_CODEC.encode(buf, msg.message());
            },
            buf -> new MsgBlueprintResult(buf.readBoolean(), ComponentSerialization.STREAM_CODEC.decode(buf)));

    public MsgBlueprintResult {
        message = message == null ? Component.empty() : message;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(MsgBlueprintResult msg, IPayloadContext context) {
        // Client-bound payload: never touched on a dedicated server, and the guard keeps the
        // client-only BlueprintClient class out of the server class-loading path.
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        context.enqueueWork(() -> BlueprintClient.onResult(msg.ok(), msg.message()));
    }
}
