package work.inception.ltsmodelshare.gui;

import static work.inception.ltsmodelshare.client.Translations.tr;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.text.TextFormatting;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import work.inception.ltsmodelshare.ModConfig;
import work.inception.ltsmodelshare.api.ApiClient;
import work.inception.ltsmodelshare.api.Dtos;
import work.inception.ltsmodelshare.client.Avatars;
import work.inception.ltsmodelshare.client.ImageDraw;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 游戏里的作者主页：横幅、头像、昵称和简介，下面按「单个作品 / 作品集」分组。
 * 直接调用网页作者主页使用的 /public/work/author；服务端要求登录并按 top 排序。
 */
public class GuiAuthorHome extends GuiScreen {

    private static final int ROW_H = 36;
    private final GuiScreen parent;
    private final long userId;
    private Dtos.Author author;
    private final List<Dtos.WorkCard> cards = new ArrayList<>();
    private GuiTextField search;
    private boolean exact;
    private boolean loading;
    private boolean needLogin;
    private boolean onlyThisVersion;
    private int versionX;
    private String status = "";
    private int requestId;
    private int page = 1;
    private int pageSize = 3;
    private long total;
    private int scroll;
    private int panelX;
    private int panelW;
    private int listTop;
    private int listBottom;
    /** 关注按钮状态 */
    private boolean following;
    private boolean followBusy;
    private boolean ownProfile;

    public GuiAuthorHome(Dtos.Author author, GuiScreen parent) {
        this.author = author;
        this.userId = author.userId;
        this.parent = parent;
    }

    boolean isAuthor(long authorId) {
        return userId == authorId;
    }

    @Override
    public void initGui() {
        boolean wasLastPage = page == maxPage();
        String keyword = search == null ? "" : search.getText();
        boolean focused = search != null && search.isFocused();
        buttonList.clear();
        panelW = Math.min(360, Math.max(140, width - 16));
        panelX = (width - panelW) / 2;
        listTop = 108;
        listBottom = height - 47;
        pageSize = Math.max(3, Math.min(12, (listBottom - listTop - 20) / ROW_H));
        page = wasLastPage ? maxPage() : Math.min(page, maxPage());
        scroll = Math.min(scroll, maxScroll());

        search = new GuiTextField(0, fontRenderer, panelX, 80, Math.max(55, panelW - 166), 18);
        search.setMaxStringLength(64);
        search.setText(keyword);
        search.setFocused(focused);
        buttonList.add(new LocalizedButton(1, panelX + panelW - 162, 79, 44, 20, tr("ltsmodelshare.common.search")));
        buttonList.add(new LocalizedButton(2, panelX + panelW - 114, 79, 62, 20, exact ? tr("ltsmodelshare.search.exact") : tr("ltsmodelshare.search.fuzzy")));
        versionX = panelX + panelW - 48;
        buttonList.add(new LocalizedButton(8, versionX, 79, 48, 20, "1.12.2"));

        int x = width / 2;
        buttonList.add(new LocalizedButton(3, GuiNavigation.MARGIN, GuiNavigation.footerY(height), 56, 20,
                tr("ltsmodelshare.common.previous")));
        buttonList.add(new LocalizedButton(4, GuiNavigation.MARGIN + 56 + GuiNavigation.GAP,
                GuiNavigation.footerY(height), 56, 20, tr("ltsmodelshare.common.next")));
        buttonList.add(GuiNavigation.backButton(5, width, height, tr("ltsmodelshare.common.back")));
        // 关注按钮放在返回旁边；自己的主页不显示。
        int followW = 64;
        buttonList.add(new LocalizedButton(7, GuiNavigation.backX(width) - GuiNavigation.GAP - followW,
                GuiNavigation.footerY(height), followW, 20, followLabel()));
        loadFollowState();

        needLogin = false;
        load();
    }

    private int maxPage() {
        return (int) Math.max(1, (total + pageSize - 1) / pageSize);
    }

