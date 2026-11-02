package work.inception.ltsmodelshare.gui;

import static work.inception.ltsmodelshare.client.Translations.tr;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import work.inception.ltsmodelshare.api.ApiClient;
import work.inception.ltsmodelshare.api.Dtos;
import work.inception.ltsmodelshare.client.Avatars;
import work.inception.ltsmodelshare.client.TextUtil;

import java.util.ArrayList;
import java.util.List;

/** All followed users with the same pagination row as the following tab. */
public class GuiFollowingUsers extends Screen {
    private static final int GAP = 6;
    private final Screen parent;
    private final long userId;
    private final List<Dtos.UserCard> users = new ArrayList<>();
    private long total;
    private int page = 1;
    private int pageSize = 24;
    private int cols = 4;
    private int cellW = 80;
    private int cellH = 52;
    private boolean loading;
    private String status = "";
    private int requestId;
    private int listTop = 44;

    GuiFollowingUsers(Screen parent, long userId, long total) {
        super(Component.translatable("ltsmodelshare.library.more_users"));
        this.parent = parent;
        this.userId = userId;
        this.total = total;
    }

    @Override
    protected void init() {
        clearWidgets();
        int footerY = GuiNavigation.footerY(height);
        int backX = GuiNavigation.backX(width);
        addRenderableWidget(GuiNavigation.button(GuiNavigation.MARGIN, footerY, 42, 20,
                "ltsmodelshare.common.previous", () -> { if (page > 1) { page--; load(); } }));
        addRenderableWidget(GuiNavigation.button(backX - GAP - 42, footerY, 42, 20,
                "ltsmodelshare.common.next", () -> { if (page < maxPage()) { page++; load(); } }));
        addRenderableWidget(GuiNavigation.button(backX, footerY, GuiNavigation.BACK_WIDTH, 20,
                "ltsmodelshare.common.back", () -> minecraft.setScreen(parent)));

        cellW = Math.max(64, Math.min(96, (width - 20) / 4));
        cols = Math.max(1, (width - 20 + GAP) / (cellW + GAP));
        int rows = Math.max(1, (footerY - 6 - listTop + GAP) / (cellH + GAP));
        pageSize = Math.max(1, Math.min(50, cols * rows));
        page = Math.min(page, maxPage());
        load();
    }

    private int maxPage() {
        return (int) Math.max(1, (total + pageSize - 1) / pageSize);
    }

    private void load() {
        final int currentRequest = ++requestId;
        loading = true;
        status = tr("ltsmodelshare.common.loading");
        ApiClient.followingUsers(userId, page, pageSize, new ApiClient.Callback<Dtos.Page<Dtos.UserCard>>() {
            @Override public void ok(Dtos.Page<Dtos.UserCard> value) {
                ApiClient.post(() -> {
                    if (currentRequest != requestId) return;
                    total = value.total;
                    if (page > maxPage()) { page = maxPage(); load(); return; }
                    loading = false;
                    users.clear();
                    if (value.records != null) users.addAll(value.records);
                    status = users.isEmpty() ? tr("ltsmodelshare.library.empty_following") : "";
                });
            }
            @Override public void fail(String message) {
                ApiClient.post(() -> { if (currentRequest == requestId) { loading = false; status = message; } });
            }
        });
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 20, 0xFFFFFF);
        for (int i = 0; i < Math.min(users.size(), pageSize); i++) {
            Dtos.UserCard user = users.get(i);
            int x = GuiNavigation.MARGIN + (i % cols) * (cellW + GAP);
            int y = listTop + (i / cols) * (cellH + GAP);
            boolean hovered = mouseX >= x && mouseX <= x + cellW && mouseY >= y && mouseY <= y + cellH;
            graphics.fill(x, y, x + cellW, y + cellH - 2, hovered ? 0x55FFFFFF : 0x33000000);
            Avatars.draw(graphics, font, user.asAuthor(), x + 4, y + 4, 20);
            String name = user.displayName == null || user.displayName.isEmpty() ? user.username : user.displayName;
            WorkCardView.small(graphics, font, TextUtil.trim(font, name, cellW - 30), x + 27, y + 5, 0xFFFFFF);
            WorkCardView.small(graphics, font, TextUtil.trim(font, "@" + (user.username == null ? "" : user.username),
                    cellW - 32), x + 27, y + 16, 0xAAAAAA);
            WorkCardView.small(graphics, font, tr("ltsmodelshare.library.follower_count", user.followerCount),
                    x + 4, y + 32, 0x999999);
        }
        if (!status.isEmpty()) {
            graphics.drawCenteredString(font, TextUtil.trim(font, status, width - 24), width / 2, height / 2, 0xFFFFFF);
        }
        int backX = GuiNavigation.backX(width);
        int midLeft = GuiNavigation.MARGIN + 42 + GAP;
        int midRight = backX - GAP - 42 - GAP;
        TextUtil.drawCenteredFit(graphics, font, tr("ltsmodelshare.common.page", page, maxPage()),
                (midLeft + midRight) / 2, GuiNavigation.footerY(height) + 6, Math.max(20, midRight - midLeft), 0xAAAAAA);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0 || loading) return false;
        for (int i = 0; i < Math.min(users.size(), pageSize); i++) {
            int x = GuiNavigation.MARGIN + (i % cols) * (cellW + GAP);
            int y = listTop + (i / cols) * (cellH + GAP);
            if (mouseX >= x && mouseX <= x + cellW && mouseY >= y && mouseY <= y + cellH) {
                minecraft.setScreen(new GuiAuthorHome(users.get(i).asAuthor(), this));
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
