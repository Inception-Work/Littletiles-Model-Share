package work.inception.ltsmodelshare.gui;

import static work.inception.ltsmodelshare.client.Translations.tr;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import work.inception.ltsmodelshare.api.ApiClient;
import work.inception.ltsmodelshare.api.Dtos;
import work.inception.ltsmodelshare.client.SiteTags;
import work.inception.ltsmodelshare.client.TextUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Advanced search: author, tags and sort, mirroring the 1.12 filter semantics. */
public class GuiAdvancedSearch extends Screen {
    public static class Filter {
        public String q = "";
        public Long authorId;
        public String authorName = "";
        public String authorQuery = "";
        public final List<Dtos.Author> authorCandidates = new ArrayList<>();
        public final Set<String> tagCodes = new LinkedHashSet<>();
        public final Map<String, Dtos.Tag> tagLabels = new LinkedHashMap<>();
        public String sort = "latest";

        public Filter copy() {
            Filter copy = new Filter();
            copy.q = q;
            copy.authorId = authorId;
            copy.authorName = authorName;
            copy.authorQuery = authorQuery;
            copy.authorCandidates.addAll(authorCandidates);
            copy.tagCodes.addAll(tagCodes);
            copy.tagLabels.putAll(tagLabels);
            copy.sort = sort;
            return copy;
        }

        public boolean isEmpty() {
            return authorId == null && tagCodes.isEmpty() && "latest".equals(sort);
        }
    }

    private static final int ROW_H = 14;
    private final GuiModels parent;
    private final Filter working;
    private final List<Dtos.Tag> tags = new ArrayList<>();
    private final List<Dtos.Author> authorHits = new ArrayList<>();
    private EditBox authorField;
    private boolean loadingTags;
    private String status = "";
    private int panelLeft;
    private int panelW;
    private int boxY;
    private int boxH;
    private int tagScroll;
    private int authorScroll;

    public GuiAdvancedSearch(GuiModels parent, Filter current) {
        super(Component.translatable("ltsmodelshare.search.title"));
        this.parent = parent;
        this.working = current.copy();
        this.authorHits.addAll(working.authorCandidates);
    }

    @Override
    protected void init() {
        clearWidgets();
        panelW = Math.min(316, width - 24);
        panelLeft = width / 2 - panelW / 2;
        int y = 40;
        y += 11;
        authorField = new EditBox(font, panelLeft, y, Math.max(80, panelW - 106), 18,
                Component.translatable("ltsmodelshare.search.filter_author"));
        authorField.setMaxLength(32);
        authorField.setValue(working.authorQuery.isEmpty() ? working.authorName : working.authorQuery);
        addRenderableWidget(authorField);
        addRenderableWidget(GuiNavigation.button(panelLeft + panelW - 102, y - 1, 102, 20,
                "ltsmodelshare.search.author", this::searchAuthors));
        y += 38;

        int sortY = height - 30 - 32;
        addRenderableWidget(new FitButton(panelLeft, sortY, 148, 18,
                Component.translatable("latest".equals(working.sort)
                        ? "ltsmodelshare.search.latest_selected" : "ltsmodelshare.search.latest"),
                b -> { working.sort = "latest"; init(); }));
        addRenderableWidget(new FitButton(panelLeft + 156, sortY, 148, 18,
                Component.translatable("hot".equals(working.sort)
                        ? "ltsmodelshare.search.hot_selected" : "ltsmodelshare.search.hot"),
                b -> { working.sort = "hot"; init(); }));

        int footerY = GuiNavigation.footerY(height);
        addRenderableWidget(GuiNavigation.button(GuiNavigation.MARGIN, footerY, 96, 20,
                "ltsmodelshare.search.apply", this::apply));
        addRenderableWidget(GuiNavigation.button(GuiNavigation.MARGIN + 102, footerY, 96, 20,
                "ltsmodelshare.search.clear", this::clear));
        addRenderableWidget(GuiNavigation.button(GuiNavigation.backX(width), footerY, GuiNavigation.BACK_WIDTH, 20,
                "ltsmodelshare.common.back", () -> minecraft.setScreen(parent)));

        boxY = 112;
        boxH = Math.max(ROW_H, sortY - 16 - boxY);
        if (tags.isEmpty() && !loadingTags) loadTags();
    }

    private void loadTags() {
        loadingTags = true;
        ApiClient.listTags(new ApiClient.Callback<List<Dtos.Tag>>() {
            @Override public void ok(List<Dtos.Tag> value) {
                ApiClient.post(() -> {
                    loadingTags = false;
                    tags.clear();
                    for (Dtos.Tag tag : value) {
                        if (tag != null && tag.code != null && tag.name != null) {
                            tags.add(tag);
                            working.tagLabels.put(tag.code, tag);
                        }
                    }
                });
            }
            @Override public void fail(String message) {
                ApiClient.post(() -> { loadingTags = false; status = "标签加载失败：" + message; });
            }
        });
    }

    private void searchAuthors() {
        String query = authorField.getValue().trim();
        if (query.isEmpty()) {
            status = tr("ltsmodelshare.search.author_required");
            return;
        }
        status = tr("ltsmodelshare.search.searching");
        ApiClient.searchAuthors(query, new ApiClient.Callback<List<Dtos.Author>>() {
            @Override public void ok(List<Dtos.Author> value) {
                ApiClient.post(() -> {
                    authorHits.clear();
                    for (Dtos.Author author : value) {
                        if (author != null && author.username != null) authorHits.add(author);
                    }
                    working.authorQuery = query;
                    working.authorCandidates.clear();
                    working.authorCandidates.addAll(authorHits);
                    authorScroll = 0;
                    status = authorHits.isEmpty() ? tr("ltsmodelshare.search.author_missing") : "";
                });
            }
            @Override public void fail(String message) {
                ApiClient.post(() -> status = message);
            }
        });
    }

