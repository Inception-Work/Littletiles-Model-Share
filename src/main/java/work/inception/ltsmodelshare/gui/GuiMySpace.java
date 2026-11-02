package work.inception.ltsmodelshare.gui;

import static work.inception.ltsmodelshare.client.Translations.tr;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.text.TextFormatting;
import work.inception.ltsmodelshare.ModConfig;
import work.inception.ltsmodelshare.api.ApiClient;
import work.inception.ltsmodelshare.api.Dtos;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 我的空间：自己的公开作品与合集。
 * 数据复用作者主页接口（本人视角），作品总数不显示，只在本地按种类分页。
 */
public class GuiMySpace extends GuiScreen {
    private static final int GAP = 6;
    private final GuiScreen parent;
    private int tab; // 0 = works, 1 = collections
    private final List<Dtos.WorkCard> all = new ArrayList<>();
    private final List<Dtos.WorkCard> shown = new ArrayList<>();
    private int page = 1;
    private int perPage = 8;
    private int cols = 4;
    private int cardW = 70;
    private int cardH = 70;
    private int imageH = 40;
    private int gridLeft;
    private int gridTop;
    private int gridBottom;
    private boolean loading;
    private boolean needLogin;
    private boolean onlyThisVersion;
    private int versionX;
    private String status = "";
    private int requestId;

