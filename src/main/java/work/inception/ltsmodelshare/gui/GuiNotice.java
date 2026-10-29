package work.inception.ltsmodelshare.gui;

import static work.inception.ltsmodelshare.client.Translations.tr;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.text.TextFormatting;
import work.inception.ltsmodelshare.client.TextUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 通用提示弹窗（小尺寸、居中的模态框）。
 *
 * <p>之前版本把面板铺得几乎满屏，看着不像弹窗；现在：</p>
 * <ul>
 *   <li>宽度取了更窄的上限（默认 260 逻辑像素），高度**按内容算**，不撑满；</li>
 *   <li>整体在窗口里居中，四周留白，背后压一层半透明遮罩；</li>
 *   <li>带 2px 边框 + 标题栏，视觉上明确是"浮在上面的框"。</li>
 * </ul>
 *
 * <p>正文用 {@link TextUtil} 按字符折行（中文没空格，MC 原生折行不管用）。</p>
 */
public class GuiNotice extends GuiScreen {

    private static final int LINE_H = 10;
    private static final int PAD = 10;
    private static final int TITLE_H = 16;
    private static final int BTN_W = 90;
    private static final int BTN_H = 20;

    private final String title;
    private final List<String> paragraphs;
    private final GuiScreen back;
    private final String closeLabel;

    // 由 initGui 算出，drawScreen 直接用
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private final List<String> wrapped = new ArrayList<>();

    public GuiNotice(String title, List<String> paragraphs, GuiScreen back, String closeLabel) {
        this.title = title;
        this.paragraphs = paragraphs == null ? new ArrayList<>() : paragraphs;
        this.back = back;
        this.closeLabel = closeLabel == null ? tr("ltsmodelshare.common.ok") : closeLabel;
    }

    @Override
    public void initGui() {
        buttonList.clear();

        // 窄一点才像弹窗；同时保证极窄窗口下也有可用宽度
        panelW = Math.max(140, Math.min(240, width - 80));

        wrapped.clear();
        for (String raw : paragraphs) {
            if (raw == null || raw.isEmpty()) {
                wrapped.add("");
                continue;
            }
            wrapped.addAll(TextUtil.wrap(fontRenderer, raw, panelW - PAD * 2));
        }

        int bodyH = wrapped.size() * LINE_H;
        panelH = TITLE_H + PAD + bodyH + PAD + BTN_H + PAD;
        panelX = (width - panelW) / 2;
        panelY = Math.max(8, (height - panelH) / 2);

        buttonList.add(new LocalizedButton(0,
                panelX + (panelW - BTN_W) / 2,
                panelY + panelH - PAD - BTN_H,
                BTN_W, BTN_H, closeLabel));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        mc.displayGuiScreen(back);
    }

    /** ESC 回到上一级（不直接退到游戏，避免玩家以为点错了） */
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
        // 背景压暗（保留游戏画面，强调"这是浮层"）
        drawDefaultBackground();

        // ---------- 边框：外面一圈黑 + 里面一圈亮灰，像素风 ----------
        Gui.drawRect(panelX - 2, panelY - 2, panelX + panelW + 2, panelY + panelH + 2, 0xFF000000);
        Gui.drawRect(panelX, panelY, panelX + panelW, panelY + panelH, 0xFF2B2B2B);

        int border = 0xFF8A8A8A;
        Gui.drawRect(panelX, panelY, panelX + panelW, panelY + 2, border);                       // 上
        Gui.drawRect(panelX, panelY + panelH - 2, panelX + panelW, panelY + panelH, border);     // 下
        Gui.drawRect(panelX, panelY, panelX + 2, panelY + panelH, border);                       // 左
        Gui.drawRect(panelX + panelW - 2, panelY, panelX + panelW, panelY + panelH, border);     // 右

        // ---------- 标题栏 ----------
        Gui.drawRect(panelX + 2, panelY + 2, panelX + panelW - 2, panelY + TITLE_H, 0xFF3A3A3A);
        drawCenteredString(fontRenderer, TextFormatting.YELLOW + title,
                panelX + panelW / 2, panelY + (TITLE_H - 8) / 2, 0xFFFF55);
        Gui.drawRect(panelX + 2, panelY + TITLE_H, panelX + panelW - 2, panelY + TITLE_H + 1, 0xFF555555);

        // ---------- 正文 ----------
        int y = panelY + TITLE_H + PAD;
        for (String line : wrapped) {
            if (!line.isEmpty()) {
                drawString(fontRenderer, line, panelX + PAD, y, 0xDDDDDD);
            }
            y += LINE_H;
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
