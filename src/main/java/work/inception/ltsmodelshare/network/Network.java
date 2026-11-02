package work.inception.ltsmodelshare.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import work.inception.ltsmodelshare.LtsModelShare;

/** Forge SimpleChannel: structure chunks up, result back. */
public final class Network {
    public static final String PROTOCOL = "1";
    public static SimpleChannel INSTANCE;

    private Network() { }

    public static void register() {
        if (INSTANCE != null) return;
        INSTANCE = NetworkRegistry.newSimpleChannel(
                new ResourceLocation(LtsModelShare.MODID, "main"),
                () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
        INSTANCE.registerMessage(0, MsgBlueprintChunk.class,
                MsgBlueprintChunk::encode, MsgBlueprintChunk::decode, MsgBlueprintChunk::handle);
        INSTANCE.registerMessage(1, MsgBlueprintResult.class,
                MsgBlueprintResult::encode, MsgBlueprintResult::decode, MsgBlueprintResult::handle);
    }
}
