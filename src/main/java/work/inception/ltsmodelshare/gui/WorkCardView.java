package work.inception.ltsmodelshare.gui;

import static work.inception.ltsmodelshare.client.Translations.tr;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import work.inception.ltsmodelshare.api.Dtos;
import work.inception.ltsmodelshare.client.ImageCache;
import work.inception.ltsmodelshare.client.ImageDraw;

/** 作品卡片 / 列表行的统一画法，收藏、关注动态和我的空间共用。 */
final class WorkCardView {
    static final float SMALL_TEXT = 0.75f;

    private WorkCardView() { }

    static void grid(FontRenderer fr, Dtos.WorkCard card, int x, int y, int w, int h,
                     int imageH, boolean hovered) {
        Gui.drawRect(x, y, x + w, y + h, hovered ? 0x50FFFFFF : 0x30000000);
        Gui.drawRect(x, y, x + w, y + 1, hovered ? 0xFFFFFFFF : 0x55FFFFFF);
        int imgX = x + 3, imgY = y + 3, imgW = w - 6;
        if (ImageCache.img(work.inception.ltsmodelshare.client.ImageUrls.thumb(card.cover)) == null)
            Gui.drawRect(imgX, imgY, imgX + imgW, imgY + imageH, 0x30FFFFFF);
        ImageDraw.drawThumb(card.cover, imgX, imgY, imgW, imageH);

        int textY = imgY + imageH + 1;
        scaled(fr, trim(fr, card.title == null ? tr("ltsmodelshare.common.untitled") : card.title,
                (int) ((w - 6) / 0.8f)), x + 3, textY, 0xFFFFFF, 0.8f);

        int ay = textY + 8;
        int avatarSize = 6;
        Dtos.Author author = card.author;
        int cursor = x + 3;
        String name = "";
        if (author != null) {
            if (author.avatarUrl != null
                    && ImageCache.img(work.inception.ltsmodelshare.client.ImageUrls.thumb(author.avatarUrl)) != null) {
                ImageDraw.drawThumb(author.avatarUrl, cursor, ay, avatarSize, avatarSize);
            } else {
                Gui.drawRect(cursor, ay, cursor + avatarSize, ay + avatarSize, 0x40FFFFFF);
            }
            cursor += avatarSize + 2;
            name = author.displayName != null && !author.displayName.isEmpty()
                    ? author.displayName : (author.username == null ? "" : author.username);
        }
        small(fr, trim(fr, name, (int) ((w - 6 - (cursor - x - 3)) / SMALL_TEXT)), cursor, ay, 0xCCCCCC);
        small(fr, trim(fr, tr("ltsmodelshare.common.work_stats", card.downloadCount, card.viewCount),
                (int) ((w - 6) / SMALL_TEXT)), x + 3, ay + 7, 0xAAAAAA);
    }

    /** 关注动态用的紧凑行。 */
    static void row(FontRenderer fr, Dtos.WorkCard card, int x, int y, int w, int h, boolean hovered) {
        Gui.drawRect(x, y, x + w, y + h, hovered ? 0x55FFFFFF : 0x33000000);
        if (ImageCache.img(work.inception.ltsmodelshare.client.ImageUrls.thumb(card.cover)) == null)
            Gui.drawRect(x + 3, y + 3, x + 41, y + h - 3, 0x30FFFFFF);
        ImageDraw.drawThumb(card.cover, x + 3, y + 3, 38, h - 6);
        int textX = x + 46;
        int textW = Math.max(30, w - 52);
        fr.drawStringWithShadow(trim(fr, card.title == null ? tr("ltsmodelshare.common.untitled") : card.title, textW),
                textX, y + 4, 0xFFFFFF);
        String author = card.author == null ? "" : (card.author.displayName != null
                && !card.author.displayName.isEmpty() ? card.author.displayName : card.author.username);
        scaled(fr, trim(fr, author, (int) (textW / 0.8f)), textX, y + 16, 0xCCCCCC, 0.8f);
        if (card.publishedAt != null && card.publishedAt.length() >= 10) {
            scaled(fr, card.publishedAt.substring(0, 10), x + w - 46, y + 16, 0x999999, 0.8f);
        }
    }

    static void small(FontRenderer fr, String text, int x, int y, int color) {
        scaled(fr, text, x, y, color, SMALL_TEXT);
    }

    static void scaled(FontRenderer fr, String text, int x, int y, int color, float scale) {
        if (text == null || text.isEmpty()) return;
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(x, y, 0);
            GlStateManager.scale(scale, scale, 1f);
            fr.drawStringWithShadow(text, 0, 0, color);
        } finally {
            GlStateManager.popMatrix();
            GlStateManager.color(1f, 1f, 1f, 1f);
        }
    }

    static String trim(FontRenderer fr, String text, int maxWidth) {
        if (text == null) return "";
        if (fr.getStringWidth(text) <= maxWidth) return text;
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            sb.append(c);
            if (fr.getStringWidth(sb + "…") > maxWidth) {
                return sb.substring(0, Math.max(0, sb.length() - 1)) + "…";
            }
        }
        return text;
    }
}
