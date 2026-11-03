package work.inception.ltsmodelshare.gui;

import static work.inception.ltsmodelshare.client.Translations.tr;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import work.inception.ltsmodelshare.client.TextUtil;

/** Small independent popup with the 1.12.2 GuiNotice chrome and parent-return semantics. */
public class GuiMessage extends Screen {

    @Override
    public void renderBackground(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // No-op: Screen.render calls this *after* content; painting here dims the UI.
    }
    private static final int PAD = 10;
    private static final int TITLE_H = 16;
    private static final int LINE_H = 10;

    public static final class Button {
        final String labelKey;
        final Runnable action;
        public Button(String labelKey, Runnable action) { this.labelKey = labelKey; this.action = action; }
    }

    private final Screen parent;
    private final String message;
    private final List<Button> buttons = new ArrayList<>();
    private List<FormattedCharSequence> lines = List.of();
    private int panelX, panelY, panelW, panelH, visibleLines, lineScroll;

    public GuiMessage(Screen parent, String titleKey, String message, List<Button> buttons) {
        super(Component.translatable(titleKey));
        this.parent = parent;
        this.message = message == null ? "" : message;
        if (buttons != null) this.buttons.addAll(buttons);
        if (this.buttons.isEmpty()) this.buttons.add(new Button("ltsmodelshare.common.ok", null));
    }

    public static GuiMessage info(Screen parent, String titleKey, String message) {
        return new GuiMessage(parent, titleKey, message, List.of());
    }

    @Override protected void init() {
        clearWidgets();
        panelW = Math.max(140, Math.min(240, width - 80));
        lines = font.split(Component.literal(message), panelW - PAD * 2);
        // Preserve the reference content-sized popup; only huge error messages need a bounded body.
        int maxLines = Math.max(1, (height - 16 - (TITLE_H + PAD * 3 + 20)) / LINE_H);
        visibleLines = Math.min(lines.size(), maxLines);
        lineScroll = Math.max(0, Math.min(lineScroll, Math.max(0, lines.size() - visibleLines)));
        panelH = TITLE_H + PAD + visibleLines * LINE_H + PAD + 20 + PAD;
        panelX = (width - panelW) / 2;
        panelY = Math.max(8, (height - panelH) / 2);
        int gap = 6;
        int buttonW = Math.min(90, (panelW - PAD * 2 - (buttons.size() - 1) * gap) / buttons.size());
        int totalW = buttons.size() * buttonW + (buttons.size() - 1) * gap;
        int x = panelX + (panelW - totalW) / 2;
        for (Button button : buttons) {
            addRenderableWidget(GuiNavigation.button(x, panelY + panelH - PAD - 20, buttonW, 20,
                    button.labelKey, () -> {
                        if (button.action != null) button.action.run();
                        else onClose();
                    }));
            x += buttonW + gap;
        }
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xE0101010);
        graphics.fill(panelX - 2, panelY - 2, panelX + panelW + 2, panelY + panelH + 2, 0xFF000000);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xFF2B2B2B);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + 2, 0xFF8A8A8A);
        graphics.fill(panelX, panelY + panelH - 2, panelX + panelW, panelY + panelH, 0xFF8A8A8A);
        graphics.fill(panelX, panelY, panelX + 2, panelY + panelH, 0xFF8A8A8A);
        graphics.fill(panelX + panelW - 2, panelY, panelX + panelW, panelY + panelH, 0xFF8A8A8A);
        graphics.fill(panelX + 2, panelY + 2, panelX + panelW - 2, panelY + TITLE_H, 0xFF3A3A3A);
        graphics.fill(panelX + 2, panelY + TITLE_H, panelX + panelW - 2, panelY + TITLE_H + 1, 0xFF555555);
        TextUtil.drawCenteredFit(graphics, font, title.getString(), panelX + panelW / 2,
                panelY + (TITLE_H - 8) / 2, panelW - 12, 0xFFFF55);
        int y = panelY + TITLE_H + PAD;
        graphics.enableScissor(panelX + PAD, y, panelX + panelW - PAD, y + visibleLines * LINE_H);
        try {
            for (int i = lineScroll; i < Math.min(lines.size(), lineScroll + visibleLines); i++) {
                graphics.drawString(font, lines.get(i), panelX + PAD, y + (i - lineScroll) * LINE_H, 0xDDDDDD, true);
            }
        } finally { graphics.disableScissor(); }
        if (lines.size() > visibleLines) {
            int barHeight = visibleLines * LINE_H;
            int thumbHeight = Math.max(6, barHeight * visibleLines / lines.size());
            int thumbY = y + (barHeight - thumbHeight) * lineScroll / (lines.size() - visibleLines);
            graphics.fill(panelX + panelW - 6, y, panelX + panelW - 4, y + barHeight, 0x30FFFFFF);
            graphics.fill(panelX + panelW - 6, thumbY, panelX + panelW - 4, thumbY + thumbHeight, 0x90FFFFFF);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double delta) {
        if (mouseX >= panelX && mouseX < panelX + panelW && mouseY >= panelY && mouseY < panelY + panelH) {
            lineScroll = Math.max(0, Math.min(Math.max(0, lines.size() - visibleLines), lineScroll - (int) Math.signum(delta)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, delta);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
