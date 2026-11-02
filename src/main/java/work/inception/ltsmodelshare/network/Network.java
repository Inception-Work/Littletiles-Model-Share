package work.inception.ltsmodelshare.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.SimpleChannel;
import work.inception.ltsmodelshare.LtsModelShare;

/** Forge 1.20.2 channel: structure chunks up, result back. */
public final class Network {
    /** Network protocol version (Forge 48 uses int versions instead of the old string). */
    public static final int PROTOCOL = 1;
    public static SimpleChannel INSTANCE;

    private Network() { }

    public static void register() {
        if (INSTANCE != null) return;
        INSTANCE = ChannelBuilder.named(new ResourceLocation(LtsModelShare.MODID, "main"))
                .networkProtocolVersion(PROTOCOL)
                .simpleChannel();
        INSTANCE.messageBuilder(MsgBlueprintChunk.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(MsgBlueprintChunk::encode)
                .decoder(MsgBlueprintChunk::decode)
                .consumerMainThread(MsgBlueprintChunk::handle)
                .add();
        INSTANCE.messageBuilder(MsgBlueprintResult.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(MsgBlueprintResult::encode)
                .decoder(MsgBlueprintResult::decode)
                .consumerMainThread(MsgBlueprintResult::handle)
                .add();
    }
}
