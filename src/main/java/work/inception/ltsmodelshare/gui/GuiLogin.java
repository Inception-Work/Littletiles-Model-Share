package work.inception.ltsmodelshare.gui;

import static work.inception.ltsmodelshare.client.Translations.tr;

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
    /** true = 正在用「访问令牌」登录（这时输入框里是 lts_… 而不是用户名） */
    private boolean tokenMode;
    private boolean busy;

    public GuiLogin(GuiScreen after) {
        this.after = after;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int cx = width / 2;
        int cy = height / 2;

        /*
         * 间距按 MC 常见节奏来：标签 10px + 输入框 18px + 间隔 12px。
         * 之前账号框 cy-55、密码框 cy-25、"密码"标签画在 cy-38 —— 正好落在账号框里，
         * 看起来像"密码被账号输入框盖住"。
         */
        fieldUser = new GuiTextField(0, fontRenderer, cx - 100, cy - 62, 200, 18);
        fieldUser.setMaxStringLength(64);
        fieldUser.setFocused(true);
        // 不预填用户名：每次打开都是空框。
        // （之前会回填 config 里上次登录的用户名，看起来像"总是默认填了 xxx"，
        //  而且令牌模式下那个值还是「访问令牌」占位，容易误导。）
        fieldUser.setText("");

        fieldPass = new GuiTextField(1, fontRenderer, cx - 100, cy - 23, 200, 18);
        fieldPass.setMaxStringLength(128);

        buttonList.add(new LocalizedButton(1, cx - 100, cy + 4, 200, 20, tr("ltsmodelshare.common.login")));
        buttonList.add(GuiNavigation.backButton(2, width, height, tr("ltsmodelshare.common.back")));
        buttonList.add(new LocalizedButton(3, cx - 100, cy + 30, 200, 20, tr("ltsmodelshare.login.token_mode")));
        buttonList.add(new LocalizedButton(4, cx - 100, cy + 56, 200, 20, tr("ltsmodelshare.login.register")));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 2) {
            mc.displayGuiScreen(after);
            return;
        }
        if (button.id == 4) {
            // 打开注册页；打不开浏览器就把地址复制到剪贴板并提示
            String url = ModConfig.get().url("/register");
            boolean opened = false;
            try {
                if (java.awt.Desktop.isDesktopSupported()
                        && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                    java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
                    opened = true;
                }
            } catch (Throwable ignored) {
                // 落到下面的复制逻辑
            }
            if (!opened) {
                try {
                    java.awt.datatransfer.StringSelection sel = new java.awt.datatransfer.StringSelection(url);
                    java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, sel);
                    status = TextFormatting.YELLOW + tr("ltsmodelshare.login.register_copied", url);
                } catch (Throwable t) {
                    status = TextFormatting.RED + tr("ltsmodelshare.login.register_url", url);
                }
            } else {
                status = TextFormatting.GRAY + tr("ltsmodelshare.login.browser_open", url);
            }
            return;
        }
        if (button.id == 3) {
            // 令牌模式：直接在输入框里粘贴 lts_ 开头的令牌，回车即登录
            tokenMode = !tokenMode;
            buttonList.get(2).displayString = tokenMode
                    ? tr("ltsmodelshare.login.password_mode") : tr("ltsmodelshare.login.token_mode");
            // 切换模式时清空输入框：免得把账号名当令牌提交，或者令牌残留在账号框里
            fieldUser.setText("");
            fieldPass.setText("");
            status = "";
            fieldUser.setFocused(true);
            return;
        }
        if (button.id != 1 || busy) {
            return;
        }
        if (tokenMode) {
            String pat = fieldUser.getText().trim();
            if (!pat.startsWith("lts_")) {
                status = TextFormatting.RED + tr("ltsmodelshare.login.token_invalid");
                return;
            }
            ModConfig cfg = ModConfig.get();
            cfg.token = pat;
            cfg.refreshToken = "";
            // 不再往 username 里写「访问令牌」这种占位：以前它会回填到账号框里，
            // 看起来就像"输入框里还留着访问令牌"
            cfg.username = "";
            cfg.save();
            status = TextFormatting.GREEN + tr("ltsmodelshare.login.token_saved");
            mc.displayGuiScreen(after != null ? after : new GuiModels());
            return;
        }
        String user = fieldUser.getText().trim();
        String pass = fieldPass.getText();
        if (user.isEmpty() || pass.isEmpty()) {
            status = TextFormatting.RED + tr("ltsmodelshare.login.fields_required");
            return;
        }
        busy = true;
        status = TextFormatting.GRAY + tr("ltsmodelshare.login.busy");
        ApiClient.login(user, pass, new ApiClient.Callback<Dtos.LoginPayload>() {
            @Override
            public void ok(Dtos.LoginPayload value) {
                ApiClient.post(() -> {
                    busy = false;
                    if (mc.currentScreen != GuiLogin.this) return;
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
        if (keyCode == 1) {
            mc.displayGuiScreen(after);
            return;
        }
        super.keyTyped(typedChar, keyCode);
        fieldUser.textboxKeyTyped(typedChar, keyCode);
        if (!tokenMode) {
            fieldPass.textboxKeyTyped(typedChar, keyCode);
        }
        if (keyCode == 28) { // Enter
            actionPerformed(buttonList.get(0));
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        fieldUser.mouseClicked(mouseX, mouseY, mouseButton);
        if (!tokenMode) {
            fieldPass.mouseClicked(mouseX, mouseY, mouseButton);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int cx = width / 2;
        int cy = height / 2;

        drawCenteredString(fontRenderer, tr("ltsmodelshare.login.title"), cx, cy - 95, 0xFFFFFF);
        drawCenteredString(fontRenderer, TextFormatting.GRAY + ModConfig.get().baseUrl,
                cx, cy - 82, 0xAAAAAA);

        drawString(fontRenderer, tokenMode ? tr("ltsmodelshare.login.token") : tr("ltsmodelshare.login.account"), cx - 100, cy - 73, 0xCCCCCC);
        if (!tokenMode) {
            drawString(fontRenderer, tr("ltsmodelshare.login.password"), cx - 100, cy - 34, 0xCCCCCC);
        }

        fieldUser.drawTextBox();
        // 令牌模式只有一个输入框，密码框整个不画
        if (!tokenMode) {
            // 密码框：不解字，只回显长度，避免明文
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < fieldPass.getText().length(); i++) {
                sb.append('●');
            }
            fieldPass.drawTextBox();
            if (!fieldPass.getText().isEmpty()) {
                drawRect(cx - 99, cy - 15, cx + 99, cy + 3, 0xFF000000);
                drawString(fontRenderer, sb.toString(), cx - 96, cy - 11, 0xFFFFFF);
            }
        }

        if (!status.isEmpty()) {
            drawCenteredString(fontRenderer, status, cx, cy + 40, 0xFFFFFF);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