    public GuiMySpace(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        needLogin = !ModConfig.get().loggedIn();
        int cx = width / 2;
        buttonList.add(new TabButton(20, cx - 96, 24, 92, 20,
                tr("ltsmodelshare.library.my_works"), tab == 0));
        buttonList.add(new TabButton(21, cx + 4, 24, 92, 20,
                tr("ltsmodelshare.library.my_collections"), tab == 1));
        // 与关注 tab 相同的分页底栏：上一页最左，下一页紧挨返回，中间页码。
        int footerY = GuiNavigation.footerY(height);
        buttonList.add(new LocalizedButton(3, GuiNavigation.MARGIN, footerY, 42, 20,
                tr("ltsmodelshare.common.previous")));
        versionX = GuiNavigation.backX(width) - GAP - 64;
        buttonList.add(new LocalizedButton(8, versionX, footerY, 64, 20, "1.12.2"));
        buttonList.add(new LocalizedButton(4, versionX - GAP - 42, footerY, 42, 20,
                tr("ltsmodelshare.common.next")));
        buttonList.add(GuiNavigation.backButton(6, width, height, tr("ltsmodelshare.common.back")));

        gridTop = 52;
        gridBottom = footerY - 6;
        computeGrid();
        if (needLogin) {
            ++requestId;
            loading = false;
            status = tr("ltsmodelshare.library.login_required");
            all.clear();
            shown.clear();
            return;
        }
        load();
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

    private void load() {
        final int currentRequest = ++requestId;
        loading = true;
        status = TextFormatting.GRAY + tr("ltsmodelshare.common.loading");
        all.clear();
        shown.clear();
        ApiClient.resolveUserId(new ApiClient.Callback<Long>() {
            @Override public void ok(Long id) {
                if (id == null) {
                    failLater(currentRequest, tr("ltsmodelshare.api.user_missing"));
                    return;
                }
                ApiClient.authorHome(id, "", false,
                        work.inception.ltsmodelshare.client.Translations.siteLocale(), 1, 100,
                        new ApiClient.Callback<Dtos.AuthorHome>() {
                            @Override public void ok(Dtos.AuthorHome value) {
                                ApiClient.post(() -> {
                                    if (currentRequest != requestId) return;
                                    loading = false;
                                    all.clear();
                                    if (value.works != null && value.works.records != null) {
                                        all.addAll(value.works.records);
                                    }
                                    rebuild();
                                });
                            }
                            @Override public void fail(String message) {
                                failLater(currentRequest, message);
                            }
                        });
            }
            @Override public void fail(String message) {
                failLater(currentRequest, message);
            }
        });
    }

    private void failLater(int request, String message) {
        ApiClient.post(() -> {
            if (request != requestId) return;
            loading = false;
            status = TextFormatting.RED + (message == null ? "" : message);
        });
    }

    private void rebuild() {
        shown.clear();
        boolean wantCollection = tab == 1;
        for (Dtos.WorkCard card : all) {
            if (card == null || wantCollection != "collection".equals(card.kind)) continue;
            if (onlyThisVersion && !matchesThisVersion(card)) continue;
            shown.add(card);
        }
        if (page > maxPage()) page = maxPage();
        status = shown.isEmpty() ? TextFormatting.GRAY + tr("ltsmodelshare.library.empty_my") : "";
    }

    private int maxPage() {
        return (int) Math.max(1, (shown.size() + perPage - 1) / perPage);
    }

    private static boolean matchesThisVersion(Dtos.WorkCard card) {
        if (card == null) return false;
        if (card.sourceLoader != null && !"forge".equalsIgnoreCase(card.sourceLoader)) return false;
        if (card.sourceGameVersion == null) return false;
        return card.sourceGameVersion.trim().startsWith("1.12");
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case 20:
            case 21:
                int next = button.id == 20 ? 0 : 1;
                if (next != tab) {
                    tab = next;
                    page = 1;
                    for (GuiButton tabButton : buttonList) {
                        if (tabButton instanceof TabButton) {
                            ((TabButton) tabButton).setSelected(
                                    (tabButton.id == 20) == (tab == 0));
                        }
                    }
                    rebuild();
                }
                break;
            case 3:
                if (page > 1) { page--; }
                break;
            case 4:
                if (page < maxPage()) { page++; }
                break;
            case 8:
                onlyThisVersion = !onlyThisVersion;
                page = 1;
                rebuild();
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
        if (button != 0 || loading || needLogin) return;
        int idx = cardAt(mx, my);
        if (idx >= 0 && idx < pageItems().size()) {
            mc.displayGuiScreen(new GuiWorkDetail(pageItems().get(idx), this));
        }
    }

    private List<Dtos.WorkCard> pageItems() {
        int from = Math.max(0, (page - 1) * perPage);
        int to = Math.min(shown.size(), from + perPage);
        if (from >= to) return new ArrayList<>();
        return shown.subList(from, to);
    }

    private int cardAt(int mx, int my) {
        List<Dtos.WorkCard> items = pageItems();
        for (int i = 0; i < items.size(); i++) {
            int x = gridLeft + (i % cols) * (cardW + GAP);
            int y = gridTop + (i / cols) * (cardH + GAP);
            if (mx >= x && mx <= x + cardW && my >= y && my <= y + cardH) return i;
        }
        return -1;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        if (onlyThisVersion) {
            Gui.drawRect(versionX, GuiNavigation.footerY(height) + 20,
                    versionX + 64, GuiNavigation.footerY(height) + 22, 0xFFFFFFFF);
        }
        drawCenteredString(fontRenderer, tr("ltsmodelshare.library.my_space"), width / 2, 8, 0xFFFFFF);

        if (needLogin) {
            drawCenteredString(fontRenderer, status, width / 2, height / 2, 0xFFCCCCCC);
            super.drawScreen(mouseX, mouseY, partialTicks);
            return;
        }

        List<Dtos.WorkCard> items = pageItems();
        for (int i = 0; i < items.size(); i++) {
            int x = gridLeft + (i % cols) * (cardW + GAP);
            int y = gridTop + (i / cols) * (cardH + GAP);
            boolean hovered = mouseX >= x && mouseX <= x + cardW && mouseY >= y && mouseY <= y + cardH;
            WorkCardView.grid(fontRenderer, items.get(i), x, y, cardW, cardH, imageH, hovered);
        }
        if (!status.isEmpty()) {
            drawCenteredString(fontRenderer, WorkCardView.trim(fontRenderer, status, width - 24),
                    width / 2, height / 2, 0xFFFFFF);
        }
        int backX = GuiNavigation.backX(width);
        int midLeft = GuiNavigation.MARGIN + 42 + GAP;
        int midRight = versionX - GAP - 42 - GAP;
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
