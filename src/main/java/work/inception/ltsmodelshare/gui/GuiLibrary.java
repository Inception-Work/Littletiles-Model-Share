package work.inception.ltsmodelshare.gui;

import static work.inception.ltsmodelshare.client.Translations.tr;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.text.TextFormatting;
import work.inception.ltsmodelshare.ModConfig;
import work.inception.ltsmodelshare.api.ApiClient;
import work.inception.ltsmodelshare.api.Dtos;
import work.inception.ltsmodelshare.client.Avatars;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 收藏与关注：顶部「收藏 / 关注」两个高亮 tab。
 * 收藏页的小 tab 与返回按钮同一行；关注页上方只放一行 5 个关注用户，
 * 下方关注动态与关注列表都使用「上一页 / 页码 / 下一页」同一行的底栏。
 */
public class GuiLibrary extends GuiScreen {
    private static final int TAB_FAVORITES = 0;
    private static final int TAB_FOLLOWING = 1;
    private static final int GAP = 6;
    private static final int USER_SLOTS = 5;
    private static final int FEED_ROW_H = 26;

    private final GuiScreen parent;
    private int tab = TAB_FAVORITES;
    private int favKind; // 0 = single, 1 = collection
    private final List<Dtos.WorkCard> cards = new ArrayList<>();
    private final List<Dtos.UserCard> users = new ArrayList<>();
    private final List<Dtos.WorkCard> feed = new ArrayList<>();
    private long total;
    private long userTotal;
    private long feedTotal;
    private int page = 1;
    private int feedPage = 1;
    private int perPage = 8;
    private int feedPerPage = 1;
    private int cols = 4;
    private int cardW = 70;
    private int cardH = 70;
    private int imageH = 40;
    private int gridLeft;
    private int gridTop;
    private int gridBottom;
    private int listTop;
    private int usersTop;
    private int userCellW;
    private int userCellH = 34;
    private boolean loading;
    private boolean needLogin;
    private String status = "";
    private int requestId;
    private long myUserId;

    public GuiLibrary(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        ++usersRequestId;
        needLogin = !ModConfig.get().loggedIn();
        int cx = width / 2;
        buttonList.add(new TabButton(20, cx - 96, 22, 92, 20,
                tr("ltsmodelshare.library.favorites"), tab == TAB_FAVORITES));
        buttonList.add(new TabButton(21, cx + 4, 22, 92, 20,
                tr("ltsmodelshare.library.following"), tab == TAB_FOLLOWING));

        int footerY = GuiNavigation.footerY(height);
        int pagY = footerY - 26;
        int backX = GuiNavigation.backX(width);
        if (tab == TAB_FAVORITES) {
            // 小 tab 放在返回这一行的左边，选中的高亮。
            int subW = 64;
            buttonList.add(new TabButton(22, GuiNavigation.MARGIN, footerY, subW, 20,
                    tr("ltsmodelshare.library.single"), favKind == 0));
            buttonList.add(new TabButton(23, GuiNavigation.MARGIN + subW + GAP, footerY, subW, 20,
                    tr("ltsmodelshare.library.collection"), favKind == 1));
            // 上一页 / 下一页在返回行的上方，左右分布，中间页码。
            buttonList.add(new LocalizedButton(3, GuiNavigation.MARGIN, pagY, 42, 20,
                    tr("ltsmodelshare.common.previous")));
            buttonList.add(new LocalizedButton(4, width - GuiNavigation.MARGIN - 42, pagY, 42, 20,
                    tr("ltsmodelshare.common.next")));
            gridTop = 48;
            gridBottom = pagY - 6;
        } else {
            buttonList.add(new LocalizedButton(3, GuiNavigation.MARGIN, footerY, 42, 20,
                    tr("ltsmodelshare.common.previous")));
            buttonList.add(new LocalizedButton(4, backX - GAP - 42, footerY, 42, 20,
                    tr("ltsmodelshare.common.next")));
            buttonList.add(new LocalizedButton(10, width - GuiNavigation.MARGIN - 56, 44, 56, 16,
                    tr("ltsmodelshare.library.more")));
            usersTop = 64;
            listTop = usersTop + userCellH + 18;
            gridBottom = footerY - 6;
        }
        buttonList.add(GuiNavigation.backButton(6, width, height, tr("ltsmodelshare.common.back")));

        computeLayout();
        if (needLogin) {
            ++requestId;
            loading = false;
            status = tr("ltsmodelshare.library.login_required");
            cards.clear();
            users.clear();
            feed.clear();
            return;
        }
        if (tab == TAB_FAVORITES) loadFavorites();
        else loadFollowing();
    }

