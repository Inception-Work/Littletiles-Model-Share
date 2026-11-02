package work.inception.ltsmodelshare.client;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import work.inception.ltsmodelshare.api.Dtos;

/**
 * 作者头像绘制。
 *
 * <p>站点上不少作者 `avatarUrl` 是 null，所以统一走这里：
 * 有头像就等比裁剪画出来，没有就画一个按用户名哈希取色的**首字母色块**
 * （详情页和高级搜索的候选列表都用它，保证两处看起来一致）。</p>
 */
public final class Avatars {

    private Avatars() {
    }

    /**
     * @param size 头像边长（逻辑像素）；候选列表用 10、详情页用 16
     */
    public static void draw(FontRenderer fr, Dtos.Author author, int x, int y, int size) {
        if (author == null || size <= 0) {
            return;
        }
        String url = author.avatarUrl;
        if (url != null && !url.isEmpty() && ImageCache.img(ImageUrls.thumb(url)) != null) {
            ImageDraw.drawThumb(url, x, y, size, size);
            return;
        }

        String seed = author.username == null || author.username.isEmpty() ? "?" : author.username;
        int hash = Math.abs(seed.hashCode());
        int r = (hash & 0x7F) + 0x30;
        int g = ((hash >> 8) & 0x7F) + 0x30;
        int b = ((hash >> 16) & 0x7F) + 0x30;
        Gui.drawRect(x, y, x + size, y + size, 0xFF000000 | (r << 16) | (g << 8) | b);

        String letter = seed.substring(0, 1).toUpperCase();
        // 字高固定 8，居中放；size 很小时（10px）也还能看清
        int textX = x + (size - fr.getStringWidth(letter)) / 2;
        int textY = y + (size - 8) / 2;
        fr.drawStringWithShadow(letter, textX, textY, 0xFFFFFF);
    }
}
