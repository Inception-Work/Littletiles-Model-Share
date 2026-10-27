package work.inception.ltsmodelshare.proxy;

import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import work.inception.ltsmodelshare.LtsModelShare;
import work.inception.ltsmodelshare.network.PacketHandler;

/**
 * 服务端也存在的那一半：只做「公共」的事（网络通道）。
 *
 * <p>1.12.2 的专用服务端不加载任何 {@code net.minecraft.client.*} 类，
 * 所以按键、GUI、客户端命令一律放在 {@link ClientProxy} 里，
 * 由 {@code @SidedProxy} 按物理端选实现。</p>
 */
public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        // 网络通道必须在两端都注册：客户端发包、服务端收包
        PacketHandler.register();
        LtsModelShare.LOG.info("网络通道已注册（{}）", PacketHandler.CHANNEL);
    }

    public void init(FMLInitializationEvent event) {
        // 服务端侧没有额外要初始化的
    }
}
