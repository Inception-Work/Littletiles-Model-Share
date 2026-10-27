package work.inception.ltsmodelshare.gui;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import work.inception.ltsmodelshare.ModConfig;
import work.inception.ltsmodelshare.api.ApiClient;
import work.inception.ltsmodelshare.api.Dtos;
import work.inception.ltsmodelshare.client.ImageCache;
import work.inception.ltsmodelshare.client.ImageDraw;
import work.inception.ltsmodelshare.client.TextUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 作品详情页（信息层次对着网页版 ModelDetailView 来）。
 *
 * <pre>
 * ┌─────────────────────────┬──────────────────────────┐
 * │                         │ 标题（最多两行）          │
 * │        封面 16:9        │ #标签 #标签               │
 * │                         │ [头像] 名字 / @账号      │
 * │  [缩略图][缩略图][...]  │ 浏览 下载 收藏 评论       │
 * ├─────────────────────────┴──────────────────────────┤
 * │ 简介（自动折行）                                    │
 * │ 模型（n）                                           │
 * │  #id 标题  [版本|box|grid|需要模组]          [下载] │
 * └─────────────────────────────────────────────────────┘
 *   状态 · 保存路径                      [返回][刷新]
 * </pre>
 *
 * <p>窗口小的时候（例如 427×240 逻辑尺寸）封面按可用高度压缩；内容超出时滚轮滚动，
 * 正文用 GL11 裁剪，滚出区域的字不会压到按钮上。</p>
 */
public class GuiWorkDetail extends GuiScreen {

    private final Dtos.WorkCard card;
    private final GuiScreen parent;

    private Dtos.WorkDetail detail;
    private final List<Dtos.Item> items = new ArrayList<>();
    private final List<String> gallery = new ArrayList<>();
    private int galleryIndex;

    private String status = "";
    private boolean loading;
    private final List<Long> downloaded = new ArrayList<>();

    // 布局
    private int colW;
    private int left;
    private int bodyTop;
    private int bodyBottom;
    private int coverW;
    private int coverH;
    private int rightX;
    private int rightW;
    private int headerH;
    private int thumbSize;

    // 滚动
    private int scroll;

    private static final int ITEM_ROW_H = 34;
    private static final int DL_W = 58;
    private static final int DL_H = 18;

    public GuiWorkDetail(Dtos.WorkCard card, GuiScreen parent) {
        this.card = card;
        this.parent = parent;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int cx = width / 2;

        bodyTop = 22;
        bodyBottom = height - 46;
        int bodyH = Math.max(40, bodyBottom - bodyTop);

        colW = Math.min(320, width - 20);
        left = cx - colW / 2;

        /*
         * 左栏封面：**4:3** 比例（按用户要求）。
         * 先按宽度 46% 定宽，推高；如果高度超过正文的 55%，就反过来按高度定宽。
         * 绘制时做等比居中裁剪（ImageDraw.drawCropped），不会被拉伸。
         */
        coverW = Math.max(72, Math.min(170, colW * 46 / 100));
        coverH = coverW * 3 / 4;
        int maxCoverH = Math.max(48, bodyH * 55 / 100);
        if (coverH > maxCoverH) {
            coverH = maxCoverH;
            coverW = coverH * 4 / 3;
        }
        rightX = left + coverW + 8;
        rightW = Math.max(60, colW - coverW - 8);
        thumbSize = Math.max(14, Math.min(24, coverW / 5));
        headerH = computeHeaderHeight();


        buttonList.add(new GuiButton(1, cx - 76, height - 24, 70, 20, "返回"));
        buttonList.add(new GuiButton(2, cx + 6, height - 24, 70, 20, "刷新"));

        scroll = 0;
        load();
    }

    // ==================== 数据 ====================

