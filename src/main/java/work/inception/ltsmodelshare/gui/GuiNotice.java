package work.inception.ltsmodelshare.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.text.TextFormatting;
import work.inception.ltsmodelshare.client.TextUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 通用提示弹窗（说明型的）。
 *
 * <p>为什么需要它：grid 不兼容的提示是一长串操作步骤，直接塞进详情页底部状态栏会
 * 超出屏幕宽度（MC 的状态栏只会一直往右画，不会折行）。这里改成独立界面，
 * 正文用 {@link TextUtil} 按字符折行，宽度按窗口算。</p>
 */
public class GuiNotice extends GuiScreen {

    private final String title;
    private final List<String> paragraphs;
    private final GuiScreen back;
    private final String closeLabel;

    public GuiNotice(String title, List<String> paragraphs, GuiScreen back, String closeLabel) {
        this.title = title;
        this.paragraphs = paragraphs == null ? new ArrayList<>() : paragraphs;
        this.back = back;
        this.closeLabel = closeLabel == null ? "知道了" : closeLabel;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int cx = width / 2;
        int bw = 100;
        buttonList.add(new GuiButton(0, cx - bw / 2, height - 34, bw, 20, closeLabel));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        mc.displayGuiScreen(back);
    }

    /** ESC 也回到上一级（不直接退到游戏，避免玩家以为点错了） */
    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1) {
            mc.displayGuiScreen(back);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        int panelW = Math.min(360, width - 24);
        int left = (width - panelW) / 2;
        int top = Math.max(14, height / 2 - 110);
        int bottom = height - 44;

        drawRect(left - 8, top - 20, left + panelW + 8, bottom, 0xC0000000);
        drawCenteredString(fontRenderer, TextFormatting.YELLOW + title, width / 2, top - 14, 0xFFFF55);

        int y = top;
        for (String raw : paragraphs) {
            if (raw == null || raw.isEmpty()) {
                y += 6;
                continue;
            }
            for (String line : TextUtil.wrap(fontRenderer, raw, panelW)) {
                if (y > bottom - 12) {
                    break;
                }
                drawString(fontRenderer, line, left, y, 0xDDDDDD);
                y += 10;
            }
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
