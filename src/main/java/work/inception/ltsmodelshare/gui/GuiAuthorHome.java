package work.inception.ltsmodelshare.gui;

import static work.inception.ltsmodelshare.client.Translations.tr;

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

/** Author profile with follow action and their works. */
public class GuiAuthorHome extends Screen {
    private final Screen parent;
    private final long userId;
    private Dtos.Author author;
    private final List<Dtos.WorkCard> cards = new ArrayList<>();
    private EditBox search;
    private boolean followLoaded;
    private boolean following;
    private boolean followBusy;
    private boolean loading;
    private boolean loaded;
    private boolean needLogin;
    private String status = "";
    private int requestId;
    private int page = 1;
    private int total;
    private int perPage = 5;
    private int listTop = 108;
    private int listBottom;
    private int scroll;
    private int rowHeight = 30;

    public GuiAuthorHome(Dtos.Author author, Screen parent) {
        super(Component.translatable("ltsmodelshare.author.title"));
        this.author = author;
        this.userId = author.userId;
        this.parent = parent;
    }

    @Override
    protected void init() {
        clearWidgets();
        int panelW = Math.min(360, width - 16);
        int panelX = width / 2 - panelW / 2;
        listBottom = GuiNavigation.footerY(height) - 6;
        perPage = Math.max(3, Math.min(12, (listBottom - listTop) / rowHeight));

        search = new EditBox(font, panelX, 80, Math.max(55, panelW - 114), 18,
                Component.translatable("ltsmodelshare.search.placeholder"));
        search.setMaxLength(64);
        addRenderableWidget(search);
        addRenderableWidget(GuiNavigation.button(panelX + panelW - 110, 79, 44, 20,
                "ltsmodelshare.common.search", () -> { page = 1; scroll = 0; loaded = false; load(); }));
        addRenderableWidget(GuiNavigation.button(panelX + panelW - 62, 79, 62, 20,
                "ltsmodelshare.search.fuzzy", () -> { page = 1; loaded = false; load(); }));

        int footerY = GuiNavigation.footerY(height);
        int backX = GuiNavigation.backX(width);
        addRenderableWidget(GuiNavigation.button(GuiNavigation.MARGIN, footerY, 42, 20,
                "ltsmodelshare.common.previous", () -> { if (page > 1) { page--; scroll = 0; loaded = false; load(); } }));
        addRenderableWidget(GuiNavigation.button(backX - GuiNavigation.GAP - 42, footerY, 42, 20,
                "ltsmodelshare.common.next", () -> { if (page < maxPage()) { page++; scroll = 0; loaded = false; load(); } }));
        addRenderableWidget(GuiNavigation.button(backX, footerY, GuiNavigation.BACK_WIDTH, 20,
                "ltsmodelshare.common.back", () -> minecraft.setScreen(parent)));
        addRenderableWidget(new FitButton(backX - GuiNavigation.GAP - 42 - GuiNavigation.GAP - 64, footerY,
                64, 20, Component.translatable(following ? "ltsmodelshare.common.following" : "ltsmodelshare.common.follow"),
                b -> toggleFollow()));

        if (!ModConfig.get().loggedIn()) {
            needLogin = true;
            status = tr("ltsmodelshare.author.login_required");
            return;
        }
        needLogin = false;
        loadFollowState();
        if (!loaded) load();
    }

    private int maxPage() {
        return Math.max(1, (total + perPage - 1) / perPage);
    }

