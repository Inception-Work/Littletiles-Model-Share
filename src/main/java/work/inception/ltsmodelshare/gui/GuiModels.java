package work.inception.ltsmodelshare.gui;

import static work.inception.ltsmodelshare.client.Translations.tr;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import work.inception.ltsmodelshare.ModConfig;
import work.inception.ltsmodelshare.api.ApiClient;
import work.inception.ltsmodelshare.api.Dtos;
import work.inception.ltsmodelshare.client.InputHandler;
import work.inception.ltsmodelshare.client.TextUtil;

import java.util.ArrayList;
import java.util.List;

/** Model browser: card grid, search, pagination and footer actions. */
public class GuiModels extends Screen {
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
    private EditBox search;
    private GuiAdvancedSearch.Filter filter = new GuiAdvancedSearch.Filter();

    public GuiModels() {
        super(Component.translatable("ltsmodelshare.models.title"));
    }

    @Override
    protected void init() {
        clearWidgets();
        int topW = Math.min(360, width - 16);
        int searchW = Math.max(70, topW - 152);
        int topX = width / 2 - topW / 2;
        search = new EditBox(font, topX, 24, searchW, 18, Component.translatable("ltsmodelshare.search.placeholder"));
        search.setMaxLength(64);
        search.setHint(Component.translatable("ltsmodelshare.search.placeholder"));
        addRenderableWidget(search);

        addRenderableWidget(GuiNavigation.button(topX + searchW + 4, 23, 44, 20,
                "ltsmodelshare.common.search", () -> { page = 1; reload(); }));
        addRenderableWidget(GuiNavigation.button(topX + searchW + 52, 23, 40, 20,
                "ltsmodelshare.common.refresh", () -> reload()));
        addRenderableWidget(GuiNavigation.button(topX + searchW + 96, 23, 56, 20,
                "ltsmodelshare.search.filters", () -> minecraft.setScreen(new GuiAdvancedSearch(this, filter))));

        int pageY = GuiNavigation.footerY(height) - 26;
        int footerW = Math.max(34, (width - 2 * GuiNavigation.MARGIN - 3 * GAP) / 4);
        int socialW = Math.max(28, (footerW - GAP) / 2);
        int social2X = width - GuiNavigation.MARGIN - socialW;
        int social1X = social2X - GAP - socialW;
        int nextX = social1X - GAP - 42;
        addRenderableWidget(GuiNavigation.button(GuiNavigation.MARGIN, pageY, 42, 20,
                "ltsmodelshare.common.previous", () -> { if (page > 1) { page--; reload(); } }));
        addRenderableWidget(GuiNavigation.button(nextX, pageY, 42, 20,
                "ltsmodelshare.common.next", () -> { if (page < maxPage()) { page++; reload(); } }));
        addRenderableWidget(GuiNavigation.button(social1X, pageY, socialW, 20,
                "ltsmodelshare.library.title", () -> openLibrary()));
        addRenderableWidget(GuiNavigation.button(social2X, pageY, socialW, 20,
                "ltsmodelshare.library.my_space", () -> openMySpace()));

        int by = GuiNavigation.footerY(height);
        int bx = GuiNavigation.MARGIN;
        addRenderableWidget(GuiNavigation.button(bx, by, footerW, 20,
                ModConfig.get().loggedIn() ? "ltsmodelshare.models.logout" : "ltsmodelshare.common.login",
                this::toggleLogin));
        bx += footerW + GAP;
        addRenderableWidget(GuiNavigation.button(bx, by, footerW, 20,
                "ltsmodelshare.models.upload", () -> {
                    if (!ModConfig.get().loggedIn()) minecraft.setScreen(new GuiLogin(this));
                    else minecraft.setScreen(new GuiUpload(this));
                }));
        bx += footerW + GAP;
        addRenderableWidget(GuiNavigation.button(bx, by, footerW, 20,
                "ltsmodelshare.models.invitation", () -> minecraft.setScreen(new GuiInviteAccess(this))));
        bx += footerW + GAP;
        addRenderableWidget(GuiNavigation.button(bx, by, footerW, 20,
                "ltsmodelshare.common.close", () -> minecraft.setScreen(null)));

        computeLayout();
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
        if (!filter.tagCodes.isEmpty()) query.tagCodes = new ArrayList<>(filter.tagCodes);
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
            minecraft.setScreen(new GuiLogin(new GuiModels()));
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
        renderBackground(graphics);
        if (loading && System.currentTimeMillis() - loadStartedAt > 30_000L) {
            loading = false;
            status = tr("ltsmodelshare.common.request_timeout");
        }
        int cx = width / 2;
        graphics.drawCenteredString(font, title, cx, 8, 0xFFFFFF);

        int hovered = cardAt(mouseX, mouseY);
        for (int i = 0; i < Math.min(cards.size(), perPage); i++) {
            int x = gridLeft + (i % columns) * (cardW + GAP);
            int y = gridTop + (i / columns) * (cardH + GAP);
            WorkCardView.grid(graphics, font, cards.get(i), x, y, cardW, cardH, imageH, hovered == i);
        }

        if (!status.isEmpty()) {
            graphics.drawCenteredString(font, TextUtil.trim(font, status, width - 24), cx, height / 2, 0xFFFFFF);
        }

        int footerW = Math.max(34, (width - 2 * GuiNavigation.MARGIN - 3 * GAP) / 4);
        int socialW = Math.max(28, (footerW - GAP) / 2);
        int nextX = width - GuiNavigation.MARGIN - socialW * 2 - GAP * 2 - 42;
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
            minecraft.setScreen(new GuiWorkDetail(cards.get(index), this));
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 && search != null && search.isFocused()) {
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