    private static boolean matchesThisVersion(Dtos.WorkCard card) {
        if (card == null) return false;
        if (card.sourceLoader != null && !"forge".equalsIgnoreCase(card.sourceLoader)) return false;
        if (card.sourceGameVersion == null) return false;
        return card.sourceGameVersion.trim().startsWith("1.12");
    }

    private String languageTag() {
        return work.inception.ltsmodelshare.client.Translations.siteLocale();
    }

    private void load() {
        final int currentRequest = ++requestId;
        final int previousScroll = scroll;
        loading = true;
        cards.clear(); // 搜索/翻页失败时不能继续展示上一页的作品
        status = TextFormatting.GRAY + tr("ltsmodelshare.common.loading");
        final String keyword = search.getText().trim();
        ApiClient.authorHome(userId, keyword, exact, languageTag(), page, pageSize,
                new ApiClient.Callback<Dtos.AuthorHome>() {
                    @Override
                    public void ok(Dtos.AuthorHome value) {
                        ApiClient.post(() -> {
                            if (currentRequest != requestId) return;
                            if (value.author != null) author = value.author;
                            Dtos.Page<Dtos.WorkCard> works = value.works;
                            total = works == null ? 0 : works.total;
                            if (page > maxPage()) {
                                page = maxPage();
                                scroll = 0;
                                load();
                                return;
                            }
                            loading = false;
                            cards.clear();
                            if (works != null && works.records != null) {
                                for (Dtos.WorkCard card : works.records) {
                                    if (!onlyThisVersion || matchesThisVersion(card)) cards.add(card);
                                }
                            }
                            scroll = Math.min(previousScroll, maxScroll());
                            status = cards.isEmpty() ? tr("ltsmodelshare.author.empty") : "";
                        });
                    }

                    @Override
                    public void fail(String message) {
                        ApiClient.post(() -> {
                            if (currentRequest != requestId) return;
                            loading = false;
                            if (message != null && message.contains("M05-047")) {
                                needLogin = true;
                                status = tr("ltsmodelshare.author.login_required");
                                buttonList.add(new LocalizedButton(6, width / 2 - 45,
                                        (listTop + listBottom) / 2 + 8, 90, 20, tr("ltsmodelshare.author.login")));
                            } else {
                                status = TextFormatting.RED + message;
                            }
                        });
                    }
                });
    }

    private String followLabel() {
        return tr(following ? "ltsmodelshare.common.following" : "ltsmodelshare.common.follow");
    }

    private void refreshFollowButton() {
        for (GuiButton button : buttonList) {
            if (button.id == 7) button.displayString = followLabel();
        }
    }

    private int passiveFollowRequest;

    private void loadFollowState() {
        if (!ModConfig.get().loggedIn()) return;
        final int requested = ++passiveFollowRequest;
        ownProfile = author != null && author.username != null && author.username.equals(ModConfig.get().username);
        refreshFollowButton();
        if (ownProfile) return;
        ApiClient.followingState(userId, new ApiClient.Callback<Boolean>() {
            @Override public void ok(Boolean value) {
                ApiClient.post(() -> {
                    if (requested != passiveFollowRequest) return;
                    following = Boolean.TRUE.equals(value); refreshFollowButton();
                });
            }
            @Override public void fail(String message) { }
        });
    }

    private void removeButton(int id) {
        buttonList.removeIf(button -> button.id == id);
    }

    private void toggleFollow() {
        if (ownProfile || followBusy) return;
        if (!ModConfig.get().loggedIn()) {
            mc.displayGuiScreen(new GuiLogin(this));
            return;
        }
        followBusy = true;
        ApiClient.toggleFollow(userId, new ApiClient.Callback<Dtos.SocialToggle>() {
            @Override public void ok(Dtos.SocialToggle value) {
                ApiClient.post(() -> {
                    followBusy = false;
                    if (value != null && value.following != null) {
                        following = value.following;
                        refreshFollowButton();
                        if (value.followerCount != null && author != null) {
                            author.followerCount = value.followerCount;
                        }
                    }
                });
            }
            @Override public void fail(String message) {
                ApiClient.post(() -> {
                    followBusy = false;
                    status = TextFormatting.RED + message;
                });
            }
        });
    }

