package work.inception.ltsmodelshare.proxy;

import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;
import work.inception.ltsmodelshare.LtsModelShare;
import work.inception.ltsmodelshare.client.BlueprintClient;
import work.inception.ltsmodelshare.client.InputHandler;
import work.inception.ltsmodelshare.client.KeyConflictFixer;
import work.inception.ltsmodelshare.server.BlueprintServer;

/**
 * 客户端那一半：按键、GUI 入口、客户端命令、以及"等服务端回执"的 tick 检查。
 */
@SideOnly(Side.CLIENT)
public class ClientProxy extends CommonProxy {

    /** 打开面板的按键（默认 I；和 LittleTiles 的 M 冲突过） */
    public static KeyBinding keyOpen;

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        keyOpen = new KeyBinding("key.ltsmodelshare.open", Keyboard.KEY_I, "key.categories.ltsmodelshare");
        ClientRegistry.registerKeyBinding(keyOpen);
        MinecraftForge.EVENT_BUS.register(new InputHandler());
        // 和 LittleTiles 的 little.mark 抢 M 键 → 启动时自动挪到空闲键
        MinecraftForge.EVENT_BUS.register(new KeyConflictFixer());
        MinecraftForge.EVENT_BUS.register(new ClientTickHandler());
        LtsModelShare.LOG.info("客户端侧初始化完成（按键 I）");
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        net.minecraftforge.client.ClientCommandHandler.instance.registerCommand(
                new work.inception.ltsmodelshare.command.CommandLts());
    }

    /** 每 tick 检查一次：等了太久没收到服务端回执（对方没装本模组）就退回剪贴板方案 */
    public static class ClientTickHandler {

        @net.minecraftforge.fml.common.eventhandler.SubscribeEvent
        public void onClientTick(net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent event) {
            if (event.phase == net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END) {
                BlueprintClient.tick();
            }
        }
    }
}
