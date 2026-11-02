package work.inception.ltsmodelshare.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Aspect-fill drawing for remote images (cover images, avatars, banners). */
public final class ImageDraw {
    private ImageDraw() { }

    public static void drawCropped(GuiGraphics graphics, String url, int x, int y, int w, int h) {
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
        graphics.blit(id, x, y, w, h, (float) srcX, (float) srcY, srcW, srcH, texW, texH);
    }
}
