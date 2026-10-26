package work.inception.ltsmodelshare.gui;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import work.inception.ltsmodelshare.ModConfig;
import work.inception.ltsmodelshare.api.ApiClient;
import work.inception.ltsmodelshare.api.Dtos;
import work.inception.ltsmodelshare.client.ImageCache;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 作品详情：上面是封面 + 作者信息 + 下载/浏览数据，下面逐条列出模型并「下载」。
 *
 * <p>排版改成"分段算 y"（每段画完累加），不再用一个固定 listTop，
 * 这样不同 GUI 缩放 / 不同文字长度都不会互相压住。</p>
 */
public class GuiWorkDetail extends GuiScreen {

    private final Dtos.WorkCard card;
    private final GuiScreen parent;

    private Dtos.WorkDetail detail;
    private final List<Dtos.Item> items = new ArrayList<>();
    private String status = "";
    private boolean loading;

    /** 本次会话里下过的条目（显示「已下载」） */
    private final List<Long> downloaded = new ArrayList<>();

    private static final int COVER_W = 120;
    private static final int COVER_H = 68;   // 16:9
    private static final int ROW_H = 30;
    private static final int TOP = 30;

    public GuiWorkDetail(Dtos.WorkCard card, GuiScreen parent) {
        this.card = card;
        this.parent = parent;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int cx = width / 2;
        buttonList.add(new GuiButton(1, cx - 100, height - 26, 70, 20, "返回"));
        buttonList.add(new GuiButton(2, cx + 20, height - 26, 70, 20, "刷新"));
        load();
    }

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

    // ==================== 交互 ====================

    /** 条目区起点（绘制与点击判定共用） */
    private int listTop() {
        return TOP + COVER_H + 30;
    }

    private int[] downloadButtonBounds(int index) {
        int cx = width / 2;
        int y = listTop() + index * ROW_H;
        return new int[]{cx + 92, y + 5, 58, 18};
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 1) {
            mc.displayGuiScreen(parent);
        } else if (button.id == 2) {
            load();
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (mouseButton != 0) {
            return;
        }
        for (int i = 0; i < items.size(); i++) {
            int[] b = downloadButtonBounds(i);
            if (mouseX >= b[0] && mouseX <= b[0] + b[2] && mouseY >= b[1] && mouseY <= b[1] + b[3]) {
                startDownload(items.get(i));
                return;
            }
        }
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
                        work.inception.ltsmodelshare.LtsModelShare.LOG.info("模型已保存到 {}", value.getAbsolutePath());
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
        int cx = width / 2;
        int x = cx - 150;

        // ---------- 头部：封面 ----------
        String cover = detail != null && detail.images != null && !detail.images.isEmpty()
                ? detail.images.get(0) : card.cover;
        drawImage(cover, x, TOP, COVER_W, COVER_H);

        // ---------- 右上：标题 / 作者 / 数据 / 标签 ----------
        int textX = x + COVER_W + 8;
        int textW = 300 - COVER_W - 8;
        int textY = TOP;

        String title = detail != null && detail.title != null ? detail.title
                : (card.title == null ? "(无标题)" : card.title);
        drawString(fontRenderer, trim(title, textW), textX, textY, 0xFFFFFF);

        Dtos.Author author = detail != null ? detail.author : card.author;
        if (author != null) {
            drawImage(author.avatarUrl, textX, textY + 12, 12, 12);
            String name = author.displayName != null && !author.displayName.isEmpty()
                    ? author.displayName : (author.username == null ? "" : author.username);
            drawString(fontRenderer, trim(name, textW - 16), textX + 15, textY + 14, 0xCCCCCC);
        }

        long dl = detail != null ? detail.downloadCount : card.downloadCount;
        long view = detail != null ? detail.viewCount : card.viewCount;
        drawString(fontRenderer, TextFormatting.GRAY + "↓ 下载 " + dl + "    ◉ 浏览 " + view,
                textX, textY + 28, 0xAAAAAA);

        if (detail != null && detail.tags != null && !detail.tags.isEmpty()) {
            StringBuilder tags = new StringBuilder();
            int shown = 0;
            for (Dtos.Tag tag : detail.tags) {
                if (tag == null || tag.name == null) {
                    continue;
                }
                if (shown > 0) {
                    tags.append(" · ");
                }
                tags.append('#').append(tag.name);
                if (++shown >= 3) {
                    break;
                }
            }
            drawString(fontRenderer, TextFormatting.DARK_AQUA + trim(tags.toString(), textW),
                    textX, textY + 40, 0x55AAAA);
        }

        // ---------- 简介：自动换行，最多 2 行（不再挤到条目区） ----------
        String summary = detail != null ? detail.summary : card.summary;
        int summaryY = TOP + COVER_H + 4;
        if (summary != null && !summary.isEmpty()) {
            List<String> lines = fontRenderer.listFormattedStringToWidth(summary, 300);
            for (int i = 0; i < Math.min(2, lines.size()); i++) {
                drawString(fontRenderer, TextFormatting.GRAY + lines.get(i), x, summaryY + i * 10, 0x999999);
            }
        }

        // ---------- 模型条目 ----------
        int top = listTop();
        drawString(fontRenderer, TextFormatting.GRAY + "模型（点右侧「下载」）", x, top - 12, 0xAAAAAA);

        for (int i = 0; i < items.size(); i++) {
            Dtos.Item item = items.get(i);
            int y = top + i * ROW_H;

            Gui.drawRect(x, y, x + 300, y + ROW_H - 4, 0x28000000);

            drawString(fontRenderer, trim(item.title == null ? ("#" + item.itemId) : item.title, 220),
                    x + 4, y + 4, 0xFFFFFF);

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
            drawString(fontRenderer, meta.toString(), x + 4, y + 15, 0xAAAAAA);

            int[] b = downloadButtonBounds(i);
            boolean done = downloaded.contains(item.itemId);
            boolean hover = mouseX >= b[0] && mouseX <= b[0] + b[2] && mouseY >= b[1] && mouseY <= b[1] + b[3];
            Gui.drawRect(b[0], b[1], b[0] + b[2], b[1] + b[3],
                    done ? 0xFF2B4A20 : (hover ? 0xFF3E7A2C : 0xFF2F5424));
            drawCenteredString(fontRenderer, done ? "已下载" : "下载",
                    b[0] + b[2] / 2, b[1] + 5, 0xFFFFFF);
        }

        // ---------- 底部 ----------
        if (!status.isEmpty()) {
            drawCenteredString(fontRenderer, status, cx, height - 40, 0xFFFFFF);
        }
        drawCenteredString(fontRenderer, TextFormatting.DARK_GRAY + "保存到：" + ModConfig.get().downloadFolder().getAbsolutePath(),
                cx, height - 52, 0x888888);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawImage(String url, int x, int y, int w, int h) {
        ResourceLocation tex = ImageCache.get(url);
        if (tex == null) {
            Gui.drawRect(x, y, x + w, y + h, 0x40FFFFFF);
            return;
        }
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        mc.getTextureManager().bindTexture(tex);
        Gui.drawModalRectWithCustomSizedTexture(x, y, 0, 0, w, h, w, h);
        GlStateManager.disableBlend();
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
