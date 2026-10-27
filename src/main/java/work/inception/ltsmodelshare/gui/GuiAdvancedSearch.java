package work.inception.ltsmodelshare.gui;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.text.TextFormatting;
import org.lwjgl.input.Mouse;
import work.inception.ltsmodelshare.LtsModelShare;
import work.inception.ltsmodelshare.api.ApiClient;
import work.inception.ltsmodelshare.api.Dtos;
import work.inception.ltsmodelshare.client.TextUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 高级搜索：按作者、标签、排序筛选，跟网页版的检索条件对齐。
 *
 * <pre>
 *   按作者筛选  [输入框........][搜索作者]
 *   已选：xxx                        [清除作者]
 *   作者候选(2)              标签（可多选，9 个）
 *   ┌────────────┐          ┌──────────────────┐
 *   │ ✔ unicoicey│          │ ✔ 建筑           │
 *   │ · teek_zha…│          │ · 家具           │
 *   └────────────┘          └──────────────────┘
 *   排序  [最新][最热]
 *   [应用] [清空条件] [取消]
 * </pre>
 *
 * <p>两列都是**定高滚动列表**：作者候选原来是一排按钮，搜出结果就把下面的内容顶下去；
 * 现在左列只在有候选时出现（没有候选则标签占满宽度），两列各自滚轮滚动、各自带滚动条。</p>
 */
public class GuiAdvancedSearch extends GuiScreen {

    /** 筛选条件（浏览页持有一份，这里编辑副本，点「应用」才写回） */
    public static class Filter {
        public String q = "";
        public Long authorId;
        public String authorName = "";
        public final Set<String> tagCodes = new LinkedHashSet<>();
        public String sort = "latest";

        public Filter copy() {
            Filter f = new Filter();
            f.q = q;
            f.authorId = authorId;
            f.authorName = authorName;
            f.tagCodes.addAll(tagCodes);
            f.sort = sort;
            return f;
        }

        public boolean isEmpty() {
            return authorId == null && tagCodes.isEmpty() && "latest".equals(sort);
        }

        public String summary() {
            List<String> parts = new ArrayList<>();
            if (authorId != null) {
                parts.add("作者 " + authorName);
            }
            if (!tagCodes.isEmpty()) {
                parts.add("标签 " + String.join(" ", tagCodes));
            }
            if (!"latest".equals(sort)) {
                parts.add("最热");
            }
            return String.join(" · ", parts);
        }
    }

    private static final int ROW_H = 14;
    private static final int COL_W = 148;

    private final GuiModels parent;
    private final Filter working;

    private GuiTextField fieldAuthor;
    private String status = "";

    private final List<Dtos.Tag> tags = new ArrayList<>();
    private final List<Dtos.Author> authorHits = new ArrayList<>();
    private boolean loadingTags;

    private int tagScroll;
    private int authorScroll;

    // 布局
    private int panelLeft;
    private int panelW;
    private int authorLabelY;
    private int selectedY;
    private int listsLabelY;
    private int boxY;
    private int boxH;
    private int authorBoxX;
    private int authorBoxW;
    private int tagsBoxX;
    private int tagsBoxW;
    private int sortY;

    public GuiAdvancedSearch(GuiModels parent, Filter current) {
        this.parent = parent;
        this.working = current.copy();
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int cx = width / 2;
        panelW = Math.min(316, width - 24);
        panelLeft = cx - panelW / 2;

        int y = 40;
        authorLabelY = y;
        y += 11;

        fieldAuthor = new GuiTextField(0, fontRenderer, panelLeft, y, Math.max(80, panelW - 106), 18);
        fieldAuthor.setMaxStringLength(32);
        fieldAuthor.setText(working.authorName == null ? "" : working.authorName);
        buttonList.add(new GuiButton(1, panelLeft + panelW - 102, y - 1, 102, 20, "搜索作者"));
        y += 24;

        selectedY = y;
        if (working.authorId != null) {
            buttonList.add(new GuiButton(2, panelLeft + panelW - 102, y - 2, 102, 18, "清除作者"));
        }
        y += 14;

        listsLabelY = y;
        y += 11;
        boxY = y;

        sortY = height - 30 - 32;
        boxH = Math.max(ROW_H, sortY - 16 - boxY);

        // 有作者候选 → 左边用户列表、右边标签；没候选 → 标签占满
        if (authorHits.isEmpty()) {
            authorBoxX = 0;
            authorBoxW = 0;
            tagsBoxX = panelLeft;
            tagsBoxW = panelW;
        } else {
            int leftW = Math.max(80, (panelW - 8) * 46 / 100);
            authorBoxX = panelLeft;
            authorBoxW = leftW;
            tagsBoxX = panelLeft + leftW + 8;
            tagsBoxW = panelW - leftW - 8;
        }

        buttonList.add(new GuiButton(3, panelLeft, sortY, COL_W, 18,
                "latest".equals(working.sort) ? "✔ 最新" : "最新"));
        buttonList.add(new GuiButton(4, panelLeft + COL_W + 8, sortY, COL_W, 18,
                "hot".equals(working.sort) ? "✔ 最热" : "最热"));

        int by = height - 30;
        buttonList.add(new GuiButton(5, panelLeft, by, 96, 20, "应用"));
        buttonList.add(new GuiButton(6, panelLeft + panelW / 2 - 48, by, 96, 20, "清空条件"));
        buttonList.add(new GuiButton(7, panelLeft + panelW - 96, by, 96, 20, "取消"));

        if (tags.isEmpty() && !loadingTags) {
            loadTags();
        }
        clampScrolls();
    }

