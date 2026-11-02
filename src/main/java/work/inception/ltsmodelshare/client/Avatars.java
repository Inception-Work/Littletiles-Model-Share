package work.inception.ltsmodelshare.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import work.inception.ltsmodelshare.api.Dtos;

/** Avatar drawing with a deterministic letter fallback, matching 1.12 behaviour. */
public final class Avatars {
    private Avatars() { }

    public static void draw(PoseStack pose, Font font, Dtos.Author author, int x, int y, int size) {
        if (author == null || size <= 0) return;
        String url = author.avatarUrl;
        if (url != null && !url.isEmpty() && ImageCache.img(ImageUrls.thumb(url)) != null) {
            ImageDraw.drawThumb(pose, url, x, y, size, size);
            return;
        }
        String seed = author.username == null || author.username.isEmpty() ? "?" : author.username;
        int hash = Math.abs(seed.hashCode());
        int r = (hash & 0x7F) + 0x30;
        int g = ((hash >> 8) & 0x7F) + 0x30;
        int b = ((hash >> 16) & 0x7F) + 0x30;
        GuiComponent.fill(pose, x, y, x + size, y + size, 0xFF000000 | (r << 16) | (g << 8) | b);
        String letter = seed.substring(0, 1).toUpperCase();
        font.draw(pose, letter, x + (size - font.width(letter)) / 2, y + (size - font.lineHeight) / 2, 0xFFFFFF);
    }
}
