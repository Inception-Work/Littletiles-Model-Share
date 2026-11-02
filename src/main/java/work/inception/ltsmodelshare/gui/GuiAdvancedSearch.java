package work.inception.ltsmodelshare.gui;

import static work.inception.ltsmodelshare.client.Translations.tr;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import work.inception.ltsmodelshare.api.ApiClient;
import work.inception.ltsmodelshare.api.Dtos;
import work.inception.ltsmodelshare.client.Avatars;
import work.inception.ltsmodelshare.client.SiteTags;
import work.inception.ltsmodelshare.client.TextUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Advanced search: the same author/tag lists and sort controls as the 1.12 screen. */
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

        public String summary() {
            List<String> parts = new ArrayList<>();
            if (authorId != null) parts.add(tr("ltsmodelshare.common.author_prefix", authorName));
            if (!tagCodes.isEmpty()) {
                parts.add(tr("ltsmodelshare.common.tags_prefix", tagCodes.stream()
                        .map(code -> tagLabels.containsKey(code) ? SiteTags.label(tagLabels.get(code)) : code)
                        .collect(java.util.stream.Collectors.joining(" "))));
            }
            if (!"latest".equals(sort)) parts.add(tr("ltsmodelshare.search.hot"));
            return String.join(" · ", parts);
        }
    }

    private static final int ROW_H = 14;
    private static final int AVATAR = 10;
    private final GuiModels parent;
    private final Filter working;
    private final List<Dtos.Tag> tags = new ArrayList<>();
    private final List<Dtos.Author> authorHits = new ArrayList<>();
    private EditBox authorField;
    private boolean loadingTags;
    private String status = "";
    private int panelLeft, panelW, boxY, boxH, sortY;
    private int tagScroll, authorScroll;

    public GuiAdvancedSearch(GuiModels parent, Filter current) {
        super(Component.translatable("ltsmodelshare.search.title"));
        this.parent = parent;
        this.working = current.copy();
        this.authorHits.addAll(working.authorCandidates);
    }

    @Override
    protected void init() {
        // Widget rebuilds (sort changes, resize) must not discard unsubmitted typing.
        String query = authorField == null
                ? (working.authorQuery == null || working.authorQuery.isEmpty() ? working.authorName : working.authorQuery)
                : authorField.getValue();
        boolean focused = authorField != null && authorField.isFocused();
        clearWidgets();
        panelW = Math.min(316, width - 24);
        panelLeft = width / 2 - panelW / 2;
        authorField = new EditBox(font, panelLeft, 51, Math.max(80, panelW - 106), 18,
                Component.translatable("ltsmodelshare.search.filter_author"));
        authorField.setMaxLength(32);
        authorField.setValue(query == null ? "" : query);
        authorField.setResponder(value -> working.authorQuery = value);
        addRenderableWidget(authorField);
        if (focused) setInitialFocus(authorField);
        addRenderableWidget(GuiNavigation.button(panelLeft + panelW - 102, 50, 102, 20,
                "ltsmodelshare.search.author", this::searchAuthors));

        sortY = height - 62;
        int sortW = Math.min(148, Math.max(1, (panelW - 8) / 2));
        addRenderableWidget(new FitButton(panelLeft, sortY, sortW, 18,
                Component.translatable("latest".equals(working.sort)
                        ? "ltsmodelshare.search.latest_selected" : "ltsmodelshare.search.latest"),
                b -> { working.sort = "latest"; init(); }));
        addRenderableWidget(new FitButton(panelLeft + sortW + 8, sortY, sortW, 18,
                Component.translatable("hot".equals(working.sort)
                        ? "ltsmodelshare.search.hot_selected" : "ltsmodelshare.search.hot"),
                b -> { working.sort = "hot"; init(); }));

        int footerY = GuiNavigation.footerY(height);
        int actionW = GuiNavigation.actionWidth(width, 2, 96);
        addRenderableWidget(GuiNavigation.button(GuiNavigation.MARGIN, footerY, actionW, 20,
                "ltsmodelshare.search.apply", this::apply));
        addRenderableWidget(GuiNavigation.button(GuiNavigation.MARGIN + actionW + GuiNavigation.GAP, footerY, actionW, 20,
                "ltsmodelshare.search.clear", this::clear));
        addRenderableWidget(GuiNavigation.button(GuiNavigation.backX(width), footerY, GuiNavigation.BACK_WIDTH, 20,
                "ltsmodelshare.common.back", () -> minecraft.setScreen(parent)));
        boxY = 100;
        boxH = Math.max(ROW_H, sortY - 16 - boxY);
        clampScrolls();
        if (tags.isEmpty() && !loadingTags) loadTags();
    }

    private int authorBoxWidth() {
        return authorHits.isEmpty() ? 0 : Math.max(80, (panelW - 8) * 46 / 100);
    }

    private int visibleRows() { return Math.max(1, boxH / ROW_H); }
    private int maxScroll(int total) { return Math.max(0, total - visibleRows()); }
    private void clampScrolls() {
        tagScroll = Math.max(0, Math.min(tagScroll, maxScroll(tags.size())));
        authorScroll = Math.max(0, Math.min(authorScroll, maxScroll(authorHits.size())));
    }
    private boolean inBox(double mx, double my, int x, int w) {
        return w > 0 && mx >= x && mx < x + w && my >= boxY && my < boxY + visibleRows() * ROW_H;
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
                    clampScrolls();
                });
            }
            @Override public void fail(String message) {
                ApiClient.post(() -> { loadingTags = false; status = ChatFormatting.RED + message; });
            }
        });
    }

    private void searchAuthors() {
        String query = authorField.getValue().trim();
        if (query.isEmpty()) {
            status = ChatFormatting.RED + tr("ltsmodelshare.search.author_required");
            return;
        }
        status = ChatFormatting.GRAY + tr("ltsmodelshare.search.searching");
        ApiClient.searchAuthors(query, new ApiClient.Callback<List<Dtos.Author>>() {
            @Override public void ok(List<Dtos.Author> value) {
                ApiClient.post(() -> {
                    authorHits.clear();
                    for (Dtos.Author author : value) {
                        if (author != null && author.username != null) authorHits.add(author);
                    }
                    working.authorCandidates.clear();
                    working.authorCandidates.addAll(authorHits);
                    authorScroll = 0;
                    status = authorHits.isEmpty() ? ChatFormatting.RED + tr("ltsmodelshare.search.author_missing") : "";
                });
            }
            @Override public void fail(String message) {
                ApiClient.post(() -> status = ChatFormatting.RED + message);
            }
        });
    }

    private void apply() {
        working.authorQuery = authorField.getValue().trim();
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
        tagScroll = authorScroll = 0;
        parent.applyFilter(working);
        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int cx = width / 2;
        graphics.fill(panelLeft - 10, 20, panelLeft + panelW + 10, height - 6, 0x70000000);
        graphics.fill(panelLeft - 10, 20, panelLeft + panelW + 10, 21, 0x40FFFFFF);
        graphics.drawCenteredString(font, title, cx, 12, 0xFFFFFF);
        graphics.drawString(font, tr("ltsmodelshare.search.filter_author"), panelLeft, 40, 0xAAAAAA, true);
        String selected = working.authorId == null ? tr("ltsmodelshare.search.no_author")
                : tr("ltsmodelshare.search.selected", working.authorName);
        graphics.drawString(font, TextUtil.trim(font, selected, panelW - 110), panelLeft, 75,
                working.authorId == null ? 0x555555 : 0x55FF55, true);

        int authorW = authorBoxWidth();
        int tagsX = authorW == 0 ? panelLeft : panelLeft + authorW + 8;
        int tagsW = authorW == 0 ? panelW : panelW - authorW - 8;
        if (authorW > 0) {
            graphics.fill(panelLeft, boxY, panelLeft + authorW, boxY + boxH, 0x40000000);
            graphics.drawString(font, tr("ltsmodelshare.search.author_hits", authorHits.size()), panelLeft, 89, 0xAAAAAA, true);
            for (int row = 0; row < visibleRows() && authorScroll + row < authorHits.size(); row++) {
                Dtos.Author author = authorHits.get(authorScroll + row);
                int y = boxY + row * ROW_H;
                boolean on = working.authorId != null && working.authorId == author.userId;
                if (inBox(mouseX, mouseY, panelLeft, authorW) && (mouseY - boxY) / ROW_H == row)
                    graphics.fill(panelLeft + 1, y, panelLeft + authorW - 5, y + ROW_H - 1, 0x40FFFFFF);
                String name = author.displayName == null || author.displayName.isEmpty() ? author.username : author.displayName;
                Avatars.draw(graphics, font, author, panelLeft + 4, y + 2, AVATAR);
                graphics.drawString(font, (on ? "✔ " : "· ") + TextUtil.trim(font, name, authorW - 16 - AVATAR),
                        panelLeft + 4 + AVATAR + 2, y + 3, on ? 0x55FF55 : 0xCCCCCC, true);
            }
            drawScrollbar(graphics, panelLeft + authorW - 4, authorScroll, authorHits.size());
        }
        graphics.fill(tagsX, boxY, tagsX + tagsW, boxY + boxH, 0x40000000);
        graphics.drawString(font, (tags.isEmpty() ? tr("ltsmodelshare.common.tags")
                        : tr("ltsmodelshare.common.tags_count", tags.size()))
                        + (loadingTags ? ChatFormatting.DARK_GRAY + tr("ltsmodelshare.common.loading_suffix") : ""),
                tagsX, 89, 0xAAAAAA, true);
        if (tags.isEmpty() && !loadingTags)
            graphics.drawString(font, tr("ltsmodelshare.search.no_tags"), tagsX + 4, boxY + 3, 0x777777, true);
        for (int row = 0; row < visibleRows() && tagScroll + row < tags.size(); row++) {
            Dtos.Tag tag = tags.get(tagScroll + row);
            int y = boxY + row * ROW_H;
            boolean on = working.tagCodes.contains(tag.code);
            if (inBox(mouseX, mouseY, tagsX, tagsW) && (mouseY - boxY) / ROW_H == row)
                graphics.fill(tagsX + 1, y, tagsX + tagsW - 5, y + ROW_H - 1, 0x40FFFFFF);
            graphics.drawString(font, TextUtil.trim(font, (on ? "✔ " : "·  ") + SiteTags.label(tag), tagsW - 8),
                    tagsX + 4, y + 3, on ? 0x55FF55 : 0xCCCCCC, true);
        }
        drawScrollbar(graphics, tagsX + tagsW - 4, tagScroll, tags.size());
        graphics.drawString(font, tr("ltsmodelshare.search.sort"), panelLeft, sortY - 12, 0xAAAAAA, true);
        if (!status.isEmpty())
            graphics.drawCenteredString(font, TextUtil.trim(font, status, width - 24), cx, height - 42, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawScrollbar(GuiGraphics graphics, int x, int scroll, int total) {
        int max = maxScroll(total);
        if (max == 0) return;
        graphics.fill(x, boxY, x + 3, boxY + boxH, 0x40000000);
        int thumbH = Math.max(8, boxH * visibleRows() / Math.max(visibleRows(), total));
        int thumbY = boxY + (boxH - thumbH) * scroll / max;
        graphics.fill(x, thumbY, x + 3, thumbY + thumbH, 0xAAFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;
        int authorW = authorBoxWidth();
        if (inBox(mouseX, mouseY, panelLeft, authorW)) {
            int index = (int) ((mouseY - boxY) / ROW_H) + authorScroll;
            if (index < authorHits.size()) {
                Dtos.Author author = authorHits.get(index);
                if (working.authorId != null && working.authorId == author.userId) {
                    working.authorId = null;
                    working.authorName = "";
                    status = "";
                } else {
                    working.authorId = author.userId;
                    working.authorName = author.displayName == null || author.displayName.isEmpty() ? author.username : author.displayName;
                    status = ChatFormatting.GREEN + tr("ltsmodelshare.search.author_selected", working.authorName);
                }
            }
            return true;
        }
        int tagsX = authorW == 0 ? panelLeft : panelLeft + authorW + 8;
        if (inBox(mouseX, mouseY, tagsX, authorW == 0 ? panelW : panelW - authorW - 8)) {
            int index = (int) ((mouseY - boxY) / ROW_H) + tagScroll;
            if (index < tags.size()) {
                String code = tags.get(index).code;
                if (!working.tagCodes.add(code)) working.tagCodes.remove(code);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double delta) {
        int authorW = authorBoxWidth();
        if (inBox(mouseX, mouseY, panelLeft, authorW)) authorScroll -= (int) Math.signum(delta);
        else {
            int tagsX = authorW == 0 ? panelLeft : panelLeft + authorW + 8;
            if (!inBox(mouseX, mouseY, tagsX, authorW == 0 ? panelW : panelW - authorW - 8))
                return super.mouseScrolled(mouseX, mouseY, deltaX, delta);
            tagScroll -= (int) Math.signum(delta);
        }
        clampScrolls();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == 257 || keyCode == 335) && authorField != null && authorField.isFocused()) {
            searchAuthors();
            return true;
        }
        if (keyCode == 256) {
            minecraft.setScreen(parent);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override public boolean isPauseScreen() { return false; }
}
