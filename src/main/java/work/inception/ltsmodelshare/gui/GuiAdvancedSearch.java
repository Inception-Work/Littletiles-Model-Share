package work.inception.ltsmodelshare.gui;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.text.TextFormatting;
import org.lwjgl.input.Mouse;
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
 * <p>站点 {@code POST /api/public/work/cards} 支持 {@code q / tagCodes / authorId / sort}，
 * 这里负责把这些填好回传给浏览页。</p>
 *
 * <p>标签是**滚动的列表**（不是一堆按钮）：标签数量会涨，之前两列排 8 行直接超出页面。
 * 现在固定一个盒子、行高 14、滚轮滚动、点行切换勾选，盒子高度按窗口剩余空间算。</p>
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

    private static final int AUTHOR_BASE = 200;
    private static final int MAX_AUTHORS = 3;
    private static final int TAG_ROW_H = 14;
    private static final int COL_W = 148;

    private final GuiModels parent;
    private final Filter working;

    private GuiTextField fieldAuthor;
    private String status = "";

    private final List<Dtos.Tag> tags = new ArrayList<>();
    private final List<Dtos.Author> authorHits = new ArrayList<>();
    private boolean loadingTags;
    private int tagScroll;

    // 布局
    private int panelLeft;
    private int panelW;
    private int authorLabelY;
    private int hitsY;
    private int selectedY;
    private int tagsLabelY;
    private int tagsBoxX;
    private int tagsBoxY;
    private int tagsBoxW;
    private int tagsBoxH;
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

        hitsY = y;
        int shown = Math.min(authorHits.size(), MAX_AUTHORS);
        for (int i = 0; i < shown; i++) {
            Dtos.Author a = authorHits.get(i);
            String name = a.displayName != null && !a.displayName.isEmpty() ? a.displayName : a.username;
            buttonList.add(new GuiButton(AUTHOR_BASE + i, panelLeft, y, panelW, 16,
                    name + "  @" + a.username));
            y += 17;
        }
        if (shown > 0) {
            y += 3;
        }

        selectedY = y;
        if (working.authorId != null) {
            buttonList.add(new GuiButton(2, panelLeft + panelW - 102, y - 2, 102, 18, "清除作者"));
        }
        y += 13;

        tagsLabelY = y;
        y += 11;
        tagsBoxX = panelLeft;
        tagsBoxY = y;
        tagsBoxW = panelW;

        // 底部按钮占 20 + 上下留白；排序行在它上面
        sortY = height - 30 - 22;
        tagsBoxH = Math.max(TAG_ROW_H, sortY - 8 - tagsBoxY);

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
        clampTagScroll();
    }

    // ==================== 标签列表 ====================

    private int visibleTagRows() {
        return Math.max(1, tagsBoxH / TAG_ROW_H);
    }

    private int maxTagScroll() {
        return Math.max(0, tags.size() - visibleTagRows());
    }

    private void clampTagScroll() {
        tagScroll = Math.max(0, Math.min(tagScroll, maxTagScroll()));
    }

    /** 鼠标落在第几行标签（-1 = 没命中） */
    private int tagRowAt(int mouseX, int mouseY) {
        if (mouseX < tagsBoxX || mouseX > tagsBoxX + tagsBoxW
                || mouseY < tagsBoxY || mouseY > tagsBoxY + tagsBoxH) {
            return -1;
        }
        int row = (mouseY - tagsBoxY) / TAG_ROW_H;
        int index = tagScroll + row;
        return index >= 0 && index < tags.size() ? index : -1;
    }

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
                    status = authorHits.isEmpty()
                            ? TextFormatting.RED + "没找到这个作者"
                            : TextFormatting.GREEN + "找到 " + authorHits.size() + " 个，点名字选中";
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
        if (mx >= tagsBoxX && mx <= tagsBoxX + tagsBoxW && my >= tagsBoxY && my <= tagsBoxY + tagsBoxH) {
            tagScroll -= Integer.signum(wheel);
            clampTagScroll();
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
            working.authorId = null;
            working.authorName = "";
            working.tagCodes.clear();
            working.sort = "latest";
            authorHits.clear();
            status = TextFormatting.GRAY + "已清空条件（点「应用」生效）";
            initGui();
            return;
        }
        if (id == 7) {
            mc.displayGuiScreen(parent);
            return;
        }
        if (id >= AUTHOR_BASE && id < AUTHOR_BASE + MAX_AUTHORS) {
            int i = id - AUTHOR_BASE;
            if (i < authorHits.size()) {
                Dtos.Author a = authorHits.get(i);
                working.authorId = a.userId;
                working.authorName = a.displayName != null && !a.displayName.isEmpty()
                        ? a.displayName : a.username;
                status = TextFormatting.GREEN + "已选作者：" + working.authorName;
                initGui();
            }
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
        int index = tagRowAt(mouseX, mouseY);
        if (index >= 0) {
            String code = tags.get(index).code;
            if (!working.tagCodes.remove(code)) {
                working.tagCodes.add(code);
            }
        }
    }

    // ==================== 绘制 ====================

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int cx = width / 2;

        Gui.drawRect(panelLeft - 8, 26, panelLeft + panelW + 8, height - 22, 0x70000000);
        drawCenteredString(fontRenderer, "高级搜索", cx, 14, 0xFFFFFF);

        drawString(fontRenderer, TextFormatting.GRAY + "按作者筛选", panelLeft, authorLabelY, 0xAAAAAA);
        fieldAuthor.drawTextBox();

        String selected = working.authorId == null ? "未选作者" : "已选：" + working.authorName;
        drawString(fontRenderer, working.authorId == null
                ? TextFormatting.DARK_GRAY + selected : TextFormatting.GREEN + selected,
                panelLeft, selectedY, 0x55FF55);

        // ---------- 标签：滚动列表 ----------
        drawString(fontRenderer, TextFormatting.GRAY + "标签（可多选"
                + (tags.isEmpty() ? "" : "，共 " + tags.size() + " 个") + "）"
                + (loadingTags ? TextFormatting.DARK_GRAY + "  加载中…" : ""),
                panelLeft, tagsLabelY, 0xAAAAAA);

        Gui.drawRect(tagsBoxX, tagsBoxY, tagsBoxX + tagsBoxW, tagsBoxY + tagsBoxH, 0x40000000);
        if (tags.isEmpty() && !loadingTags) {
            drawString(fontRenderer, TextFormatting.DARK_GRAY + "（没有标签）",
                    tagsBoxX + 4, tagsBoxY + 3, 0x777777);
        }
        for (int row = 0; row < visibleTagRows(); row++) {
            int index = tagScroll + row;
            if (index >= tags.size()) {
                break;
            }
            Dtos.Tag tag = tags.get(index);
            int rowY = tagsBoxY + row * TAG_ROW_H;
            boolean on = working.tagCodes.contains(tag.code);
            boolean hover = tagRowAt(mouseX, mouseY) == index;

            if (hover) {
                Gui.drawRect(tagsBoxX + 1, rowY, tagsBoxX + tagsBoxW - 5, rowY + TAG_ROW_H - 1, 0x40FFFFFF);
            }
            drawString(fontRenderer, (on ? TextFormatting.GREEN + "✔ " : TextFormatting.GRAY + "·  ")
                    + tag.name, tagsBoxX + 4, rowY + 3, on ? 0x55FF55 : 0xCCCCCC);
        }
        // 滚动条
        if (maxTagScroll() > 0) {
            int barX = tagsBoxX + tagsBoxW - 4;
            Gui.drawRect(barX, tagsBoxY, barX + 3, tagsBoxY + tagsBoxH, 0x40000000);
            int thumbH = Math.max(8, tagsBoxH * visibleTagRows() / tags.size());
            int thumbY = tagsBoxY + (tagsBoxH - thumbH) * tagScroll / maxTagScroll();
            Gui.drawRect(barX, thumbY, barX + 3, thumbY + thumbH, 0xAAFFFFFF);
        }

        drawString(fontRenderer, TextFormatting.GRAY + "排序", panelLeft, sortY - 11, 0xAAAAAA);

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

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
