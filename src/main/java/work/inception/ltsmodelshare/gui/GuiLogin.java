package work.inception.ltsmodelshare.gui;

import static work.inception.ltsmodelshare.client.Translations.tr;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import work.inception.ltsmodelshare.ModConfig;
import work.inception.ltsmodelshare.api.ApiClient;
import work.inception.ltsmodelshare.api.Dtos;
import work.inception.ltsmodelshare.client.TextUtil;

/** The reference account/password or personal-access-token login form. */
public class GuiLogin extends Screen {

    @Override
    public void renderBackground(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // No-op: Screen.render calls this *after* content; painting here dims the UI.
    }
    private final Screen parent;
    private EditBox user;
    private EditBox pass;
    private FitButton modeButton;
    private boolean tokenMode;
    private boolean busy;
    private String status = "";

    public GuiLogin(Screen parent) {
        super(Component.translatable("ltsmodelshare.login.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        String name = user == null ? "" : user.getValue();
        String password = pass == null ? "" : pass.getValue();
        boolean passwordFocused = pass != null && pass.isFocused();
        clearWidgets();
        int cx = width / 2;
        int cy = height / 2;
        user = new EditBox(font, cx - 100, cy - 62, 200, 18,
                Component.translatable(tokenMode ? "ltsmodelshare.login.token" : "ltsmodelshare.login.account"));
        user.setMaxLength(64);
        // Never prefill the stored username or a token placeholder.
        user.setValue(name);
        addRenderableWidget(user);
        pass = new EditBox(font, cx - 100, cy - 23, 200, 18,
                Component.translatable("ltsmodelshare.login.password"));
        pass.setMaxLength(128);
        pass.setValue(password);
        pass.setFormatter((text, cursor) -> FormattedCharSequence.forward(
                "*".repeat(text.length()), net.minecraft.network.chat.Style.EMPTY));
        pass.visible = !tokenMode;
        pass.active = !tokenMode;
        addRenderableWidget(pass);
        setInitialFocus(passwordFocused && !tokenMode ? pass : user);
        addRenderableWidget(GuiNavigation.button(cx - 100, cy + 4, 200, 20,
                "ltsmodelshare.common.login", this::submit));
        addRenderableWidget(GuiNavigation.button(GuiNavigation.backX(width), GuiNavigation.footerY(height),
                GuiNavigation.BACK_WIDTH, 20, "ltsmodelshare.common.back", this::onClose));
        modeButton = addRenderableWidget(GuiNavigation.button(cx - 100, cy + 30, 200, 20,
                tokenMode ? "ltsmodelshare.login.password_mode" : "ltsmodelshare.login.token_mode", this::switchMode));
        modeButton.active = !busy;
        addRenderableWidget(GuiNavigation.button(cx - 100, cy + 56, 200, 20,
                "ltsmodelshare.login.register", this::openRegister));
    }

    private void switchMode() {
        if (busy) return;
        tokenMode = !tokenMode;
        user.setValue("");
        pass.setValue("");
        status = "";
        init();
        setInitialFocus(user);
    }

    private void openRegister() {
        String url = ModConfig.get().url("/register");
        try {
            if (java.awt.Desktop.isDesktopSupported()
                    && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
                status = ChatFormatting.GRAY + tr("ltsmodelshare.login.browser_open", url);
                return;
            }
        } catch (Throwable ignored) { }
        try {
            minecraft.keyboardHandler.setClipboard(url);
            status = ChatFormatting.YELLOW + tr("ltsmodelshare.login.register_copied", url);
        } catch (Throwable error) {
            status = ChatFormatting.RED + tr("ltsmodelshare.login.register_url", url);
        }
    }

    private void submit() {
        if (busy) return;
        String name = user.getValue().trim();
        if (tokenMode) {
            if (!name.startsWith("lts_")) {
                status = ChatFormatting.RED + tr("ltsmodelshare.login.token_invalid");
                return;
            }
            // ApiClient already sends cfg.token as Bearer and supports PAT /account/info.
            // This is the legacy local token attach flow, not a new login endpoint.
            ModConfig cfg = ModConfig.get();
            cfg.token = name;
            cfg.refreshToken = "";
            cfg.username = "";
            cfg.save();
            minecraft.setScreen(parent != null ? parent : new GuiModels());
            return;
        }
        String password = pass.getValue();
        if (name.isEmpty() || password.isEmpty()) {
            status = ChatFormatting.RED + tr("ltsmodelshare.login.fields_required");
            return;
        }
        busy = true;
        modeButton.active = false;
        status = ChatFormatting.GRAY + tr("ltsmodelshare.login.busy");
        ApiClient.login(name, password, new ApiClient.Callback<Dtos.LoginPayload>() {
            @Override public void ok(Dtos.LoginPayload value) {
                ApiClient.post(() -> {
                    busy = false;
                    modeButton.active = true;
                    if (minecraft.screen != GuiLogin.this) return;
                    minecraft.setScreen(parent != null ? parent : new GuiModels());
                });
            }
            @Override public void fail(String message) {
                ApiClient.post(() -> {
                    busy = false;
                    modeButton.active = true;
                    status = ChatFormatting.RED + message;
                });
            }
        });
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xE0101010);
        int cx = width / 2;
        int cy = height / 2;
        graphics.drawCenteredString(font, title, cx, cy - 95, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.literal(ModConfig.get().baseUrl), cx, cy - 82, 0xAAAAAA);
        graphics.drawString(font, tr(tokenMode ? "ltsmodelshare.login.token" : "ltsmodelshare.login.account"),
                cx - 100, cy - 73, 0xCCCCCC, true);
        if (!tokenMode)
            graphics.drawString(font, tr("ltsmodelshare.login.password"), cx - 100, cy - 34, 0xCCCCCC, true);
        // Below the form rather than behind the token-mode button as in the old screen.
        if (!status.isEmpty())
            graphics.drawCenteredString(font, TextUtil.trim(font, status, width - 24), cx, cy + 84, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) { submit(); return true; }
        if (keyCode == 256) { onClose(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
