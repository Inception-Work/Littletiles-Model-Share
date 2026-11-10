package work.inception.ltsmodelshare.gui;

import static work.inception.ltsmodelshare.client.Translations.tr;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import work.inception.ltsmodelshare.ModConfig;
import work.inception.ltsmodelshare.api.ApiClient;
import work.inception.ltsmodelshare.api.Dtos;
import work.inception.ltsmodelshare.client.TextUtil;

import java.util.ArrayList;
import java.util.List;

/** Model browser: card grid, search, pagination and footer actions. */
public class GuiModels extends Screen {

    @Override
    public void renderBackground(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // No-op: Screen.render calls this *after* content; painting here dims the UI.
    }
    private static final int GAP = 6;

    private final List<Dtos.WorkCard> cards = new ArrayList<>();
    private int page = 1;
    private long total;
    private int perPage = 6;
    private int columns = 3;
    private int cardW = 78;
    private int cardH = 68;
    private int imageH = 40;
    private int gridLeft;
    private int gridTop;
    private String status = "";
    private boolean loading;
    private long loadStartedAt;
    private int requestId;
    private boolean onlyThisVersion;
    private static final int[] GRID_OPTIONS={0,2,4,8,16,32};
    private int gridOption;
    private int versionX;
    private int versionY;
    private int versionW;
    private int socialW1;
    private int socialW2;
    private int social1X;
    private int social2X;
    private int nextX;
    private EditBox search;
    private FitButton sessionButton;
    private GuiAdvancedSearch.Filter filter = new GuiAdvancedSearch.Filter();

    public GuiModels() {
        // 1.12.2 draws the product name here as plain text, not a translation key.
        super(Component.literal("Littletiles Model Share"));
    }

    @Override
    protected void init() {
        boolean wasLastPage = page == maxPage();
        String keyword = search == null ? filter.q : search.getValue();
        boolean focused = search == null || search.isFocused();
        clearWidgets();
        int topW = Math.min(504, width - 16);
        int searchW = Math.max(40, topW - 262);
        int topX = width / 2 - topW / 2;
        search = new EditBox(font, topX, 24, searchW, 18, Component.translatable("ltsmodelshare.search.placeholder"));
        search.setMaxLength(64);
        search.setValue(keyword == null ? "" : keyword);
        addRenderableWidget(search);
        if (focused) setInitialFocus(search);

        addRenderableWidget(GuiNavigation.button(topX + searchW + 4, 23, 44, 20,
                "ltsmodelshare.common.search", () -> { page = 1; reload(); }));
        addRenderableWidget(GuiNavigation.button(topX + searchW + 52, 23, 40, 20,
                "ltsmodelshare.common.refresh", () -> { page = 1; reload(); }));
        addRenderableWidget(GuiNavigation.button(topX + searchW + 96, 23, 50, 20,
                "ltsmodelshare.search.filters", () -> minecraft.setScreen(new GuiAdvancedSearch(this, filter))));
        versionX = topX + searchW + 150;
        versionY = 23;
        versionW = 48;
        addRenderableWidget(new FitButton(versionX, versionY, versionW, 20,
                Component.literal(work.inception.ltsmodelshare.LtsModelShare.GAME_VERSION), b -> {
            onlyThisVersion = !onlyThisVersion;
            page = 1;
            reload();
        }));

        addRenderableWidget(new FitButton(versionX+52,versionY,60,20,Component.literal(gridLabel()),b -> {
            gridOption=(gridOption+1)%GRID_OPTIONS.length;
            b.setMessage(Component.literal(gridLabel()));page=1;reload();
        }));

        int pageY = GuiNavigation.footerY(height) - 26;
        int footerW = Math.max(34, (width - 2 * GuiNavigation.MARGIN - 3 * GAP) / 4);
        // Grow these two buttons per language; "Favorites & Following" does not fit the
        // equal-width grid used by the footer buttons.
        int baseSocial = Math.max(28, (footerW - GAP) / 2);
        socialW1 = Math.max(baseSocial, font.width(tr("ltsmodelshare.library.title")) + 10);
        socialW2 = Math.max(baseSocial, font.width(tr("ltsmodelshare.library.my_space")) + 10);
        int minNextX = GuiNavigation.MARGIN + 42 + GAP;
        int socialRoom = width - GuiNavigation.MARGIN - (minNextX + 42 + GAP);
        if (socialW1 + socialW2 > socialRoom) {
            float scale = socialRoom / (float) (socialW1 + socialW2);
            socialW1 = Math.max(24, (int) Math.floor(socialW1 * scale));
            socialW2 = Math.max(24, socialRoom - socialW1);
        }
        social2X = width - GuiNavigation.MARGIN - socialW2;
        social1X = social2X - GAP - socialW1;
        nextX = social1X - GAP - 42;
        addRenderableWidget(GuiNavigation.button(GuiNavigation.MARGIN, pageY, 42, 20,
                "ltsmodelshare.common.previous", () -> { if (page > 1) { page--; reload(); } }));
        addRenderableWidget(GuiNavigation.button(nextX, pageY, 42, 20,
                "ltsmodelshare.common.next", () -> { if (page < maxPage()) { page++; reload(); } }));
        addRenderableWidget(GuiNavigation.button(social1X, pageY, socialW1, 20,
                "ltsmodelshare.library.title", () -> openLibrary()));
        addRenderableWidget(GuiNavigation.button(social2X, pageY, socialW2, 20,
                "ltsmodelshare.library.my_space", () -> openMySpace()));

        int by = GuiNavigation.footerY(height);
        int bx = GuiNavigation.MARGIN;
        sessionButton = addRenderableWidget(GuiNavigation.button(bx, by, footerW, 20,
                ModConfig.get().loggedIn() ? "ltsmodelshare.models.logout" : "ltsmodelshare.common.login",
                this::toggleLogin));
        bx += footerW + GAP;
        addRenderableWidget(GuiNavigation.button(bx, by, footerW, 20,
                "ltsmodelshare.models.upload", () -> minecraft.setScreen(ModConfig.get().loggedIn() ? new GuiUpload(this) : new GuiLogin(this))));
        bx += footerW + GAP;
        addRenderableWidget(GuiNavigation.button(bx, by, footerW, 20,
                "ltsmodelshare.models.invitation", () -> minecraft.setScreen(new GuiInviteAccess(this))));
        bx += footerW + GAP;
        addRenderableWidget(GuiNavigation.button(bx, by, footerW, 20,
                "ltsmodelshare.common.close", () -> minecraft.setScreen(null)));

        computeLayout();
        page = wasLastPage ? maxPage() : Math.min(page, maxPage());
        work.inception.ltsmodelshare.LtsModelShare.LOG.info("[ui] GuiModels 打开，开始加载列表");
        reload();
    }

