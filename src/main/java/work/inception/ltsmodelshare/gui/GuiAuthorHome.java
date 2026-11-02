package work.inception.ltsmodelshare.gui;

import static work.inception.ltsmodelshare.client.Translations.tr;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import work.inception.ltsmodelshare.ModConfig;
import work.inception.ltsmodelshare.api.ApiClient;
import work.inception.ltsmodelshare.api.Dtos;
import work.inception.ltsmodelshare.client.Avatars;
import work.inception.ltsmodelshare.client.ImageDraw;
import work.inception.ltsmodelshare.client.TextUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Author home: identity banner, search, and one kind of work at a time.
 *
 * <p>Footer follows the favourites page design: bottom-left switches between single works and
 * collections (left aligned), bottom-right returns; the row above holds previous on the left and
 * next + follow on the right, whose combined width equals the back button's width.</p>
 */
public class GuiAuthorHome extends Screen {
    private static final int ROW_H = 36;
    private static final int BACK_W = AuthorFooterLayout.BACK_W;
    private static final int PAGE_W = AuthorFooterLayout.PAGE_W;
    private static final int FOLLOW_W = AuthorFooterLayout.FOLLOW_W;
    private static final int GAP = AuthorFooterLayout.GAP;
    /** The endpoint returns at most 100 rows per request and has no kind filter. */
    private static final int SERVER_PAGE_SIZE = 100;
    private static final int MAX_SERVER_PAGES = 5;

    private final Screen parent;
    private final long userId;
    private Dtos.Author author;
    private final List<Dtos.WorkCard> all = new ArrayList<>();
    private EditBox search;
    private FitButton followButton;
    private FitButton loginButton;
    private boolean exact;
    private boolean following;
    private boolean followBusy;
    private boolean ownProfile;
    private boolean loading;
    private boolean needLogin;
    private String status = "";
    private int requestId;
    private int followRequestId;
    private int kind;
    private final int[] page = {1, 1};
    private long total;
    private int perPage = 4;
    private int panelX, panelW;
    private int listTop = 108;
    private int listBottom;

    public GuiAuthorHome(Dtos.Author author, Screen parent) {
        super(Component.translatable("ltsmodelshare.author.title"));
        this.author = author;
        this.userId = author.userId;
        this.parent = parent;
    }

    boolean isAuthor(long authorId) { return userId == authorId; }

    @Override
    protected void init() {
        String keyword = search == null ? "" : search.getValue();
        boolean focused = search != null && search.isFocused();
        clearWidgets();
        loginButton = null;
        panelW = Math.min(360, Math.max(140, width - 16));
        panelX = (width - panelW) / 2;

        AuthorFooterLayout footer = new AuthorFooterLayout(width, height);
        int footerY = footer.footerY;
        int pagY = footer.pagY;
        listBottom = footer.listBottom;
        perPage = Math.max(1, (listBottom - listTop) / ROW_H);
        for (int i = 0; i < page.length; i++) page[i] = Math.min(page[i], maxPage(i));

        search = new EditBox(font, panelX, 80, Math.max(55, panelW - 114), 18,
                Component.translatable("ltsmodelshare.search.placeholder"));
        search.setMaxLength(64);
        search.setValue(keyword);
        addRenderableWidget(search);
        if (focused) setInitialFocus(search);
        addRenderableWidget(GuiNavigation.button(panelX + panelW - 110, 79, 44, 20,
                "ltsmodelshare.common.search", this::newSearch));
        addRenderableWidget(new FitButton(panelX + panelW - 62, 79, 62, 20,
                Component.translatable(exact ? "ltsmodelshare.search.exact" : "ltsmodelshare.search.fuzzy"),
                b -> {
                    exact = !exact;
                    b.setMessage(Component.translatable(exact ? "ltsmodelshare.search.exact"
                            : "ltsmodelshare.search.fuzzy"));
                    newSearch();
                }));

        // Bottom row: kind switch on the left, back on the right.
        addRenderableWidget(tabButton(footer.kindX(0), footerY, footer.kindWidth(), "ltsmodelshare.library.single",
                0, () -> kind == 0));
        addRenderableWidget(tabButton(footer.kindX(1), footerY, footer.kindWidth(), "ltsmodelshare.library.collection",
                1, () -> kind == 1));
        addRenderableWidget(GuiNavigation.button(footer.backX(), footerY, BACK_W, 20,
                "ltsmodelshare.common.back", () -> minecraft.setScreen(parent)));

        // Row above: previous on the left; next + follow together span the back button's width.
        addRenderableWidget(GuiNavigation.button(footer.previousX(), pagY, PAGE_W, 20,
                "ltsmodelshare.common.previous", () -> turn(-1)));
        addRenderableWidget(GuiNavigation.button(footer.nextX(), pagY, PAGE_W, 20,
                "ltsmodelshare.common.next", () -> turn(1)));
        followButton = addRenderableWidget(new FitButton(footer.followX(), pagY,
                FOLLOW_W, 20, followLabel(), b -> toggleFollow()));
        refreshFollowButton();

        if (!ModConfig.get().loggedIn()) {
            ++requestId;
            ++followRequestId;
            loading = false;
            needLogin = true;
            all.clear();
            status = tr("ltsmodelshare.author.login_required");
            showLoginButton();
        } else {
            needLogin = false;
            loadFollowState();
            load();
        }
    }