    private void load() {
        if (loading) return;
        final int currentRequest = ++requestId;
        loading = true;
        cards.clear();
        status = tr("ltsmodelshare.common.loading");
        ApiClient.authorHome(userId, search == null ? "" : search.getValue().trim(), false,
                work.inception.ltsmodelshare.client.Translations.siteLocale(), page, perPage,
                new ApiClient.Callback<Dtos.AuthorHome>() {
                    @Override public void ok(Dtos.AuthorHome value) {
                        ApiClient.post(() -> {
                            if (currentRequest != requestId) return;
                            if (value.author != null) author = value.author;
                            total = value.works == null ? 0 : (int) value.works.total;
                            loading = false;
                            loaded = true;
                            cards.clear();
                            if (value.works != null && value.works.records != null) cards.addAll(value.works.records);
                            status = cards.isEmpty() ? tr("ltsmodelshare.author.empty") : "";
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

    private void loadFollowState() {
        if (followLoaded) return;
        followLoaded = true;
        ApiClient.resolveUserId(new ApiClient.Callback<Long>() {
            @Override public void ok(Long myId) {
                if (myId != null && myId == userId) return; // own profile: no follow button
                ApiClient.followingState(userId, new ApiClient.Callback<Boolean>() {
                    @Override public void ok(Boolean value) {
                        ApiClient.post(() -> { following = Boolean.TRUE.equals(value); init(); });
                    }
                    @Override public void fail(String message) { }
                });
            }
            @Override public void fail(String message) { }
        });
    }

    private void toggleFollow() {
        if (followBusy) return;
        if (!ModConfig.get().loggedIn()) {
            minecraft.setScreen(new GuiLogin(this));
            return;
        }
        followBusy = true;
        ApiClient.toggleFollow(userId, new ApiClient.Callback<Dtos.SocialToggle>() {
            @Override public void ok(Dtos.SocialToggle value) {
                ApiClient.post(() -> {
                    followBusy = false;
                    if (value != null && value.following != null) following = value.following;
                    if (value != null && value.followerCount != null && author != null) {
                        author.followerCount = value.followerCount;
                    }
                    init();
                });
            }
            @Override public void fail(String message) {
                ApiClient.post(() -> { followBusy = false; status = message; });
            }
        });
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int panelW = Math.min(360, width - 16);
        int panelX = width / 2 - panelW / 2;
        graphics.fill(panelX, 14, panelX + panelW, 73, 0xFF303137);
        if (author != null) {
            if (author.bannerUrl != null && !author.bannerUrl.isEmpty()) {
                ImageDraw.drawCropped(graphics, author.bannerUrl, panelX, 14, panelW, 59);
                graphics.fill(panelX, 14, panelX + panelW, 73, 0x99000000);
            }
            Avatars.draw(graphics, font, author, panelX + 8, 28, 28);
            String name = author.displayName == null || author.displayName.isEmpty()
                    ? author.username : author.displayName;
            graphics.drawString(font, TextUtil.trim(font, name, panelW - 50), panelX + 42, 27, 0xFFFFFF, true);
            graphics.drawString(font, "@" + (author.username == null ? "" : author.username),
                    panelX + 42, 40, 0xCCCCCC, false);
            graphics.drawString(font, TextUtil.trim(font, author.bio == null ? "" : author.bio, panelW - 52),
                    panelX + 42, 53, 0xDDDDDD, false);
            graphics.drawString(font, tr("ltsmodelshare.author.follow_stats",
                            author.followingCount, author.followerCount), panelX + 42, 64, 0xAAAAAA, false);
        }

        int y = listTop + 2 - scroll;
        for (Dtos.WorkCard card : cards) {
            if (y + rowHeight > listTop && y < listBottom) {
                graphics.fill(panelX + 3, y, panelX + panelW - 3, y + rowHeight - 2,
                        mouseX >= panelX + 3 && mouseY >= y && mouseY < y + rowHeight - 2 ? 0x55FFFFFF : 0x33000000);
                ImageDraw.drawCropped(graphics, card.cover, panelX + 6, y + 2, 38, rowHeight - 6);
                String title = card.title == null ? tr("ltsmodelshare.common.untitled") : card.title;
                graphics.drawString(font, TextUtil.trim(font, title, panelW - 60), panelX + 49, y + 4, 0xFFFFFF, true);
                graphics.drawString(font, tr("ltsmodelshare.common.work_stats", card.downloadCount, card.viewCount),
                        panelX + 49, y + 16, 0xAAAAAA, false);
            }
            y += rowHeight;
        }

        if (!status.isEmpty()) {
            graphics.drawCenteredString(font, TextUtil.trim(font, status, panelW - 4), width / 2,
                    Math.max(listTop + 12, height / 2), 0xFFFFFF);
        }
        int backX = GuiNavigation.backX(width);
        int midLeft = GuiNavigation.MARGIN + 42 + GuiNavigation.GAP;
        int midRight = backX - GuiNavigation.GAP - 42 - GuiNavigation.GAP - 64 - GuiNavigation.GAP;
        TextUtil.drawCenteredFit(graphics, font,
                TextUtil.trim(font, tr("ltsmodelshare.common.page", page, maxPage()), Math.max(20, midRight - midLeft)),
                (midLeft + midRight) / 2, GuiNavigation.footerY(height) + 6, Math.max(20, midRight - midLeft), 0xAAAAAA);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0 || loading) return false;
        int panelW = Math.min(360, width - 16);
        int panelX = width / 2 - panelW / 2;
        if (mouseX < panelX || mouseX > panelX + panelW) return false;
        int index = (int) ((mouseY - listTop + scroll) / rowHeight);
        if (mouseY >= listTop && mouseY < listBottom && index >= 0 && index < cards.size()) {
            minecraft.setScreen(new GuiWorkDetail(cards.get(index), this));
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scroll = Math.max(0, Math.min(Math.max(0, cards.size() * rowHeight - (listBottom - listTop)),
                scroll - (int) (delta * rowHeight)));
        return true;
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
