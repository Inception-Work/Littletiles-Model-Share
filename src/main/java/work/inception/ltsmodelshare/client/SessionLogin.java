package work.inception.ltsmodelshare.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import work.inception.ltsmodelshare.ModConfig;
import work.inception.ltsmodelshare.api.ApiClient;
import work.inception.ltsmodelshare.gui.GuiLogin;

/** Show one login form only while the user is still in this mod's UI. */
public final class SessionLogin {
    private SessionLogin() { }
    public static void required(String base) {
        ApiClient.post(() -> {
            ModConfig cfg = ModConfig.get();
            if (!java.util.Objects.equals(base, cfg.baseUrl) || cfg.loggedIn()) return;
            Minecraft mc = Minecraft.getInstance();
            Screen screen = mc.screen;
            if (screen == null || screen instanceof GuiLogin
                    || !screen.getClass().getName().startsWith("work.inception.ltsmodelshare.gui.")) return;
            mc.setScreen(new GuiLogin(screen));
        });
    }
}
