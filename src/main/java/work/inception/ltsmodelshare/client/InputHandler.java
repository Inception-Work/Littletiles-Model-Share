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
        if (LtsModelShare.keyOpen == null || !LtsModelShare.keyOpen.isPressed()) {
            return;
        }
        if (Minecraft.getMinecraft().world == null) {
            return; // 只在游戏里（有世界）响应，主菜单不弹
        }
        openPanel();
    }

    /** 统一的入口：两个界面共用 */
    public static void openPanel() {
        Minecraft mc = Minecraft.getMinecraft();
        ModConfig cfg = ModConfig.get();
        if (!cfg.loggedIn()) {
            mc.displayGuiScreen(new GuiLogin(new GuiModels()));
        } else {
            mc.displayGuiScreen(new GuiModels());
        }
    }
}
