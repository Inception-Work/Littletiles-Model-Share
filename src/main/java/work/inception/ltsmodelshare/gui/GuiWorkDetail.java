package work.inception.ltsmodelshare.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.text.TextFormatting;
import work.inception.ltsmodelshare.ModConfig;
import work.inception.ltsmodelshare.api.ApiClient;
import work.inception.ltsmodelshare.api.Dtos;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 作品详情：拉 /api/public/work/detail，列出这条作品里的模型，点「下载」落盘。
 *
 * <p>下载走两步：先 POST /api/work/download-snbt 拿地址（需要登录），再 GET 那个地址。
 * 文件存到 {@code ltsmodels/} 目录，路径会显示在界面上。</p>
 */
public class GuiWorkDetail extends GuiScreen {

    private final Dtos.WorkCard card;
    private final GuiScreen parent;

    private Dtos.WorkDetail detail;
    private final List<Dtos.Item> items = new ArrayList<>();
    private String status = "";
    private boolean loading;

    private int listTop = 84;
    private static final int ROW_H = 30;

    public GuiWorkDetail(Dtos.WorkCard card, GuiScreen parent) {
        this.card = card;
        this.parent = parent;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int cx = width / 2;
        buttonList.add(new GuiButton(1, cx - 100, height - 30, 80, 20, "返回"));
        buttonList.add(new GuiButton(2, cx + 20, height - 30, 80, 20, "刷新"));
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

    private int[] downloadButtonBounds(int index) {
        int cx = width / 2;
        int y = listTop + index * ROW_H;
        return new int[]{cx + 90, y + 4, 60, 18};
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
        // 1.12 客户端优先要 1.12 那一代（站点的模型多半是 1.12 上传的；转换由服务端做）
        ApiClient.downloadInfo(item.itemId, "1.12", new ApiClient.Callback<Dtos.DownloadInfo>() {
            @Override
            public void ok(Dtos.DownloadInfo info) {
                setStatus(TextFormatting.YELLOW + "下载中：" + info.fileName);
                ApiClient.downloadFile(info, new ApiClient.Callback<File>() {
                    @Override
                    public void ok(File value) {
                        setStatus(TextFormatting.GREEN + "已保存：" + value.getName());
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

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int cx = width / 2;

        String title = detail != null && detail.title != null ? detail.title
                : (card.title != null ? card.title : "(无标题)");
        drawCenteredString(fontRenderer, title, cx, 16, 0xFFFFFF);

        String author = "";
        Dtos.Author a = detail != null ? detail.author : card.author;
        if (a != null) {
            author = a.displayName != null ? a.displayName : a.username;
        }
        drawCenteredString(fontRenderer, TextFormatting.GRAY + "作者 " + author
                + "   下载 " + (detail != null ? detail.downloadCount : card.downloadCount)
                + "   浏览 " + (detail != null ? detail.viewCount : card.viewCount), cx, 30, 0xAAAAAA);

        String summary = detail != null ? detail.summary : card.summary;
        if (summary != null && !summary.isEmpty()) {
            drawCenteredString(fontRenderer, TextFormatting.DARK_GRAY + trim(summary, width - 60), cx, 44, 0x999999);
        }

        drawString(fontRenderer, TextFormatting.GRAY + "模型（点击右侧「下载」）", cx - 150, listTop - 14, 0xAAAAAA);

        for (int i = 0; i < items.size(); i++) {
            Dtos.Item item = items.get(i);
            int y = listTop + i * ROW_H;
            int left = cx - 150;
            int right = cx + 150;
            drawRect(left, y, right, y + ROW_H - 4, 0x20000000);

            drawString(fontRenderer, item.title == null ? ("#" + item.itemId) : item.title, left + 4, y + 3, 0xFFFFFF);
            StringBuilder metaSb = new StringBuilder();
            metaSb.append(TextFormatting.DARK_GRAY).append('#').append(item.itemId);
            if (item.mcVersion != null) metaSb.append("   ").append(item.mcVersion);
            if (item.boxCount != null) metaSb.append("   ").append(item.boxCount).append(" box");
            if (item.grid != null) metaSb.append("   grid ").append(item.grid);
            if (item.preview != null && item.preview.sizeBytes != null) {
                metaSb.append("   ").append(item.preview.sizeBytes / 1024).append(" KB");
            }
            String meta = metaSb.toString();
            drawString(fontRenderer, meta, left + 4, y + 14, 0xAAAAAA);

            int[] b = downloadButtonBounds(i);
            boolean hover = mouseX >= b[0] && mouseX <= b[0] + b[2] && mouseY >= b[1] && mouseY <= b[1] + b[3];
            drawRect(b[0], b[1], b[0] + b[2], b[1] + b[3], hover ? 0xFF3A6B2A : 0xFF2C4A22);
            drawCenteredString(fontRenderer, "下载", b[0] + b[2] / 2, b[1] + 5, 0xFFFFFF);
        }

        if (!status.isEmpty()) {
            drawCenteredString(fontRenderer, status, cx, height - 46, 0xFFFFFF);
        }
        drawCenteredString(fontRenderer, TextFormatting.DARK_GRAY
                + "下载目录：" + ModConfig.get().downloadFolder().getAbsolutePath(), cx, height - 58, 0x888888);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private String trim(String text, int maxWidth) {
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
