package work.inception.ltsmodelshare.gui;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import work.inception.ltsmodelshare.ModConfig;
import work.inception.ltsmodelshare.api.ApiClient;
import work.inception.ltsmodelshare.api.Dtos;
import work.inception.ltsmodelshare.client.ImageCache;
import work.inception.ltsmodelshare.client.ImageDraw;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 模型浏览：像网页那样按卡片排（封面 + 标题 + 作者头像 + 下载/浏览数），
 * 支持搜索、翻页、点卡片进详情。
 *
 * <p>排版全部按 {@code width/height} 现算，避免不同 GUI 缩放下文字互相压。</p>
 */
public class GuiModels extends GuiScreen {

    /*
     * 布局全部**按窗口现算**：GUI 缩放 / 窗口大小都会变，写死就会出现
     * 「第三列被切掉」和「下载数压到作者名上」这种问题（截图里踩过）。
     */
    private int cols = 3;
    private int rows = 2;
    private int cardW = 100;
    private int cardH = 74;
    /** 卡片背景遮罩比内容高 0.5 行字（约 5px），内容位置不变 */
    private static final int CARD_BG_EXTRA = 5;
    private int cardBgH = 79;
    private int imgH = 50;
    private static final int GAP_X = 6;
    private static final int GAP_Y = 6;
    /** 一页多少张（随窗口变化） */
    private int perPage = 6;

    /**
     * 先定卡片尺寸，再由窗口宽度反推列数。
     *
     * （反过来"按宽度算卡片尺寸"在 GUI 缩放很大时会算出巨大的卡片 —— 截图里就只剩两列、
     * 文字还挤在一起。固定尺寸 + 自适应列数在任何缩放下都稳。）
     */
    private void computeLayout() {
        int margin = 8;
        /*
         * 首页卡片：封面缩小、作者与数据用小一号字（0.75 倍），标题保持原大小。
         * 文字区高度 = 标题 9 + 作者 7 + 数据 7（共 24），比原来矮两行左右。
         */
        cardW = width >= 300 ? 78 : (width >= 210 ? 66 : Math.max(44, width - margin * 2));
        imgH = Math.max(24, cardW * 9 / 16);
        cardH = imgH + 24;
        cardBgH = cardH + CARD_BG_EXTRA;

        cols = Math.max(1, (width - margin * 2 + GAP_X) / (cardW + GAP_X));
        // 最多 3 列：再多作者名就挤没了
        cols = Math.min(cols, 3);

        // 底部留 44px 给按钮组，顶部留 50px 给标题+搜索
        int top = 50;
        int bottom = height - 44;
        rows = Math.max(1, Math.min(3, (bottom - top + GAP_Y) / (cardBgH + GAP_Y)));
        perPage = cols * rows;

        int gridW = cols * cardW + (cols - 1) * GAP_X;
        int gridH = rows * cardBgH + (rows - 1) * GAP_Y;
        gridLeft = Math.max(margin, (width - gridW) / 2);
        gridTop = top + Math.max(0, (bottom - top - gridH) / 2);
    }

    private final List<Dtos.WorkCard> cards = new ArrayList<>();
    private int page = 1;
    private long total;
    private String status = "";
    private boolean loading;

    private GuiTextField searchField;
    /** 高级搜索的条件（作者 / 标签 / 排序） */
    private GuiAdvancedSearch.Filter filter = new GuiAdvancedSearch.Filter();
    private int gridLeft;
    private int gridTop;

    @Override
    public void initGui() {
        buttonList.clear();
        int cx = width / 2;

        int topW = Math.min(360, width - 16);
        int searchW = Math.max(70, topW - 152);
        int topX = cx - topW / 2;
        searchField = new GuiTextField(0, fontRenderer, topX, 24, searchW, 18);
        searchField.setMaxStringLength(64);
        searchField.setText(filter.q == null ? "" : filter.q);
        searchField.setFocused(true);

        buttonList.add(new GuiButton(1, topX + searchW + 4, 23, 44, 20, "搜索"));
        buttonList.add(new GuiButton(2, topX + searchW + 52, 23, 40, 20, "刷新"));
        buttonList.add(new GuiButton(8, topX + searchW + 96, 23, 56, 20, "高级搜索"));
        int by = height - 26;
        buttonList.add(new GuiButton(3, cx - 150, by, 56, 20, "上一页"));
        buttonList.add(new GuiButton(4, cx - 90, by, 56, 20, "下一页"));
        buttonList.add(new GuiButton(5, cx - 20, by, 66, 20, ModConfig.get().loggedIn() ? "退出登录" : "登录"));
        buttonList.add(new GuiButton(7, cx + 50, by, 78, 20, "上传蓝图"));
        buttonList.add(new GuiButton(6, cx + 132, by, 46, 20, "关闭"));

        computeLayout();
        reload();
    }