    private void computeLayout() {
        ModelGridLayout layout = new ModelGridLayout(width, height);
        cardW = layout.cardWidth;
        imageH = layout.imageHeight;
        cardH = layout.cardHeight;
        columns = layout.columns;
        perPage = Math.max(1, Math.min(48, columns * layout.rows));
        gridLeft = layout.left;
        gridTop = layout.top;
    }

    private String gridLabel() {
        return gridOption==0?tr("ltsmodelshare.models.all_grid"):"Grid "+GRID_OPTIONS[gridOption];
    }

    private int maxPage() {
        return (int) Math.max(1, (total + perPage - 1) / perPage);
    }

    private void reload() {
        final int currentRequest = ++requestId;
        loading = true;
        loadStartedAt = System.currentTimeMillis();
        cards.clear();
        status = tr("ltsmodelshare.common.loading");
        Dtos.CardQuery query = new Dtos.CardQuery(search == null ? "" : search.getValue().trim(),
                filter.sort == null ? "latest" : filter.sort, page, perPage,
                work.inception.ltsmodelshare.client.Translations.siteLocale());
        query.authorId = filter.authorId;
        query.grid=GRID_OPTIONS[gridOption]==0?null:GRID_OPTIONS[gridOption];
        if (!filter.tagCodes.isEmpty()) query.tagCodes = new ArrayList<>(filter.tagCodes);
        if (onlyThisVersion) {
            query.sourceLoader = "forge";
            query.sourceGameVersions = java.util.List.of(work.inception.ltsmodelshare.LtsModelShare.GAME_VERSION);
        }
        ApiClient.listCards(query, new ApiClient.Callback<Dtos.Page<Dtos.WorkCard>>() {
            @Override public void ok(Dtos.Page<Dtos.WorkCard> value) {
                ApiClient.post(() -> {
                    if (currentRequest != requestId) return;
                    total = value.total;
                    if (page > maxPage()) { page = maxPage(); reload(); return; }
                    loading = false;
                    cards.clear();
                    if (value.records != null) cards.addAll(value.records);
                    status = cards.isEmpty() ? tr("ltsmodelshare.search.no_results") : "";
                });
            }
            @Override public void fail(String message) {
                ApiClient.post(() -> {
                    if (currentRequest != requestId) return;
                    loading = false;
                    if (!ModConfig.get().loggedIn() && sessionButton != null)
                        sessionButton.setMessage(Component.translatable("ltsmodelshare.common.login"));
                    status = message;
                });
            }
        });
    }

