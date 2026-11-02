package work.inception.ltsmodelshare.client;

import net.minecraft.client.Minecraft;
import work.inception.ltsmodelshare.ModConfig;
import work.inception.ltsmodelshare.gui.GuiLogin;
import work.inception.ltsmodelshare.gui.GuiModels;

/** Shared entry point for the key binding and the client command. */
public final class InputHandler {
    private InputHandler() { }

    public static void openPanel() {
        Minecraft mc = Minecraft.getInstance();
        if (!ModConfig.get().loggedIn()) {
            mc.setScreen(new GuiLogin(new GuiModels()));
        } else {
            mc.setScreen(new GuiModels());
        }
    }
}
