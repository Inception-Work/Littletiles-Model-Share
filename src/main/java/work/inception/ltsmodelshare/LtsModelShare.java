package work.inception.ltsmodelshare;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import work.inception.ltsmodelshare.proxy.CommonProxy;

/**
 * Littletiles Model Share —— 在游戏内登录、浏览、上传和获取 LittleTiles 模型。
 *
 * <p>入口：按 <b>I</b> 打开面板，或者聊天栏输入 <code>/ltsmodels</code>。</p>
 *
 * <p><b>为什么不再是 {@code clientSideOnly}</b>：联机时要由**服务端**生成蓝图
 * （{@code importStructure} + 塞背包），否则道具会被服务端同步覆盖。
 * 客户端专属的东西（按键 / GUI / 客户端命令）都放在
 * {@link work.inception.ltsmodelshare.proxy.ClientProxy} 里，
 * 专用服务端只加载 {@link CommonProxy} + 网络通道。</p>
 */
@Mod(
        modid = LtsModelShare.MODID,
        name = LtsModelShare.NAME,
        version = LtsModelShare.VERSION,
        acceptedMinecraftVersions = "[1.12,1.13)",
        acceptableRemoteVersions = "*"
)
public class LtsModelShare {

    public static final String MODID = "ltsmodelshare";
    public static final String NAME = "Littletiles Model Share";
    public static final String VERSION = "0.1.3";

    public static final Logger LOG = LogManager.getLogger(MODID);

    /**
     * 按物理端选实现：客户端拿到 {@code ClientProxy}（按键 / GUI / 客户端命令），
     * 专用服务端拿到 {@code CommonProxy}（只注册网络通道）。
     */
    @SidedProxy(
            clientSide = "work.inception.ltsmodelshare.proxy.ClientProxy",
            serverSide = "work.inception.ltsmodelshare.proxy.CommonProxy"
    )
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        ModConfig.get();
        proxy.preInit(event);
        LOG.info("{} {} 预初始化完成（side={}）", NAME, VERSION, event.getSide());
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
        LOG.info("{} {} 初始化完成", NAME, VERSION);
    }
}
