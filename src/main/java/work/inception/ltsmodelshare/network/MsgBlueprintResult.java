package work.inception.ltsmodelshare.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import work.inception.ltsmodelshare.client.BlueprintClient;

import java.util.function.Supplier;

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

    public static void handle(MsgBlueprintResult msg, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> BlueprintClient.onResult(msg.ok, msg.text)));
        ctx.setPacketHandled(true);
    }
}
