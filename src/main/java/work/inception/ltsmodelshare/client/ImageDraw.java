package work.inception.ltsmodelshare.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.resources.ResourceLocation;

/** Aspect-fill drawing for remote images (cover images, avatars, banners). */
public final class ImageDraw {
    private ImageDraw() { }

    /** Card-sized rendering; uses DogeCloud /thumb (<=480px). */
    public static void drawThumb(PoseStack pose, String url, int x, int y, int w, int h) {
        drawCropped(pose, ImageUrls.thumb(url), x, y, w, h);
    }

    /** Large rendering (detail cover, banner); uses DogeCloud /preview (<=1600px). */
    public static void drawPreview(PoseStack pose, String url, int x, int y, int w, int h) {
        drawCropped(pose, ImageUrls.preview(url), x, y, w, h);
    }

    public static void drawCropped(PoseStack pose, String url, int x, int y, int w, int h) {
        if (url == null || url.isEmpty() || w <= 0 || h <= 0) return;
        ResourceLocation id = ImageCache.img(url);
        int[] size = ImageCache.size(url);
        if (id == null || size == null || size[0] <= 0 || size[1] <= 0) return;
        int texW = size[0];
        int texH = size[1];
        double targetRatio = w / (double) h;
        double sourceRatio = texW / (double) texH;
        int srcW = texW;
        int srcH = texH;
        if (sourceRatio > targetRatio) {
            srcW = (int) Math.round(texH * targetRatio);
        } else {
            srcH = (int) Math.round(texW / targetRatio);
        }
        int srcX = (texW - srcW) / 2;
        int srcY = (texH - srcH) / 2;
        // 1.19.4 has no GuiGraphics: bind the texture explicitly, then blit through GuiComponent.
        RenderSystem.setShaderTexture(0, id);
        GuiComponent.blit(pose, x, y, w, h, (float) srcX, (float) srcY, srcW, srcH, texW, texH);
    }
}
