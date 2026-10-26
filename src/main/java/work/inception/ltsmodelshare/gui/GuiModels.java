package work.inception.ltsmodelshare.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.text.TextFormatting;
import work.inception.ltsmodelshare.ModConfig;
import work.inception.ltsmodelshare.api.ApiClient;
import work.inception.ltsmodelshare.api.Dtos;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 模型列表：搜索 + 分页 + 点一行进详情。
 *
 * <p>数据来自 {@code POST /api/public/work/cards}（和网页端同一个接口，游客也能看，
 * 登录只是为了下载）。</p>
 */
public class GuiModels extends GuiScreen {

    private static final int PER_PAGE = 8;

    private final List<Dtos.WorkCard> rows = new ArrayList<>();
    private int page = 1;
    private long total;
    private String status = "";
    private boolean loading;

    private GuiTextField searchField;

    /** 行区域 */
    private int listTop;
    private int rowHeight = 22;

    @Override
    public void initGui() {
        buttonList.clear();
        int cx = width / 2;

        searchField = new GuiTextField(0, fontRenderer, cx - 150, 26, 200, 18);
        searchField.setMaxStringLength(64);
        searchField.setFocused(true);

        buttonList.add(new GuiButton(1, cx + 54, 25, 46, 20, "搜索"));
        buttonList.add(new GuiButton(2, cx + 104, 25, 46, 20, "刷新"));
        buttonList.add(new GuiButton(3, cx - 150, height - 30, 60, 20, "上一页"));
        buttonList.add(new GuiButton(4, cx - 85, height - 30, 60, 20, "下一页"));
        buttonList.add(new GuiButton(5, cx + 30, height - 30, 60, 20,
                ModConfig.get().loggedIn() ? "退出登录" : "登录"));
        buttonList.add(new GuiButton(6, cx + 95, height - 30, 60, 20, "关闭"));

        listTop = 52;
        reload();
    }

    // ==================== 数据 ====================

    private void reload() {
        if (loading) {
            return;
        }
        loading = true;
        status = TextFormatting.GRAY + "加载中…";
        Dtos.CardQuery query = new Dtos.CardQuery(searchField.getText().trim(), "latest", page, PER_PAGE,
                mc.getLanguageManager().getCurrentLanguage().getLanguageCode());
        ApiClient.listCards(query, new ApiClient.Callback<Dtos.Page<Dtos.WorkCard>>() {
            @Override
            public void ok(Dtos.Page<Dtos.WorkCard> value) {
                ApiClient.post(() -> {
                    loading = false;
                    rows.clear();
                    if (value.records != null) {
                        rows.addAll(value.records);
                    }
                    total = value.total;
                    status = rows.isEmpty() ? TextFormatting.GRAY + "没有结果" : "";
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

    private int maxPage() {
        return (int) Math.max(1, (total + PER_PAGE - 1) / PER_PAGE);
    }

    // ==================== 交互 ====================

    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case 1:
                page = 1;
                reload();
                break;
            case 2:
                reload();
                break;
            case 3:
                if (page > 1) {
                    page--;
                    reload();
                }
                break;
            case 4:
                if (page < maxPage()) {
                    page++;
                    reload();
                }
                break;
            case 5:
                if (ModConfig.get().loggedIn()) {
                    ModConfig cfg = ModConfig.get();
                    cfg.token = "";
                    cfg.refreshToken = "";
                    cfg.save();
                    button.displayString = "登录";
                    status = TextFormatting.YELLOW + "已退出登录";
                } else {
                    mc.displayGuiScreen(new GuiLogin(this));
                }
                break;
            case 6:
                mc.displayGuiScreen(null);
                break;
            default:
                break;
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (searchField.isFocused()) {
            searchField.textboxKeyTyped(typedChar, keyCode);
            if (keyCode == 28) { // Enter
                page = 1;
                reload();
            }
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        searchField.mouseClicked(mouseX, mouseY, mouseButton);
        if (mouseButton != 0) {
            return;
        }
        int idx = rowAt(mouseX, mouseY);
        if (idx >= 0 && idx < rows.size()) {
            Dtos.WorkCard card = rows.get(idx);
            mc.displayGuiScreen(new GuiWorkDetail(card, this));
        }
    }

    /** 鼠标落在第几行（-1 = 没命中） */
    private int rowAt(int mouseX, int mouseY) {
        int left = width / 2 - 150;
        int right = width / 2 + 150;
        if (mouseX < left || mouseX > right) {
            return -1;
        }
        int rel = mouseY - listTop;
        if (rel < 0) {
            return -1;
        }
        int idx = rel / rowHeight;
        return idx < PER_PAGE ? idx : -1;
    }

    // ==================== 绘制 ====================

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int cx = width / 2;

        drawCenteredString(fontRenderer, "Littletiles Model Share", cx, 10, 0xFFFFFF);
        searchField.drawTextBox();

        int hovered = rowAt(mouseX, mouseY);
        for (int i = 0; i < PER_PAGE; i++) {
            int y = listTop + i * rowHeight;
            int left = cx - 150;
            int right = cx + 150;

            boolean has = i < rows.size();
            if (hovered == i && has) {
                drawRect(left, y, right, y + rowHeight - 2, 0x40FFFFFF);
            } else if (has) {
                drawRect(left, y, right, y + rowHeight - 2, 0x20000000);
            }
            if (!has) {
                continue;
            }
            Dtos.WorkCard card = rows.get(i);
            String title = card.title == null ? "(无标题)" : card.title;
            String author = card.author == null ? ""
                    : (card.author.displayName != null ? card.author.displayName : card.author.username);
            String meta = TextFormatting.DARK_GRAY + "· " + author
                    + "  下载 " + card.downloadCount + "  浏览 " + card.viewCount;

            drawString(fontRenderer, trim(title, 150), left + 4, y + 3, 0xFFFFFF);
            drawString(fontRenderer, meta, left + 4, y + 12, 0xAAAAAA);
        }

        if (!status.isEmpty()) {
            drawCenteredString(fontRenderer, status, cx, height - 46, 0xFFFFFF);
        }
        String foot = TextFormatting.GRAY + "第 " + page + " / " + maxPage() + " 页   共 " + total + " 个"
                + (ModConfig.get().loggedIn() ? "   已登录：" + ModConfig.get().username : "   未登录（只能浏览）");
        drawCenteredString(fontRenderer, foot, cx, height - 44, 0xAAAAAA);

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
