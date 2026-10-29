package work.inception.ltsmodelshare.gui;

import static work.inception.ltsmodelshare.client.Translations.tr;

import com.creativemd.littletiles.client.world.LittleAnimationHandlerClient;
import com.creativemd.littletiles.common.entity.AnimationPreview;
import com.creativemd.littletiles.common.tile.preview.LittlePreview;
import com.creativemd.littletiles.common.tile.preview.LittlePreviews;
import com.creativemd.littletiles.common.util.converation.StructureStringUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.item.ItemStack;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.Project;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import work.inception.ltsmodelshare.LtsModelShare;
import work.inception.ltsmodelshare.api.ApiClient;
import work.inception.ltsmodelshare.api.Dtos;
import work.inception.ltsmodelshare.client.GridLimits;

import javax.vecmath.Vector3d;
import java.lang.reflect.Field;
import java.nio.IntBuffer;
import java.util.Arrays;

/**
 * 详情页内的原生 LittleTiles 模型窗口。仅在点击后取 1.12 SNBT，不向背包或世界放置。
 * FakeWorld / EntityAnimation 由 LittleTiles 自己的 AnimationPreview 构建。
 */
final class ModelPreviewPanel {
    private static final long MAX_SNBT_BYTES = 2L * 1024 * 1024;
    private static final int MAX_BOXES = 6000;

    /** 上传时统计的 SNBT 属性决定入口能否显示；白模 OBJ 的 preview.available 与此无关。 */
    static boolean available(Dtos.Item item) {
        return item != null && item.itemId > 0
                && item.boxCount != null && item.boxCount > 0 && item.boxCount <= MAX_BOXES
                && item.grid != null && item.grid > 0 && !GridLimits.exceeds(item.grid)
                // 兼容尚未部署 snbtSizeBytes 的后端；真实大小仍由下载响应二次校验。
                && (item.snbtSizeBytes == null || item.snbtSizeBytes <= MAX_SNBT_BYTES);
    }

    /**
     * LittleTiles 自带 GuiAnimationViewer 会将 256 格 lightmap 全设为白色，但不复原。
     * 这里仅在绘制预览期间借用该方式，结束后立即还原游戏原有的光照贴图。
     */
    private static final class BrightLightmap implements AutoCloseable {
        private final EntityRenderer renderer;
        private final DynamicTexture texture;
        private final int[] colors;
        private final int[] original;
        private final Field updateNeeded;

        private BrightLightmap(EntityRenderer renderer) throws IllegalAccessException {
            this.renderer = renderer;
            Field textureField = ReflectionHelper.findField(EntityRenderer.class,
                    "lightmapTexture", "field_78513_d");
            Field colorsField = ReflectionHelper.findField(EntityRenderer.class,
                    "lightmapColors", "field_78504_Q");
            this.updateNeeded = ReflectionHelper.findField(EntityRenderer.class,
                    "lightmapUpdateNeeded", "field_78536_aa");
            texture = (DynamicTexture) textureField.get(renderer);
            colors = (int[]) colorsField.get(renderer);
            original = Arrays.copyOf(colors, colors.length);
            Arrays.fill(colors, 0xFFFFFFFF);
            texture.updateDynamicTexture();
            updateNeeded.setBoolean(renderer, true);
        }

        @Override public void close() {
            try {
                System.arraycopy(original, 0, colors, 0, colors.length);
                texture.updateDynamicTexture();
                updateNeeded.setBoolean(renderer, true);
            } catch (Exception error) {
                LtsModelShare.LOG.warn("LittleTiles 预览恢复光照失败", error);
            }
        }
    }

    private static boolean lightmapWarningLogged;

    private static BrightLightmap brighten(Minecraft mc) {
        try {
            return new BrightLightmap(mc.entityRenderer);
        } catch (Exception error) {
            if (!lightmapWarningLogged) {
                lightmapWarningLogged = true;
                LtsModelShare.LOG.warn("LittleTiles 预览无法临时提亮光照贴图", error);
            }
            return null;
        }
    }

    private final GuiWorkDetail owner;
    private final Dtos.Item item;
    private final String accessCode;
    private AnimationPreview preview;
    private boolean closed;
    private String message = tr("ltsmodelshare.preview.fetching");
    private double distance;
    private float pitch = 24;
    private float yaw = 35;
    private int x, y, w, h;
    private boolean dragging;
    private int lastX, lastY;

    ModelPreviewPanel(GuiWorkDetail owner, Dtos.Item item, String accessCode) {
        this.owner = owner;
        this.item = item;
        this.accessCode = accessCode;
    }

