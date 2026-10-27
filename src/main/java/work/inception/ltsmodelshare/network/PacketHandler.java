package work.inception.ltsmodelshare.network;

import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
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
        // 0 = 客户端 → 服务端：请求生成蓝图
        NET.registerMessage(MsgBlueprintRequest.Handler.class, MsgBlueprintRequest.class, 0, Side.SERVER);
        // 1 = 服务端 → 客户端：结果回执（只在客户端注册处理器；服务端只负责发）
        tryRegisterClientResult();
        LtsModelShare.LOG.debug("通道 {} 注册完成", CHANNEL);
    }

    /** 反射式注册，避免服务端加载客户端类（专用服务端没有 Minecraft 类） */
    private static void tryRegisterClientResult() {
        if (!net.minecraftforge.fml.common.FMLCommonHandler.instance().getSide().isClient()) {
            return;
        }
        registerClientResult();
    }

    /**
     * 拆分到单独方法，并用 SideOnly 标注：服务端不会加载这个方法体里的客户端消息处理器。
     */
    @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
    private static void registerClientResult() {
        NET.registerMessage(MsgBlueprintResult.Handler.class, MsgBlueprintResult.class, 1, Side.CLIENT);
    }
}
