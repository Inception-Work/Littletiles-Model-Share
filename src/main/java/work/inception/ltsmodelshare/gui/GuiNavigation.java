package work.inception.ltsmodelshare.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Shared footer geometry in scaled GUI pixels. */
public final class GuiNavigation {
    public static final int MARGIN = 10;
    public static final int GAP = 6;
    public static final int BACK_WIDTH = 80;
    public static final int BUTTON_HEIGHT = 20;

    private GuiNavigation() { }

    public static int backX(int screenWidth) {
        return screenWidth - MARGIN - BACK_WIDTH;
    }

    public static int footerY(int screenHeight) {
        return screenHeight - MARGIN - BUTTON_HEIGHT;
    }

    public static FitButton button(int x, int y, int width, int height, String key, Runnable action) {
        return new FitButton(x, y, width, height, Component.translatable(key), b -> action.run());
    }

    public static FitButton back(Screen parent, String key) {
        return new FitButton(backX(parent.width), footerY(parent.height), BACK_WIDTH, BUTTON_HEIGHT,
                Component.translatable(key), b -> Minecraft.getInstance().setScreen(parent));
    }

    public static int actionWidth(int screenWidth, int count, int preferred) {
        return Math.min(preferred, Math.max(1, (backX(screenWidth) - MARGIN - count * GAP) / count));
    }
}