    void load() {
        if (!available(item)) {
            message = tr("ltsmodelshare.preview.limit");
            return;
        }
        // 接口按文件内容检测真实方言；非 1.12 原件会在服务端先转换并缓存为 1.12。
        if (item.mcVersion != null && !item.mcVersion.startsWith("1.12")) {
            message = tr("ltsmodelshare.preview.converting");
        }
        ApiClient.downloadInfo(item.itemId, "1.12", accessCode, new ApiClient.Callback<Dtos.DownloadInfo>() {
            @Override public void ok(Dtos.DownloadInfo info) {
                if (!"1.12".equals(info.version)) {
                    fail(tr("ltsmodelshare.preview.version"));
                    return;
                }
                if (info.sizeBytes > MAX_SNBT_BYTES) {
                    ApiClient.post(() -> { if (!closed) message = tr("ltsmodelshare.preview.too_large"); });
                    return;
                }
                ApiClient.fetchText(info, new ApiClient.Callback<String>() {
                    @Override public void ok(String text) {
                        // 网络线程只传文本。Minecraft / LittleTiles 的注册表、FakeWorld、GL 均在主线程使用。
                        ApiClient.post(() -> {
                            if (!closed && Minecraft.getMinecraft().currentScreen == owner) accept(text);
                        });
                    }
                    @Override public void fail(String error) { fail(error); }
                });
            }
            @Override public void fail(String error) { fail(error); }
        });
    }

    private void fail(String error) {
        ApiClient.post(() -> { if (!closed) message = tr("ltsmodelshare.preview.load_failed", error); });
    }

    private void accept(String text) {
        if (text == null || text.isEmpty() || text.length() > MAX_SNBT_BYTES) {
            message = tr("ltsmodelshare.preview.empty_large");
            return;
        }
        if (Minecraft.getMinecraft().world == null || Minecraft.getMinecraft().getRenderViewEntity() == null) {
            message = tr("ltsmodelshare.preview.world_required");
            return;
        }
        try {
            ItemStack stack = StructureStringUtils.importStructure(text);
            if (stack == null || stack.isEmpty()) throw new IllegalArgumentException(tr("ltsmodelshare.preview.empty"));
            LittlePreviews tiles = LittlePreview.getPreview(stack, false);
            if (tiles == null || tiles.isEmptyIncludeChildren()) throw new IllegalArgumentException(tr("ltsmodelshare.preview.no_boxes"));
            if (tiles.totalSize() > MAX_BOXES) throw new IllegalArgumentException(tr("ltsmodelshare.preview.box_limit"));
            AnimationPreview result = new AnimationPreview(tiles);
            if (result.animation == null) throw new IllegalArgumentException(tr("ltsmodelshare.preview.no_animation"));
            preview = result;
            distance = Math.max(2, result.context.toVanillaGrid(result.entireBox.getLongestSide()) / 2D + 2);
            message = "";
        } catch (Exception error) {
            LtsModelShare.LOG.warn("LittleTiles 原生预览构建失败 itemId={}", item.itemId, error);
            releasePreview();
            message = tr("ltsmodelshare.preview.parse_failed", error.getClass().getSimpleName());
        }
    }

    private void layout() {
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution res = new ScaledResolution(mc);
        int sw = res.getScaledWidth(), sh = res.getScaledHeight();
        w = Math.max(160, Math.min(sw - 16, 340));
        h = Math.max(110, Math.min(sh - 24, 230));
        x = (sw - w) / 2;
        y = (sh - h) / 2;
    }

    void draw(int mouseX, int mouseY, float partialTicks) {
        layout();
        Minecraft mc = Minecraft.getMinecraft();
        Gui.drawRect(0, 0, mc.currentScreen.width, mc.currentScreen.height, 0xB8000000);
        Gui.drawRect(x - 2, y - 2, x + w + 2, y + h + 2, 0xFF151515);
        Gui.drawRect(x, y, x + w, y + h, 0xFF292929);
        Gui.drawRect(x + 4, y + 23, x + w - 4, y + h - 24, 0xFF394047);
        String title = item.title == null ? tr("ltsmodelshare.preview.title") : item.title;
        mc.fontRenderer.drawStringWithShadow(mc.fontRenderer.trimStringToWidth(title, w - 72), x + 7, y + 7, 0xFFFFFF);
        Gui.drawRect(x + w - 22, y + 3, x + w - 4, y + 20, 0xFF603330);
        mc.fontRenderer.drawStringWithShadow("X", x + w - 16, y + 7, 0xFFFFFF);
        if (preview != null) {
            try {
                renderNative(x + 4, y + 23, w - 8, h - 47);
            } catch (Exception error) {
                LtsModelShare.LOG.warn("LittleTiles 原生预览绘制失败 itemId={}", item.itemId, error);
                releasePreview();
                message = tr("ltsmodelshare.preview.render_failed", error.getClass().getSimpleName());
            }
        }
        if (!message.isEmpty()) {
            mc.fontRenderer.drawStringWithShadow(mc.fontRenderer.trimStringToWidth(message, w - 18),
                    x + 8, y + h / 2, 0xFFCCCCCC);
        }
        mc.fontRenderer.drawStringWithShadow(tr("ltsmodelshare.preview.controls"), x + 7, y + h - 16, 0xFFAAAAAA);
    }