    private void load() {
        if (loading) {
            return;
        }
        loading = true;
        status = TextFormatting.GRAY + "加载中…";
        ApiClient.detail(card.workId, new ApiClient.Callback<Dtos.WorkDetail>() {
            @Override
            public void ok(Dtos.WorkDetail value) {
                ApiClient.post(() -> {
                    loading = false;
                    detail = value;
                    items.clear();
                    if (value.items != null) {
                        items.addAll(value.items);
                    }
                    gallery.clear();
                    if (value.images != null) {
                        for (String img : value.images) {
                            if (img != null && !img.isEmpty()) {
                                gallery.add(img);
                            }
                        }
                    }
                    if (gallery.isEmpty() && card.cover != null && !card.cover.isEmpty()) {
                        gallery.add(card.cover);
                    }
                    galleryIndex = 0;
                    scroll = 0;
                    headerH = computeHeaderHeight();
                    status = items.isEmpty() ? TextFormatting.GRAY + "这条作品里没有可直接下载的模型" : "";
                });
            }

            @Override
            public void fail(String message) {
                ApiClient.post(() -> {
                    loading = false;
                    status = TextFormatting.RED + message;
                });
            }
        });
    }

    // ==================== 版式计算 ====================

    private String titleText() {
        return detail != null && detail.title != null
                ? detail.title : (card.title == null ? "(无标题)" : card.title);
    }

    /** 标题在右栏折行（按字符断，中文才会换行；最多 2 行） */
    private List<String> titleLines() {
        return TextUtil.wrapLimited(fontRenderer, titleText(), rightW, 2);
    }

    private List<Dtos.Tag> tags() {
        List<Dtos.Tag> out = new ArrayList<>();
        List<Dtos.Tag> src = new ArrayList<>();
        if (detail != null && detail.tags != null) {
            src.addAll(detail.tags);
        } else if (card.tags != null) {
            src.addAll(card.tags);
        }
        for (Dtos.Tag t : src) {
            if (t != null && t.name != null) {
                out.add(t);
            }
        }
        return out;
    }

    /** 标签在右栏折行 */
    private List<List<String>> tagRows() {
        List<List<String>> rowsArr = new ArrayList<>();
        List<String> current = new ArrayList<>();
        int used = 0;
        for (Dtos.Tag tag : tags()) {
            String text = "#" + tag.name;
            int w = fontRenderer.getStringWidth(text) + 8;
            if (used + w > rightW && !current.isEmpty()) {
                rowsArr.add(current);
                current = new ArrayList<>();
                used = 0;
            }
            current.add(text);
            used += w;
        }
        if (!current.isEmpty()) {
            rowsArr.add(current);
        }
        return rowsArr;
    }

    private String[] statPairs() {
        long dl = detail != null ? detail.downloadCount : card.downloadCount;
        long view = detail != null ? detail.viewCount : card.viewCount;
        long fav = detail != null && detail.favoriteCount != null ? detail.favoriteCount : 0;
        long cmt = detail != null && detail.commentCount != null ? detail.commentCount : 0;
        String all = "浏览 " + view + "   下载 " + dl + "   收藏 " + fav + "   评论 " + cmt;
        if (fontRenderer.getStringWidth(all) <= rightW) {
            return new String[]{all};
        }
        return new String[]{"浏览 " + view + "   下载 " + dl, "收藏 " + fav + "   评论 " + cmt};
    }

    private int rightColumnHeight() {
        int h = titleLines().size() * 10 + 2;
        h += tagRows().size() * 10 + 2;
        h += 18;
        h += statPairs().length * 10;
        return h;
    }

    private int computeHeaderHeight() {
        int thumbs = gallery.size() > 1 ? thumbSize + 4 : 0;
        return Math.max(coverH + thumbs, rightColumnHeight()) + 6;
    }

    private List<String> summaryLines() {
        String summary = detail != null ? detail.summary : card.summary;
        List<String> out = new ArrayList<>();
        if (summary == null || summary.trim().isEmpty()) {
            return out;
        }
        out.addAll(TextUtil.wrap(fontRenderer, summary, colW - 4));
        return out;
    }

    private boolean hasCollectionDeps() {
        return detail != null && detail.dependencies != null && !detail.dependencies.isEmpty();
    }

