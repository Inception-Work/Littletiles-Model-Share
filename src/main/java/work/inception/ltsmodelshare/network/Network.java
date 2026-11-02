package work.inception.ltsmodelshare.network;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import work.inception.ltsmodelshare.LtsModelShare;

/**
 * NeoForge 1.21 payload channel: structure chunks up, result back.
 *
 * <p>Replaces the old Forge {@code SimpleChannel}. Registration happens on the mod
 * event bus during {@link RegisterPayloadHandlersEvent}.</p>
 */
public final class Network {
    public static final String PROTOCOL = "1";

    private Network() { }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(Network::onRegisterPayloadHandlers);
    }

    private static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL);
        registrar.playToServer(MsgBlueprintChunk.TYPE, MsgBlueprintChunk.STREAM_CODEC, MsgBlueprintChunk::handle);
        registrar.playToClient(MsgBlueprintResult.TYPE, MsgBlueprintResult.STREAM_CODEC, MsgBlueprintResult::handle);
        LtsModelShare.LOG.info("网络通道已注册（NeoForge payloads，协议 {}）", PROTOCOL);
    }
}
