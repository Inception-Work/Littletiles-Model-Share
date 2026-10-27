package work.inception.ltsmodelshare.client;

import net.minecraft.client.gui.FontRenderer;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本折行工具。
 *
 * <p>坑：Minecraft 自带的 {@code listFormattedStringToWidth} 只按**空格**断词，
 * 中文没有空格 → 整段当成一个词，结果一行画出去溢出屏幕（第一版就这样）。
 * 这里按**字符**折行，中英文都正常。</p>
 */
public final class TextUtil {

    private TextUtil() {
    }

    /** 按像素宽度折行（逐字符），已存在的 \n 也保留 */
    public static List<String> wrap(FontRenderer fr, String text, int maxWidth) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return out;
        }
        for (String raw : text.replace("\r", "").split("\n", -1)) {
            if (raw.isEmpty()) {
                out.add("");
                continue;
            }
            StringBuilder line = new StringBuilder();
            for (int i = 0; i < raw.length(); i++) {
                char c = raw.charAt(i);
                String candidate = line.toString() + c;
                if (fr.getStringWidth(candidate) > maxWidth && line.length() > 0) {
                    out.add(line.toString());
                    line.setLength(0);
                }
                line.append(c);
            }
            if (line.length() > 0) {
                out.add(line.toString());
            }
        }
        return out;
    }

    /** 折行后最多保留 maxLines 行，超出用 … 收尾 */
    public static List<String> wrapLimited(FontRenderer fr, String text, int maxWidth, int maxLines) {
        List<String> all = wrap(fr, text, maxWidth);
        if (maxLines <= 0 || all.size() <= maxLines) {
            return all;
        }
        List<String> out = new ArrayList<>(all.subList(0, maxLines));
        String last = out.get(maxLines - 1);
        while (!last.isEmpty() && fr.getStringWidth(last + "…") > maxWidth) {
            last = last.substring(0, last.length() - 1);
        }
        out.set(maxLines - 1, last + "…");
        return out;
    }
}
