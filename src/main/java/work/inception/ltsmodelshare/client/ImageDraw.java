package work.inception.ltsmodelshare.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;

/**
 * 画远程图片（封面 / 头像）。
 *
 * <p>核心是**等比居中裁剪**：把源图的中间一块按目标宽高比裁出来，再缩放到目标框里。
 * 直接用 {@code drawModalRectWithCustomSizedTexture} 会把图拉伸变形（第一版就踩了），
 * 正确做法是用 {@code drawScaledCustomSizeModalRect} 指定源子矩形。</p>
 */
public final class ImageDraw {

    private ImageDraw() {
    }

    /**
     * 把图片按目标框的比例居中裁剪后绘制（不变形）。
     *
     * @param x,y    左上角
     * @param w,h    目标框（宽高比即裁剪比例，例如 4:3 就传 4 与 3 的倍数）
     */
    /** Card-sized rendering; uses DogeCloud /thumb (<=480px). */
    public static void drawThumb(String url, int x, int y, int w, int h) {
        drawCropped(ImageUrls.thumb(url), x, y, w, h);
    }

    /** Large rendering (detail cover, banner); uses DogeCloud /preview (<=1600px). */
    public static void drawPreview(String url, int x, int y, int w, int h) {
        drawCropped(ImageUrls.preview(url), x, y, w, h);
    }

    public static void drawCropped(String url, int x, int y, int w, int h) {
        ImageCache.Img img = ImageCache.img(url);
        if (img == null) {
            Gui.drawRect(x, y, x + w, y + h, 0x40FFFFFF);
            return;
        }
        int sw = img.width;
        int sh = img.height;
        int u = 0;
        int v = 0;

        // 源图比目标更宽 → 裁左右；更高 → 裁上下
        float target = w / (float) h;
        float source = img.width / (float) img.height;
        if (source > target) {
            sw = Math.round(img.height * target);
            u = (img.width - sw) / 2;
        } else if (source < target) {
            sh = Math.round(img.width / target);
            v = (img.height - sh) / 2;
        }
        sw = Math.max(1, Math.min(sw, img.width));
        sh = Math.max(1, Math.min(sh, img.height));

        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        Minecraft.getMinecraft().getTextureManager().bindTexture(img.tex);
        Gui.drawScaledCustomSizeModalRect(x, y, u, v, sw, sh, w, h, img.width, img.height);
        GlStateManager.disableBlend();
    }

    /** 按固定比例（如 4:3）算封面框：先给宽，再推高；反过来也支持 */
    public static int heightForRatio(int width, int ratioW, int ratioH) {
        return Math.max(1, width * ratioH / ratioW);
    }

    public static int widthForRatio(int height, int ratioW, int ratioH) {
        return Math.max(1, height * ratioW / ratioH);
    }
}
