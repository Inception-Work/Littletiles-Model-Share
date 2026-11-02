package work.inception.ltsmodelshare.gui;

import static work.inception.ltsmodelshare.client.Translations.tr;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import work.inception.ltsmodelshare.ModConfig;
import work.inception.ltsmodelshare.api.ApiClient;
import work.inception.ltsmodelshare.api.Dtos;
import work.inception.ltsmodelshare.client.TextUtil;

/** Account login (username/password). */
public class GuiLogin extends Screen {
    private final Screen parent;
    private EditBox user;
    private EditBox pass;
    private boolean busy;
    private String status = "";

    public GuiLogin(Screen parent) {
        super(Component.translatable("ltsmodelshare.login.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        clearWidgets();
        int cx = width / 2;
        int cy = height / 2;
        int fieldW = Math.min(220, width - 40);

        user = new EditBox(font, cx - fieldW / 2, cy - 40, fieldW, 20,
                Component.translatable("ltsmodelshare.login.account"));
        user.setMaxLength(64);
        user.setValue(ModConfig.get().username == null ? "" : ModConfig.get().username);
        addRenderableWidget(user);

        pass = new EditBox(font, cx - fieldW / 2, cy - 8, fieldW, 20,
                Component.translatable("ltsmodelshare.login.password"));
        pass.setMaxLength(128);
        pass.setFormatter((text, cursor) -> FormattedCharSequence.forward(
                "*".repeat(text.length()), net.minecraft.network.chat.Style.EMPTY));
        addRenderableWidget(pass);

        addRenderableWidget(GuiNavigation.button(cx - fieldW / 2, cy + 24, fieldW, 20,
                "ltsmodelshare.common.login", this::submit));
        addRenderableWidget(new FitButton(cx - fieldW / 2, cy + 48, fieldW, 20,
                Component.translatable("ltsmodelshare.common.cancel"), b -> minecraft.setScreen(parent)));
        user.setFocused(true);
    }

    private void submit() {
        if (busy) return;
        String name = user.getValue().trim();
        String password = pass.getValue();
        if (name.isEmpty() || password.isEmpty()) {
            status = tr("ltsmodelshare.login.fields_required");
            return;
        }
        busy = true;
        status = tr("ltsmodelshare.login.busy");
        ApiClient.login(name, password, new ApiClient.Callback<Dtos.LoginPayload>() {
            @Override public void ok(Dtos.LoginPayload value) {
                ApiClient.post(() -> {
                    busy = false;
                    minecraft.setScreen(parent);
                });
            }
            @Override public void fail(String message) {
                ApiClient.post(() -> {
                    busy = false;
                    status = message;
                });
            }
        });
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int cx = width / 2;
        int cy = height / 2;
        graphics.drawCenteredString(font, title, cx, cy - 95, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.literal(ModConfig.get().baseUrl), cx, cy - 82, 0xAAAAAA);
        graphics.drawString(font, tr("ltsmodelshare.login.account"), cx - Math.min(220, width - 40) / 2,
                cy - 52, 0xCCCCCC, false);
        graphics.drawString(font, tr("ltsmodelshare.login.password"), cx - Math.min(220, width - 40) / 2,
                cy - 20, 0xCCCCCC, false);
        if (!status.isEmpty()) {
            graphics.drawCenteredString(font, TextUtil.trim(font, status, width - 24), cx, cy + 78, 0xFF8080);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257) {
            submit();
            return true;
        }
        if (keyCode == 256) {
            minecraft.setScreen(parent);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