    private void renderNative(int px, int py, int pw, int ph) {
        Minecraft mc = Minecraft.getMinecraft();
        if (preview == null || mc.world == null || mc.getRenderViewEntity() == null) return;
        IntBuffer oldViewport = BufferUtils.createIntBuffer(16);
        IntBuffer oldScissor = BufferUtils.createIntBuffer(16);
        GL11.glGetInteger(GL11.GL_VIEWPORT, oldViewport);
        GL11.glGetInteger(GL11.GL_SCISSOR_BOX, oldScissor);
        boolean wasScissored = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        int oldMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        ScaledResolution sr = new ScaledResolution(mc);
        float sx = (float) mc.displayWidth / sr.getScaledWidth();
        float sy = (float) mc.displayHeight / sr.getScaledHeight();
        int vx = Math.round(px * sx), vy = Math.round((sr.getScaledHeight() - py - ph) * sy);
        int vw = Math.max(1, Math.round(pw * sx)), vh = Math.max(1, Math.round(ph * sy));
        GL11.glViewport(vx, vy, vw, vh);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        // 只清预览矩形的深度；不会擦掉屏幕其他区域。
        GL11.glScissor(vx, vy, vw, vh);
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        Project.gluPerspective(90, (float) vw / vh, 0.05F, 2048F);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        try {
            GL11.glLoadIdentity();
            GlStateManager.translate(0, 0, -distance);
            GlStateManager.rotate(pitch, 1, 0, 0);
            GlStateManager.rotate(yaw, 0, 1, 0);
            Vector3d center = new Vector3d(preview.animation.center.rotationCenter);
            center.y -= 75;
            GlStateManager.translate(-preview.entireBox.getMinVec().getPosX(preview.context),
                    -preview.entireBox.getMinVec().getPosY(preview.context),
                    -preview.entireBox.getMinVec().getPosZ(preview.context));
            GlStateManager.translate(-center.x, -center.y, -center.z);
            GlStateManager.translate(TileEntityRendererDispatcher.staticPlayerX,
                    TileEntityRendererDispatcher.staticPlayerY, TileEntityRendererDispatcher.staticPlayerZ);
            GlStateManager.translate(0, -75, 0);
            GlStateManager.enableDepth();
            GlStateManager.enableRescaleNormal();
            GlStateManager.enableBlend();
            GlStateManager.color(1, 1, 1, 1);
            mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            BrightLightmap bright = brighten(mc);
            try {
                LittleAnimationHandlerClient.render.doRender(preview.animation, 0, 0, 0, 0, mc.getRenderPartialTicks());
            } finally {
                if (bright != null) bright.close();
            }
        } finally {
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glViewport(oldViewport.get(0), oldViewport.get(1), oldViewport.get(2), oldViewport.get(3));
            GL11.glScissor(oldScissor.get(0), oldScissor.get(1), oldScissor.get(2), oldScissor.get(3));
            if (!wasScissored) GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glMatrixMode(oldMode);
            GlStateManager.disableRescaleNormal();
            GlStateManager.disableDepth();
            GlStateManager.disableBlend();
            GlStateManager.enableTexture2D();
            GlStateManager.color(1, 1, 1, 1);
            mc.entityRenderer.setupOverlayRendering();
        }
    }

    void click(int mx, int my, int button) {
        if (button != 0) return;
        layout();
        if (mx >= x + w - 25 && mx < x + w && my >= y && my < y + 22) {
            owner.closeModelPreview();
            return;
        }
        if (mx >= x + 4 && mx < x + w - 4 && my >= y + 23 && my < y + h - 24) {
            dragging = true;
            lastX = mx;
            lastY = my;
        }
    }

    void drag(int mx, int my, int button) {
        if (!dragging || button != 0) return;
        yaw += (mx - lastX) * 0.8f;
        pitch = Math.max(-89, Math.min(89, pitch + (my - lastY) * 0.8f));
        lastX = mx;
        lastY = my;
    }

    void release() { dragging = false; }

    void wheel(int delta) {
        if (delta == 0 || preview == null) return;
        double base = Math.max(2, preview.context.toVanillaGrid(preview.entireBox.getLongestSide()) / 2D + 2);
        distance = Math.max(base * 0.3, Math.min(base * 12, distance * (delta > 0 ? 0.84 : 1.19)));
    }

    private void releasePreview() {
        if (preview != null) {
            // LT 的 EntityAnimation 会递归清理 FakeWorld 子实体并卸载渲染 chunk/VBO。
            try {
                preview.animation.markRemoved();
            } catch (Exception error) {
                LtsModelShare.LOG.warn("LittleTiles 原生预览资源清理失败 itemId={}", item.itemId, error);
            }
            preview = null;
        }
    }

    void close() {
        closed = true; // 网络回调晚到也不能再把旧预览装进界面
        releasePreview();
        dragging = false;
    }
}
