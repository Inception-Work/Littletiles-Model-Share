package work.inception.ltsmodelshare.gui;

import static work.inception.ltsmodelshare.client.Translations.tr;
import work.inception.ltsmodelshare.client.SiteTags;

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
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.item.ItemStack;
import com.creativemd.littletiles.common.util.converation.StructureStringUtils;
import net.minecraft.client.entity.EntityPlayerSP;
import work.inception.ltsmodelshare.client.GridLimits;
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
    /**
     * 凭邀请（unlisted）作品的访问码：进详情页要带给详情接口，取蓝图时也要带给下载接口。
     * 从列表点进来的公开作品是 null（和以前完全一样）。
     */
    private final String accessCode;

    private Dtos.WorkDetail detail;
    private final List<Dtos.Item> items = new ArrayList<>();
    private final List<String> gallery = new ArrayList<>();
    private int galleryIndex;

    private String status = "";
    private boolean loading;
    /** 已经生成过蓝图的条目 */
    private final List<Long> madeBlueprint = new ArrayList<>();
    /** 游戏内 LittleTiles 原生模型预览（按需创建；不改玩家背包）。 */
    private ModelPreviewPanel modelPreview;
    /** 收藏按钮状态（详情接口返回 favorited 后回填） */
    private boolean favorited;
    private boolean favoriteBusy;

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

    /** 条目行高（左侧放 4:3 缩略图） */
    private static final int ITEM_ROW_H = 68;
    private static final int THUMB_W = 64;
    private static final int THUMB_H = 48;
    private static final int ARROW_W = 12;
    private static final int ARROW_H = 12;

    /** 每个条目当前显示第几张图（itemId → 索引）。合集里每个作品有自己的图，要各自切换 */
    private final java.util.Map<Long, Integer> itemImageIndex = new java.util.HashMap<>();

    /*
     * 描述窗口的行数**跟封面图数量挂钩**：
     * - 多张封面（有画廊/缩略图）→ 7 行，因为左栏高、右栏有空间；
     * - 只有一张封面 → 4 行，避免右栏被描述撑得比封面还高。
     */
    private static final int SUMMARY_WINDOW_MULTI = 7;
    private static final int SUMMARY_WINDOW_SINGLE = 4;

    /** 当前该显示几行描述 */
    private int summaryWindow() {
        return gallery.size() > 1 ? SUMMARY_WINDOW_MULTI : SUMMARY_WINDOW_SINGLE;
    }
    private int summaryScroll;
    /** 描述区在屏幕上的矩形（滚轮落在这里就滚描述，否则滚整页） */
    private int summaryBoxX;
    private int summaryBoxY;
    private int summaryBoxW;
    private int summaryBoxH;
    private static final int DL_W = 72;   // 「获取蓝图」= 4 个汉字，比原来的「下载」宽
    private static final int DL_H = 18;
    private static final int PREVIEW_W = 52;
    private static final int ACTION_GAP = 4;

    private int[] previewBounds(int rowY) {
        int[] download = downloadBounds(rowY);
        return new int[]{download[0] - ACTION_GAP - PREVIEW_W, download[1], PREVIEW_W, DL_H};
    }

    public GuiWorkDetail(Dtos.WorkCard card, GuiScreen parent) {
        this(card, null, parent);
    }

    /**
     * 凭邀请进来的详情页：带上访问码，详情 / 取蓝图两条请求都会附上它。
     * unlisted 作品没有码时服务端会拒（M05-030），所以这里必须记住。
     */
    public GuiWorkDetail(Dtos.WorkCard card, String accessCode, GuiScreen parent) {
        this.card = card;
        this.accessCode = accessCode;
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

        buttonList.add(GuiNavigation.backButton(1, width, height, tr("ltsmodelshare.common.back")));
        // 收藏按钮放在返回旁边。
        int favoriteW = 64;
        buttonList.add(new LocalizedButton(20, GuiNavigation.backX(width) - GuiNavigation.GAP - favoriteW,
                GuiNavigation.footerY(height), favoriteW, 20, favoriteLabel()));

        scroll = 0;
        load();
    }

    // ==================== 数据 ====================

    private void load() {
        if (loading) {
            return;
        }
        loading = true;
        status = TextFormatting.GRAY + tr("ltsmodelshare.common.loading");
        ApiClient.detail(card.workId, accessCode, new ApiClient.Callback<Dtos.WorkDetail>() {
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
                    favorited = Boolean.TRUE.equals(value.favorited);
                    refreshFavoriteButton();
                    status = items.isEmpty() ? TextFormatting.GRAY + tr("ltsmodelshare.detail.no_models") : "";
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

    private String favoriteLabel() {
        return tr(favorited ? "ltsmodelshare.common.favorited" : "ltsmodelshare.common.favorite");
    }

    private void refreshFavoriteButton() {
        for (GuiButton button : buttonList) {
            if (button.id == 20) button.displayString = favoriteLabel();
        }
    }

    private void toggleFavorite() {
        if (!ModConfig.get().loggedIn()) {
            mc.displayGuiScreen(new GuiLogin(this));
            return;
        }
        if (favoriteBusy) return;
        favoriteBusy = true;
        ApiClient.toggleFavorite(card.workId, new ApiClient.Callback<Dtos.SocialToggle>() {
            @Override public void ok(Dtos.SocialToggle value) {
                ApiClient.post(() -> {
                    favoriteBusy = false;
                    if (value != null && value.favorited != null) {
                        favorited = value.favorited;
                        refreshFavoriteButton();
                    }
                });
            }
            @Override public void fail(String message) {
                ApiClient.post(() -> {
                    favoriteBusy = false;
                    status = TextFormatting.RED + message;
                });
            }
        });
    }

    // ==================== 版式计算 ====================

    private String titleText() {
        return detail != null && detail.title != null
                ? detail.title : (card.title == null ? tr("ltsmodelshare.common.untitled") : card.title);
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
            String text = "#" + SiteTags.label(tag);
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
        String all = tr("ltsmodelshare.detail.stats", view, dl, fav, cmt);
        if (fontRenderer.getStringWidth(all) <= rightW) {
            return new String[]{all};
        }
        return new String[]{tr("ltsmodelshare.detail.stats_first", view, dl), tr("ltsmodelshare.detail.stats_second", fav, cmt)};
    }

    private int rightColumnHeight() {
        int h = titleLines().size() * 10 + 2;
        h += tagRows().size() * 10 + 2;
        h += 4;                                   // 标签 → 作者 之间的间隔
        h += 18;                                  // 作者行（头像 + 名字 + @账号）
        h += 6;                                   // 作者 → 作品数据 的间隔（原来贴着）
        h += statPairs().length * 10;             // 浏览/下载/收藏/评论
        if (hasCollectionDeps()) {
            h += 6 + effectiveDeps().size() * 10;
        }
        int sLines = summaryLineCount();
        if (sLines > 0) {
            h += 6 + Math.min(sLines, summaryWindow()) * 10 + 2;
        }
        return h;
    }

    private int computeHeaderHeight() {
        int thumbs = gallery.size() > 1 ? thumbSize + 4 : 0;
        return Math.max(coverH + thumbs, rightColumnHeight()) + 6;
    }

    /** 描述在右栏折行（放在「作品数据」下面） */
    private List<String> summaryLines() {
        String summary = detail != null ? detail.summary : card.summary;
        List<String> out = new ArrayList<>();
        if (summary == null || summary.trim().isEmpty()) {
            return out;
        }
        out.addAll(TextUtil.wrap(fontRenderer, summary, rightW - 6));
        return out;
    }

    private int summaryLineCount() {
        return summaryLines().size();
    }

    /** 超过 7 行时，描述区自己滚动 */
    private int summaryMaxScroll() {
        return Math.max(0, summaryLineCount() - summaryWindow());
    }

    /**
     * 这条作品实际要显示的依赖。
     *
     * <p>合集的依赖在顶层 {@code detail.dependencies}，**单个作品的在 {@code items[].dependencies}**
     * （后端 DTO 的注释就是这么写的）。以前只读顶层，所以单个作品永远显示「原版」。</p>
     */
    private java.util.List<Dtos.Dependency> effectiveDeps() {
        if (detail == null) {
            return java.util.Collections.emptyList();
        }
        if (detail.dependencies != null && !detail.dependencies.isEmpty()) {
            return detail.dependencies;
        }
        java.util.List<Dtos.Dependency> out = new java.util.ArrayList<>();
        if (detail.items != null) {
            for (Dtos.Item it : detail.items) {
                if (it != null && it.dependencies != null) {
                    out.addAll(it.dependencies);
                }
            }
        }
        return out;
    }

    private boolean hasCollectionDeps() {
        return !effectiveDeps().isEmpty();
    }

    private int computeContentHeight() {
        int h = headerH;
        h += 14;                                  // "模型（n）"
        h += items.size() * ITEM_ROW_H;
        h += 8;
        return h;
    }

    private int maxScroll() {
        return Math.max(0, computeContentHeight() - (bodyBottom - bodyTop));
    }

    /** 条目区在内容坐标里的起点（依赖/描述都已包含在 header 高度里） */
    private int itemRowTop() {
        return headerH + 14;
    }

    // ==================== 交互 ====================

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 1) {
            mc.displayGuiScreen(parent);
        } else if (button.id == 20) {
            toggleFavorite();
        }
    }

    /** ESC closes the preview first, then returns to the page that opened this detail. */
    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1 && modelPreview != null) {
            closeModelPreview();
            return;
        }
        if (keyCode == 1) {
            mc.displayGuiScreen(parent);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (modelPreview != null) {
            modelPreview.wheel(wheel);
            return;
        }
        if (wheel == 0) {
            return;
        }
        int mx = Mouse.getEventX() * width / Math.max(1, mc.displayWidth);
        int my = height - Mouse.getEventY() * height / Math.max(1, mc.displayHeight) - 1;
        boolean overSummary = summaryBoxH > 0
                && mx >= summaryBoxX && mx <= summaryBoxX + summaryBoxW
                && my >= summaryBoxY && my <= summaryBoxY + summaryBoxH;
        if (overSummary && summaryMaxScroll() > 0) {
            // 描述自己滚（这样长描述不会把整页顶走）
            summaryScroll = Math.max(0, Math.min(summaryScroll - Integer.signum(wheel), summaryMaxScroll()));
        } else {
            scroll = Math.max(0, Math.min(scroll - Integer.signum(wheel) * 18, maxScroll()));
        }
    }

    /** Avatar and author names share one hitbox, following the scrolled text layout. */
    private int[] authorBounds() {
        int y = bodyTop - scroll + titleLines().size() * 10 + 2 + tagRows().size() * 10 + 2;
        return new int[]{rightX, y, rightW, 20};
    }

    private Dtos.Author displayedAuthor() {
        return detail != null && detail.author != null ? detail.author : card.author;
    }

    private int[] thumbBounds(int index) {
        int y = bodyTop + coverH + 4 - scroll;
        int perRow = Math.max(1, coverW / (thumbSize + 4));
        int col = index % perRow;
        int row = index / perRow;
        return new int[]{left + col * (thumbSize + 4), y + row * (thumbSize + 4), thumbSize, thumbSize};
    }

    void closeModelPreview() {
        if (modelPreview != null) {
            modelPreview.close();
            modelPreview = null;
        }
    }

    @Override
    public void onGuiClosed() {
        closeModelPreview();
        super.onGuiClosed();
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (modelPreview != null) {
            modelPreview.drag(mouseX, mouseY, clickedMouseButton);
            return;
        }
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        if (modelPreview != null) {
            modelPreview.release();
            return;
        }
        super.mouseReleased(mouseX, mouseY, state);
    }

    private int[] downloadBounds(int rowY) {
        return new int[]{left + colW - DL_W - 4, rowY + ITEM_ROW_H - DL_H - 9, DL_W, DL_H};
    }

    /** 条目缩略图位置（内容坐标转屏幕坐标由调用方加 -scroll） */
    private int[] itemThumbBounds(int rowY) {
        return new int[]{left + 4, rowY + (ITEM_ROW_H - THUMB_H) / 2, THUMB_W, THUMB_H};
    }

    /** 缩略图上的左右切换按钮 */
    /** 箭头画的位置 */
    private int[] arrowBounds(int rowY, boolean leftArrow) {
        int[] th = itemThumbBounds(rowY);
        int y = th[1] + th[3] - ARROW_H - 1;
        int x = leftArrow ? th[0] + 1 : th[0] + th[2] - ARROW_W - 1;
        return new int[]{x, y, ARROW_W, ARROW_H};
    }

    /** 箭头的**可点**区域：比画出来的每边大 2px，12x12 实在太小 */
    private int[] arrowHitBounds(int rowY, boolean leftArrow) {
        int[] b = arrowBounds(rowY, leftArrow);
        return new int[]{b[0] - 2, b[1] - 2, b[2] + 4, b[3] + 4};
    }

    /** 条目自己的图片列表（合集里每个作品各自一套） */
    private java.util.List<String> itemImages(Dtos.Item item) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (item != null && item.images != null) {
            for (String img : item.images) {
                if (img != null && !img.isEmpty()) {
                    out.add(img);
                }
            }
        }
        return out;
    }

    private int itemImagePos(Dtos.Item item) {
        Integer idx = itemImageIndex.get(item.itemId);
        int size = itemImages(item).size();
        if (idx == null || size == 0) {
            return 0;
        }
        return Math.floorMod(idx, size);
    }

    private void cycleItemImage(Dtos.Item item, int delta) {
        int size = itemImages(item).size();
        if (size <= 1) {
            return;
        }
        itemImageIndex.put(item.itemId, Math.floorMod(itemImagePos(item) + delta, size));
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (modelPreview != null) {
            modelPreview.click(mouseX, mouseY, mouseButton);
            return;
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (mouseButton != 0) {
            return;
        }
        Dtos.Author author = displayedAuthor();
        if (author != null && author.userId > 0 && mouseY >= bodyTop && mouseY < bodyBottom
                && hit(mouseX, mouseY, authorBounds())) {
            if (parent instanceof GuiAuthorHome && ((GuiAuthorHome) parent).isAuthor(author.userId)) {
                mc.displayGuiScreen(parent);
            } else {
                mc.displayGuiScreen(new GuiAuthorHome(author, this));
            }
            return;
        }
        for (int i = 0; i < Math.min(gallery.size(), 8); i++) {
            if (hit(mouseX, mouseY, thumbBounds(i))) {
                galleryIndex = i;
                return;
            }
        }
        // 绘制用的是 screen = bodyTop - scroll + content，所以反算要求回 bodyTop
        int cy = mouseY - bodyTop + scroll;
        for (int i = 0; i < items.size(); i++) {
            Dtos.Item item = items.get(i);
            int rowY = itemRowTop() + i * ITEM_ROW_H;

            // 缩略图上的左右切换（只有多图时才响应）
            if (itemImages(item).size() > 1) {
                int[] l = arrowHitBounds(rowY, true);
                int[] r = arrowHitBounds(rowY, false);
                if (mouseX >= l[0] && mouseX <= l[0] + l[2] && cy >= l[1] && cy <= l[1] + l[3]
                        && mouseY >= bodyTop && mouseY <= bodyBottom) {
                    cycleItemImage(item, -1);
                    return;
                }
                if (mouseX >= r[0] && mouseX <= r[0] + r[2] && cy >= r[1] && cy <= r[1] + r[3]
                        && mouseY >= bodyTop && mouseY <= bodyBottom) {
                    cycleItemImage(item, 1);
                    return;
                }
            }

            int[] preview = previewBounds(rowY);
            if (ModelPreviewPanel.canOpen(item) && hit(mouseX, cy, preview)
                    && mouseY >= bodyTop && mouseY <= bodyBottom) {
                if (!ModConfig.get().loggedIn()) {
                    mc.displayGuiScreen(new GuiLogin(this));
                } else {
                    modelPreview = new ModelPreviewPanel(this, item, accessCode);
                    modelPreview.load();
                }
                return;
            }
            int[] b = downloadBounds(rowY);
            if (mouseX >= b[0] && mouseX <= b[0] + b[2] && cy >= b[1] && cy <= b[1] + b[3]
                    && mouseY >= bodyTop && mouseY <= bodyBottom) {
                fetchBlueprint(item);
                return;
            }
        }
    }

    private boolean hit(int mx, int my, int[] b) {
        return mx >= b[0] && mx <= b[0] + b[2] && my >= b[1] && my <= b[1] + b[3];
    }

    /**
     * 「获取蓝图」：拿结构文本 → 交给 LittleTiles 解析成蓝图道具。
     *
     * <p>两步：</p>
     * <ol>
     *   <li>{@code POST /api/work/download-snbt?itemId=&version=1.12} ——
     *       **版本不是 1.12 的结构由服务端先转成 1.12 再给我们**（返回 converted=true）；</li>
     *   <li>{@code StructureStringUtils.importStructure(snbt)} ——
     *       LittleTiles 自带的解析器，把 SNBT 文本变成蓝图道具，直接放进背包。</li>
     * </ol>
     */
    private void fetchBlueprint(final Dtos.Item item) {
        if (!ModConfig.get().loggedIn()) {
            mc.displayGuiScreen(new GuiLogin(this));
            return;
        }
        if (Minecraft.getMinecraft().player == null) {
            status = TextFormatting.RED + tr("ltsmodelshare.detail.world_required");
            return;
        }
        /*
         * grid 兼容性：站点上的结构可能用 grid 48/64，而客户端默认只支持到 32。
         * 这种结构即使解析出来也放不下（LittleTiles 会报 grid 不兼容），提前拦住并告诉玩家怎么改配置。
         */
        if (GridLimits.exceeds(item.grid)) {
            // 文案尽量短：弹窗要小，长了会撑成大对话框
            int need = GridLimits.suggestScale(item.grid);
            java.util.List<String> lines = new java.util.ArrayList<>();
            lines.add(TextFormatting.RED + tr("ltsmodelshare.detail.grid_exceeded", item.grid, GridLimits.maxGrid()));
            lines.add("");
            lines.add(tr("ltsmodelshare.detail.grid_config"));
            lines.add(tr("ltsmodelshare.detail.grid_scale", need));
            lines.add("");
            lines.add(TextFormatting.GRAY + tr("ltsmodelshare.detail.grid_doubling"));
            mc.displayGuiScreen(new GuiNotice(tr("ltsmodelshare.detail.grid_incompatible"), lines, this, tr("ltsmodelshare.common.ok")));
            return;
        }
        status = TextFormatting.YELLOW + tr("ltsmodelshare.detail.fetching");
        ApiClient.downloadInfo(item.itemId, "1.12", accessCode, new ApiClient.Callback<Dtos.DownloadInfo>() {
            @Override
            public void ok(Dtos.DownloadInfo info) {
                setStatus(TextFormatting.YELLOW + tr("ltsmodelshare.detail.parsing")
                        + (info.converted ? tr("ltsmodelshare.detail.converted") : ""));
                ApiClient.fetchText(info, new ApiClient.Callback<String>() {
                    @Override
                    public void ok(String text) {
                        ApiClient.post(() -> handToLittleTiles(item, text));
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

        /**
     * 用 LittleTiles 的 API 直接把 SNBT 变成蓝图道具，塞进玩家背包（满了掉在脚下）。
     *
     * <p>关键点：**在服务端生成**。客户端只有 `EntityPlayerSP`，往里塞东西会被同步覆盖，
     * 所以单人游戏走"集成服务端"（IntegratedServer）的玩家实体，
     * 这样背包变化是服务端权威的，退出重进也还在。</p>
     *
     * <p>联机（专用服务端）没有我们的服务端代码，只能退回 LittleTiles 的导入界面。</p>
     */
    private void handToLittleTiles(final Dtos.Item item, final String snbt) {
        final Minecraft mc = Minecraft.getMinecraft();
        final EntityPlayerSP clientPlayer = mc.player;
        if (clientPlayer == null) {
            status = TextFormatting.RED + tr("ltsmodelshare.detail.world_required");
            return;
        }

        // ===== 单人 / 局域网主机：直接在集成服务端生成道具 =====
        net.minecraft.server.integrated.IntegratedServer server = mc.getIntegratedServer();
        if (server != null) {
            final java.util.UUID uuid = clientPlayer.getUniqueID();
            server.addScheduledTask(() -> {
                try {
                    net.minecraft.entity.player.EntityPlayerMP mp =
                            server.getPlayerList().getPlayerByUUID(uuid);
                    if (mp == null) {
                        setStatus(TextFormatting.RED + tr("ltsmodelshare.detail.player_missing"));
                        return;
                    }
                    ItemStack stack = StructureStringUtils.importStructure(snbt);
                    if (stack == null || stack.isEmpty()) {
                        setStatus(TextFormatting.RED + tr("ltsmodelshare.common.parse_incompatible"));
                        return;
                    }
                    if (item.title != null && !item.title.trim().isEmpty()) {
                        stack.setStackDisplayName(item.title.trim());
                    }
                    boolean added = mp.inventory.addItemStackToInventory(stack);
                    if (!added) {
                        // 背包满了就掉在脚下
                        mp.dropItem(stack, false);
                        setStatus(TextFormatting.YELLOW + tr("ltsmodelshare.common.blueprint_dropped", stack.getDisplayName()));
                        work.inception.ltsmodelshare.LtsModelShare.LOG.info(
                                "背包已满，蓝图已掉落：itemId={}", item.itemId);
                    } else {
                        setStatus(TextFormatting.GREEN + tr("ltsmodelshare.server.created", stack.getDisplayName(), snbt.length()));
                        work.inception.ltsmodelshare.LtsModelShare.LOG.info(
                                "已生成蓝图：itemId={} 字符数={}", item.itemId, snbt.length());
                    }
                    mp.inventory.markDirty();
                } catch (Throwable t) {
                    setStatus(TextFormatting.RED + tr("ltsmodelshare.common.error_generate", t));
                    work.inception.ltsmodelshare.LtsModelShare.LOG.warn(
                            "生成蓝图失败 itemId={}：{}", item.itemId, t.toString());
                }
            });
            return;
        }

        // ===== 联机（专用服务端）：交给服务端生成 =====
        // 服务端装了本模组时，它会 importStructure 并把蓝图塞进背包（道具才是权威的）；
        // 6 秒没回执说明对方没装，BlueprintClient 会自动退回「复制 SNBT + /lt-import」。
        status = TextFormatting.YELLOW + tr("ltsmodelshare.detail.requested");
        work.inception.ltsmodelshare.client.BlueprintClient.request(snbt, item.title);
    }

    /** 服务端回执（由 BlueprintClient 在主线程调用） */
    public void onServerBlueprint(boolean ok, String text) {
        status = (ok ? TextFormatting.GREEN : TextFormatting.RED) + text;
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

        Dtos.Author author = displayedAuthor();
        if (author != null) {
            boolean authorHovered = author.userId > 0 && mouseY >= bodyTop && mouseY < bodyBottom
                    && hit(mouseX, mouseY, authorBounds());
            Gui.drawRect(rightX, y, rightX + rightW, y + 20,
                    authorHovered ? 0x50FFFFFF : 0x30000000);
            if (authorHovered) {
                Gui.drawRect(rightX, y, rightX + rightW, y + 1, 0xFFFFFFFF);
                Gui.drawRect(rightX, y + 19, rightX + rightW, y + 20, 0xFFFFFFFF);
            }
            work.inception.ltsmodelshare.client.Avatars.draw(fontRenderer, author, rightX, y + 2, 16);
            String name = author.displayName != null && !author.displayName.isEmpty()
                    ? author.displayName : (author.username == null ? "" : author.username);
            drawString(fontRenderer, trim(name, rightW - 20), rightX + 20, y + 1, 0xFFFFFF);
            if (author.username != null && !author.username.isEmpty()) {
                drawString(fontRenderer, TextFormatting.GRAY + "@" + trim(author.username, rightW - 22),
                        rightX + 20, y + 10, 0x888888);
            }
            y += 18 + 2;
        }

        // 作者 → 作品数据 之间留 6px（原来贴着）
        y += 6;
        for (String line : statPairs()) {
            drawString(fontRenderer, TextFormatting.GRAY + line, rightX, y, 0xAAAAAA);
            y += 10;
        }

        // 依赖（合集）接在数据下面
        if (hasCollectionDeps()) {
            y += 6;
            for (Dtos.Dependency dep : effectiveDeps()) {
                String type = dep != null && dep.type != null ? dep.type : tr("ltsmodelshare.detail.dependency");
                String name = dep != null && dep.name != null ? dep.name : "?";
                drawString(fontRenderer, TextFormatting.DARK_PURPLE + "[" + type + "] "
                        + trim(name, rightW - 4), rightX, y, 0xAA88CC);
                y += 10;
            }
        }

        // ---------- 描述：放在「作品数据」下面，超过 7 行时它自己滚动 ----------
        List<String> lines = summaryLines();
        summaryBoxX = rightX;
        summaryBoxY = y;
        summaryBoxW = rightW;
        summaryBoxH = 0;
        if (!lines.isEmpty()) {
            y += 6;
            int window = Math.min(lines.size(), summaryWindow());
            summaryBoxY = y;
            summaryBoxH = window * 10;
            summaryScroll = Math.max(0, Math.min(summaryScroll, summaryMaxScroll()));

            boolean scrollable = lines.size() > summaryWindow();
            if (scrollable) {
                // 描述区单独裁剪，滚动只影响这块
                enableScissor(rightX - 1, y - 1, rightW + 2, window * 10 + 2);
            }
            int sy = y;
            for (int i = summaryScroll; i < Math.min(lines.size(), summaryScroll + summaryWindow()); i++) {
                drawString(fontRenderer, TextFormatting.GRAY + trim(lines.get(i), rightW - 6), rightX, sy, 0x999999);
                sy += 10;
            }
            if (scrollable) {
                // GL 只有一个 scissor 矩形，内层用完要**恢复外层**，否则后面的条目就跑到按钮上去了
                disableScissor();
                enableScissor(left - 4, bodyTop, colW + 12, bodyBottom - bodyTop);
                // 右侧小滚动条 + 提示
                int barX = rightX + rightW - 2;
                Gui.drawRect(barX, y, barX + 2, y + window * 10, 0x30FFFFFF);
                int barH = window * 10;
                int thumbH = Math.max(8, (int) ((long) barH * window / lines.size()));
                int thumbY = y + (int) ((long) (barH - thumbH) * summaryScroll / summaryMaxScroll());
                Gui.drawRect(barX, thumbY, barX + 2, thumbY + thumbH, 0x90FFFFFF);
            }
            y += window * 10 + 2;
        }

        // ---------- 全宽：模型列表 ----------
        int cy = bodyTop - scroll + headerH;
        drawString(fontRenderer, TextFormatting.GRAY + tr("ltsmodelshare.detail.models", items.size()), left, cy, 0xAAAAAA);
        cy += 14;

        for (int i = 0; i < items.size(); i++) {
            Dtos.Item item = items.get(i);
            int ry = cy + i * ITEM_ROW_H;
            Gui.drawRect(left, ry, left + colW, ry + ITEM_ROW_H - 4, 0x28000000);

            // ---------- 左：条目自己的缩略图（合集里每个作品一套图） ----------
            int[] th = itemThumbBounds(ry);
            java.util.List<String> imgs = itemImages(item);
            if (imgs.isEmpty()) {
                Gui.drawRect(th[0], th[1], th[0] + th[2], th[1] + th[3], 0x30FFFFFF);
                drawCenteredString(fontRenderer, "#" + item.itemId,
                        th[0] + th[2] / 2, th[1] + th[3] / 2 - 4, 0x777777);
            } else {
                int pos = itemImagePos(item);
                ImageDraw.drawCropped(imgs.get(pos), th[0], th[1], th[2], th[3]);
                if (imgs.size() > 1) {
                    // 左右切换按钮（半透明黑底 + < >，MC 默认字体没有 ◀ ▶ 字形）
                    int[] la = arrowBounds(ry, true);
                    int[] ra = arrowBounds(ry, false);
                    boolean hoverL = hit(mouseX, mouseY, la);
                    boolean hoverR = hit(mouseX, mouseY, ra);
                    Gui.drawRect(la[0], la[1], la[0] + la[2], la[1] + la[3], hoverL ? 0xB0000000 : 0x80000000);
                    Gui.drawRect(ra[0], ra[1], ra[0] + ra[2], ra[1] + ra[3], hoverR ? 0xB0000000 : 0x80000000);
                    drawCenteredString(fontRenderer, "<", la[0] + la[2] / 2 + 1, la[1] + 2, 0xFFFFFF);
                    drawCenteredString(fontRenderer, ">", ra[0] + ra[2] / 2 - 1, ra[1] + 2, 0xFFFFFF);
                    // 第几张 / 共几张
                    String counter = (pos + 1) + "/" + imgs.size();
                    drawCenteredString(fontRenderer, counter,
                            th[0] + th[2] / 2, th[1] + th[3] - 9, 0xFFFFFF);
                }
            }

            // ---------- 中：标题 / 元信息 ----------
            int textX = th[0] + th[2] + 6;
            int textW = Math.max(20, left + colW - 6 - textX);
            drawString(fontRenderer, trim(item.title == null ? ("#" + item.itemId) : item.title,
                    textW), textX, ry + 6, 0xFFFFFF);

            StringBuilder meta = new StringBuilder();
            meta.append(TextFormatting.DARK_GRAY).append('#').append(item.itemId);
            if (item.mcVersion != null) {
                meta.append("  ").append(item.mcVersion);
            }
            if (item.boxCount != null) {
                meta.append("  ").append(item.boxCount).append(" box");
            }
            boolean gridTooBig = GridLimits.exceeds(item.grid);
            if (item.grid != null) {
                meta.append("  ").append(gridTooBig ? TextFormatting.RED : TextFormatting.DARK_GRAY)
                        .append("grid ").append(item.grid);
                if (gridTooBig) {
                    meta.append(tr("ltsmodelshare.detail.limit", GridLimits.maxGrid()));
                }
            }
            if (item.modNamespaces != null && !item.modNamespaces.isEmpty()) {
                meta.append(tr("ltsmodelshare.detail.mods", join(item.modNamespaces)));
            }
            drawString(fontRenderer, trim(meta.toString(), textW), textX, ry + 18, 0xAAAAAA);

            if (ModelPreviewPanel.canOpen(item)) {
                int[] pb = previewBounds(ry);
                Gui.drawRect(pb[0], pb[1], pb[0] + pb[2], pb[1] + pb[3],
                        hit(mouseX, mouseY, pb) ? 0xFF48718A : 0xFF304858);
                TextUtil.drawCenteredFit(fontRenderer, tr("ltsmodelshare.detail.preview"), pb[0] + pb[2] / 2, pb[1] + 5, pb[2] - 8, 0xFFFFFF);
            }

            int[] b = downloadBounds(ry);
            boolean done = madeBlueprint.contains(item.itemId);
            boolean hover = hit(mouseX, mouseY, b);
            Gui.drawRect(b[0], b[1], b[0] + b[2], b[1] + b[3],
                    done ? 0xFF2B4A20 : (hover ? 0xFF3E7A2C : 0xFF2F5424));
            TextUtil.drawCenteredFit(fontRenderer, done ? tr("ltsmodelshare.detail.generated") : tr("ltsmodelshare.detail.get_blueprint"), b[0] + b[2] / 2, b[1] + 5, b[2] - 8, 0xFFFFFF);
        }

        disableScissor();

        // ---------- 固定页脚 ----------
        int cx = width / 2;
        if (!status.isEmpty()) {
            drawCenteredString(fontRenderer, status, cx, height - 40, 0xFFFFFF);
        }


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
        if (modelPreview != null) {
            modelPreview.draw(mouseX, mouseY, partialTicks);
        }
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
