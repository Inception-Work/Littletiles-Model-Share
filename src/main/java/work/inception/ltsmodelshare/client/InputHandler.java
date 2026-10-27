package work.inception.ltsmodelshare.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import work.inception.ltsmodelshare.LtsModelShare;
import work.inception.ltsmodelshare.ModConfig;
import work.inception.ltsmodelshare.gui.GuiLogin;
import work.inception.ltsmodelshare.gui.GuiModels;

/**
 * 按键处理：M 打开模型面板；没登录时先弹登录框。
 */
public class InputHandler {

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (work.inception.ltsmodelshare.proxy.ClientProxy.keyOpen == null || !work.inception.ltsmodelshare.proxy.ClientProxy.keyOpen.isPressed()) {
            return;
        }
        // 主菜单也允许打开（浏览/登录不需要世界，只有「获取蓝图」需要）
        LtsModelShare.LOG.info("[key] M 键被按下 → 打开面板");
        openPanel();
    }

    /** 统一的入口：两个界面共用 */
    public static void openPanel() {
        Minecraft mc = Minecraft.getMinecraft();
        ModConfig cfg = ModConfig.get();
        if (!cfg.loggedIn()) {
            mc.displayGuiScreen(new GuiLogin(new GuiModels()));
        } else {
            // 已登录：顺便静默续期一次（服务端是滑动窗口，用过就把过期时间往后推）
            work.inception.ltsmodelshare.api.ApiClient.refreshQuietly();
            mc.displayGuiScreen(new GuiModels());
        }
    }
}
