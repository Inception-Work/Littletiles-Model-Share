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
        // 账号密码模式才回填上次的用户名；令牌模式要干净的空框
        // （令牌模式下 cfg.username 存的是「访问令牌」这种占位，填进去只会碍事）
        fieldUser.setText(tokenMode ? "" : ModConfig.get().username);

        fieldPass = new GuiTextField(1, fontRenderer, cx - 100, cy - 23, 200, 18);
        fieldPass.setMaxStringLength(128);

        buttonList.add(new GuiButton(1, cx - 100, cy + 4, 96, 20, "登录"));
        buttonList.add(new GuiButton(2, cx + 4, cy + 4, 96, 20, "取消"));
        buttonList.add(new GuiButton(3, cx - 100, cy + 30, 200, 20, "使用访问令牌"));
        buttonList.add(new GuiButton(4, cx - 100, cy + 56, 200, 20, "没有账号？去注册 →"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 2) {
            mc.displayGuiScreen(null);
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
                    status = TextFormatting.YELLOW + "打不开浏览器，注册地址已复制：" + url;
                } catch (Throwable t) {
                    status = TextFormatting.RED + "注册地址：" + url;
                }
            } else {
                status = TextFormatting.GRAY + "已在浏览器打开：" + url;
            }
            return;
        }
        if (button.id == 3) {
            // 令牌模式：直接在输入框里粘贴 lts_ 开头的令牌，回车即登录
            tokenMode = !tokenMode;
            buttonList.get(2).displayString = tokenMode
                    ? "返回账号密码登录" : "使用访问令牌";
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
                status = TextFormatting.RED + "这串文本不像访问令牌，请回到站点整串复制";
                return;
            }
            ModConfig cfg = ModConfig.get();
            cfg.token = pat;
            cfg.refreshToken = "";
            cfg.username = "访问令牌";
            cfg.save();
            status = TextFormatting.GREEN + "已保存访问令牌，过期时间由站点控制";
            mc.displayGuiScreen(after != null ? after : new GuiModels());
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

        drawCenteredString(fontRenderer, "Littletiles Model Share · 登录", cx, cy - 95, 0xFFFFFF);
        drawCenteredString(fontRenderer, TextFormatting.GRAY + ModConfig.get().baseUrl,
                cx, cy - 82, 0xAAAAAA);

        drawString(fontRenderer, tokenMode ? "访问令牌" : "账号", cx - 100, cy - 73, 0xCCCCCC);
        if (!tokenMode) {
            drawString(fontRenderer, "密码", cx - 100, cy - 34, 0xCCCCCC);
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
