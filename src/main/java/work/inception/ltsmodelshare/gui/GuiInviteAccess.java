package work.inception.ltsmodelshare.gui;

import static work.inception.ltsmodelshare.client.Translations.tr;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import work.inception.ltsmodelshare.api.ApiClient;
import work.inception.ltsmodelshare.api.Dtos;
import work.inception.ltsmodelshare.client.TextUtil;

/** Invite access: open an unlisted work with its work id and access code. */
public class GuiInviteAccess extends Screen {
    private final Screen parent;
    private EditBox idField;
    private EditBox codeField;
    private String status = "";
    private boolean busy;

    public GuiInviteAccess(Screen parent) {
        super(Component.translatable("ltsmodelshare.invite.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        clearWidgets();
        int fieldW = Math.min(220, width - 40);
        int cx = width / 2;
        int cy = height / 2;
        idField = new EditBox(font, cx - fieldW / 2, cy - 30, fieldW, 20,
                Component.translatable("ltsmodelshare.invite.id"));
        idField.setMaxLength(20);
        addRenderableWidget(idField);
        codeField = new EditBox(font, cx - fieldW / 2, cy + 4, fieldW, 20,
                Component.translatable("ltsmodelshare.invite.code"));
        codeField.setMaxLength(64);
        addRenderableWidget(codeField);
        addRenderableWidget(GuiNavigation.button(cx - fieldW / 2, cy + 34, fieldW, 20,
                "ltsmodelshare.invite.open", this::open));
        addRenderableWidget(GuiNavigation.button(GuiNavigation.backX(width), GuiNavigation.footerY(height),
                GuiNavigation.BACK_WIDTH, 20, "ltsmodelshare.common.back", () -> minecraft.setScreen(parent)));
    }

    private void open() {
        if (busy) return;
        String text = idField.getValue().trim();
        if (text.isEmpty()) {
            status = tr("ltsmodelshare.invite.id_required");
            return;
        }
        long workId;
        try {
            workId = Long.parseLong(text);
        } catch (NumberFormatException error) {
            status = tr("ltsmodelshare.invite.id_number");
            return;
        }
        busy = true;
        status = tr("ltsmodelshare.invite.opening");
        ApiClient.detailCard(workId, codeField.getValue().trim(), new ApiClient.Callback<Dtos.WorkCard>() {
            @Override public void ok(Dtos.WorkCard value) {
                ApiClient.post(() -> {
                    busy = false;
                    minecraft.setScreen(new GuiWorkDetail(value, codeField.getValue().trim(), GuiInviteAccess.this));
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
        int fieldW = Math.min(220, width - 40);
        graphics.drawCenteredString(font, tr("ltsmodelshare.invite.title"), cx, cy - 80, 0xFFFFFF);
        graphics.drawCenteredString(font, tr("ltsmodelshare.invite.help"), cx, cy - 66, 0xAAAAAA);
        graphics.drawString(font, tr("ltsmodelshare.invite.id"), cx - fieldW / 2, cy - 42, 0xCCCCCC, false);
        graphics.drawString(font, tr("ltsmodelshare.invite.code"), cx - fieldW / 2, cy - 8, 0xCCCCCC, false);
        if (!status.isEmpty()) {
            graphics.drawCenteredString(font, TextUtil.trim(font, status, width - 24), cx, cy + 64, 0xFFFFFF);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257) {
            open();
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
