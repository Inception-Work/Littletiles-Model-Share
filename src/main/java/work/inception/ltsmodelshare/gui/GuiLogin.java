package work.inception.ltsmodelshare.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.text.TextFormatting;
import work.inception.ltsmodelshare.api.ApiClient;
import work.inception.ltsmodelshare.api.Dtos;
import work.inception.ltsmodelshare.ModConfig;

import java.io.IOException;

/**
 * 登录界面：站点账号密码 → /api/account/login/password-login → 令牌存进 config。
 *
 * <p>登录成功后进入 {@link GuiModels}（由调用方传进来的 parent 决定）。</p>
 */
public class GuiLogin extends GuiScreen {

    private final GuiScreen after;

    private GuiTextField fieldUser;
    private GuiTextField fieldPass;
    private String status = "";
    private boolean busy;

    public GuiLogin(GuiScreen after) {
        this.after = after;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int cx = width / 2;
        int cy = height / 2;

        fieldUser = new GuiTextField(0, fontRenderer, cx - 100, cy - 55, 200, 20);
        fieldUser.setMaxStringLength(64);
        fieldUser.setFocused(true);
        fieldUser.setText(ModConfig.get().username);

        fieldPass = new GuiTextField(1, fontRenderer, cx - 100, cy - 25, 200, 20);
        fieldPass.setMaxStringLength(128);

        buttonList.add(new GuiButton(1, cx - 100, cy + 10, 96, 20, "登录"));
        buttonList.add(new GuiButton(2, cx + 4, cy + 10, 96, 20, "取消"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 2) {
            mc.displayGuiScreen(null);
            return;
        }
        if (button.id != 1 || busy) {
            return;
        }
        String user = fieldUser.getText().trim();
        String pass = fieldPass.getText();
        if (user.isEmpty() || pass.isEmpty()) {
            status = TextFormatting.RED + "用户名和密码都要填";
            return;
        }
        busy = true;
        status = TextFormatting.GRAY + "登录中…";
        ApiClient.login(user, pass, new ApiClient.Callback<Dtos.LoginPayload>() {
            @Override
            public void ok(Dtos.LoginPayload value) {
                ApiClient.post(() -> {
                    busy = false;
                    mc.displayGuiScreen(after != null ? after : new GuiModels());
                });
            }

            @Override
            public void fail(String message) {
                ApiClient.post(() -> {
                    busy = false;
                    status = TextFormatting.RED + message;
                });
            }
        });
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        super.keyTyped(typedChar, keyCode);
        fieldUser.textboxKeyTyped(typedChar, keyCode);
        fieldPass.textboxKeyTyped(typedChar, keyCode);
        if (keyCode == 28) { // Enter
            actionPerformed(buttonList.get(0));
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        fieldUser.mouseClicked(mouseX, mouseY, mouseButton);
        fieldPass.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int cx = width / 2;
        int cy = height / 2;

        drawCenteredString(fontRenderer, "Littletiles Model Share · 登录", cx, cy - 95, 0xFFFFFF);
        drawCenteredString(fontRenderer, TextFormatting.GRAY + ModConfig.get().baseUrl,
                cx, cy - 82, 0xAAAAAA);

        drawString(fontRenderer, "账号", cx - 100, cy - 68, 0xCCCCCC);
        drawString(fontRenderer, "密码", cx - 100, cy - 38, 0xCCCCCC);

        fieldUser.drawTextBox();
        // 密码框：不解字，只回显长度，避免明文
        int dots = fieldPass.getText().length();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < dots; i++) {
            sb.append('●');
        }
        fieldPass.drawTextBox();
        if (!fieldPass.getText().isEmpty()) {
            drawRect(cx - 99, cy - 24, cx + 99, cy - 6, 0xFF000000);
            drawString(fontRenderer, sb.toString(), cx - 96, cy - 20, 0xFFFFFF);
        }

        if (!status.isEmpty()) {
            drawCenteredString(fontRenderer, status, cx, cy + 40, 0xFFFFFF);
        }
        drawCenteredString(fontRenderer, TextFormatting.DARK_GRAY + "没有账号就去站点注册（游戏里暂不支持注册）",
                cx, height - 30, 0x888888);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
