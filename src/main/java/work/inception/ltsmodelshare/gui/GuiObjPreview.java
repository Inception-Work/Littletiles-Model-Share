package work.inception.ltsmodelshare.gui;

import static work.inception.ltsmodelshare.client.Translations.tr;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** In-game white-model preview for a site-generated OBJ. */
public class GuiObjPreview extends Screen {
    private final Screen parent;
    private final ObjModel model;
    private final String workTitle;
    private float yaw = 35f;
    private float pitch = 24f;
    private float zoom = 1f;
    private boolean dragging;
    private double lastX;
    private double lastY;

    GuiObjPreview(Screen parent, ObjModel model, String title) {
        super(Component.translatable("ltsmodelshare.preview.title"));
        this.parent = parent;
        this.model = model;
        this.workTitle = title == null ? "" : title;
    }

    @Override
    protected void init() {
        clearWidgets();
        addRenderableWidget(GuiNavigation.button(GuiNavigation.backX(width), GuiNavigation.footerY(height),
                GuiNavigation.BACK_WIDTH, 20, "ltsmodelshare.common.back", () -> minecraft.setScreen(parent)));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int cx = width / 2;
        int cy = height / 2 - 8;
        graphics.drawCenteredString(font, workTitle, cx, 10, 0xFFFFFF);
        graphics.drawCenteredString(font, tr("ltsmodelshare.preview.obj_mode"), cx, 22, 0xFFCC88);

        PoseStack pose = graphics.pose();
        pose.pushPose();
        try {
            pose.translate(cx, cy, 300);
            pose.scale(120f * zoom, -120f * zoom, 120f * zoom);
            pose.mulPose(Axis.XP.rotationDegrees(pitch));
            pose.mulPose(Axis.YP.rotationDegrees(yaw));
            model.render(pose);
        } finally {
            pose.popPose();
        }

        graphics.drawCenteredString(font, tr("ltsmodelshare.preview.controls"), cx,
                GuiNavigation.footerY(height) - 14, 0xAAAAAA);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button == 0) {
            dragging = true;
            lastX = mouseX;
            lastY = mouseY;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging && button == 0) {
            yaw += (float) (mouseX - lastX) * 0.8f;
            pitch = Math.max(-89f, Math.min(89f, pitch + (float) (mouseY - lastY) * 0.8f));
            lastX = mouseX;
            lastY = mouseY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double delta) {
        zoom = Math.max(0.25f, Math.min(8f, zoom * (float) Math.pow(1.15, delta)));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
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