    private void toggleLogin() {
        if (ModConfig.get().loggedIn()) {
            ApiClient.revokeRefreshQuietly(ModConfig.get().refreshToken);
            ModConfig.get().clearSession();
            ModConfig.get().username = "";
            ModConfig.get().save();
            minecraft.setScreen(new GuiLogin(this));
        } else {
            minecraft.setScreen(new GuiLogin(this));
        }
    }

    private void openLibrary() {
        if (!ModConfig.get().loggedIn()) minecraft.setScreen(new GuiLogin(this));
        else minecraft.setScreen(new GuiLibrary(this));
    }

    /** Called by the advanced search screen when the user applies filters. */
    public void applyFilter(GuiAdvancedSearch.Filter next) {
        this.filter = next;
        if (search != null) filter.q = search.getValue().trim();
        page = 1;
        reload();
    }

    private void openMySpace() {
        if (!ModConfig.get().loggedIn()) minecraft.setScreen(new GuiLogin(this));
        else minecraft.setScreen(new GuiMySpace(this));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xE0101010);
        if (loading && System.currentTimeMillis() - loadStartedAt > 30_000L) {
            loading = false;
            status = tr("ltsmodelshare.common.request_timeout");
        }
        int cx = width / 2;
        graphics.drawCenteredString(font, title, cx, 8, 0xFFFFFF);
        if (onlyThisVersion) {
            graphics.fill(versionX, versionY + 20, versionX + versionW, versionY + 22, 0xFFFFFFFF);
        }
        search.setHint(search.isFocused() ? Component.empty() : Component.translatable("ltsmodelshare.search.placeholder"));

        int hovered = cardAt(mouseX, mouseY);
        for (int i = 0; i < Math.min(cards.size(), perPage); i++) {
            int x = gridLeft + (i % columns) * (cardW + GAP);
            int y = gridTop + (i / columns) * (cardH + GAP);
            WorkCardView.grid(graphics, font, cards.get(i), x, y, cardW, cardH, imageH, hovered == i);
        }

        if (!status.isEmpty()) {
            TextUtil.drawCenteredFit(graphics, font, status, cx, gridTop + cardH / 2, width - 20, 0xFFFFFF);
        }

        int midLeft = GuiNavigation.MARGIN + 42 + GAP;
        int midRight = nextX - GAP;
        String mid = (loading ? "" : tr("ltsmodelshare.common.page", page, maxPage()))
                + (ModConfig.get().loggedIn()
                ? tr("ltsmodelshare.models.logged_in", ModConfig.get().username)
                : tr("ltsmodelshare.models.guest"));
        TextUtil.drawCenteredFit(graphics, font, TextUtil.trim(font, mid, Math.max(20, midRight - midLeft)),
                (midLeft + midRight) / 2, GuiNavigation.footerY(height) - 20, Math.max(20, midRight - midLeft), 0xAAAAAA);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private int cardAt(int mouseX, int mouseY) {
        for (int i = 0; i < Math.min(cards.size(), perPage); i++) {
            int x = gridLeft + (i % columns) * (cardW + GAP);
            int y = gridTop + (i / columns) * (cardH + GAP);
            if (mouseX >= x && mouseX <= x + cardW && mouseY >= y && mouseY <= y + cardH) return i;
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0 || loading) return false;
        int index = cardAt((int) mouseX, (int) mouseY);
        if (index >= 0 && index < cards.size()) {
            minecraft.setScreen(new GuiWorkDetail(cards.get(index), this, GRID_OPTIONS[gridOption]==0?null:GRID_OPTIONS[gridOption]));
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == 257 || keyCode == 335) && search != null && search.isFocused()) {
            page = 1;
            reload();
            return true;
        }
        if (keyCode == 256) {
            minecraft.setScreen(null);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
