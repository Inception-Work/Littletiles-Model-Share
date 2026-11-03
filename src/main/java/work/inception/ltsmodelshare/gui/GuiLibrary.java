package work.inception.ltsmodelshare.gui;

import static work.inception.ltsmodelshare.client.Translations.tr;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import work.inception.ltsmodelshare.ModConfig;
import work.inception.ltsmodelshare.api.ApiClient;
import work.inception.ltsmodelshare.api.Dtos;
import work.inception.ltsmodelshare.client.Avatars;
import work.inception.ltsmodelshare.client.TextUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Favorites and following, with the same tab/pagination layout as the 1.12 build. */
public class GuiLibrary extends Screen {

    @Override
    public void renderBackground(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // No-op: Screen.render calls this *after* content; painting here dims the UI.
    }
    private static final int GAP = 6;
    private static final int TAB_FAVORITES = 0;
    private static final int TAB_FOLLOWING = 1;
    private static final int USER_SLOTS = 5;
    private static final int FEED_ROW_H = 26;

    private final Screen parent;
    private int tab = TAB_FAVORITES;
    private int favKind;
    private final List<Dtos.WorkCard> cards = new ArrayList<>();
    private final List<Dtos.UserCard> users = new ArrayList<>();
    private final List<Dtos.WorkCard> feed = new ArrayList<>();
    private long total;
    private long userTotal;
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
    private int usersTop = 64;
    private int userCellW;
    private int listTop;
    private boolean loading;
    private boolean needLogin;
    private String status = "";
    private int requestId;
    private long myUserId;