    private void computeLayout() {
        userCellW = Math.max(40, (width - 2 * GuiNavigation.MARGIN - (USER_SLOTS - 1) * GAP) / USER_SLOTS);
        int prefW = width >= 300 ? 78 : 66;
        cardW = Math.max(40, Math.min(width - 16, prefW));
        imageH = Math.max(18, (cardW - 6) * 9 / 16);
        cardH = imageH + 28;
        cols = Math.max(1, (width - 16 + GAP) / (cardW + GAP));
        int rows = Math.max(1, (gridBottom - gridTop + GAP) / (cardH + GAP));
        perPage = Math.max(1, Math.min(48, cols * rows));
        gridLeft = Math.max(8, (width - (cols * cardW + (cols - 1) * GAP)) / 2);
        feedPerPage = Math.max(1, (gridBottom - listTop) / FEED_ROW_H);
    }

    // ==================== 数据 ====================

    private int maxPage() {
        return (int) Math.max(1, (total + perPage - 1) / perPage);
    }

    private int maxFeedPage() {
        return (int) Math.max(1, (feedTotal + feedPerPage - 1) / feedPerPage);
    }

    private void loadFavorites() {
        final int currentRequest = ++requestId;
        loading = true;
        cards.clear();
        status = TextFormatting.GRAY + tr("ltsmodelshare.common.loading");
        String kind = favKind == 1 ? "collection" : "single";
        ApiClient.favorites(kind, page, perPage, new ApiClient.Callback<Dtos.Page<Dtos.WorkCard>>() {
            @Override public void ok(Dtos.Page<Dtos.WorkCard> value) {
                ApiClient.post(() -> {
                    if (currentRequest != requestId) return;
                    total = value.total;
                    if (page > maxPage()) {
                        page = maxPage();
                        loadFavorites();
                        return;
                    }
                    loading = false;
                    cards.clear();
                    if (value.records != null) cards.addAll(value.records);
                    status = cards.isEmpty()
                            ? TextFormatting.GRAY + tr("ltsmodelshare.library.empty_favorites") : "";
                });
            }
            @Override public void fail(String message) {
                ApiClient.post(() -> {
                    if (currentRequest != requestId) return;
                    loading = false;
                    status = TextFormatting.RED + message;
                });
            }
        });
    }

    private int usersRequestId;

    private void loadFollowing() {
        loadFollowedAuthors();
        loadFeed();
    }

    private void loadFollowedAuthors() {
        final int currentRequest = ++usersRequestId;
        users.clear();
        ApiClient.resolveUserId(new ApiClient.Callback<Long>() {
            @Override public void ok(Long id) {
                ApiClient.post(() -> {
                    if (currentRequest != usersRequestId) return;
                    myUserId = id == null ? 0 : id;
                    ApiClient.followingUsers(myUserId, 1, USER_SLOTS, new ApiClient.Callback<Dtos.Page<Dtos.UserCard>>() {
                        @Override public void ok(Dtos.Page<Dtos.UserCard> value) {
                            ApiClient.post(() -> {
                                if (currentRequest != usersRequestId) return;
                                userTotal = value.total; users.clear();
                                if (value.records != null) users.addAll(value.records);
                            });
                        }
                        @Override public void fail(String message) { }
                    });
                });
            }
            @Override public void fail(String message) { }
        });
    }

