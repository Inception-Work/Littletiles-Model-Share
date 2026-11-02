package work.inception.ltsmodelshare.gui;

import static work.inception.ltsmodelshare.client.Translations.tr;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import work.inception.ltsmodelshare.ModConfig;
import work.inception.ltsmodelshare.api.ApiClient;
import work.inception.ltsmodelshare.api.Dtos;
import work.inception.ltsmodelshare.client.TextUtil;

import java.util.ArrayList;
import java.util.List;

/** My space: own works and collections without avatar/profile chrome. */
public class GuiMySpace extends Screen {
    private static final int GAP = 6;
    private final Screen parent;
    private int tab;
    private final List<Dtos.WorkCard> all = new ArrayList<>();
    private final List<Dtos.WorkCard> shown = new ArrayList<>();
    private int page = 1;
    private long total;
    private int perPage = 8;
    private int cols = 4;
    private int cardW = 70;
    private int cardH = 70;
    private int imageH = 40;
    private int gridLeft;
    private int gridTop = 52;
    private int gridBottom;
    private boolean loading;
    private boolean needLogin;
    private boolean onlyThisVersion;
    private int versionX;
    private String status = "";
    private int requestId;

    public GuiMySpace(Screen parent) {
        super(Component.translatable("ltsmodelshare.library.my_space"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        clearWidgets();
        needLogin = !ModConfig.get().loggedIn();
        int cx = width / 2;
        addRenderableWidget(tabButton(cx - 96, "ltsmodelshare.library.my_works", 0));
        addRenderableWidget(tabButton(cx + 4, "ltsmodelshare.library.my_collections", 1));

        int footerY = GuiNavigation.footerY(height);
        int backX = GuiNavigation.backX(width);
        addRenderableWidget(GuiNavigation.button(GuiNavigation.MARGIN, footerY, 42, 20,
                "ltsmodelshare.common.previous", () -> { if (page > 1) { page--; load(); } }));
        versionX = backX - GAP - 64;
        addRenderableWidget(new FitButton(versionX, footerY, 64, 20,
                Component.literal(work.inception.ltsmodelshare.LtsModelShare.GAME_VERSION), b -> {
            onlyThisVersion = !onlyThisVersion;
            page = 1;
            load();
        }));
        addRenderableWidget(GuiNavigation.button(versionX - GAP - 42, footerY, 42, 20,
                "ltsmodelshare.common.next", () -> { if (page < maxPage()) { page++; load(); } }));
        addRenderableWidget(GuiNavigation.button(backX, footerY, GuiNavigation.BACK_WIDTH, 20,
                "ltsmodelshare.common.back", () -> minecraft.setScreen(parent)));

        gridBottom = footerY - 6;
        computeGrid();
        if (needLogin) {
            ++requestId;
            loading = false;
            all.clear();
            shown.clear();
            status = tr("ltsmodelshare.library.login_required");
            return;
        }
        load();
    }

    private FitButton tabButton(int x, String key, int selectedTab) {
        return new FitButton(x, 24, 92, 20, Component.translatable(key), b -> switchTab(selectedTab)) {
            @Override
            public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                if (tab != selectedTab) { super.renderWidget(graphics, mouseX, mouseY, partialTick); return; }
                graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0xFF3F6E8C);
                graphics.fill(getX(), getY() + getHeight() - 1, getX() + getWidth(), getY() + getHeight(), 0xFFFFFFFF);
                TextUtil.drawCenteredFit(graphics, font, getMessage().getString(), getX() + getWidth() / 2,
                        getY() + (getHeight() - 8) / 2, getWidth() - 8, 0xFFFFFF);
            }
        };
    }

    private void computeGrid() {
        int prefW = width >= 300 ? 78 : 66;
        cardW = Math.max(40, Math.min(width - 16, prefW));
        imageH = Math.max(18, (cardW - 6) * 9 / 16);
        cardH = imageH + 28;
        cols = Math.max(1, (width - 16 + GAP) / (cardW + GAP));
        int rows = Math.max(1, (gridBottom - gridTop + GAP) / (cardH + GAP));
        perPage = Math.max(1, Math.min(48, cols * rows));
        gridLeft = Math.max(8, (width - (cols * cardW + (cols - 1) * GAP)) / 2);
    }

    private void switchTab(int next) {
        if (tab != next) {
            tab = next;
            page = 1;
            load();
        }
    }

