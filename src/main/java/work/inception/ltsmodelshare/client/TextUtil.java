package work.inception.ltsmodelshare.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;
import com.mojang.blaze3d.vertex.PoseStack;

import java.util.List;

/** Text fitting helpers shared by all 1.20.1 screens. */
public final class TextUtil {
    private TextUtil() { }

    public static String trim(Font font, String text, int maxWidth) {
        if (text == null) return "";
        if (font.width(text) <= maxWidth) return text;
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            sb.append(c);
            if (font.width(sb + "…") > maxWidth) {
                return sb.substring(0, Math.max(0, sb.length() - 1)) + "…";
            }
        }
        return text;
    }

    /** Draw centered, scaling down when the translation is wider than the button. */
    public static void drawCenteredFit(GuiGraphics graphics, Font font, String text,
                                       int centerX, int y, int maxWidth, int color) {
        if (text == null || text.isEmpty()) return;
        float scale = 1f;
        int width = font.width(text);
        if (width > maxWidth && width > 0) {
            scale = Math.max(0.5f, maxWidth / (float) width);
        }
        PoseStack pose = graphics.pose();
        pose.pushPose();
        try {
            pose.translate(centerX, y + (scale < 1f ? (font.lineHeight * (1f - scale)) / 2f : 0f), 0f);
            pose.scale(scale, scale, 1f);
            graphics.drawString(font, text, -Math.round(font.width(text) / 2f), 0, color, true);
        } finally {
            pose.popPose();
        }
    }

    public static List<FormattedCharSequence> wrap(Font font, String text, int width, int maxLines) {
        List<FormattedCharSequence> lines = font.split(net.minecraft.network.chat.Component.literal(text == null ? "" : text), width);
        return lines.size() <= maxLines ? lines : lines.subList(0, maxLines);
    }
}