    // ==================== 数据 ====================

    private void reload() {
        if (loading) {
            return;
        }
        loading = true;
        status = TextFormatting.GRAY + "加载中…";
        Dtos.CardQuery query = new Dtos.CardQuery(searchField.getText().trim(),
                filter.sort == null ? "latest" : filter.sort, page, perPage, languageTag());
        query.authorId = filter.authorId;
        if (!filter.tagCodes.isEmpty()) {
            query.tagCodes = new java.util.ArrayList<>(filter.tagCodes);
        }
        ApiClient.listCards(query, new ApiClient.Callback<Dtos.Page<Dtos.WorkCard>>() {
            @Override
            public void ok(Dtos.Page<Dtos.WorkCard> value) {
                ApiClient.post(() -> {
                    loading = false;
                    cards.clear();
                    if (value.records != null) {
                        cards.addAll(value.records);
                    }
                    total = value.total;
                    status = cards.isEmpty() ? TextFormatting.GRAY + "没有结果" : "";
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

    /** 高级搜索点「应用」时回传条件：记住并重新查第一页 */
    public void applyFilter(GuiAdvancedSearch.Filter next) {
        this.filter = next;
        if (searchField != null) {
            // 关键词以搜索框为准，高级搜索只管作者/标签/排序
            filter.q = searchField.getText().trim();
        }
        page = 1;
        reload();
    }

    private int maxPage() {
        return (int) Math.max(1, (total + perPage - 1) / perPage);
    }

    /** zh-CN 这种给站点用；拿不到就用 en */
    private String languageTag() {
        try {
            String code = mc.getLanguageManager().getCurrentLanguage().getLanguageCode();
            return code == null || code.isEmpty() ? "zh-CN" : code;
        } catch (Exception e) {
            return "zh-CN";
        }
    }

    // ==================== 交互 ====================

    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case 1:
            case 2:
                page = 1;
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
            case 7:
                mc.displayGuiScreen(new GuiUpload(this));
                break;
            case 8:
                mc.displayGuiScreen(new GuiAdvancedSearch(this, filter));
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
        /*
         * ESC 必须先处理：搜索框聚焦时下面会 return，
         * 不先拦的话 ESC 会被当成搜索框输入（什么也不发生），面板就关不掉。
         */
        if (keyCode == 1) {
            mc.displayGuiScreen(null);
            return;
        }
        if (searchField.isFocused()) {
            searchField.textboxKeyTyped(typedChar, keyCode);
            if (keyCode == 28) {
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
        int idx = cardAt(mouseX, mouseY);
        if (idx >= 0 && idx < cards.size()) {
            mc.displayGuiScreen(new GuiWorkDetail(cards.get(idx), this));
        }
    }

    /** 命中第几张卡片（-1 没命中） */
    private int cardAt(int mouseX, int mouseY) {
        for (int i = 0; i < Math.min(cards.size(), perPage); i++) {
            int col = i % cols;
            int row = i / cols;
            int x = gridLeft + col * (cardW + GAP_X);
            int y = gridTop + row * (cardBgH + GAP_Y);
            if (mouseX >= x && mouseX <= x + cardW && mouseY >= y && mouseY <= y + cardBgH) {
                return i;
            }
        }
        return -1;
    }

    // ==================== 绘制 ====================

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int cx = width / 2;

        drawCenteredString(fontRenderer, "Littletiles Model Share", cx, 8, 0xFFFFFF);
        searchField.drawTextBox();
        if (searchField.getText().isEmpty() && !searchField.isFocused()) {
            drawString(fontRenderer, TextFormatting.DARK_GRAY + "搜索标题…", searchField.x + 4, searchField.y + 5, 0x777777);
        }

        int hovered = cardAt(mouseX, mouseY);
        for (int i = 0; i < Math.min(cards.size(), perPage); i++) {
            drawCard(i, cards.get(i), hovered == i);
        }

        if (cards.isEmpty() && !loading) {
            drawCenteredString(fontRenderer, TextFormatting.GRAY + "这里没有作品", cx, gridTop + cardBgH / 2, 0xAAAAAA);
        }
        if (!status.isEmpty()) {
            drawCenteredString(fontRenderer, status, cx, height - 54, 0xFFFFFF);
        }
        /*
         * 页脚：页码 + 登录状态 + 筛选摘要。
         * 标签选多了摘要会很长 —— 超出窗口宽度就用 … 收尾（原来是直接画出去，被裁掉半截）。
         */
        String base = TextFormatting.GRAY + String.format("第 %d / %d 页 · 共 %d 个", page, maxPage(), total)
                + (ModConfig.get().loggedIn() ? " · 已登录 " + ModConfig.get().username : " · 未登录（只能浏览）");
        String filterPart = "";
        if (filter != null && !filter.isEmpty()) {
            int baseW = fontRenderer.getStringWidth(stripCodes(base));
            int room = width - 24 - baseW - fontRenderer.getStringWidth("  []");
            if (room > 18) {
                filterPart = "  [" + trim(filter.summary(), room) + "]";
            }
        }
        drawCenteredString(fontRenderer, base + TextFormatting.YELLOW + filterPart, cx, height - 40, 0xAAAAAA);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawCard(int index, Dtos.WorkCard card, boolean hovered) {
        int col = index % cols;
        int row = index / cols;
        int x = gridLeft + col * (cardW + GAP_X);
        int y = gridTop + row * (cardBgH + GAP_Y);

        // 卡片底（比内容高 0.5 行字，视觉上更透气）
        Gui.drawRect(x, y, x + cardW, y + cardBgH, hovered ? 0x50FFFFFF : 0x30000000);
        Gui.drawRect(x, y, x + cardW, y + 1, hovered ? 0xFFFFFFFF : 0x55FFFFFF);

        // 封面（16:9，内缩 3px）
        int imgX = x + 3;
        int imgY = y + 3;
        int imgW = cardW - 6;
        drawImage(card.cover, imgX, imgY, imgW, imgH, 0x30FFFFFF);

        // 第一行：标题（保持原大小）
        int textY = imgY + imgH + 1;
        drawString(fontRenderer, trim(card.title == null ? "(无标题)" : card.title, cardW - 6),
                x + 3, textY, 0xFFFFFF);

        // 第二行：作者头像 + 名字（0.75 倍字）
        int ay = textY + 9;
        int avatarSize = 6;
        Dtos.Author author = card.author;
        int cursor = x + 3;
        String name = "";
        if (author != null) {
            drawImage(author.avatarUrl, cursor, ay, avatarSize, avatarSize, 0x40FFFFFF);
            cursor += avatarSize + 2;
            name = author.displayName != null && !author.displayName.isEmpty()
                    ? author.displayName : (author.username == null ? "" : author.username);
        }
        drawSmall(trim(name, (int) ((cardW - 6 - (cursor - x - 3)) / SMALL_TEXT)), cursor, ay, 0xCCCCCC);

        // 第三行：下载 / 浏览（同样 0.75 倍）
        drawSmall(trim("下载 " + card.downloadCount + "  浏览 " + card.viewCount,
                (int) ((cardW - 6) / SMALL_TEXT)), x + 3, ay + 7, 0xAAAAAA);
    }

    /** 小一号字（0.75 倍）—— 作者与数据用，标题不用 */
    private static final float SMALL_TEXT = 0.75f;

    private void drawSmall(String text, int x, int y, int color) {
        if (text == null || text.isEmpty()) {
            return;
        }
        GlStateManager.pushMatrix();
        GlStateManager.scale(SMALL_TEXT, SMALL_TEXT, 1f);
        drawString(fontRenderer, text, (int) (x / SMALL_TEXT), (int) (y / SMALL_TEXT), color);
        GlStateManager.popMatrix();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    /**
     * 画一张远程图片；没加载好就画占位块。
     * 用 drawModalRectWithCustomSizedTexture 让图片缩放到目标尺寸（保持 16:9 由调用方算好）。
     */
    /** 等比裁剪绘制（不拉伸）；卡片封面按 16:9 的框裁 */
    private void drawImage(String url, int x, int y, int w, int h, int placeholderColor) {
        if (ImageCache.img(url) == null) {
            Gui.drawRect(x, y, x + w, y + h, placeholderColor);
        }
        ImageDraw.drawCropped(url, x, y, w, h);
    }

    /** 去掉 §x 颜色码（算宽度时要排除，不然会被算成两个字符） */
    private String stripCodes(String text) {
        return text == null ? "" : text.replaceAll("\u00A7.", "");
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
