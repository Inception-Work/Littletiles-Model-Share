package work.inception.ltsmodelshare.gui;

import static work.inception.ltsmodelshare.client.Translations.tr;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
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
        ModelGridLayout layout = new ModelGridLayout(width, height);
        cardW = layout.cardWidth;
        imgH = layout.imageHeight;
        cardBgH = layout.cardHeight;
        cols = layout.columns;
        rows = layout.rows;
        perPage = cols * rows;
        gridLeft = layout.left;
        gridTop = layout.top;
    }

    private final List<Dtos.WorkCard> cards = new ArrayList<>();
    private int page = 1;
    private long total;
    private String status = "";
    private boolean loading;
    /** 只接受最后一次查询的结果；调整窗口时旧尺寸的请求可能比新请求晚返回。 */
    private int requestId;

    private GuiTextField searchField;
    /** 高级搜索的条件（作者 / 标签 / 排序） */
    private GuiAdvancedSearch.Filter filter = new GuiAdvancedSearch.Filter();
    private int gridLeft;
    private int gridTop;

    @Override
    public void initGui() {
        // MC 1.12.2 调整窗口 / GUI 缩放时会重新调用 initGui()。先记住旧布局是否在末页，
        // 新布局算出每页数量后才能将旧末页映射到新末页（仅 clamp 无法处理页数增加）。
        boolean wasLastPage = page == maxPage();
        String searchText = searchField == null ? filter.q : searchField.getText();
        boolean searchFocused = searchField == null || searchField.isFocused();
        buttonList.clear();
        int cx = width / 2;

        int topW = Math.min(360, width - 16);
        int searchW = Math.max(70, topW - 152);
        int topX = cx - topW / 2;
        searchField = new GuiTextField(0, fontRenderer, topX, 24, searchW, 18);
        searchField.setMaxStringLength(64);
        searchField.setText(searchText == null ? "" : searchText);
        searchField.setFocused(searchFocused);

        buttonList.add(new LocalizedButton(1, topX + searchW + 4, 23, 44, 20, tr("ltsmodelshare.common.search")));
        buttonList.add(new LocalizedButton(2, topX + searchW + 52, 23, 40, 20, tr("ltsmodelshare.common.refresh")));
        buttonList.add(new LocalizedButton(8, topX + searchW + 96, 23, 56, 20, tr("ltsmodelshare.search.filters")));
        // Pagination occupies its own row; page actions leave the fixed bottom-right exit free.
        int pageY = GuiNavigation.footerY(height) - 26;
        buttonList.add(new LocalizedButton(3, GuiNavigation.MARGIN, pageY, 42, 20,
                tr("ltsmodelshare.common.previous")));
        buttonList.add(new LocalizedButton(4, width - GuiNavigation.MARGIN - 42, pageY, 42, 20,
                tr("ltsmodelshare.common.next")));
        int by = GuiNavigation.footerY(height);
        int actionWidth = GuiNavigation.actionWidth(width, 3, 90);
        int bx = GuiNavigation.MARGIN;
        buttonList.add(new LocalizedButton(5, bx, by, actionWidth, 20,
                ModConfig.get().loggedIn() ? tr("ltsmodelshare.models.logout") : tr("ltsmodelshare.common.login")));
        bx += actionWidth + GuiNavigation.GAP;
        buttonList.add(new LocalizedButton(7, bx, by, actionWidth, 20, tr("ltsmodelshare.models.upload")));
        bx += actionWidth + GuiNavigation.GAP;
        buttonList.add(new LocalizedButton(9, bx, by, actionWidth, 20, tr("ltsmodelshare.models.invitation")));
        buttonList.add(GuiNavigation.backButton(6, width, height, tr("ltsmodelshare.common.close")));

        computeLayout();
        page = wasLastPage ? maxPage() : Math.min(page, maxPage());
        reload();
    }

    // ==================== 数据 ====================

    private void reload() {
        // 尺寸变化时旧请求可能尚未结束：不能跳过新页查询，更不能让旧响应覆盖新页。
        final int currentRequest = ++requestId;
        loading = true;
        cards.clear();
        status = TextFormatting.GRAY + tr("ltsmodelshare.common.loading");
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
                    if (currentRequest != requestId) {
                        return;
                    }
                    total = value.total;
                    // 服务器的数据也可能在请求期间变少，别把空页当作最终结果。
                    if (page > maxPage()) {
                        page = maxPage();
                        reload();
                        return;
                    }
                    loading = false;
                    cards.clear();
                    if (value.records != null) {
                        cards.addAll(value.records);
                    }
                    status = cards.isEmpty() ? TextFormatting.GRAY + tr("ltsmodelshare.search.no_results") : "";
                });
            }

            @Override
            public void fail(String message) {
                ApiClient.post(() -> {
                    if (currentRequest != requestId) {
                        return;
                    }
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
        return work.inception.ltsmodelshare.client.Translations.siteLocale();
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
                    cfg.username = "";
                    cfg.save();
                    button.displayString = tr("ltsmodelshare.common.login");
                    status = TextFormatting.YELLOW + tr("ltsmodelshare.models.logged_out");
                    // 退出后直接回登录界面（登录成功会自动回到浏览页）
                    mc.displayGuiScreen(new GuiLogin(this));
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
            case 9:
                mc.displayGuiScreen(new GuiInviteAccess(this));
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
            drawString(fontRenderer, TextFormatting.DARK_GRAY + tr("ltsmodelshare.search.placeholder"), searchField.x + 4, searchField.y + 5, 0x777777);
        }

        int hovered = cardAt(mouseX, mouseY);
        for (int i = 0; i < Math.min(cards.size(), perPage); i++) {
            drawCard(i, cards.get(i), hovered == i);
        }

        if (!status.isEmpty()) {
            work.inception.ltsmodelshare.client.TextUtil.drawCenteredFit(fontRenderer, status,
                    cx, gridTop + cardBgH / 2, width - 20, 0xFFFFFF);
        }
        /*
         * 页脚：页码 + 登录状态 + 筛选摘要。
         * 标签选多了摘要会很长 —— 超出窗口宽度就用 … 收尾（原来是直接画出去，被裁掉半截）。
         */
        String base = TextFormatting.GRAY + tr("ltsmodelshare.models.pagination", page, maxPage(), total)
                + (ModConfig.get().loggedIn() ? tr("ltsmodelshare.models.logged_in", ModConfig.get().username) : tr("ltsmodelshare.models.guest"));
        String filterPart = "";
        if (filter != null && !filter.isEmpty()) {
            int baseW = fontRenderer.getStringWidth(stripCodes(base));
            int room = width - 124 - baseW - fontRenderer.getStringWidth("  []");
            if (room > 18) {
                filterPart = "  [" + trim(filter.summary(), room) + "]";
            }
        }
        drawCenteredString(fontRenderer, trim(base + TextFormatting.YELLOW + filterPart, width - 124),
                cx, GuiNavigation.footerY(height) - 20, 0xAAAAAA);

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

        // Title at 0.8 of the original font size.
        int textY = imgY + imgH + 1;
        drawScaled(trim(card.title == null ? tr("ltsmodelshare.common.untitled") : card.title,
                (int) ((cardW - 6) / 0.8f)), x + 3, textY, 0xFFFFFF, 0.8f);

        // 第二行：作者头像 + 名字（0.75 倍字）
        int ay = textY + 8;
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
        drawSmall(trim(tr("ltsmodelshare.common.work_stats", card.downloadCount, card.viewCount),
                (int) ((cardW - 6) / SMALL_TEXT)), x + 3, ay + 7, 0xAAAAAA);
    }

    /** Author and statistics font scale. */
    private static final float SMALL_TEXT = 0.75f;

    private void drawSmall(String text, int x, int y, int color) {
        drawScaled(text, x, y, color, SMALL_TEXT);
    }

    private void drawScaled(String text, int x, int y, int color, float scale) {
        if (text == null || text.isEmpty()) return;
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(x, y, 0);
            GlStateManager.scale(scale, scale, 1f);
            drawString(fontRenderer, text, 0, 0, color);
        } finally {
            GlStateManager.popMatrix();
            GlStateManager.color(1f, 1f, 1f, 1f);
        }
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
