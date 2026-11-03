package work.inception.ltsmodelshare.gui;

import static work.inception.ltsmodelshare.client.Translations.tr;

import java.util.List;

import org.joml.Matrix4f;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import team.creative.creativecore.common.util.math.vec.Vec3d;
import team.creative.littletiles.common.action.LittleActionException;
import team.creative.littletiles.common.block.little.tile.group.LittleGroup;
import team.creative.littletiles.common.gui.AnimationPreview;
import team.creative.littletiles.common.gui.controls.animation.GuiAnimationViewerStorage;
import work.inception.ltsmodelshare.client.TextUtil;

/**
 * LittleTiles 原生 3D 预览：把 SNBT 解析成 {@link LittleGroup}，交给 LT 自己的
 * {@link AnimationPreview}（带方块模型与材质），在 GUI 里用 LT 的
 * {@link GuiAnimationViewerStorage#renderAll} 画出来。
 *
 * <p>为什么不用"渲染蓝图物品"那招：蓝图物品在 GUI 上下文里画的是那张平面蓝图贴图，
 * 里面的方块只在手持/掉落等上下文才渲染，所以看到的永远是物品图标，不是模型内容。
 * 这里直接走 LT 的预览实体，材质、方块形状都跟游戏内一致。
 *
 * <p>为什么不直接复用 {@code GuiAnimationViewer}：它要求宿主是 CreativeCore 的
 * {@code GuiScreen}，而本模组所有界面都是原版 {@code Screen}；所以只借它的
 * storage 接口 + 默认 renderAll，把相机/投影这几行按同样顺序自己摆一遍。
 */
public class GuiTilesPreview extends Screen {

    @Override
    public void renderBackground(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // No-op: Screen.render calls this *after* content; painting here dims the UI.
    }

    private final Screen parent;
    private final String title;
    private final AnimationPreview preview;
    private final GuiAnimationViewerStorage storage;
    private final Vec3d center;
    private final double longest;

    private float yaw = 30f;
    private float pitch = 18f;
    private double distance;
    private boolean dragging;
    private boolean renderFailed;
    private double lastX;
    private double lastY;

    public GuiTilesPreview(Screen parent, LittleGroup group, String title) throws LittleActionException {
        super(Component.translatable("ltsmodelshare.preview.title"));
        this.parent = parent;
        this.title = title == null ? "" : title;

        // 1.20.2 (LT pre063): AnimationPreview builds its own FakeClientLevel and takes
        // (LittleStructure, LittleGroup); a null structure is supported for plain groups.
        this.preview = new AnimationPreview(null, group);
        AABB box = this.preview.box;
        this.center = new Vec3d(box.getCenter());
        this.longest = Math.max(box.getXsize(), Math.max(box.getYsize(), box.getZsize()));
        this.distance = Math.max(2.0, longest * 1.6);
        this.storage = new Storage();

        this.preview.setupRendering(new PoseStack());
        this.preview.tick();
    }

    private final class Storage implements GuiAnimationViewerStorage {
        @Override public boolean isReady() { return true; }
        @Override public double longestSide() { return longest; }
        @Override public AABB overall() { return preview.box; }
        @Override public Vec3d center() { return center; }
        @Override public boolean highlightSelected() { return false; }
        @Override public void highlightSelected(boolean value) { }
        @Override public Iterable<AnimationPreview> previewsToRender() { return List.of(preview); }
    }

    @Override
    protected void init() {
        clearWidgets();
        addRenderableWidget(GuiNavigation.button(GuiNavigation.backX(width), GuiNavigation.footerY(height),
                GuiNavigation.BACK_WIDTH, 20, "ltsmodelshare.common.back", () -> minecraft.setScreen(parent)));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xE0101010);
        graphics.drawCenteredString(font, TextUtil.trim(font, title, width - 20), width / 2, 10, 0xFFFFFF);
        graphics.drawCenteredString(font, tr("ltsmodelshare.preview.native_mode"), width / 2, 22, 0xFFCC88);

        renderScene(graphics, partialTick);

        if (renderFailed) {
            graphics.drawCenteredString(font, tr("ltsmodelshare.preview.native_failed"), width / 2, height / 2, 0xFF8888);
        }
        graphics.drawCenteredString(font, tr("ltsmodelshare.preview.controls"), width / 2,
                height - 34, 0xAAAAAA);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** LT 的预览实体在透视投影下画；画完把 GUI 的正交投影还回去，别把后面的控件带歪。 */
    private void renderScene(GuiGraphics graphics, float partialTick) {
        Minecraft mc = minecraft;
        if (mc == null) return;
        PoseStack pose = graphics.pose();
        RenderSystem.enableDepthTest();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // 跟 LT 的 GuiAnimationViewer 同一套顺序：视口铺满窗口、清深度、modelview 归零后再摆相机。
        // 少了这几步就是"预览页一片空白"——透视投影有了，但深度/矩阵状态还停在 GUI 那套。
        var window = mc.getWindow();
        RenderSystem.viewport(0, 0, window.getWidth(), window.getHeight());
        RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);

        var modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.setIdentity();
        RenderSystem.applyModelViewMatrix();

        Matrix4f projection = new Matrix4f().perspective((float) Math.toRadians(90),
                (float) window.getWidth() / Math.max(1, window.getHeight()), 0.05f, 2048f);
        RenderSystem.setProjectionMatrix(projection, VertexSorting.DISTANCE_TO_ORIGIN);

        PoseStack scene = new PoseStack();
        scene.translate(0f, 0f, (float) -distance);
        scene.mulPose(Axis.XP.rotationDegrees(pitch));
        scene.mulPose(Axis.YP.rotationDegrees(yaw));
        scene.translate(-center.x, -center.y, -center.z);
        try {
            storage.renderAll(scene, projection, mc);
        } catch (Throwable error) {
            // 画不出来也别留一块空白：记下原因，下面画一行提示，用户至少知道发生了什么
            renderFailed = true;
            work.inception.ltsmodelshare.LtsModelShare.LOG.warn("原生预览渲染失败：{}", error.toString());
        }
        modelView.popPose();
        RenderSystem.applyModelViewMatrix();

        RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0f, width, height, 0f, 1000f, 3000f),
                VertexSorting.ORTHOGRAPHIC_Z);
        RenderSystem.disableDepthTest();
        GlStateManager._enableBlend();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 先给控件（返回按钮）机会，再开始拖拽，否则按钮永远点不到
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
        if (dragging) {
            yaw += (float) (mouseX - lastX) * 1.6f;
            pitch = Math.max(-89f, Math.min(89f, pitch + (float) (mouseY - lastY) * 1.6f));
            lastX = mouseX;
            lastY = mouseY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double delta) {
        distance = Math.max(longest * 0.4, Math.min(longest * 6.0, distance - delta * longest * 0.12));
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