    public GuiLibrary(Screen parent) {
        super(Component.translatable("ltsmodelshare.library.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        clearWidgets();
        needLogin = !ModConfig.get().loggedIn();
        int cx = width / 2;
        addRenderableWidget(tabButton(cx - 96, 22, 92, "ltsmodelshare.library.favorites",
                () -> tab == TAB_FAVORITES, () -> switchTab(TAB_FAVORITES)));
        addRenderableWidget(tabButton(cx + 4, 22, 92, "ltsmodelshare.library.following",
                () -> tab == TAB_FOLLOWING, () -> switchTab(TAB_FOLLOWING)));

        int footerY = GuiNavigation.footerY(height);
        int pagY = footerY - 26;
        int backX = GuiNavigation.backX(width);
        if (tab == TAB_FAVORITES) {
            int subW = 64;
            addRenderableWidget(tabButton(GuiNavigation.MARGIN, footerY, subW, "ltsmodelshare.library.single",
                    () -> favKind == 0, () -> switchKind(0)));
            addRenderableWidget(tabButton(GuiNavigation.MARGIN + subW + GAP, footerY, subW, "ltsmodelshare.library.collection",
                    () -> favKind == 1, () -> switchKind(1)));
            addRenderableWidget(GuiNavigation.button(GuiNavigation.MARGIN, pagY, 42, 20,
                    "ltsmodelshare.common.previous", () -> { if (!needLogin && !loading && page > 1) { page--; loadFavorites(); } }));
            addRenderableWidget(GuiNavigation.button(width - GuiNavigation.MARGIN - 42, pagY, 42, 20,
                    "ltsmodelshare.common.next", () -> { if (!needLogin && !loading && page < maxPage()) { page++; loadFavorites(); } }));
            gridTop = 48;
        } else {
            addRenderableWidget(GuiNavigation.button(GuiNavigation.MARGIN, footerY, 42, 20,
                    "ltsmodelshare.common.previous", () -> { if (!needLogin && !loading && feedPage > 1) feedPage--; }));
            addRenderableWidget(GuiNavigation.button(backX - GAP - 42, footerY, 42, 20,
                    "ltsmodelshare.common.next", () -> { if (!needLogin && !loading && feedPage < maxFeedPage()) feedPage++; }));
            addRenderableWidget(GuiNavigation.button(width - GuiNavigation.MARGIN - 56, 44, 56, 16,
                    "ltsmodelshare.library.more", () -> {
                        if (!loading && myUserId > 0) minecraft.setScreen(new GuiFollowingUsers(this, myUserId, userTotal));
                    }));
            usersTop = 64;
            listTop = usersTop + 34 + 18;
            gridTop = listTop;
        }
        addRenderableWidget(GuiNavigation.button(backX, footerY, GuiNavigation.BACK_WIDTH, 20,
                "ltsmodelshare.common.back", () -> minecraft.setScreen(parent)));

        gridBottom = (tab == TAB_FAVORITES ? pagY : footerY) - 6;
        computeLayout();
        if (needLogin) {
            ++requestId;
            loading = false;
            cards.clear();
            users.clear();
            feed.clear();
            status = tr("ltsmodelshare.library.login_required");
            return;
        }
        if (tab == TAB_FAVORITES) loadFavorites();
        else loadFollowing();
    }

    private FitButton tabButton(int x, int y, int w, String key,
            java.util.function.BooleanSupplier selected, Runnable action) {
        return new FitButton(x, y, w, 20, Component.translatable(key), b -> action.run()) {
            @Override
            public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                if (!selected.getAsBoolean()) { super.renderWidget(graphics, mouseX, mouseY, partialTick); return; }
                graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0xFF3F6E8C);
                graphics.fill(getX(), getY() + getHeight() - 1, getX() + getWidth(), getY() + getHeight(), 0xFFFFFFFF);
                TextUtil.drawCenteredFit(graphics, font, getMessage().getString(), getX() + getWidth() / 2,
                        getY() + (getHeight() - 8) / 2, getWidth() - 8, 0xFFFFFF);
            }
        };
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

    private void switchTab(int next) {
        if (tab != next) {
            tab = next;
            page = 1;
            feedPage = 1;
            init();
        }
    }

    private void switchKind(int next) {
        if (favKind != next) {
            favKind = next;
            page = 1;
            init();
        }
    }

    private int maxPage() {
        return (int) Math.max(1, (total + perPage - 1) / perPage);
    }

    private int maxFeedPage() {
        return (int) Math.max(1, (feed.size() + feedPerPage - 1) / feedPerPage);
    }

    private int feedStart() {
        return Math.max(0, (feedPage - 1) * feedPerPage);
    }

    private void loadFavorites() {
        final int currentRequest = ++requestId;
        loading = true;
        cards.clear();
        status = tr("ltsmodelshare.common.loading");
        ApiClient.favorites(favKind == 1 ? "collection" : "single", page, perPage,
                new ApiClient.Callback<Dtos.Page<Dtos.WorkCard>>() {
                    @Override public void ok(Dtos.Page<Dtos.WorkCard> value) {
                        ApiClient.post(() -> {
                            if (currentRequest != requestId) return;
                            total = value.total;
                            if (page > maxPage()) { page = maxPage(); loadFavorites(); return; }
                            loading = false;
                            cards.clear();
                            if (value.records != null) cards.addAll(value.records);
                            status = cards.isEmpty() ? tr("ltsmodelshare.library.empty_favorites") : "";
                        });
                    }
                    @Override public void fail(String message) {
                        ApiClient.post(() -> { if (currentRequest == requestId) { loading = false; status = message; } });
                    }
                });
    }

    private void loadFollowing() {
        final int currentRequest = ++requestId;
        loading = true;
        users.clear();
        feed.clear();
        status = tr("ltsmodelshare.common.loading");
        ApiClient.resolveUserId(new ApiClient.Callback<Long>() {
            @Override public void ok(Long id) {
                myUserId = id == null ? 0 : id;
                ApiClient.followingUsers(myUserId, 1, 50, new ApiClient.Callback<Dtos.Page<Dtos.UserCard>>() {
                    @Override public void ok(Dtos.Page<Dtos.UserCard> value) {
                        ApiClient.post(() -> {
                            if (currentRequest != requestId) return;
                            userTotal = value.total;
                            users.clear();
                            if (value.records != null) users.addAll(value.records);
                            if (users.isEmpty()) {
                                loading = false;
                                status = tr("ltsmodelshare.library.empty_following");
                                return;
                            }
                            Set<Long> ids = new HashSet<>();
                            for (Dtos.UserCard user : users) ids.add(user.userId);
                            ApiClient.followingFeed(ids, 8, new ApiClient.Callback<List<Dtos.WorkCard>>() {
                                @Override public void ok(List<Dtos.WorkCard> feedValue) {
                                    ApiClient.post(() -> {
                                        if (currentRequest != requestId) return;
                                        loading = false;
                                        feed.clear();
                                        if (feedValue != null) feed.addAll(feedValue);
                                        feedPage = 1;
                                        status = feed.isEmpty() ? tr("ltsmodelshare.library.empty_feed") : "";
                                    });
                                }
                                @Override public void fail(String message) {
                                    ApiClient.post(() -> { if (currentRequest == requestId) { loading = false; status = message; } });
                                }
                            });
                        });
                    }
                    @Override public void fail(String message) {
                        ApiClient.post(() -> { if (currentRequest == requestId) { loading = false; status = message; } });
                    }
                });
            }
            @Override public void fail(String message) {
                ApiClient.post(() -> { if (currentRequest == requestId) { loading = false; status = message; } });
            }
        });
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xE0101010);
        int cx = width / 2;
        graphics.drawCenteredString(font, tr("ltsmodelshare.library.title"), cx, 8, 0xFFFFFF);

        if (needLogin) {
            graphics.drawCenteredString(font, status, cx, height / 2, 0xFFCCCCCC);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        if (tab == TAB_FAVORITES) {
            for (int i = 0; i < Math.min(cards.size(), perPage); i++) {
                int x = gridLeft + (i % cols) * (cardW + GAP);
                int y = gridTop + (i / cols) * (cardH + GAP);
                WorkCardView.grid(graphics, font, cards.get(i), x, y, cardW, cardH, imageH,
                        mouseX >= x && mouseX <= x + cardW && mouseY >= y && mouseY <= y + cardH);
            }
            graphics.drawCenteredString(font, tr("ltsmodelshare.common.page", page, maxPage()), cx,
                    GuiNavigation.footerY(height) - 20, 0xAAAAAA);
        } else {
            graphics.drawString(font, tr("ltsmodelshare.library.following_users"), GuiNavigation.MARGIN, 48, 0xFFCC88, false);
            for (int i = 0; i < Math.min(users.size(), USER_SLOTS); i++) {
                Dtos.UserCard user = users.get(i);
                int x = GuiNavigation.MARGIN + i * (userCellW + GAP);
                int y = usersTop;
                boolean hovered = mouseX >= x && mouseX <= x + userCellW && mouseY >= y && mouseY <= y + 32;
                graphics.fill(x, y, x + userCellW, y + 32, hovered ? 0x55FFFFFF : 0x33000000);
                Avatars.draw(graphics, font, user.asAuthor(), x + 3, y + 3, 16);
                String name = user.displayName == null || user.displayName.isEmpty() ? user.username : user.displayName;
                WorkCardView.small(graphics, font, TextUtil.trim(font, name, userCellW - 24), x + 22, y + 4, 0xFFFFFF);
                WorkCardView.small(graphics, font, TextUtil.trim(font, "@" + (user.username == null ? "" : user.username),
                        userCellW - 26), x + 22, y + 14, 0xAAAAAA);
                WorkCardView.small(graphics, font, tr("ltsmodelshare.library.follower_count", user.followerCount),
                        x + 3, y + 23, 0x999999);
            }
            graphics.drawString(font, tr("ltsmodelshare.library.feed"), GuiNavigation.MARGIN, listTop - 12, 0xFFCC88, false);
            int start = feedStart();
            for (int i = 0; i < feedPerPage && start + i < feed.size(); i++) {
                int y = listTop + i * FEED_ROW_H;
                boolean hovered = mouseX >= GuiNavigation.MARGIN && mouseX <= width - GuiNavigation.MARGIN
                        && mouseY >= y && mouseY <= y + FEED_ROW_H - 2;
                WorkCardView.row(graphics, font, feed.get(start + i), GuiNavigation.MARGIN, y,
                        width - GuiNavigation.MARGIN * 2, FEED_ROW_H - 2, hovered);
            }
            int backX = GuiNavigation.backX(width);
            int midLeft = GuiNavigation.MARGIN + 42 + GAP;
            int midRight = backX - GAP - 42 - GAP;
            TextUtil.drawCenteredFit(graphics, font, tr("ltsmodelshare.common.page", feedPage, maxFeedPage()),
                    (midLeft + midRight) / 2, GuiNavigation.footerY(height) + 6, Math.max(20, midRight - midLeft), 0xAAAAAA);
        }

        if (!status.isEmpty()) {
            graphics.drawCenteredString(font, TextUtil.trim(font, status, width - 24), cx, height / 2, 0xFFFFFF);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0 || loading || needLogin) return false;
        if (tab == TAB_FAVORITES) {
            for (int i = 0; i < Math.min(cards.size(), perPage); i++) {
                int x = gridLeft + (i % cols) * (cardW + GAP);
                int y = gridTop + (i / cols) * (cardH + GAP);
                if (mouseX >= x && mouseX <= x + cardW && mouseY >= y && mouseY <= y + cardH) {
                    minecraft.setScreen(new GuiWorkDetail(cards.get(i), this));
                    return true;
                }
            }
        } else {
            for (int i = 0; i < Math.min(users.size(), USER_SLOTS); i++) {
                int x = GuiNavigation.MARGIN + i * (userCellW + GAP);
                if (mouseX >= x && mouseX <= x + userCellW && mouseY >= usersTop && mouseY <= usersTop + 32) {
                    minecraft.setScreen(new GuiAuthorHome(users.get(i).asAuthor(), this));
                    return true;
                }
            }
            int index = feedStart() + (int) ((mouseY - listTop) / FEED_ROW_H);
            if (mouseX >= GuiNavigation.MARGIN && mouseX <= width - GuiNavigation.MARGIN
                    && mouseY >= listTop && mouseY < gridBottom && index >= 0 && index < feed.size()) {
                minecraft.setScreen(new GuiWorkDetail(feed.get(index), this));
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            minecraft.setScreen(parent);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