    private void newSearch() {
        page = 1;
        scroll = 0;
        load();
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case 1:
                newSearch();
                break;
            case 2:
                exact = !exact;
                button.displayString = exact ? tr("ltsmodelshare.search.exact") : tr("ltsmodelshare.search.fuzzy");
                newSearch();
                break;
            case 3:
                if (!needLogin && page > 1) {
                    page--;
                    scroll = 0;
                    load();
                }
                break;
            case 4:
                if (!needLogin && page < maxPage()) {
                    page++;
                    scroll = 0;
                    load();
                }
                break;
            case 5:
                mc.displayGuiScreen(parent);
                break;
            case 6:
                mc.displayGuiScreen(new GuiLogin(this));
                break;
            case 7:
                toggleFollow();
                break;
            case 8:
                onlyThisVersion = !onlyThisVersion;
                page = 1;
                load();
                break;
            default:
                break;
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1) { // ESC 返回原作品，不退出游戏 GUI
            mc.displayGuiScreen(parent);
            return;
        }
        if (search.isFocused()) {
            if (keyCode == 28) {
                newSearch();
            } else {
                search.textboxKeyTyped(typedChar, keyCode);
            }
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) throws IOException {
        super.mouseClicked(mx, my, button);
        search.mouseClicked(mx, my, button);
        if (button != 0 || needLogin || loading || mx < panelX || mx > panelX + panelW
                || my < listTop || my >= listBottom) return;
        Dtos.WorkCard selected = cardAt(mx, my);
        if (selected != null) mc.displayGuiScreen(new GuiWorkDetail(selected, this));
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0 || needLogin) return;
        int mx = Mouse.getEventX() * width / Math.max(1, mc.displayWidth);
        int my = height - Mouse.getEventY() * height / Math.max(1, mc.displayHeight) - 1;
        if (mx >= panelX && mx <= panelX + panelW && my >= listTop && my < listBottom) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - Integer.signum(wheel) * ROW_H));
        }
    }

    private List<Dtos.WorkCard> group(boolean collection) {
        List<Dtos.WorkCard> out = new ArrayList<>();
        for (Dtos.WorkCard card : cards) {
            if (card != null && collection == "collection".equals(card.kind)) out.add(card);
        }
        return out;
    }

    private int contentHeight() {
        int n = 8;
        for (boolean collection : new boolean[]{false, true}) {
            int count = group(collection).size();
            if (count > 0) n += 14 + count * ROW_H;
        }
        return n;
    }

    private int maxScroll() {
        return Math.max(0, contentHeight() - Math.max(1, listBottom - listTop));
    }

    private Dtos.WorkCard cardAt(int mx, int my) {
        int y = listTop + 4 - scroll;
        for (boolean collection : new boolean[]{false, true}) {
            List<Dtos.WorkCard> section = group(collection);
            if (section.isEmpty()) continue;
            y += 14;
            for (Dtos.WorkCard card : section) {
                if (my >= y && my < y + ROW_H - 2 && mx >= panelX + 3 && mx <= panelX + panelW - 3) {
                    return card;
                }
                y += ROW_H;
            }
        }
        return null;
    }

    private String trim(String text, int maxWidth) {
        if (text == null) return "";
        if (fontRenderer.getStringWidth(text) <= maxWidth) return text;
        while (!text.isEmpty() && fontRenderer.getStringWidth(text + "…") > maxWidth) {
            text = text.substring(0, text.length() - 1);
        }
        return text + "…";
    }

    private void drawSection(boolean collection, int startY, int mx, int my) {
        List<Dtos.WorkCard> section = group(collection);
        if (section.isEmpty()) return;
        drawString(fontRenderer, TextFormatting.GOLD + (collection ? tr("ltsmodelshare.author.collection") : tr("ltsmodelshare.author.single"))
                + TextFormatting.GRAY + tr("ltsmodelshare.author.page_count", section.size()), panelX + 5, startY, 0xFFCC88);
        int y = startY + 14;
        for (Dtos.WorkCard card : section) {
            boolean hovered = !needLogin && my >= listTop && my < listBottom
                    && mx >= panelX + 3 && mx < panelX + panelW - 3 && my >= y && my < y + ROW_H - 2;
            Gui.drawRect(panelX + 3, y, panelX + panelW - 3, y + ROW_H - 2,
                    hovered ? 0x55FFFFFF : 0x33000000);
            ImageDraw.drawThumb(card.cover, panelX + 6, y + 3, 38, 28);
            int textX = panelX + 49;
            int textW = Math.max(30, panelW - 56);
            drawString(fontRenderer, trim(card.title == null ? tr("ltsmodelshare.common.untitled") : card.title, textW),
                    textX, y + 4, 0xFFFFFF);
            drawString(fontRenderer, TextFormatting.GRAY + trim(tr("ltsmodelshare.common.work_stats", card.downloadCount, card.viewCount), textW), textX, y + 17, 0xAAAAAA);
            y += ROW_H;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        if (onlyThisVersion) {
            Gui.drawRect(versionX, 99, versionX + 48, 101, 0xFFFFFFFF);
        }
        // 对齐网页版作者身份通栏：横幅底图、头像、昵称、@账号、简介。
        Gui.drawRect(panelX, 14, panelX + panelW, 73, 0xFF303137);
        if (author != null && author.bannerUrl != null && !author.bannerUrl.isEmpty()) {
            ImageDraw.drawPreview(author.bannerUrl, panelX, 14, panelW, 59);
            Gui.drawRect(panelX, 14, panelX + panelW, 73, 0x99000000);
        }
        if (author != null) {
            Avatars.draw(fontRenderer, author, panelX + 8, 28, 28);
            String name = author.displayName == null || author.displayName.isEmpty()
                    ? author.username : author.displayName;
            drawString(fontRenderer, trim(name, panelW - 50), panelX + 42, 27, 0xFFFFFF);
            drawString(fontRenderer, TextFormatting.GRAY + "@" + trim(author.username, panelW - 55),
                    panelX + 42, 39, 0xCCCCCC);
            drawString(fontRenderer, trim(author.bio == null ? "" : author.bio, panelW - 52),
                    panelX + 42, 52, 0xDDDDDD);
            drawString(fontRenderer, TextFormatting.GRAY + tr("ltsmodelshare.author.follow_stats", author.followingCount, author.followerCount), panelX + 42, 64, 0xAAAAAA);
        }
        search.drawTextBox();
        Gui.drawRect(panelX, listTop, panelX + panelW, listBottom, 0x30000000);
        if (!needLogin) {
            scroll = Math.min(scroll, maxScroll());
            double scale = mc.displayWidth / (double) Math.max(1, width);
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor((int) (panelX * scale), (int) ((height - listBottom) * scale),
                    (int) (panelW * scale), (int) ((listBottom - listTop) * scale));
            try {
                int y = listTop + 4 - scroll;
                List<Dtos.WorkCard> singles = group(false);
                if (!singles.isEmpty()) {
                    drawSection(false, y, mouseX, mouseY);
                    y += 14 + singles.size() * ROW_H;
                }
                if (!group(true).isEmpty()) drawSection(true, y, mouseX, mouseY);
            } finally {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
            }
        }
        if (!status.isEmpty()) {
            drawCenteredString(fontRenderer, trim(status, panelW - 4), width / 2,
                    listTop + Math.max(10, (listBottom - listTop) / 2 - 12), 0xFFFFFF);
        }
        if (!needLogin) {
            drawCenteredString(fontRenderer, TextFormatting.GRAY + tr("ltsmodelshare.common.page", page, maxPage()), width / 2, height - 40, 0xAAAAAA);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