    // ==================== 列表尺寸 / 命中 ====================

    private int visibleRows(int boxHeight) {
        return Math.max(1, boxHeight / ROW_H);
    }

    private int maxTagScroll() {
        return Math.max(0, tags.size() - visibleRows(boxH));
    }

    private int maxAuthorScroll() {
        return Math.max(0, authorHits.size() - visibleRows(boxH));
    }

    private void clampScrolls() {
        tagScroll = Math.max(0, Math.min(tagScroll, maxTagScroll()));
        authorScroll = Math.max(0, Math.min(authorScroll, maxAuthorScroll()));
    }

    private int tagIndexAt(int mouseX, int mouseY) {
        if (tagsBoxW <= 0 || mouseX < tagsBoxX || mouseX > tagsBoxX + tagsBoxW
                || mouseY < boxY || mouseY > boxY + boxH) {
            return -1;
        }
        int index = tagScroll + (mouseY - boxY) / ROW_H;
        return index >= 0 && index < tags.size() ? index : -1;
    }

    private int authorIndexAt(int mouseX, int mouseY) {
        if (authorBoxW <= 0 || mouseX < authorBoxX || mouseX > authorBoxX + authorBoxW
                || mouseY < boxY || mouseY > boxY + boxH) {
            return -1;
        }
        int index = authorScroll + (mouseY - boxY) / ROW_H;
        return index >= 0 && index < authorHits.size() ? index : -1;
    }

    private boolean inAuthorBox(int mx, int my) {
        return authorBoxW > 0 && mx >= authorBoxX && mx <= authorBoxX + authorBoxW
                && my >= boxY && my <= boxY + boxH;
    }

    private boolean inTagsBox(int mx, int my) {
        return mx >= tagsBoxX && mx <= tagsBoxX + tagsBoxW && my >= boxY && my <= boxY + boxH;
    }

    // ==================== 数据 ====================

    private void loadTags() {
        loadingTags = true;
        ApiClient.listTags(new ApiClient.Callback<List<Dtos.Tag>>() {
            @Override
            public void ok(List<Dtos.Tag> value) {
                ApiClient.post(() -> {
                    loadingTags = false;
                    tags.clear();
                    for (Dtos.Tag t : value) {
                        if (t != null && t.code != null && t.name != null) {
                            tags.add(t);
                        }
                    }
                    initGui();
                });
            }

            @Override
            public void fail(String message) {
                ApiClient.post(() -> {
                    loadingTags = false;
                    status = TextFormatting.RED + message;
                });
            }
        });
    }

    private void searchAuthors() {
        String q = fieldAuthor.getText().trim();
        if (q.isEmpty()) {
            status = TextFormatting.RED + "先输入作者名";
            return;
        }
        status = TextFormatting.GRAY + "搜索中…";
        ApiClient.searchAuthors(q, new ApiClient.Callback<List<Dtos.Author>>() {
            @Override
            public void ok(List<Dtos.Author> value) {
                ApiClient.post(() -> {
                    authorHits.clear();
                    for (Dtos.Author a : value) {
                        if (a != null && a.username != null) {
                            authorHits.add(a);
                        }
                    }
                    authorScroll = 0;
                    status = authorHits.isEmpty()
                            ? TextFormatting.RED + "没找到这个作者"
                            : TextFormatting.GREEN + "左边是候选，点名字选中";
                    initGui();
                });
            }

            @Override
            public void fail(String message) {
                ApiClient.post(() -> status = TextFormatting.RED + message);
            }
        });
    }

