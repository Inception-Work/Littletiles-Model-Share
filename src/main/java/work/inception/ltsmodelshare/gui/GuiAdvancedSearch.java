package work.inception.ltsmodelshare.gui;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.text.TextFormatting;
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
 * <p>站点 {@code POST /api/public/work/cards} 支持的字段：{@code q / tagCodes / authorId / sort}，
 * 这里负责把这些填好再回传给浏览页。</p>
 *
 * <p>布局按"从上到下的游标"排：作者搜索行 → 作者候选按钮 → 已选作者 → 标签（两列）→
 * 排序 → 底部操作。所有可点的东西都是真按钮，避免画了文字却点不到。</p>
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

    private static final int TAG_BASE = 100;
    private static final int AUTHOR_BASE = 200;
    private static final int MAX_TAGS = 16;
    private static final int MAX_AUTHORS = 5;
    private static final int COL_W = 148;

    private final GuiModels parent;
    private final Filter working;

    private GuiTextField fieldAuthor;
    private String status = "";

    private final List<Dtos.Tag> tags = new ArrayList<>();
    private final List<Dtos.Author> authorHits = new ArrayList<>();
    private boolean loadingTags;

    // 绘制用（initGui 里算好）
    private int panelLeft;
    private int authorLabelY;
    private int hitsY;
    private int selectedY;
    private int tagsLabelY;
    private int sortY;

    public GuiAdvancedSearch(GuiModels parent, Filter current) {
        this.parent = parent;
        this.working = current.copy();
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int cx = width / 2;
        panelLeft = cx - 156;

        int y = 44;
        authorLabelY = y;
        y += 12;

        // 作者搜索行：输入框 + 按钮
        fieldAuthor = new GuiTextField(0, fontRenderer, panelLeft, y, 200, 18);
        fieldAuthor.setMaxStringLength(32);
        fieldAuthor.setText(working.authorName == null ? "" : working.authorName);
        buttonList.add(new GuiButton(1, panelLeft + 206, y - 1, 100, 20, "搜索作者"));
        y += 24;

        // 作者候选按钮
        hitsY = y;
        for (int i = 0; i < Math.min(authorHits.size(), MAX_AUTHORS); i++) {
            Dtos.Author a = authorHits.get(i);
            String name = a.displayName != null && !a.displayName.isEmpty() ? a.displayName : a.username;
            buttonList.add(new GuiButton(AUTHOR_BASE + i, panelLeft, y, 306, 18,
                    name + "  @" + a.username));
            y += 20;
        }
        if (!authorHits.isEmpty()) {
            y += 4;
        }

        // 已选作者 / 清除
        selectedY = y;
        if (working.authorId != null) {
            buttonList.add(new GuiButton(2, panelLeft + 206, y - 1, 100, 20, "清除作者"));
        }
        y += 22;

        // 标签
        tagsLabelY = y;
        y += 12;
        int perCol = (MAX_TAGS + 1) / 2;
        for (int i = 0; i < Math.min(tags.size(), MAX_TAGS); i++) {
            Dtos.Tag tag = tags.get(i);
            int col = i / perCol;
            int row = i % perCol;
            boolean on = working.tagCodes.contains(tag.code);
            buttonList.add(new GuiButton(TAG_BASE + i, panelLeft + col * (COL_W + 10), y + row * 20,
                    COL_W, 18, (on ? "✔ " : "") + tag.name));
        }
        y += Math.max(1, Math.min(tags.size(), MAX_TAGS) == 0 ? 0 : perCol) * 20 + 4;

        // 排序
        sortY = y;
        buttonList.add(new GuiButton(3, panelLeft, y, COL_W, 18,
                "latest".equals(working.sort) ? "✔ 最新" : "最新"));
        buttonList.add(new GuiButton(4, panelLeft + COL_W + 10, y, COL_W, 18,
                "hot".equals(working.sort) ? "✔ 最热" : "最热"));

        // 底部操作
        int by = height - 30;
        buttonList.add(new GuiButton(5, panelLeft, by, 96, 20, "应用"));
        buttonList.add(new GuiButton(6, panelLeft + 104, by, 96, 20, "清空条件"));
        buttonList.add(new GuiButton(7, panelLeft + 208, by, 98, 20, "取消"));

        if (tags.isEmpty() && !loadingTags) {
            loadTags();
        }
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
                    status = authorHits.isEmpty()
                            ? TextFormatting.RED + "没找到这个作者"
                            : TextFormatting.GREEN + "找到 " + authorHits.size() + " 个，点上面的名字选中";
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
            return;
        }
        if (id >= TAG_BASE && id < TAG_BASE + MAX_TAGS) {
            int i = id - TAG_BASE;
            if (i < tags.size()) {
                String code = tags.get(i).code;
                if (!working.tagCodes.remove(code)) {
                    working.tagCodes.add(code);
                }
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
    }

    // ==================== 绘制 ====================

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int cx = width / 2;

        Gui.drawRect(panelLeft - 10, 26, panelLeft + 316, height - 38, 0x70000000);
        drawCenteredString(fontRenderer, "高级搜索", cx, 14, 0xFFFFFF);

        drawString(fontRenderer, TextFormatting.GRAY + "按作者筛选", panelLeft, authorLabelY, 0xAAAAAA);
        fieldAuthor.drawTextBox();

        // 已选作者
        String selected = working.authorId == null ? "未选作者" : "已选：" + working.authorName;
        drawString(fontRenderer, working.authorId == null
                ? TextFormatting.DARK_GRAY + selected
                : TextFormatting.GREEN + selected, panelLeft, selectedY, 0x55FF55);

        // 标签标题
        drawString(fontRenderer, TextFormatting.GRAY + "标签（可多选）"
                + (loadingTags ? TextFormatting.DARK_GRAY + "  加载中…" : ""),
                panelLeft, tagsLabelY, 0xAAAAAA);

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
