package work.inception.ltsmodelshare.gui;

import static work.inception.ltsmodelshare.client.Translations.tr;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.text.TextFormatting;
import work.inception.ltsmodelshare.api.ApiClient;
import work.inception.ltsmodelshare.api.Dtos;
import work.inception.ltsmodelshare.client.Avatars;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** 全部关注用户列表：分页展示，点用户进入作者主页。 */
public class GuiFollowingUsers extends GuiScreen {
    private static final int GAP = 6;
    private final GuiScreen parent;
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
    private int listBottom;

    GuiFollowingUsers(GuiScreen parent, long userId, long total) {
        this.parent = parent;
        this.userId = userId;
        this.total = total;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int footerY = GuiNavigation.footerY(height);
        listBottom = footerY - 6;
        // 与关注 tab 相同的底栏：上一页最左，下一页紧挨返回，中间页码。
        buttonList.add(new LocalizedButton(3, GuiNavigation.MARGIN, footerY, 42, 20,
                tr("ltsmodelshare.common.previous")));
        buttonList.add(new LocalizedButton(4, GuiNavigation.backX(width) - GAP - 42, footerY, 42, 20,
                tr("ltsmodelshare.common.next")));
        buttonList.add(GuiNavigation.backButton(6, width, height, tr("ltsmodelshare.common.back")));
        cellW = Math.max(64, Math.min(96, (width - 20) / 4));
        cols = Math.max(1, (width - 20 + GAP) / (cellW + GAP));
        int rows = Math.max(1, (listBottom - listTop + GAP) / (cellH + GAP));
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
        status = TextFormatting.GRAY + tr("ltsmodelshare.common.loading");
        ApiClient.followingUsers(userId, page, pageSize, new ApiClient.Callback<Dtos.Page<Dtos.UserCard>>() {
            @Override public void ok(Dtos.Page<Dtos.UserCard> value) {
                ApiClient.post(() -> {
                    if (currentRequest != requestId) return;
                    total = value.total;
                    if (page > maxPage()) {
                        page = maxPage();
                        load();
                        return;
                    }
                    loading = false;
                    users.clear();
                    if (value.records != null) users.addAll(value.records);
                    status = users.isEmpty()
                            ? TextFormatting.GRAY + tr("ltsmodelshare.library.empty_following") : "";
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

    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case 3:
                if (page > 1) { page--; load(); }
                break;
            case 4:
                if (page < maxPage()) { page++; load(); }
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
        if (button != 0 || loading) return;
        int idx = userAt(mx, my);
        if (idx >= 0 && idx < users.size()) {
            mc.displayGuiScreen(new GuiAuthorHome(users.get(idx).asAuthor(), this));
        }
    }

    private int userAt(int mx, int my) {
        for (int i = 0; i < Math.min(users.size(), pageSize); i++) {
            int x = GuiNavigation.MARGIN + (i % cols) * (cellW + GAP);
            int y = listTop + (i / cols) * (cellH + GAP);
            if (mx >= x && mx <= x + cellW && my >= y && my <= y + cellH) return i;
        }
        return -1;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRenderer, tr("ltsmodelshare.library.more_users"), width / 2, 20, 0xFFFFFF);
        for (int i = 0; i < Math.min(users.size(), pageSize); i++) {
            Dtos.UserCard user = users.get(i);
            int x = GuiNavigation.MARGIN + (i % cols) * (cellW + GAP);
            int y = listTop + (i / cols) * (cellH + GAP);
            boolean hovered = mouseX >= x && mouseX <= x + cellW && mouseY >= y && mouseY <= y + cellH;
            Gui.drawRect(x, y, x + cellW, y + cellH - 2, hovered ? 0x55FFFFFF : 0x33000000);
            Avatars.draw(fontRenderer, user.asAuthor(), x + 4, y + 4, 20);
            String name = user.displayName == null || user.displayName.isEmpty()
                    ? user.username : user.displayName;
            WorkCardView.small(fontRenderer, WorkCardView.trim(fontRenderer, name, cellW - 30),
                    x + 27, y + 5, 0xFFFFFF);
            WorkCardView.small(fontRenderer, WorkCardView.trim(fontRenderer,
                    "@" + (user.username == null ? "" : user.username), cellW - 32),
                    x + 27, y + 16, 0xAAAAAA);
            WorkCardView.small(fontRenderer, WorkCardView.trim(fontRenderer,
                    tr("ltsmodelshare.library.follower_count", user.followerCount), cellW - 8),
                    x + 4, y + 32, 0x999999);
        }
        if (!status.isEmpty()) {
            drawCenteredString(fontRenderer, status, width / 2, height / 2, 0xFFFFFF);
        }
        int backX = GuiNavigation.backX(width);
        int midLeft = GuiNavigation.MARGIN + 42 + GAP;
        int midRight = backX - GAP - 42 - GAP;
        drawCenteredString(fontRenderer, WorkCardView.trim(fontRenderer, TextFormatting.GRAY
                        + tr("ltsmodelshare.common.page", page, maxPage()), Math.max(20, midRight - midLeft)),
                (midLeft + midRight) / 2, GuiNavigation.footerY(height) + 6, 0xAAAAAA);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