    private FitButton tabButton(int x, int y, int w, String key, int value,
                                java.util.function.BooleanSupplier selected) {
        return new FitButton(x, y, w, 20, Component.translatable(key), b -> {
            if (kind == value) return;
            kind = value;
            init();
        }) {
            @Override
            public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                if (!selected.getAsBoolean()) { super.renderWidget(graphics, mouseX, mouseY, partialTick); return; }
                graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0xFF3F6E8C);
                graphics.fill(getX(), getY() + getHeight() - 1, getX() + getWidth(), getY() + getHeight(),
                        0xFFFFFFFF);
                TextUtil.drawCenteredFit(graphics, font, getMessage().getString(), getX() + getWidth() / 2,
                        getY() + (getHeight() - 8) / 2, getWidth() - 8, 0xFFFFFF);
            }
        };
    }

    private List<Dtos.WorkCard> singles = new ArrayList<>();
    private List<Dtos.WorkCard> collections = new ArrayList<>();

    private List<Dtos.WorkCard> of(int type) {
        return type == 1 ? collections : singles;
    }

    private void split() {
        singles = new ArrayList<>();
        collections = new ArrayList<>();
        for (Dtos.WorkCard card : all) {
            if (card == null) continue;
            ("collection".equals(card.kind) ? collections : singles).add(card);
        }
    }

    private int maxPage(int type) {
        return (int) Math.max(1, (of(type).size() + perPage - 1) / perPage);
    }

    private List<Dtos.WorkCard> visible() {
        List<Dtos.WorkCard> items = of(kind);
        int from = Math.min(items.size(), (page[kind] - 1) * perPage);
        int to = Math.min(items.size(), from + perPage);
        return items.subList(from, to);
    }

    private void turn(int delta) {
        if (needLogin || loading) return;
        int next = page[kind] + delta;
        if (next < 1 || next > maxPage(kind)) return;
        page[kind] = next;
        init();
    }

    private void showLoginButton() {
        if (loginButton == null) {
            loginButton = addRenderableWidget(GuiNavigation.button(width / 2 - 45,
                    (listTop + listBottom) / 2 + 8, 90, 20, "ltsmodelshare.author.login",
                    () -> minecraft.setScreen(new GuiLogin(this))));
        }
    }

    private String keyword() { return search == null ? "" : search.getValue().trim(); }

    private void load() {
        if (needLogin) return;
        final int request = ++requestId;
        loading = true;
        status = ChatFormatting.GRAY + tr("ltsmodelshare.common.loading");
        fetch(request, 1, new ArrayList<>());
    }

    /** The author endpoint pages and does not filter by kind, so gather the rows once and page here. */
    private void fetch(int request, int serverPage, List<Dtos.WorkCard> collected) {
        ApiClient.authorHome(userId, keyword(), exact,
                work.inception.ltsmodelshare.client.Translations.siteLocale(), serverPage, SERVER_PAGE_SIZE,
                new ApiClient.Callback<Dtos.AuthorHome>() {
                    @Override public void ok(Dtos.AuthorHome value) {
                        ApiClient.post(() -> {
                            if (request != requestId) return;
                            if (value != null && value.author != null) author = value.author;
                            List<Dtos.WorkCard> records = value == null || value.works == null
                                    || value.works.records == null ? List.of() : value.works.records;
                            total = value == null || value.works == null ? 0 : value.works.total;
                            collected.addAll(records);
                            boolean more = !records.isEmpty() && collected.size() < total
                                    && serverPage < MAX_SERVER_PAGES;
                            if (more) { fetch(request, serverPage + 1, collected); return; }
                            loading = false;
                            all.clear();
                            all.addAll(collected);
                            split();
                            for (int i = 0; i < page.length; i++) page[i] = Math.min(page[i], maxPage(i));
                            status = all.isEmpty() ? tr("ltsmodelshare.author.empty") : "";
                        });
                    }
                    @Override public void fail(String message) {
                        ApiClient.post(() -> {
                            if (request != requestId) return;
                            loading = false;
                            if (message != null && message.contains("M05-047")) {
                                needLogin = true;
                                status = tr("ltsmodelshare.author.login_required");
                                showLoginButton();
                            } else status = ChatFormatting.RED + message;
                        });
                    }
                });
    }

    private Component followLabel() {
        return Component.translatable(following ? "ltsmodelshare.common.following"
                : "ltsmodelshare.common.follow");
    }

    private void refreshFollowButton() {
        if (followButton == null) return;
        followButton.setMessage(followLabel());
        followButton.visible = !ownProfile;
        followButton.active = !followBusy && !ownProfile;
    }

    private void loadFollowState() {
        final int request = ++followRequestId;
        ApiClient.resolveUserId(new ApiClient.Callback<Long>() {
            @Override public void ok(Long myId) {
                ApiClient.post(() -> {
                    if (request != followRequestId) return;
                    ownProfile = myId != null && myId == userId;
                    refreshFollowButton();
                    if (ownProfile) return;
                    ApiClient.followingState(userId, new ApiClient.Callback<Boolean>() {
                        @Override public void ok(Boolean value) {
                            ApiClient.post(() -> {
                                if (request != followRequestId) return;
                                following = Boolean.TRUE.equals(value);
                                refreshFollowButton();
                            });
                        }
                        @Override public void fail(String message) { }
                    });
                });
            }
            @Override public void fail(String message) { }
        });
    }

    private void toggleFollow() {
        if (ownProfile || followBusy) return;
        if (!ModConfig.get().loggedIn()) {
            minecraft.setScreen(new GuiLogin(this));
            return;
        }
        followBusy = true;
        refreshFollowButton();
        ++followRequestId;
        ApiClient.toggleFollow(userId, new ApiClient.Callback<Dtos.SocialToggle>() {
            @Override public void ok(Dtos.SocialToggle value) {
                ApiClient.post(() -> {
                    followBusy = false;
                    if (value != null && value.following != null) following = value.following;
                    if (value != null && value.followerCount != null && author != null)
                        author.followerCount = value.followerCount;
                    refreshFollowButton();
                });
            }
            @Override public void fail(String message) {
                ApiClient.post(() -> {
                    followBusy = false;
                    refreshFollowButton();
                    status = ChatFormatting.RED + message;
                });
            }
        });
    }

    private void newSearch() {
        page[0] = 1;
        page[1] = 1;
        load();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(panelX, 14, panelX + panelW, 73, 0xFF303137);
        if (author != null) {
            if (author.bannerUrl != null && !author.bannerUrl.isEmpty()) {
                ImageDraw.drawPreview(graphics, author.bannerUrl, panelX, 14, panelW, 59);
                graphics.fill(panelX, 14, panelX + panelW, 73, 0x99000000);
            }
            Avatars.draw(graphics, font, author, panelX + 8, 28, 28);
            String name = author.displayName == null || author.displayName.isEmpty()
                    ? author.username : author.displayName;
            graphics.drawString(font, TextUtil.trim(font, name, panelW - 50), panelX + 42, 27, 0xFFFFFF, true);
            graphics.drawString(font, "@" + TextUtil.trim(font, author.username, panelW - 55),
                    panelX + 42, 39, 0xCCCCCC, true);
            graphics.drawString(font, TextUtil.trim(font, author.bio == null ? "" : author.bio, panelW - 52),
                    panelX + 42, 52, 0xDDDDDD, true);
            graphics.drawString(font, tr("ltsmodelshare.author.follow_stats",
                            author.followingCount, author.followerCount), panelX + 42, 64, 0xAAAAAA, true);
        }
        graphics.fill(panelX, listTop, panelX + panelW, listBottom, 0x30000000);
        if (!needLogin) {
            graphics.enableScissor(panelX, listTop, panelX + panelW, listBottom);
            try {
                List<Dtos.WorkCard> rows = visible();
                int y = listTop + 4;
                for (Dtos.WorkCard card : rows) {
                    boolean hovered = mouseX >= panelX + 3 && mouseX < panelX + panelW - 3
                            && mouseY >= Math.max(listTop, y) && mouseY < Math.min(listBottom, y + ROW_H - 2);
                    graphics.fill(panelX + 3, y, panelX + panelW - 3, y + ROW_H - 2,
                            hovered ? 0x55FFFFFF : 0x33000000);
                    ImageDraw.drawThumb(graphics, card.cover, panelX + 6, y + 3, 38, 28);
                    int textW = Math.max(30, panelW - 56);
                    graphics.drawString(font, TextUtil.trim(font,
                            card.title == null ? tr("ltsmodelshare.common.untitled") : card.title, textW),
                            panelX + 49, y + 4, 0xFFFFFF, true);
                    graphics.drawString(font, TextUtil.trim(font,
                            tr("ltsmodelshare.common.work_stats", card.downloadCount, card.viewCount), textW),
                            panelX + 49, y + 17, 0xAAAAAA, true);
                    y += ROW_H;
                }
            } finally {
                graphics.disableScissor();
            }
        }
        if (!status.isEmpty())
            graphics.drawCenteredString(font, TextUtil.trim(font, status, panelW - 4), width / 2,
                    listTop + Math.max(10, (listBottom - listTop) / 2 - 12), 0xFFFFFF);
        else if (!needLogin && !loading && visible().isEmpty())
            graphics.drawCenteredString(font, tr("ltsmodelshare.author.empty"), width / 2,
                    listTop + Math.max(10, (listBottom - listTop) / 2 - 12), 0xAAAAAA);
        if (!needLogin) {
            AuthorFooterLayout footer = new AuthorFooterLayout(width, height);
            int midLeft = footer.previousX() + PAGE_W + GAP;
            int midRight = footer.nextX() - GAP;
            int widthAvailable = Math.max(20, midRight - midLeft);
            String label = tr("ltsmodelshare.common.page", page[kind], maxPage(kind));
            TextUtil.drawCenteredFit(graphics, font, TextUtil.trim(font, label, widthAvailable),
                    (midLeft + midRight) / 2, footer.pagY + 6, widthAvailable, 0xAAAAAA);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0 || loading || needLogin || mouseX < panelX + 3 || mouseX >= panelX + panelW - 3
                || mouseY < listTop || mouseY >= listBottom) return false;
        int index = (int) (mouseY - (listTop + 4)) / ROW_H;
        List<Dtos.WorkCard> rows = visible();
        if (index < 0 || index >= rows.size()) return false;
        minecraft.setScreen(new GuiWorkDetail(rows.get(index), this));
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (needLogin || mouseX < panelX || mouseX > panelX + panelW || mouseY < listTop || mouseY >= listBottom)
            return super.mouseScrolled(mouseX, mouseY, delta);
        turn(delta > 0 ? -1 : 1);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            minecraft.setScreen(parent);
            return true;
        }
        if ((keyCode == 257 || keyCode == 335) && search != null && search.isFocused()) {
            newSearch();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override public boolean isPauseScreen() { return false; }
}