    // ==================== 交互 ====================

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }
        int mx = Mouse.getEventX() * width / Math.max(1, mc.displayWidth);
        int my = height - Mouse.getEventY() * height / Math.max(1, mc.displayHeight) - 1;

        if (inAuthorBox(mx, my)) {
            authorScroll -= Integer.signum(wheel);
            clampScrolls();
            return;
        }
        if (inTagsBox(mx, my)) {
            tagScroll -= Integer.signum(wheel);
            clampScrolls();
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        int id = button.id;
        if (id == 1) {
            searchAuthors();
            return;
        }
        if (id == 2) {
            working.authorId = null;
            working.authorName = "";
            initGui();
            return;
        }
        if (id == 3) {
            working.sort = "latest";
            initGui();
            return;
        }
        if (id == 4) {
            working.sort = "hot";
            initGui();
            return;
        }
        if (id == 5) {
            parent.applyFilter(working);
            mc.displayGuiScreen(parent);
            return;
        }
        if (id == 6) {
            // 立刻清空并生效（原来只清工作副本、还要再点应用，容易被当成"没清掉"）
            working.authorId = null;
            working.authorName = "";
            working.tagCodes.clear();
            working.sort = "latest";
            authorHits.clear();
            tagScroll = 0;
            authorScroll = 0;
            LtsModelShare.LOG.info("[adv] 清空条件：tags={} author={}",
                    working.tagCodes.size(), working.authorId);
            parent.applyFilter(working);
            mc.displayGuiScreen(parent);
            return;
        }
        if (id == 7) {
            mc.displayGuiScreen(parent);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1) {
            mc.displayGuiScreen(parent);
            return;
        }
        if (fieldAuthor.isFocused()) {
            fieldAuthor.textboxKeyTyped(typedChar, keyCode);
            if (keyCode == 28) {
                searchAuthors();
            }
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        fieldAuthor.mouseClicked(mouseX, mouseY, mouseButton);
        if (mouseButton != 0) {
            return;
        }

        int authorIndex = authorIndexAt(mouseX, mouseY);
        if (authorIndex >= 0) {
            Dtos.Author a = authorHits.get(authorIndex);
            working.authorId = a.userId;
            working.authorName = a.displayName != null && !a.displayName.isEmpty()
                    ? a.displayName : a.username;
            status = TextFormatting.GREEN + "已选作者：" + working.authorName;
            initGui();
            return;
        }

        int tagIndex = tagIndexAt(mouseX, mouseY);
        if (tagIndex >= 0) {
            String code = tags.get(tagIndex).code;
            boolean added = working.tagCodes.add(code);
            if (!added) {
                working.tagCodes.remove(code);
            }
            LtsModelShare.LOG.info("[adv] 标签 {} {}（当前 {} 个）",
                    code, added ? "选中" : "取消", working.tagCodes.size());
        }
    }

    // ==================== 绘制 ====================

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int cx = width / 2;

        Gui.drawRect(panelLeft - 10, 20, panelLeft + panelW + 10, height - 6, 0x70000000);
        Gui.drawRect(panelLeft - 10, 20, panelLeft + panelW + 10, 21, 0x40FFFFFF);
        drawCenteredString(fontRenderer, "高级搜索", cx, 12, 0xFFFFFF);

        drawString(fontRenderer, TextFormatting.GRAY + "按作者筛选", panelLeft, authorLabelY, 0xAAAAAA);
        fieldAuthor.drawTextBox();

        String selected = working.authorId == null ? "未选作者" : "已选：" + working.authorName;
        drawString(fontRenderer, working.authorId == null
                ? TextFormatting.DARK_GRAY + selected : TextFormatting.GREEN + selected,
                panelLeft, selectedY, 0x55FF55);

        // 列标题
        if (authorBoxW > 0) {
            drawString(fontRenderer, TextFormatting.GRAY + "作者候选（" + authorHits.size() + "）",
                    authorBoxX, listsLabelY, 0xAAAAAA);
        }
        drawString(fontRenderer, TextFormatting.GRAY + "标签（可多选"
                + (tags.isEmpty() ? "" : "，" + tags.size() + " 个") + "）"
                + (loadingTags ? TextFormatting.DARK_GRAY + " 加载中…" : ""),
                tagsBoxX, listsLabelY, 0xAAAAAA);

        // ---------- 左列：作者候选 ----------
        if (authorBoxW > 0) {
            Gui.drawRect(authorBoxX, boxY, authorBoxX + authorBoxW, boxY + boxH, 0x40000000);
            for (int row = 0; row < visibleRows(boxH); row++) {
                int index = authorScroll + row;
                if (index >= authorHits.size()) {
                    break;
                }
                Dtos.Author a = authorHits.get(index);
                int rowY = boxY + row * ROW_H;
                boolean isSelected = working.authorId != null && working.authorId.equals(a.userId);
                boolean hover = authorIndexAt(mouseX, mouseY) == index;
                if (hover) {
                    Gui.drawRect(authorBoxX + 1, rowY, authorBoxX + authorBoxW - 5,
                            rowY + ROW_H - 1, 0x40FFFFFF);
                }
                String name = a.displayName != null && !a.displayName.isEmpty()
                        ? a.displayName : a.username;
                drawString(fontRenderer, (isSelected ? TextFormatting.GREEN + "✔ " : TextFormatting.GRAY + "· ")
                                + trim(name, authorBoxW - 12), authorBoxX + 4, rowY + 3,
                        isSelected ? 0x55FF55 : 0xCCCCCC);
            }
            drawScrollbar(authorBoxX + authorBoxW - 4, boxY, boxH, authorScroll,
                    maxAuthorScroll(), authorHits.size());
        }

        // ---------- 右列：标签 ----------
        Gui.drawRect(tagsBoxX, boxY, tagsBoxX + tagsBoxW, boxY + boxH, 0x40000000);
        if (tags.isEmpty() && !loadingTags) {
            drawString(fontRenderer, TextFormatting.DARK_GRAY + "（没有标签）",
                    tagsBoxX + 4, boxY + 3, 0x777777);
        }
        for (int row = 0; row < visibleRows(boxH); row++) {
            int index = tagScroll + row;
            if (index >= tags.size()) {
                break;
            }
            Dtos.Tag tag = tags.get(index);
            int rowY = boxY + row * ROW_H;
            boolean on = working.tagCodes.contains(tag.code);
            boolean hover = tagIndexAt(mouseX, mouseY) == index;
            if (hover) {
                Gui.drawRect(tagsBoxX + 1, rowY, tagsBoxX + tagsBoxW - 5, rowY + ROW_H - 1, 0x40FFFFFF);
            }
            drawString(fontRenderer, (on ? TextFormatting.GREEN + "✔ " : TextFormatting.GRAY + "·  ")
                    + tag.name, tagsBoxX + 4, rowY + 3, on ? 0x55FF55 : 0xCCCCCC);
        }
        drawScrollbar(tagsBoxX + tagsBoxW - 4, boxY, boxH, tagScroll, maxTagScroll(), tags.size());

        // ---------- 排序 / 状态 ----------
        drawString(fontRenderer, TextFormatting.GRAY + "排序", panelLeft, sortY - 12, 0xAAAAAA);

        if (!status.isEmpty()) {
            drawCenteredString(fontRenderer, status, cx, height - 42, 0xFFFFFF);
        }
        if (!working.isEmpty()) {
            List<String> s = TextUtil.wrap(fontRenderer, "当前条件：" + working.summary(), width - 40);
            int y = height - 54 - s.size() * 10;
            for (String line : s) {
                drawCenteredString(fontRenderer, TextFormatting.GRAY + line, cx, y, 0xAAAAAA);
                y += 10;
            }
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawScrollbar(int barX, int boxTop, int boxHeight, int scroll, int maxScroll, int total) {
        if (maxScroll <= 0) {
            return;
        }
        Gui.drawRect(barX, boxTop, barX + 3, boxTop + boxHeight, 0x40000000);
        int visible = visibleRows(boxHeight);
        int thumbH = Math.max(8, boxHeight * visible / Math.max(visible, total));
        int thumbY = boxTop + (boxHeight - thumbH) * scroll / maxScroll;
        Gui.drawRect(barX, thumbY, barX + 3, thumbY + thumbH, 0xAAFFFFFF);
    }

    private String trim(String text, int maxWidth) {
        if (text == null) {
            return "";
        }
        if (fontRenderer.getStringWidth(text) <= maxWidth) {
            return text;
        }
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            sb.append(c);
            if (fontRenderer.getStringWidth(sb.toString() + "…") > maxWidth) {
                return sb.substring(0, Math.max(0, sb.length() - 1)) + "…";
            }
        }
        return text;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