    private int computeContentHeight() {
        int h = headerH;
        if (hasCollectionDeps()) {
            h += detail.dependencies.size() * 12;
        }
        List<String> s = summaryLines();
        if (!s.isEmpty()) {
            h += s.size() * 10 + 6;
        }
        h += 14;
        h += items.size() * ITEM_ROW_H;
        h += 8;
        return h;
    }

    private int maxScroll() {
        return Math.max(0, computeContentHeight() - (bodyBottom - bodyTop));
    }

    /** 条目区在内容坐标里的起点 */
    private int itemRowTop() {
        int h = headerH;
        if (hasCollectionDeps()) {
            h += detail.dependencies.size() * 12;
        }
        List<String> s = summaryLines();
        if (!s.isEmpty()) {
            h += s.size() * 10 + 6;
        }
        return h + 14;
    }

    // ==================== 交互 ====================

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 1) {
            mc.displayGuiScreen(parent);
        } else if (button.id == 2) {
            load();
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            scroll = Math.max(0, Math.min(scroll - Integer.signum(wheel) * 18, maxScroll()));
        }
    }

    private int[] thumbBounds(int index) {
        int y = bodyTop + coverH + 4 - scroll;
        int perRow = Math.max(1, coverW / (thumbSize + 4));
        int col = index % perRow;
        int row = index / perRow;
        return new int[]{left + col * (thumbSize + 4), y + row * (thumbSize + 4), thumbSize, thumbSize};
    }

    private int[] downloadBounds(int rowY) {
        return new int[]{left + colW - DL_W - 2, rowY + 6, DL_W, DL_H};
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (mouseButton != 0) {
            return;
        }
        for (int i = 0; i < Math.min(gallery.size(), 8); i++) {
            if (hit(mouseX, mouseY, thumbBounds(i))) {
                galleryIndex = i;
                return;
            }
        }
        int cy = mouseY + scroll;
        for (int i = 0; i < items.size(); i++) {
            int[] b = downloadBounds(itemRowTop() + i * ITEM_ROW_H);
            if (mouseX >= b[0] && mouseX <= b[0] + b[2] && cy >= b[1] && cy <= b[1] + b[3]
                    && mouseY >= bodyTop && mouseY <= bodyBottom) {
                startDownload(items.get(i));
                return;
            }
        }
    }

    private boolean hit(int mx, int my, int[] b) {
        return mx >= b[0] && mx <= b[0] + b[2] && my >= b[1] && my <= b[1] + b[3];
    }

    private void startDownload(final Dtos.Item item) {
        if (!ModConfig.get().loggedIn()) {
            mc.displayGuiScreen(new GuiLogin(this));
            return;
        }
        status = TextFormatting.YELLOW + "取下载地址…";
        ApiClient.downloadInfo(item.itemId, "1.12", new ApiClient.Callback<Dtos.DownloadInfo>() {
            @Override
            public void ok(Dtos.DownloadInfo info) {
                setStatus(TextFormatting.YELLOW + "下载中：" + info.fileName);
                ApiClient.downloadFile(info, new ApiClient.Callback<File>() {
                    @Override
                    public void ok(File value) {
                        setStatus(TextFormatting.GREEN + "已保存：" + value.getName());
                        downloaded.add(item.itemId);
                    }

                    @Override
                    public void fail(String message) {
                        setStatus(TextFormatting.RED + message);
                    }
                });
            }

            @Override
            public void fail(String message) {
                setStatus(TextFormatting.RED + message);
            }
        });
    }

    private void setStatus(String text) {
        ApiClient.post(() -> status = text);
    }

    // ==================== 绘制 ====================

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        scroll = Math.max(0, Math.min(scroll, maxScroll()));


        enableScissor(left - 4, bodyTop, colW + 12, bodyBottom - bodyTop);

        // ---------- 左栏：封面 + 缩略图 ----------
        String mainImage = gallery.isEmpty()
                ? card.cover : gallery.get(Math.min(galleryIndex, gallery.size() - 1));
        drawImage(mainImage, left, bodyTop - scroll, coverW, coverH);
        if (gallery.size() > 1) {
            for (int i = 0; i < Math.min(gallery.size(), 8); i++) {
                int[] b = thumbBounds(i);
                drawImage(gallery.get(i), b[0], b[1], b[2], b[3]);
                if (i == galleryIndex) {
                    Gui.drawRect(b[0] - 1, b[1] - 1, b[0] + b[2] + 1, b[1] + b[3] + 1, 0x80FFFFFF);
                }
            }
        }

        // ---------- 右栏：标题 / 标签 / 作者 / 数据 ----------
        int y = bodyTop - scroll;
        for (String line : titleLines()) {
            drawString(fontRenderer, line, rightX, y, 0xFFFFFF);
            y += 10;
        }
        y += 2;

        for (List<String> row : tagRows()) {
            int tx = rightX;
            for (String text : row) {
                int w = fontRenderer.getStringWidth(text) + 6;
                Gui.drawRect(tx, y - 1, tx + w, y + 9, 0x402080C0);
                drawString(fontRenderer, TextFormatting.AQUA + text, tx + 3, y, 0x66CCDD);
                tx += w + 2;
            }
            y += 10;
        }
        y += 2;

        Dtos.Author author = detail != null ? detail.author : card.author;
        if (author != null) {
            drawAvatar(author, rightX, y, 16);
            String name = author.displayName != null && !author.displayName.isEmpty()
                    ? author.displayName : (author.username == null ? "" : author.username);
            drawString(fontRenderer, trim(name, rightW - 20), rightX + 20, y + 1, 0xFFFFFF);
            if (author.username != null && !author.username.isEmpty()) {
                drawString(fontRenderer, TextFormatting.GRAY + "@" + trim(author.username, rightW - 22),
                        rightX + 20, y + 10, 0x888888);
            }
            y += 18;
        }

        for (String line : statPairs()) {
            drawString(fontRenderer, TextFormatting.GRAY + line, rightX, y, 0xAAAAAA);
            y += 10;
        }

        // ---------- 全宽：依赖 / 简介 / 模型 ----------
        int cy = bodyTop - scroll + headerH;

        if (hasCollectionDeps()) {
            for (Dtos.Dependency dep : detail.dependencies) {
                String type = dep != null && dep.type != null ? dep.type : "依赖";
                String name = dep != null && dep.name != null ? dep.name : "?";
                drawString(fontRenderer, TextFormatting.DARK_PURPLE + "[" + type + "] "
                        + trim(name, colW - 44), left, cy, 0xAA88CC);
                cy += 12;
            }
        }

        List<String> lines = summaryLines();
        if (!lines.isEmpty()) {
            for (String line : lines) {
                drawString(fontRenderer, TextFormatting.GRAY + line, left, cy, 0x999999);
                cy += 10;
            }
            cy += 6;
        }

        drawString(fontRenderer, TextFormatting.GRAY + "模型（" + items.size() + "）", left, cy, 0xAAAAAA);
        cy += 14;

        for (int i = 0; i < items.size(); i++) {
            Dtos.Item item = items.get(i);
            int ry = cy + i * ITEM_ROW_H;
            Gui.drawRect(left, ry, left + colW, ry + ITEM_ROW_H - 4, 0x28000000);

            drawString(fontRenderer, trim(item.title == null ? ("#" + item.itemId) : item.title,
                    colW - DL_W - 12), left + 4, ry + 3, 0xFFFFFF);

            StringBuilder meta = new StringBuilder();
            meta.append(TextFormatting.DARK_GRAY).append('#').append(item.itemId);
            if (item.mcVersion != null) {
                meta.append("  ").append(item.mcVersion);
            }
            if (item.boxCount != null) {
                meta.append("  ").append(item.boxCount).append(" box");
            }
            if (item.grid != null) {
                meta.append("  grid ").append(item.grid);
            }
            if (item.modNamespaces != null && !item.modNamespaces.isEmpty()) {
                meta.append("   需要模组: ").append(join(item.modNamespaces));
            }
            drawString(fontRenderer, trim(meta.toString(), colW - DL_W - 10), left + 4, ry + 14, 0xAAAAAA);

            int[] b = downloadBounds(ry);
            boolean done = downloaded.contains(item.itemId);
            boolean hover = hit(mouseX, mouseY, b);
            Gui.drawRect(b[0], b[1], b[0] + b[2], b[1] + b[3],
                    done ? 0xFF2B4A20 : (hover ? 0xFF3E7A2C : 0xFF2F5424));
            drawCenteredString(fontRenderer, done ? "已下载" : "下载", b[0] + b[2] / 2, b[1] + 5, 0xFFFFFF);
        }

        disableScissor();

        // ---------- 固定页脚 ----------
        int cx = width / 2;
        if (!status.isEmpty()) {
            drawCenteredString(fontRenderer, status, cx, height - 40, 0xFFFFFF);
        }
        String hint = maxScroll() > 0 ? "（滚轮可滚动）" : "";
        drawCenteredString(fontRenderer, TextFormatting.DARK_GRAY
                + "保存到 .minecraft/" + ModConfig.get().downloadFolder().getName() + hint,
                cx, height - 52, 0x888888);

        super.drawScreen(mouseX, mouseY, partialTicks);

        // 滚动条
        if (maxScroll() > 0) {
            int barX = left + colW + 4;
            int barH = bodyBottom - bodyTop;
            Gui.drawRect(barX, bodyTop, barX + 2, bodyBottom, 0x30FFFFFF);
            int thumbH = Math.max(12, (int) ((long) barH * barH / (barH + maxScroll())));
            int thumbY = bodyTop + (int) ((long) (barH - thumbH) * scroll / maxScroll());
            Gui.drawRect(barX, thumbY, barX + 2, thumbY + thumbH, 0x90FFFFFF);
        }
    }

    /** 有头像就用头像；没有就画个首字母色块（站点上很多人没传头像） */
    private void drawAvatar(Dtos.Author author, int x, int y, int size) {
        String url = author.avatarUrl;
        if (url != null && !url.isEmpty() && ImageCache.img(url) != null) {
            drawImage(url, x, y, size, size);
            return;
        }
        String seed = author.username == null ? "?" : author.username;
        int hash = Math.abs(seed.hashCode());
        int r = (hash & 0x7F) + 0x30;
        int g = ((hash >> 8) & 0x7F) + 0x30;
        int b = ((hash >> 16) & 0x7F) + 0x30;
        Gui.drawRect(x, y, x + size, y + size, 0xFF000000 | (r << 16) | (g << 8) | b);
        String letter = seed.isEmpty() ? "?" : seed.substring(0, 1).toUpperCase();
        drawCenteredString(fontRenderer, letter, x + size / 2, y + (size - 8) / 2, 0xFFFFFF);
    }

    private String join(List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size() && i < 3; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(list.get(i));
        }
        if (list.size() > 3) {
            sb.append("…");
        }
        return sb.toString();
    }

    // ==================== 工具 ====================

    /** 等比裁剪绘制（不拉伸）；封面用 4:3，缩略图用同样的裁剪逻辑 */
    private void drawImage(String url, int x, int y, int w, int h) {
        ImageDraw.drawCropped(url, x, y, w, h);
    }

    private double guiScale() {
        return mc.displayWidth / (double) Math.max(1, width);
    }

    /** 1.12.2 的 GlStateManager 没有 scissor 包装，直接用 GL11（注意 y 轴翻转） */
    private void enableScissor(int x, int y, int w, int h) {
        double s = guiScale();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor((int) (x * s), (int) ((height - (y + h)) * s),
                (int) Math.max(0, w * s), (int) Math.max(0, h * s));
    }

    private void disableScissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    private String trim(String text, int maxWidth) {
        if (text == null) {
            return "";
        }
        if (fontRenderer.getStringWidth(text) <= maxWidth) {
            return text;
        }
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            sb.append(c);
            if (fontRenderer.getStringWidth(sb.toString() + "…") > maxWidth) {
                return sb.substring(0, Math.max(0, sb.length() - 1)) + "…";
            }
        }
        return text;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
