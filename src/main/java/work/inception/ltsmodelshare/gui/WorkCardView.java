package work.inception.ltsmodelshare.gui;

import static work.inception.ltsmodelshare.client.Translations.tr;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import work.inception.ltsmodelshare.api.Dtos;
import work.inception.ltsmodelshare.client.Avatars;
import work.inception.ltsmodelshare.client.ImageCache;
import work.inception.ltsmodelshare.client.ImageDraw;
import work.inception.ltsmodelshare.client.TextUtil;

/** Shared rendering for work cards and compact feed rows. */
final class WorkCardView {
    static final float SMALL_TEXT = 0.75f;

    private WorkCardView() { }

    static void grid(GuiGraphics g, Font font, Dtos.WorkCard card, int x, int y, int w, int h,
                     int imageH, boolean hovered) {
        g.fill(x, y, x + w, y + h, hovered ? 0x50FFFFFF : 0x30000000);
        g.fill(x, y, x + w, y + 1, hovered ? 0xFFFFFFFF : 0x55FFFFFF);
        int imgX = x + 3, imgY = y + 3, imgW = w - 6;
        if (ImageCache.img(work.inception.ltsmodelshare.client.ImageUrls.thumb(card.cover)) == null)
            g.fill(imgX, imgY, imgX + imgW, imgY + imageH, 0x30FFFFFF);
        ImageDraw.drawThumb(g, card.cover, imgX, imgY, imgW, imageH);

        int textY = imgY + imageH + 1;
        scaled(g, font, TextUtil.trim(font, card.title == null ? tr("ltsmodelshare.common.untitled") : card.title,
                (int) ((w - 6) / 0.8f)), x + 3, textY, 0xFFFFFF, 0.8f);

        int ay = textY + 8;
        int avatarSize = 6;
        Dtos.Author author = card.author;
        int cursor = x + 3;
        String name = "";
        if (author != null) {
            Avatars.draw(g, font, author, cursor, ay, avatarSize);
            cursor += avatarSize + 2;
            name = author.displayName != null && !author.displayName.isEmpty()
                    ? author.displayName : (author.username == null ? "" : author.username);
        }
        small(g, font, TextUtil.trim(font, name, (int) ((w - 6 - (cursor - x - 3)) / SMALL_TEXT)),
                cursor, ay, 0xCCCCCC);
        small(g, font, TextUtil.trim(font, tr("ltsmodelshare.common.work_stats", card.downloadCount, card.viewCount),
                (int) ((w - 6) / SMALL_TEXT)), x + 3, ay + 7, 0xAAAAAA);
    }

    static void row(GuiGraphics g, Font font, Dtos.WorkCard card, int x, int y, int w, int h, boolean hovered) {
        g.fill(x, y, x + w, y + h, hovered ? 0x55FFFFFF : 0x33000000);
        if (ImageCache.img(work.inception.ltsmodelshare.client.ImageUrls.thumb(card.cover)) == null)
            g.fill(x + 3, y + 3, x + 41, y + h - 3, 0x30FFFFFF);
        ImageDraw.drawThumb(g, card.cover, x + 3, y + 3, 38, h - 6);
        int textX = x + 46;
        int textW = Math.max(30, w - 52);
        g.drawString(font, TextUtil.trim(font, card.title == null ? tr("ltsmodelshare.common.untitled") : card.title, textW),
                textX, y + 4, 0xFFFFFF, true);
        String author = card.author == null ? "" : (card.author.displayName != null
                && !card.author.displayName.isEmpty() ? card.author.displayName : card.author.username);
        scaled(g, font, TextUtil.trim(font, author, (int) (textW / 0.8f)), textX, y + 16, 0xCCCCCC, 0.8f);
        if (card.publishedAt != null && card.publishedAt.length() >= 10) {
            scaled(g, font, card.publishedAt.substring(0, 10), x + w - 46, y + 16, 0x999999, 0.8f);
        }
    }

    static void small(GuiGraphics g, Font font, String text, int x, int y, int color) {
        scaled(g, font, text, x, y, color, SMALL_TEXT);
    }

    static void scaled(GuiGraphics g, Font font, String text, int x, int y, int color, float scale) {
        if (text == null || text.isEmpty()) return;
        PoseStack pose = g.pose();
        pose.pushPose();
        try {
            pose.translate(x, y, 0);
            pose.scale(scale, scale, 1f);
            g.drawString(font, text, 0, 0, color, true);
        } finally {
            pose.popPose();
        }
    }
}