    private void apply() {
        working.authorQuery = authorField.getValue().trim();
        working.q = working.authorQuery;
        parent.applyFilter(working);
        minecraft.setScreen(parent);
    }

    private void clear() {
        working.authorId = null;
        working.authorName = "";
        working.authorQuery = "";
        working.authorCandidates.clear();
        working.tagCodes.clear();
        working.sort = "latest";
        authorHits.clear();
        parent.applyFilter(working);
        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int cx = width / 2;
        graphics.drawCenteredString(font, title, cx, 12, 0xFFFFFF);
        graphics.drawString(font, tr("ltsmodelshare.search.filter_author"), panelLeft, 28, 0xAAAAAA, false);
        String selected = working.authorId == null
                ? tr("ltsmodelshare.search.no_author")
                : tr("ltsmodelshare.search.selected", working.authorName);
        graphics.drawString(font, TextUtil.trim(font, selected, panelW), panelLeft, 62, 0x55FF55, false);

        int authorBoxW = authorHits.isEmpty() ? 0 : Math.max(80, (panelW - 8) * 46 / 100);
        int tagsX = authorBoxW == 0 ? panelLeft : panelLeft + authorBoxW + 8;
        int tagsW = authorBoxW == 0 ? panelW : panelW - authorBoxW - 8;

        if (authorBoxW > 0) {
            graphics.fill(panelLeft, boxY, panelLeft + authorBoxW, boxY + boxH, 0x40000000);
            graphics.drawString(font, tr("ltsmodelshare.search.author_hits", authorHits.size()),
                    panelLeft, boxY - 11, 0xAAAAAA, false);
            for (int row = 0; row < Math.min(authorHits.size(), boxH / ROW_H); row++) {
                Dtos.Author author = authorHits.get(row);
                int y = boxY + row * ROW_H;
                boolean on = working.authorId != null && working.authorId == author.userId;
                if (mouseX >= panelLeft && mouseX <= panelLeft + authorBoxW && mouseY >= y && mouseY < y + ROW_H) {
                    graphics.fill(panelLeft + 1, y, panelLeft + authorBoxW - 4, y + ROW_H - 1, 0x40FFFFFF);
                }
                String name = author.displayName == null || author.displayName.isEmpty()
                        ? author.username : author.displayName;
                graphics.drawString(font, TextUtil.trim(font, (on ? "✔ " : "· ") + name, authorBoxW - 8),
                        panelLeft + 4, y + 3, on ? 0x55FF55 : 0xCCCCCC, false);
            }
        }

        graphics.fill(tagsX, boxY, tagsX + tagsW, boxY + boxH, 0x40000000);
        graphics.drawString(font, tags.isEmpty() ? tr("ltsmodelshare.common.tags")
                        : tr("ltsmodelshare.common.tags_count", tags.size()),
                tagsX, boxY - 11, 0xAAAAAA, false);
        for (int row = 0; row < Math.min(tags.size(), boxH / ROW_H); row++) {
            Dtos.Tag tag = tags.get(row);
            int y = boxY + row * ROW_H;
            boolean on = working.tagCodes.contains(tag.code);
            if (mouseX >= tagsX && mouseX <= tagsX + tagsW && mouseY >= y && mouseY < y + ROW_H) {
                graphics.fill(tagsX + 1, y, tagsX + tagsW - 4, y + ROW_H - 1, 0x40FFFFFF);
            }
            graphics.drawString(font, TextUtil.trim(font, (on ? "✔ " : "· ") + SiteTags.label(tag), tagsW - 8),
                    tagsX + 4, y + 3, on ? 0x55FF55 : 0xCCCCCC, false);
        }

        if (!status.isEmpty()) {
            graphics.drawCenteredString(font, TextUtil.trim(font, status, width - 24), cx, height - 42, 0xFFFFFF);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;
        int authorBoxW = authorHits.isEmpty() ? 0 : Math.max(80, (panelW - 8) * 46 / 100);
        if (authorBoxW > 0 && mouseX >= panelLeft && mouseX <= panelLeft + authorBoxW
                && mouseY >= boxY && mouseY <= boxY + boxH) {
            int index = (int) ((mouseY - boxY) / ROW_H) + authorScroll;
            if (index >= 0 && index < authorHits.size()) {
                Dtos.Author author = authorHits.get(index);
                if (working.authorId != null && working.authorId == author.userId) {
                    working.authorId = null;
                    working.authorName = "";
                } else {
                    working.authorId = author.userId;
                    working.authorName = author.displayName == null || author.displayName.isEmpty()
                            ? author.username : author.displayName;
                }
                status = "";
            }
            return true;
        }
        int tagsX = authorBoxW == 0 ? panelLeft : panelLeft + authorBoxW + 8;
        int tagsW = authorBoxW == 0 ? panelW : panelW - authorBoxW - 8;
        if (mouseX >= tagsX && mouseX <= tagsX + tagsW && mouseY >= boxY && mouseY <= boxY + boxH) {
            int index = (int) ((mouseY - boxY) / ROW_H) + tagScroll;
            if (index >= 0 && index < tags.size()) {
                String code = tags.get(index).code;
                if (!working.tagCodes.add(code)) working.tagCodes.remove(code);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 && authorField != null && authorField.isFocused()) {
            searchAuthors();
            return true;
        }
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
