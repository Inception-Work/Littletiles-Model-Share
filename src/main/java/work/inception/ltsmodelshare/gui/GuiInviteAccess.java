package work.inception.ltsmodelshare.gui;

import static work.inception.ltsmodelshare.client.Translations.tr;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import work.inception.ltsmodelshare.api.ApiClient;
import work.inception.ltsmodelshare.api.Dtos;
import work.inception.ltsmodelshare.client.TextUtil;

/** The 1.12.2 invitation form and access-code behavior, using modern widgets. */
public class GuiInviteAccess extends Screen {

    @Override
    public void renderBackground(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // No-op: Screen.render calls this *after* content; painting here dims the UI.
    }
    private final Screen parent;
    private EditBox idField;
    private EditBox codeField;
    private FitButton openButton;
    private String status = "";
    private boolean busy;

    public GuiInviteAccess(Screen parent) {
        super(Component.translatable("ltsmodelshare.invite.title"));
        this.parent = parent;
    }

    private int fieldWidth() { return Math.min(200, width - 40); }

    @Override protected void init() {
        EditBox oldId = idField;
        EditBox oldCode = codeField;
        boolean focusCode = oldCode != null && oldCode.isFocused();
        clearWidgets();
        int cx = width / 2, cy = height / 2, fw = fieldWidth();
        Component idLabel = Component.translatable("ltsmodelshare.invite.id");
        Component codeLabel = Component.translatable("ltsmodelshare.invite.code");
        idField = oldId == null ? new EditBox(font, cx - fw / 2, cy - 62, fw, 18, idLabel)
                : new EditBox(font, cx - fw / 2, cy - 62, fw, 18, oldId, idLabel);
        idField.setMaxLength(18);
        // Filtering the replacement text also handles clipboard paste, not just key presses.
        idField.setResponder(value -> {
            String digits = value.replaceAll("[^0-9]", "");
            if (!digits.equals(value)) idField.setValue(digits);
        });
        codeField = oldCode == null ? new EditBox(font, cx - fw / 2, cy - 23, fw, 18, codeLabel)
                : new EditBox(font, cx - fw / 2, cy - 23, fw, 18, oldCode, codeLabel);
        codeField.setMaxLength(64);
        addRenderableWidget(idField);
        addRenderableWidget(codeField);
        openButton = addRenderableWidget(GuiNavigation.button(cx - fw / 2, cy + 4, fw, 20,
                "ltsmodelshare.invite.open", this::open));
        openButton.active = !busy;
        addRenderableWidget(GuiNavigation.button(GuiNavigation.backX(width), GuiNavigation.footerY(height),
                GuiNavigation.BACK_WIDTH, 20, "ltsmodelshare.common.back", this::onClose));
        setInitialFocus(focusCode ? codeField : idField);
    }

    private void open() {
        if (busy) return;
        String text = idField.getValue().trim();
        if (text.isEmpty()) { status = ChatFormatting.RED + tr("ltsmodelshare.invite.id_required"); return; }
        long workId;
        try { workId = Long.parseLong(text); }
        catch (NumberFormatException error) { status = ChatFormatting.RED + tr("ltsmodelshare.invite.id_number"); return; }
        if (workId <= 0) { status = ChatFormatting.RED + tr("ltsmodelshare.invite.id_number"); return; }
        final String code = codeField.getValue().trim();
        busy = true;
        openButton.active = false;
        status = ChatFormatting.GRAY + tr("ltsmodelshare.invite.opening");
        ApiClient.detailCard(workId, code, new ApiClient.Callback<Dtos.WorkCard>() {
            @Override public void ok(Dtos.WorkCard value) {
                ApiClient.post(() -> {
                    busy = false;
                    openButton.active = true;
                    if (minecraft.screen == GuiInviteAccess.this) {
                        status = "";
                        minecraft.setScreen(new GuiWorkDetail(value, code, GuiInviteAccess.this));
                    }
                });
            }
            @Override public void fail(String message) {
                ApiClient.post(() -> {
                    busy = false;
                    openButton.active = true;
                    status = ChatFormatting.RED + friendly(message);
                });
            }
        });
    }

    private String friendly(String message) {
        if (message == null || message.isBlank()) return tr("ltsmodelshare.invite.failed");
        if (message.contains("M05-030")) return tr("ltsmodelshare.invite.code_required");
        if (message.contains("M05-031")) return tr("ltsmodelshare.invite.code_wrong");
        if (message.contains("M05-064")) return tr("ltsmodelshare.invite.expired");
        return message;
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xE0101010);
        int cx = width / 2, cy = height / 2;
        graphics.drawCenteredString(font, title, cx, cy - 95, 0xFFFFFF);
        graphics.drawCenteredString(font, tr("ltsmodelshare.invite.help"), cx, cy - 82, 0xAAAAAA);
        graphics.drawString(font, tr("ltsmodelshare.invite.id"), cx - fieldWidth() / 2, cy - 73, 0xCCCCCC, true);
        graphics.drawString(font, tr("ltsmodelshare.invite.code"), cx - fieldWidth() / 2, cy - 34, 0xCCCCCC, true);
        if (!status.isEmpty()) graphics.drawCenteredString(font,
                TextUtil.trim(font, status, width - 24), cx, cy + 34, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) { open(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    @Override public void tick() { /* EditBox.tick() removed in 1.20.2. */ }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
