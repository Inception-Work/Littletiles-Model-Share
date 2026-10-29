package work.inception.ltsmodelshare.gui;

import static work.inception.ltsmodelshare.client.Translations.tr;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.text.TextFormatting;
import work.inception.ltsmodelshare.api.ApiClient;
import work.inception.ltsmodelshare.api.Dtos;

import java.io.IOException;

/**
 * 凭邀请访问作品：作者把「作品链接 / 作品 ID / 访问码」发给别人，
 * 对方在这里填 ID + 访问码就能进详情页（unlisted 作品不进列表，只能在详情页看）。
 *
 * <p>流程和 {@link GuiLogin} 一样：两个输入框 + 两颗按钮 + 一行状态。
 * 访问成功后直接开 {@link GuiWorkDetail}，并把访问码带过去 ——
 * 详情页刷新、取蓝图都要用它（后端 unlisted 的详情 / 下载都校验码）。</p>
 */
public class GuiInviteAccess extends GuiScreen {

    private final GuiScreen parent;

    /** 作品 ID（只允许数字） */
    private GuiTextField fieldWorkId;
    /** 访问码（可空：作者没设码的 unlisted 作品 / 公开作品都不填也能进） */
    private GuiTextField fieldCode;
    private String status = "";
    private boolean busy;

    public GuiInviteAccess(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        String workId = fieldWorkId == null ? "" : fieldWorkId.getText();
        String code = fieldCode == null ? "" : fieldCode.getText();
        buttonList.clear();
        int cx = width / 2;
        int cy = height / 2;

        // 与登录页同一套节奏：标签 10px + 输入框 18px + 间隔 12px
        fieldWorkId = new GuiTextField(0, fontRenderer, cx - 100, cy - 62, 200, 18);
        fieldWorkId.setMaxStringLength(18);
        fieldWorkId.setText(workId);
        fieldWorkId.setFocused(true);

        fieldCode = new GuiTextField(1, fontRenderer, cx - 100, cy - 23, 200, 18);
        fieldCode.setMaxStringLength(64);
        fieldCode.setText(code);

        buttonList.add(new LocalizedButton(1, cx - 100, cy + 4, 200, 20, tr("ltsmodelshare.invite.open")));
        buttonList.add(GuiNavigation.backButton(2, width, height, tr("ltsmodelshare.common.back")));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 2) {
            mc.displayGuiScreen(parent);
            return;
        }
        if (button.id != 1 || busy) {
            return;
        }
        submit();
    }

    private void submit() {
        String raw = fieldWorkId.getText().trim();
        if (raw.isEmpty()) {
            status = TextFormatting.RED + tr("ltsmodelshare.invite.id_required");
            return;
        }
        long workId;
        try {
            workId = Long.parseLong(raw);
        } catch (NumberFormatException e) {
            status = TextFormatting.RED + tr("ltsmodelshare.invite.id_number");
            return;
        }
        if (workId <= 0) {
            status = TextFormatting.RED + tr("ltsmodelshare.invite.id_number");
            return;
        }

        final String code = fieldCode.getText().trim();
        busy = true;
        status = TextFormatting.GRAY + tr("ltsmodelshare.invite.opening");
        ApiClient.detailCard(workId, code, new ApiClient.Callback<Dtos.WorkCard>() {
            @Override
            public void ok(final Dtos.WorkCard card) {
                ApiClient.post(() -> {
                    busy = false;
                    if (mc.currentScreen != GuiInviteAccess.this) return;
                    // 详情页还会带着访问码再拉一次详情：权限对错以服务端那次为准
                    mc.displayGuiScreen(new GuiWorkDetail(card, code, GuiInviteAccess.this));
                });
            }

            @Override
            public void fail(final String message) {
                ApiClient.post(() -> {
                    busy = false;
                    status = TextFormatting.RED + friendly(message);
                });
            }
        });
    }

    /** 访问码错误与旧邀请失效的服务端错误码翻成人话。 */
    private String friendly(String message) {
        if (message == null || message.trim().isEmpty()) {
            return tr("ltsmodelshare.invite.failed");
        }
        if (message.contains("M05-030")) {
            return tr("ltsmodelshare.invite.code_required");
        }
        if (message.contains("M05-031")) {
            return tr("ltsmodelshare.invite.code_wrong");
        }
        if (message.contains("M05-064")) {
            return tr("ltsmodelshare.invite.expired");
        }
        return message;
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        // ESC：回上一级（与登录页一致）
        if (keyCode == 1) {
            mc.displayGuiScreen(parent);
            return;
        }
        super.keyTyped(typedChar, keyCode);
        if (fieldWorkId.isFocused()) {
            fieldWorkId.textboxKeyTyped(typedChar, keyCode);
            // 只允许数字：粘进来的字母 / 空格直接剔掉，免得提交才发现不合法
            String text = fieldWorkId.getText();
            StringBuilder digits = new StringBuilder();
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c >= '0' && c <= '9') {
                    digits.append(c);
                }
            }
            if (digits.length() != text.length()) {
                fieldWorkId.setText(digits.toString());
                fieldWorkId.setCursorPositionEnd();
            }
        }
        if (fieldCode.isFocused()) {
            fieldCode.textboxKeyTyped(typedChar, keyCode);
        }
        if (keyCode == 28) { // Enter = 访问
            actionPerformed(buttonList.get(0));
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        fieldWorkId.mouseClicked(mouseX, mouseY, mouseButton);
        fieldCode.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int cx = width / 2;
        int cy = height / 2;

        drawCenteredString(fontRenderer, tr("ltsmodelshare.invite.title"), cx, cy - 95, 0xFFFFFF);
        drawCenteredString(fontRenderer, TextFormatting.GRAY + tr("ltsmodelshare.invite.help"),
                cx, cy - 82, 0xAAAAAA);

        drawString(fontRenderer, tr("ltsmodelshare.invite.id"), cx - 100, cy - 73, 0xCCCCCC);
        drawString(fontRenderer, tr("ltsmodelshare.invite.code"), cx - 100, cy - 34, 0xCCCCCC);

        fieldWorkId.drawTextBox();
        fieldCode.drawTextBox();

        if (!status.isEmpty()) {
            drawCenteredString(fontRenderer, status, cx, cy + 34, 0xFFFFFF);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
