package work.inception.ltsmodelshare.gui;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import work.inception.ltsmodelshare.client.TextUtil;

/** Uses vanilla button behavior while fitting longer translations inside the label area. */
public final class LocalizedButton extends GuiButton {
    public LocalizedButton(int id, int x, int y, int width, int height, String text) {
        super(id, x, y, width, height, text);
    }

    @Override
    public void drawCenteredString(FontRenderer font, String text, int centerX, int top, int color) {
        TextUtil.drawCenteredFit(font, text, centerX, top, width - 8, color);
    }
}