    private void loadFeed() {
        final int currentRequest = ++requestId;
        loading = true; feed.clear();
        status = tr("ltsmodelshare.common.loading");
        ApiClient.followingFeed(feedPage, feedPerPage, new ApiClient.Callback<Dtos.Page<Dtos.WorkCard>>() {
            @Override public void ok(Dtos.Page<Dtos.WorkCard> value) {
                ApiClient.post(() -> {
                    if (currentRequest != requestId) return;
                    loading = false; feedTotal = value.total; feed.clear();
                    if (value.records != null) feed.addAll(value.records);
                    if (feedPage > maxFeedPage()) { feedPage = maxFeedPage(); loadFeed(); return; }
                    status = feed.isEmpty() ? tr("ltsmodelshare.library.empty_feed") : "";
                });
            }
            @Override public void fail(String message) {
                ApiClient.post(() -> { if (currentRequest == requestId) { loading = false; status = message; } });
            }
        });
    }

    // ==================== 交互 ====================

    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case 20:
                if (tab != TAB_FAVORITES) {
                    tab = TAB_FAVORITES;
                    page = 1;
                    initGui();
                }
                break;
            case 21:
                if (tab != TAB_FOLLOWING) {
                    tab = TAB_FOLLOWING;
                    feedPage = 1;
                    initGui();
                }
                break;
            case 22:
            case 23:
                int nextKind = button.id == 22 ? 0 : 1;
                if (favKind != nextKind) {
                    favKind = nextKind;
                    page = 1;
                    initGui();
                }
                break;
            case 3:
                if (needLogin || loading) return;
                if (tab == TAB_FAVORITES) {
                    if (page > 1) { page--; loadFavorites(); }
                } else if (feedPage > 1) {
                    feedPage--; loadFeed();
                }
                break;
            case 4:
                if (needLogin || loading) return;
                if (tab == TAB_FAVORITES) {
                    if (page < maxPage()) { page++; loadFavorites(); }
                } else if (feedPage < maxFeedPage()) {
                    feedPage++; loadFeed();
                }
                break;
            case 10:
                if (!loading && myUserId > 0) {
                    mc.displayGuiScreen(new GuiFollowingUsers(this, myUserId, userTotal));
                }
                break;
            case 6:
                mc.displayGuiScreen(parent);
                break;
            default:
                break;
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1) {
            mc.displayGuiScreen(parent);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) throws IOException {
        super.mouseClicked(mx, my, button);
        if (button != 0 || needLogin || loading) return;
        if (tab == TAB_FAVORITES) {
            int idx = cardAt(mx, my);
            if (idx >= 0 && idx < cards.size()) {
                mc.displayGuiScreen(new GuiWorkDetail(cards.get(idx), this));
            }
        } else {
            int user = userAt(mx, my);
            if (user >= 0 && user < Math.min(users.size(), USER_SLOTS)) {
                mc.displayGuiScreen(new GuiAuthorHome(users.get(user).asAuthor(), this));
                return;
            }
            int row = feedAt(mx, my);
            if (row >= 0 && row < feed.size()) {
                mc.displayGuiScreen(new GuiWorkDetail(feed.get(row), this));
            }
        }
    }

    private int cardAt(int mx, int my) {
        for (int i = 0; i < Math.min(cards.size(), perPage); i++) {
            int x = gridLeft + (i % cols) * (cardW + GAP);
            int y = gridTop + (i / cols) * (cardH + GAP);
            if (mx >= x && mx <= x + cardW && my >= y && my <= y + cardH) return i;
        }
        return -1;
    }

    private int userAt(int mx, int my) {
        for (int i = 0; i < Math.min(users.size(), USER_SLOTS); i++) {
            int x = GuiNavigation.MARGIN + i * (userCellW + GAP);
            if (mx >= x && mx <= x + userCellW && my >= usersTop && my <= usersTop + userCellH) return i;
        }
        return -1;
    }

    private int feedAt(int mx, int my) {
        if (mx < GuiNavigation.MARGIN || mx > width - GuiNavigation.MARGIN) return -1;
        if (my < listTop || my >= gridBottom) return -1;
        int idx = feedPageStart() + (my - listTop) / FEED_ROW_H;
        return idx >= 0 && idx < feed.size() ? idx : -1;
    }

    private int feedPageStart() {
        return 0;
    }

    // ==================== 绘制 ====================

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRenderer, tr("ltsmodelshare.library.title"), width / 2, 8, 0xFFFFFF);

        if (needLogin) {
            drawCenteredString(fontRenderer, status, width / 2, height / 2, 0xFFCCCCCC);
            super.drawScreen(mouseX, mouseY, partialTicks);
            return;
        }

        if (tab == TAB_FAVORITES) drawFavorites(mouseX, mouseY);
        else drawFollowing(mouseX, mouseY);

        if (!status.isEmpty()) {
            drawCenteredString(fontRenderer, WorkCardView.trim(fontRenderer, status, width - 24),
                    width / 2, height / 2, 0xFFFFFF);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawFavorites(int mouseX, int mouseY) {
        for (int i = 0; i < Math.min(cards.size(), perPage); i++) {
            int x = gridLeft + (i % cols) * (cardW + GAP);
            int y = gridTop + (i / cols) * (cardH + GAP);
            boolean hovered = mouseX >= x && mouseX <= x + cardW && mouseY >= y && mouseY <= y + cardH;
            WorkCardView.grid(fontRenderer, cards.get(i), x, y, cardW, cardH, imageH, hovered);
        }
        drawCenteredString(fontRenderer, TextFormatting.GRAY + tr("ltsmodelshare.common.page", page, maxPage()),
                width / 2, GuiNavigation.footerY(height) - 20, 0xAAAAAA);
    }

    private void drawFollowing(int mouseX, int mouseY) {
        drawString(fontRenderer, TextFormatting.GOLD + tr("ltsmodelshare.library.following_users"),
                GuiNavigation.MARGIN, 48, 0xFFCC88);
        for (int i = 0; i < Math.min(users.size(), USER_SLOTS); i++) {
            Dtos.UserCard user = users.get(i);
            int x = GuiNavigation.MARGIN + i * (userCellW + GAP);
            int y = usersTop;
            boolean hovered = mouseX >= x && mouseX <= x + userCellW
                    && mouseY >= y && mouseY <= y + userCellH;
            Gui.drawRect(x, y, x + userCellW, y + userCellH - 2, hovered ? 0x55FFFFFF : 0x33000000);
            Avatars.draw(fontRenderer, user.asAuthor(), x + 3, y + 3, 16);
            String name = user.displayName == null || user.displayName.isEmpty()
                    ? user.username : user.displayName;
            WorkCardView.small(fontRenderer, WorkCardView.trim(fontRenderer, name, userCellW - 24),
                    x + 22, y + 4, 0xFFFFFF);
            WorkCardView.small(fontRenderer, WorkCardView.trim(fontRenderer,
                    "@" + (user.username == null ? "" : user.username), userCellW - 26),
                    x + 22, y + 14, 0xAAAAAA);
            WorkCardView.small(fontRenderer, WorkCardView.trim(fontRenderer,
                    tr("ltsmodelshare.library.follower_count", user.followerCount), userCellW - 8),
                    x + 3, y + 23, 0x999999);
        }

        drawString(fontRenderer, TextFormatting.GOLD + tr("ltsmodelshare.library.feed"),
                GuiNavigation.MARGIN, listTop - 12, 0xFFCC88);
        int start = feedPageStart();
        for (int i = 0; i < feedPerPage && start + i < feed.size(); i++) {
            int y = listTop + i * FEED_ROW_H;
            boolean hovered = mouseX >= GuiNavigation.MARGIN && mouseX <= width - GuiNavigation.MARGIN
                    && mouseY >= y && mouseY <= y + FEED_ROW_H - 2;
            WorkCardView.row(fontRenderer, feed.get(start + i), GuiNavigation.MARGIN, y,
                    width - GuiNavigation.MARGIN * 2, FEED_ROW_H - 2, hovered);
        }

        int backX = GuiNavigation.backX(width);
        int midLeft = GuiNavigation.MARGIN + 42 + GAP;
        int midRight = backX - GAP - 42 - GAP;
        drawCenteredString(fontRenderer, trim(TextFormatting.GRAY
                        + tr("ltsmodelshare.common.page", feedPage, maxFeedPage()),
                Math.max(20, midRight - midLeft)),
                (midLeft + midRight) / 2, GuiNavigation.footerY(height) + 6, 0xAAAAAA);
    }

    private String trim(String text, int maxWidth) {
        return WorkCardView.trim(fontRenderer, text, maxWidth);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
