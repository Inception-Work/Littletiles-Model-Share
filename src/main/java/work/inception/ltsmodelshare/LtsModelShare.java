package work.inception.ltsmodelshare;

import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;
import work.inception.ltsmodelshare.client.InputHandler;

/**
 * Littletiles Model Share —— 在游戏内登录、浏览并下载站点的 LittleTiles 模型。
 *
 * <p>当前实现：配置 + 登录 + 列表 + 详情 + 下载（SNBT 落盘）。
 * 还没做：直接在游戏里把下载的模型"贴"到世界里（导入 LittleTiles 结构）、上传。</p>
 *
 * <p>入口：按 <b>M</b> 打开面板，或者聊天栏输入 <code>/ltsmodels</code>。</p>
 */
@Mod(
        modid = LtsModelShare.MODID,
        name = LtsModelShare.NAME,
        version = LtsModelShare.VERSION,
        acceptedMinecraftVersions = "[1.12,1.13)",
        clientSideOnly = true,
        acceptableRemoteVersions = "*"
)
public class LtsModelShare {

    public static final String MODID = "ltsmodelshare";
    public static final String NAME = "Littletiles Model Share";
    public static final String VERSION = "0.1.0";

    public static final Logger LOG = LogManager.getLogger(MODID);

    /** 默认按键：M */
    @SideOnly(Side.CLIENT)
    public static KeyBinding keyOpen;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        ModConfig.get();
        if (event.getSide().isClient()) {
            keyOpen = new KeyBinding("key.ltsmodelshare.open", Keyboard.KEY_M, "key.categories.ltsmodelshare");
            ClientRegistry.registerKeyBinding(keyOpen);
            MinecraftForge.EVENT_BUS.register(new InputHandler());
        }
        LOG.info("{} {} 预初始化完成", NAME, VERSION);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        if (event.getSide().isClient()) {
            net.minecraftforge.client.ClientCommandHandler.instance.registerCommand(
                    new work.inception.ltsmodelshare.command.CommandLts());
        }
        LOG.info("{} {} 初始化完成（M 键或 /ltsmodels 打开面板）", NAME, VERSION);


    }
}
