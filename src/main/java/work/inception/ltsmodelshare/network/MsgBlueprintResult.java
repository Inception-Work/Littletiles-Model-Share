package work.inception.ltsmodelshare.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;
import work.inception.ltsmodelshare.client.BlueprintClient;

/** Server → client: blueprint creation result (component JSON or plain text). */
public class MsgBlueprintResult {
    private final boolean ok;
    private final String text;

    public MsgBlueprintResult(boolean ok, String text) {
        this.ok = ok;
        this.text = text == null ? "" : text;
    }

    public static void encode(MsgBlueprintResult msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.ok);
        buf.writeUtf(msg.text, 32767);
    }

    public static MsgBlueprintResult decode(FriendlyByteBuf buf) {
        return new MsgBlueprintResult(buf.readBoolean(), buf.readUtf(32767));
    }

    public static void handle(MsgBlueprintResult msg, CustomPayloadEvent.Context ctx) {
        // Registered PLAY_TO_CLIENT only, so this runs on the client; consumerMainThread already
        // dispatches on the main thread (DistExecutor was removed in Forge 1.20.2).
        ctx.enqueueWork(() -> BlueprintClient.onResult(msg.ok, msg.text));
        ctx.setPacketHandled(true);
    }
}