    private void load() {
        final int currentRequest = ++requestId;
        loading = true;
        status = tr("ltsmodelshare.common.loading");
        all.clear();
        shown.clear();
        ApiClient.resolveUserId(new ApiClient.Callback<Long>() {
            @Override public void ok(Long id) {
                if (id == null) {
                    ApiClient.post(() -> {
                        if (currentRequest != requestId) return;
                        loading = false;
                        status = tr("ltsmodelshare.api.user_missing");
                    });
                    return;
                }
                ApiClient.authorHome(id, "", false,
                        work.inception.ltsmodelshare.client.Translations.siteLocale(),
                        tab == 1 ? "collection" : "single", "latest", page, perPage,
                        new ApiClient.Callback<Dtos.AuthorHome>() {
                            @Override public void ok(Dtos.AuthorHome value) {
                                ApiClient.post(() -> {
                                    if (currentRequest != requestId) return;
                                    loading = false;
                                    all.clear();
                                    if (value.works != null && value.works.records != null) {
                                        for (Dtos.WorkCard card : value.works.records) {
                                            if (!onlyThisVersion || matchesThisVersion(card)) all.add(card);
                                        }
                                    }
                                    total = onlyThisVersion ? all.size()
                                            : (value.works == null ? 0 : value.works.total);
                                    page = Math.min(page, maxPage());
                                    rebuild();
                                });
                            }
                            @Override public void fail(String message) {
                                ApiClient.post(() -> {
                                    if (currentRequest == requestId) { loading = false; status = message; }
                                });
                            }
                        });
            }
            @Override public void fail(String message) {
                ApiClient.post(() -> { if (currentRequest == requestId) { loading = false; status = message; } });
            }
        });
    }

    private static boolean matchesThisVersion(Dtos.WorkCard card) {
        if (card == null) return false;
        if (card.sourceLoader != null && !"forge".equalsIgnoreCase(card.sourceLoader)) return false;
        if (card.sourceGameVersion == null) return false;
        return card.sourceGameVersion.trim().startsWith("1.20");
    }

    private void rebuild() {
        shown.clear();
        shown.addAll(all);
        status = shown.isEmpty() ? tr("ltsmodelshare.library.empty_my") : "";
    }

    private int maxPage() {
        return (int) Math.max(1, (total + perPage - 1) / perPage);
    }

    private List<Dtos.WorkCard> pageItems() {
        int from = Math.max(0, (page - 1) * perPage);
        int to = Math.min(shown.size(), from + perPage);
        return from >= to ? List.of() : shown.subList(from, to);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 8, 0xFFFFFF);
        if (onlyThisVersion) {
            graphics.fill(versionX, GuiNavigation.footerY(height) + 20,
                    versionX + 64, GuiNavigation.footerY(height) + 22, 0xFFFFFFFF);
        }
        if (needLogin) {
            graphics.drawCenteredString(font, status, width / 2, height / 2, 0xFFCCCCCC);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }
        List<Dtos.WorkCard> items = pageItems();
        for (int i = 0; i < items.size(); i++) {
            int x = gridLeft + (i % cols) * (cardW + GAP);
            int y = gridTop + (i / cols) * (cardH + GAP);
            WorkCardView.grid(graphics, font, items.get(i), x, y, cardW, cardH, imageH,
                    mouseX >= x && mouseX <= x + cardW && mouseY >= y && mouseY <= y + cardH);
        }
        if (!status.isEmpty()) {
            graphics.drawCenteredString(font, TextUtil.trim(font, status, width - 24), width / 2, height / 2, 0xFFFFFF);
        }
        int backX = GuiNavigation.backX(width);
        int midLeft = GuiNavigation.MARGIN + 42 + GAP;
        int midRight = versionX - GAP - 42 - GAP;
        TextUtil.drawCenteredFit(graphics, font, tr("ltsmodelshare.common.page", page, maxPage()),
                (midLeft + midRight) / 2, GuiNavigation.footerY(height) + 6, Math.max(20, midRight - midLeft), 0xAAAAAA);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0 || loading || needLogin) return false;
        List<Dtos.WorkCard> items = pageItems();
        for (int i = 0; i < items.size(); i++) {
            int x = gridLeft + (i % cols) * (cardW + GAP);
            int y = gridTop + (i / cols) * (cardH + GAP);
            if (mouseX >= x && mouseX <= x + cardW && mouseY >= y && mouseY <= y + cardH) {
                minecraft.setScreen(new GuiWorkDetail(items.get(i), this));
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
