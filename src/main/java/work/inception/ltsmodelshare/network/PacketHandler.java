package work.inception.ltsmodelshare.network;

import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import work.inception.ltsmodelshare.LtsModelShare;

/**
 * 网络通道：客户端把「我要这个结构」发给服务端，服务端生成蓝图后再回执。
 *
 * <p>为什么需要它：专用服务端上没有我们的客户端逻辑，
 * 只有让**服务端**去 {@code importStructure} 并塞进玩家背包，道具才是权威的、
 * 重进游戏还在、别的玩家也看得见。集成服务端（单人/局域网主机）走的是本地路径，
 * 不经过这里。</p>
 */
public final class PacketHandler {

    public static final String CHANNEL = "ltsmodelshare";

    public static SimpleNetworkWrapper NET;

    private PacketHandler() {
    }

    public static void register() {
        if (NET != null) {
            return;
        }
        NET = NetworkRegistry.INSTANCE.newSimpleChannel(CHANNEL);
        // 0 = 客户端 → 服务端：结构分片（gzip 后切开，单包不超过 30KB）
        NET.registerMessage(MsgBlueprintChunk.Handler.class, MsgBlueprintChunk.class, 0, Side.SERVER);

        /*
         * 1 = 服务端 → 客户端：结果回执。
         * **两端都必须注册**：SimpleNetworkWrapper 编码时要在本地查 discriminator，
         * 服务端只注册了「收」用的 0 号包，发回执时就抛 Undefined discriminator，
         * 而且是直接把连接打死（客户端被踢：A fatal error has occurred）。
         * 服务端不需要处理这个包，所以挂空处理器，避免加载客户端类。
         */
        if (FMLCommonHandler.instance().getSide().isClient()) {
            NET.registerMessage(MsgBlueprintResult.Handler.class, MsgBlueprintResult.class, 1, Side.CLIENT);
        } else {
            NET.registerMessage(MsgBlueprintResult.NoopHandler.class, MsgBlueprintResult.class, 1, Side.CLIENT);
        }
    }

}
