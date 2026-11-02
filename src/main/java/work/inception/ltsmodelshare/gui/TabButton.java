package work.inception.ltsmodelshare.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import work.inception.ltsmodelshare.client.TextUtil;

/** 可高亮的分页签按钮：选中项用实色底，未选中项保持普通按钮外观。 */
public final class TabButton extends GuiButton {
    private boolean selected;

    public TabButton(int id, int x, int y, int width, int height, String text, boolean selected) {
        super(id, x, y, width, height, text);
        this.selected = selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    @Override
    public void drawCenteredString(FontRenderer font, String text, int centerX, int top, int color) {
        TextUtil.drawCenteredFit(font, text, centerX, top, width - 8, color);
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        if (!visible) return;
        hovered = mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
        if (selected) {
            Gui.drawRect(x, y, x + width, y + height, 0xFF3F6E8C);
            Gui.drawRect(x, y + height - 1, x + width, y + height, 0xFFFFFFFF);
            TextUtil.drawCenteredFit(mc.fontRenderer, displayString,
                    x + width / 2, y + (height - 8) / 2, width - 8, 0xFFFFFFFF);
        } else {
            super.drawButton(mc, mouseX, mouseY, partialTicks);
        }
    }
}
