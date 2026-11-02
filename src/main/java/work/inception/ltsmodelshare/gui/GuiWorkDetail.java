package work.inception.ltsmodelshare.gui;

import static work.inception.ltsmodelshare.client.Translations.tr;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import team.creative.littletiles.common.block.little.tile.group.LittleGroup;
import work.inception.ltsmodelshare.ModConfig;
import work.inception.ltsmodelshare.api.ApiClient;
import work.inception.ltsmodelshare.api.Dtos;
import work.inception.ltsmodelshare.client.Avatars;
import work.inception.ltsmodelshare.client.BlueprintClient;
import work.inception.ltsmodelshare.client.ImageDraw;
import work.inception.ltsmodelshare.client.SiteTags;
import work.inception.ltsmodelshare.client.TextUtil;

/** API port of forge/1.12.2 GuiWorkDetail.
 * Left: cover/gallery. Right: title, tags, author, stats, dependencies, scrollable summary.
 * The full-width model list follows the actual bottom of those columns, with a 6px gap.
 * The working 1.20 native preview is deliberately retained as a separate screen.
 */
public class GuiWorkDetail extends Screen {
    private static final int DL_W = 72;
    private static final int DL_H = 18;
    private static final int PREVIEW_W = 52;
    private static final int ACTION_GAP = 4;
    private static final int ARROW_SIZE = 12;

    private final Screen parent;
    private final Dtos.WorkCard card;
    private final String accessCode;
    private Dtos.WorkDetail detail;
    private final List<Dtos.Item> items = new ArrayList<>();
    private final List<String> gallery = new ArrayList<>();
    private final Map<Long, Integer> itemImageIndex = new HashMap<>();
    private final Set<Long> madeBlueprint = new LinkedHashSet<>();
    private int galleryIndex;
    private int scroll;
    private int summaryScroll;
    private boolean loading;
    private String loadError;
    private Screen blueprintProgress;
    private boolean favorited;
    private boolean favoriteBusy;
    private boolean blueprintPending;
    private Long pendingItemId;
    private FitButton favoriteButton;

    public GuiWorkDetail(Dtos.WorkCard card, Screen parent) {
        this(card, null, parent);
    }

    public GuiWorkDetail(Dtos.WorkCard card, String accessCode, Screen parent) {
        super(Component.translatable("ltsmodelshare.detail.title"));
        this.card = card;
        this.accessCode = accessCode;
        this.parent = parent;
        if (card.cover != null && !card.cover.isBlank()) gallery.add(card.cover);
    }

    @Override
    protected void init() {
        clearWidgets();
        int backX = GuiNavigation.backX(width);
        addRenderableWidget(GuiNavigation.button(backX, GuiNavigation.footerY(height), GuiNavigation.BACK_WIDTH, 20,
                "ltsmodelshare.common.back", this::onClose));
        favoriteButton = addRenderableWidget(new FitButton(backX - GuiNavigation.GAP - 64,
                GuiNavigation.footerY(height), 64, 20, Component.translatable(favoriteKey()), b -> toggleFavorite()));
        favoriteButton.active = !favoriteBusy;
        load();
    }

    private String favoriteKey() {
        return favorited ? "ltsmodelshare.common.favorited" : "ltsmodelshare.common.favorite";
    }

    private Dtos.Author displayedAuthor() {
        return detail != null && detail.author != null ? detail.author : card.author;
    }

    private String titleText() {
        String text = detail != null && detail.title != null ? detail.title : card.title;
        return text == null ? tr("ltsmodelshare.common.untitled") : text;
    }

    private String summaryText() {
        String text = detail != null ? detail.summary : card.summary;
        return text == null ? "" : text;
    }

    private List<FormattedCharSequence> summaryLines(int rightWidth) {
        return summaryText().isBlank() ? List.of()
                : font.split(Component.literal(summaryText()), Math.max(1, rightWidth - 6));
    }

    private List<List<String>> tagRows(int rightWidth) {
        List<Dtos.Tag> tags = detail != null && detail.tags != null ? detail.tags : card.tags;
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        int used = 0;
        if (tags != null) for (Dtos.Tag tag : tags) {
            if (tag == null) continue;
            String label = SiteTags.label(tag);
            if (label == null || label.isBlank()) continue;
            String text = TextUtil.trim(font, "#" + label, Math.max(1, rightWidth - 8));
            int w = font.width(text) + 8;
            if (used + w > rightWidth && !row.isEmpty()) {
                rows.add(row);
                row = new ArrayList<>();
                used = 0;
            }
            row.add(text);
            used += w;
        }
        if (!row.isEmpty()) rows.add(row);
        return rows;
    }

    private List<String> statPairs(int rightWidth) {
        long view = detail != null ? detail.viewCount : card.viewCount;
        long dl = detail != null ? detail.downloadCount : card.downloadCount;
        long fav = detail != null && detail.favoriteCount != null ? detail.favoriteCount : 0;
        long comments = detail != null && detail.commentCount != null ? detail.commentCount : 0;
        String all = tr("ltsmodelshare.detail.stats", view, dl, fav, comments);
        if (font.width(all) <= rightWidth) return List.of(all);
        return List.of(tr("ltsmodelshare.detail.stats_first", view, dl),
                tr("ltsmodelshare.detail.stats_second", fav, comments));
    }

    /** A collection declares top-level dependencies; otherwise aggregate all its item declarations. */
    private List<Dtos.Dependency> effectiveDeps() {
        if (detail == null) return List.of();
        List<Dtos.Dependency> source = new ArrayList<>();
        if (detail.dependencies != null && !detail.dependencies.isEmpty()) source.addAll(detail.dependencies);
        else for (Dtos.Item item : items) {
            if (item != null && item.dependencies != null) source.addAll(item.dependencies);
        }
        Map<String, Dtos.Dependency> unique = new LinkedHashMap<>();
        for (Dtos.Dependency dep : source) {
            if (dep != null && dep.name != null && !dep.name.isBlank()) {
                unique.putIfAbsent((dep.type == null ? "" : dep.type) + "\u0000" + dep.name, dep);
            }
        }
        return List.copyOf(unique.values());
    }

    /** One snapshot drives rendering, page height, description height and every content hitbox. */
    private PageLayout layout() {
        WorkDetailLayout geometry = new WorkDetailLayout(width, height);
        List<FormattedCharSequence> titles = TextUtil.wrap(font, titleText(), geometry.rightWidth, 2);
        List<List<String>> tags = tagRows(geometry.rightWidth);
        List<String> stats = statPairs(geometry.rightWidth);
        List<Dtos.Dependency> deps = effectiveDeps();
        List<FormattedCharSequence> summary = summaryLines(geometry.rightWidth);
        WorkDetailLayout.Header header = geometry.header(titles.size(), tags.size(), displayedAuthor() != null,
                stats.size(), deps.size(), summary.size(), gallery.size());
        return new PageLayout(geometry, header, titles, tags, stats, deps, summary);
    }

    private record PageLayout(WorkDetailLayout geometry, WorkDetailLayout.Header header,
            List<FormattedCharSequence> titles, List<List<String>> tags, List<String> stats,
            List<Dtos.Dependency> dependencies, List<FormattedCharSequence> summary) { }

    private record Rect(int x, int y, int w, int h) {
        boolean contains(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
        Rect grow(int pixels) { return new Rect(x - pixels, y - pixels, w + pixels * 2, h + pixels * 2); }
    }

    private Rect authorBounds(PageLayout p) {
        return new Rect(p.geometry.rightX, WorkDetailLayout.BODY_TOP - scroll + p.header.authorY(),
                p.geometry.rightWidth, 20);
    }

    private Rect galleryBounds(WorkDetailLayout g, int index) {
        return new Rect(g.left + index % g.galleryColumns() * (g.thumbSize + 4),
                WorkDetailLayout.BODY_TOP - scroll + g.coverHeight + 4
                        + index / g.galleryColumns() * (g.thumbSize + 4), g.thumbSize, g.thumbSize);
    }

    private Rect itemThumbBounds(WorkDetailLayout g, int rowY) {
        return new Rect(g.left + 4, rowY + (WorkDetailLayout.ROW_HEIGHT - WorkDetailLayout.THUMB_HEIGHT) / 2,
                WorkDetailLayout.THUMB_WIDTH, WorkDetailLayout.THUMB_HEIGHT);
    }

    private Rect downloadBounds(WorkDetailLayout g, int rowY) {
        return new Rect(g.left + g.width - DL_W - 4, rowY + WorkDetailLayout.ROW_HEIGHT - DL_H - 9, DL_W, DL_H);
    }

    private Rect previewBounds(WorkDetailLayout g, int rowY) {
        Rect dl = downloadBounds(g, rowY);
        return new Rect(dl.x - ACTION_GAP - PREVIEW_W, dl.y, PREVIEW_W, DL_H);
    }

    private Rect arrowBounds(WorkDetailLayout g, int rowY, boolean previous) {
        Rect th = itemThumbBounds(g, rowY);
        return new Rect(previous ? th.x + 1 : th.x + th.w - ARROW_SIZE - 1,
                th.y + th.h - ARROW_SIZE - 1, ARROW_SIZE, ARROW_SIZE);
    }

    private List<String> itemImages(Dtos.Item item) {
        if (item.images == null) return List.of();
        return item.images.stream().filter(s -> s != null && !s.isBlank()).toList();
    }

    private int itemImagePos(Dtos.Item item, int count) {
        return count == 0 ? 0 : Math.floorMod(itemImageIndex.getOrDefault(item.itemId, 0), count);
    }

    private void load() {
        if (loading || detail != null || loadError != null) return;
        loading = true;
        ApiClient.detail(card.workId, accessCode, new ApiClient.Callback<Dtos.WorkDetail>() {
            @Override public void ok(Dtos.WorkDetail value) {
                ApiClient.post(() -> {
                    loading = false;
                    if (value == null) return;
                    detail = value;
                    items.clear();
                    if (value.items != null) items.addAll(value.items.stream().filter(it -> it != null).toList());
                    gallery.clear();
                    if (value.images != null) for (String image : value.images) {
                        if (image != null && !image.isBlank()) gallery.add(image);
                    }
                    if (gallery.isEmpty() && card.cover != null && !card.cover.isBlank()) gallery.add(card.cover);
                    galleryIndex = 0;
                    scroll = 0;
                    summaryScroll = 0;
                    favorited = Boolean.TRUE.equals(value.favorited);
                    if (favoriteButton != null) favoriteButton.setMessage(Component.translatable(favoriteKey()));
                });
            }
            @Override public void fail(String message) {
                ApiClient.post(() -> {
                    loading = false;
                    loadError = message == null ? "" : message;
                });
            }
        });
    }

    private void toggleFavorite() {
        if (!ModConfig.get().loggedIn()) {
            minecraft.setScreen(new GuiLogin(this));
            return;
        }
        if (favoriteBusy) return;
        favoriteBusy = true;
        favoriteButton.active = false;
        ApiClient.toggleFavorite(card.workId, new ApiClient.Callback<Dtos.SocialToggle>() {
            @Override public void ok(Dtos.SocialToggle value) {
                ApiClient.post(() -> {
                    favoriteBusy = false;
                    if (value != null && value.favorited != null) favorited = value.favorited;
                    favoriteButton.active = true;
                    favoriteButton.setMessage(Component.translatable(favoriteKey()));
                });
            }
            @Override public void fail(String message) {
                ApiClient.post(() -> {
                    favoriteBusy = false;
                    favoriteButton.active = true;
                    if (minecraft.screen == GuiWorkDetail.this) minecraft.setScreen(
                            GuiMessage.info(GuiWorkDetail.this, "ltsmodelshare.common.favorite", message));
                });
            }
        });
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        PageLayout p = layout();
        WorkDetailLayout g = p.geometry;
        WorkDetailLayout.Header h = p.header;
        scroll = Math.max(0, Math.min(scroll, g.maxScroll(h, items.size())));
        summaryScroll = Math.max(0, Math.min(summaryScroll, h.summaryMaxScroll(p.summary.size())));
        int top = WorkDetailLayout.BODY_TOP - scroll;
        boolean inBody = g.inBody(mouseX, mouseY);

        graphics.enableScissor(g.left - 4, WorkDetailLayout.BODY_TOP, g.left + g.width + 8, g.bodyBottom);
        try {
            String cover = gallery.isEmpty() ? card.cover : gallery.get(Math.min(galleryIndex, gallery.size() - 1));
            ImageDraw.drawCropped(graphics, cover, g.left, top, g.coverWidth, g.coverHeight);
            if (gallery.size() > 1) for (int i = 0; i < Math.min(8, gallery.size()); i++) {
                Rect b = galleryBounds(g, i);
                ImageDraw.drawCropped(graphics, gallery.get(i), b.x, b.y, b.w, b.h);
                if (i == galleryIndex) graphics.renderOutline(b.x - 1, b.y - 1, b.w + 2, b.h + 2, 0xCCFFFFFF);
            }

            int y = top;
            for (FormattedCharSequence line : p.titles) {
                graphics.drawString(font, line, g.rightX, y, 0xFFFFFF, true);
                y += 10;
            }
            y = top + h.tagsY();
            for (List<String> row : p.tags) {
                int tx = g.rightX;
                for (String text : row) {
                    int w = font.width(text) + 6;
                    graphics.fill(tx, y - 1, tx + w, y + 9, 0x402080C0);
                    graphics.drawString(font, text, tx + 3, y, 0x66CCDD, true);
                    tx += w + 2;
                }
                y += 10;
            }

            Dtos.Author author = displayedAuthor();
            if (author != null) {
                Rect a = authorBounds(p);
                boolean hover = inBody && author.userId > 0 && a.contains(mouseX, mouseY);
                graphics.fill(a.x, a.y, a.x + a.w, a.y + a.h, hover ? 0x50FFFFFF : 0x30000000);
                if (hover) {
                    graphics.fill(a.x, a.y, a.x + a.w, a.y + 1, 0xFFFFFFFF);
                    graphics.fill(a.x, a.y + 19, a.x + a.w, a.y + 20, 0xFFFFFFFF);
                }
                Avatars.draw(graphics, font, author, a.x, a.y + 2, 16);
                String name = author.displayName != null && !author.displayName.isBlank()
                        ? author.displayName : author.username;
                graphics.drawString(font, TextUtil.trim(font, name, a.w - 20), a.x + 20, a.y + 1, 0xFFFFFF, true);
                if (author.username != null && !author.username.isBlank()) {
                    graphics.drawString(font, TextUtil.trim(font, "@" + author.username, a.w - 20),
                            a.x + 20, a.y + 10, 0x888888, true);
                }
            }

            y = top + h.statsY();
            for (String line : p.stats) {
                graphics.drawString(font, TextUtil.trim(font, line, g.rightWidth), g.rightX, y, 0xAAAAAA, true);
                y += 10;
            }
            y = top + h.dependencyY();
            for (Dtos.Dependency dep : p.dependencies) {
                String type = dep.type == null || dep.type.isBlank() ? tr("ltsmodelshare.detail.dependency") : dep.type;
                graphics.drawString(font, TextUtil.trim(font, "[" + type + "] " + dep.name, g.rightWidth),
                        g.rightX, y, 0xAA88CC, true);
                y += 10;
            }

            // Scroll only the work description, not the author's profile bio.
            if (h.summaryLines() > 0) {
                y = top + h.summaryY();
                graphics.enableScissor(g.rightX - 1, y - 1, g.rightX + g.rightWidth + 1,
                        y + h.summaryLines() * 10 + 1);
                try {
                    for (int i = summaryScroll; i < Math.min(p.summary.size(), summaryScroll + h.summaryWindow()); i++) {
                        graphics.drawString(font, p.summary.get(i), g.rightX,
                                y + (i - summaryScroll) * 10, 0x999999, true);
                    }
                } finally {
                    // GuiGraphics maintains a scissor stack: pop the summary, retain the body.
                    graphics.disableScissor();
                }
                int maxSummary = h.summaryMaxScroll(p.summary.size());
                if (maxSummary > 0) {
                    int barX = g.rightX + g.rightWidth - 2;
                    int barHeight = h.summaryLines() * 10;
                    int thumbHeight = Math.max(8, barHeight * h.summaryLines() / p.summary.size());
                    int thumbY = y + (barHeight - thumbHeight) * summaryScroll / maxSummary;
                    graphics.fill(barX, y, barX + 2, y + barHeight, 0x30FFFFFF);
                    graphics.fill(barX, thumbY, barX + 2, thumbY + thumbHeight, 0x90FFFFFF);
                }
            }

            graphics.drawString(font, tr("ltsmodelshare.detail.models", items.size()),
                    g.left, top + h.height(), 0xAAAAAA, true);
            for (int i = 0; i < items.size(); i++) {
                int rowY = top + h.itemTop() + i * WorkDetailLayout.ROW_HEIGHT;
                if (rowY >= g.bodyBottom || rowY + WorkDetailLayout.ROW_HEIGHT <= WorkDetailLayout.BODY_TOP) continue;
                renderItem(graphics, p, items.get(i), rowY, mouseX, mouseY, inBody);
            }
        } finally {
            graphics.disableScissor();
        }

        int maxScroll = g.maxScroll(h, items.size());
        if (maxScroll > 0) {
            int barHeight = g.bodyBottom - WorkDetailLayout.BODY_TOP;
            int thumbHeight = Math.max(12, barHeight * barHeight / (barHeight + maxScroll));
            int thumbY = WorkDetailLayout.BODY_TOP + (barHeight - thumbHeight) * scroll / maxScroll;
            graphics.fill(g.left + g.width + 4, WorkDetailLayout.BODY_TOP, g.left + g.width + 6, g.bodyBottom, 0x30FFFFFF);
            graphics.fill(g.left + g.width + 4, thumbY, g.left + g.width + 6, thumbY + thumbHeight, 0x90FFFFFF);
        }
        if (loadError != null) {
            graphics.drawCenteredString(font, TextUtil.trim(font, loadError, Math.max(1, width - 20)),
                    width / 2, height - 40, 0xFF5555);
        } else if (loading || items.isEmpty()) {
            graphics.drawCenteredString(font, tr(loading ? "ltsmodelshare.common.loading" : "ltsmodelshare.detail.no_models"),
                    width / 2, height - 40, 0xAAAAAA);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderItem(GuiGraphics graphics, PageLayout p, Dtos.Item item, int rowY,
                            int mouseX, int mouseY, boolean inBody) {
        WorkDetailLayout g = p.geometry;
        graphics.fill(g.left, rowY, g.left + g.width, rowY + WorkDetailLayout.ROW_HEIGHT - 4, 0x28000000);
        Rect th = itemThumbBounds(g, rowY);
        List<String> images = itemImages(item);
        if (images.isEmpty()) {
            graphics.fill(th.x, th.y, th.x + th.w, th.y + th.h, 0x30FFFFFF);
            graphics.drawCenteredString(font, "#" + item.itemId, th.x + th.w / 2, th.y + th.h / 2 - 4, 0x777777);
        } else {
            int pos = itemImagePos(item, images.size());
            ImageDraw.drawCropped(graphics, images.get(pos), th.x, th.y, th.w, th.h);
            if (images.size() > 1) {
                for (boolean previous : new boolean[] { true, false }) {
                    Rect arrow = arrowBounds(g, rowY, previous);
                    boolean hover = inBody && arrow.grow(2).contains(mouseX, mouseY);
                    graphics.fill(arrow.x, arrow.y, arrow.x + arrow.w, arrow.y + arrow.h,
                            hover ? 0xB0000000 : 0x80000000);
                    graphics.drawCenteredString(font, previous ? "<" : ">", arrow.x + arrow.w / 2, arrow.y + 2, 0xFFFFFF);
                }
                String counter = (pos + 1) + "/" + images.size();
                graphics.fill(th.x + 1, th.y + 1, th.x + 3 + font.width(counter), th.y + 11, 0x80000000);
                graphics.drawString(font, counter, th.x + 2, th.y + 2, 0xFFFFFF, true);
            }
        }

        int tx = th.x + th.w + 6;
        int textWidth = Math.max(20, g.left + g.width - 6 - tx);
        int ty = rowY + 6;
        for (FormattedCharSequence line : TextUtil.wrap(font,
                item.title == null ? "#" + item.itemId : item.title, textWidth, 2)) {
            graphics.drawString(font, line, tx, ty, 0xFFFFFF, true);
            ty += 10;
        }
        StringBuilder meta = new StringBuilder("#" + item.itemId);
        if (item.mcVersion != null) meta.append("  ").append(item.mcVersion);
        if (item.boxCount != null) meta.append("  ").append(item.boxCount).append(" box");
        if (item.grid != null) meta.append("  grid ").append(item.grid);
        if (item.modNamespaces != null && !item.modNamespaces.isEmpty()) {
            String mods = String.join(", ", item.modNamespaces.subList(0, Math.min(3, item.modNamespaces.size())));
            if (item.modNamespaces.size() > 3) mods += "…";
            meta.append(tr("ltsmodelshare.detail.mods", mods));
        }
        graphics.drawString(font, TextUtil.trim(font, meta.toString(), textWidth), tx, ty + 2, 0xAAAAAA, true);

        drawAction(graphics, previewBounds(g, rowY), tr("ltsmodelshare.detail.preview"),
                mouseX, mouseY, inBody, false, false);
        boolean done = madeBlueprint.contains(item.itemId);
        drawAction(graphics, downloadBounds(g, rowY), tr(done ? "ltsmodelshare.detail.generated"
                : "ltsmodelshare.detail.get_blueprint"), mouseX, mouseY, inBody, true, done);
    }

    private void drawAction(GuiGraphics graphics, Rect b, String label, int mouseX, int mouseY,
                            boolean inBody, boolean download, boolean done) {
        boolean hover = inBody && b.contains(mouseX, mouseY);
        int color = download ? done ? 0xFF2B4A20 : hover ? 0xFF3E7A2C : 0xFF2F5424
                : hover ? 0xFF48718A : 0xFF304858;
        graphics.fill(b.x, b.y, b.x + b.w, b.y + b.h, color);
        TextUtil.drawCenteredFit(graphics, font, label, b.x + b.w / 2, b.y + 5, b.w - 8, 0xFFFFFF);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        PageLayout p = layout();
        WorkDetailLayout g = p.geometry;
        if (!g.inBody(mouseX, mouseY) || delta == 0) return super.mouseScrolled(mouseX, mouseY, delta);
        int sy = WorkDetailLayout.BODY_TOP - scroll + p.header.summaryY();
        Rect summary = new Rect(g.rightX, sy, g.rightWidth, p.header.summaryLines() * 10);
        if (p.header.summaryMaxScroll(p.summary.size()) > 0 && summary.contains(mouseX, mouseY)) {
            summaryScroll = Math.max(0, Math.min(p.header.summaryMaxScroll(p.summary.size()),
                    summaryScroll - (int) Math.signum(delta)));
        } else {
            scroll = Math.max(0, Math.min(g.maxScroll(p.header, items.size()), scroll - (int) Math.signum(delta) * 18));
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;
        PageLayout p = layout();
        WorkDetailLayout g = p.geometry;
        // Drawing and clicking have exactly the same body boundary (including partially visible buttons).
        if (!g.inBody(mouseX, mouseY)) return false;
        Dtos.Author author = displayedAuthor();
        if (author != null && author.userId > 0 && authorBounds(p).contains(mouseX, mouseY)) {
            if (parent instanceof GuiAuthorHome home && home.isAuthor(author.userId)) minecraft.setScreen(parent);
            else minecraft.setScreen(new GuiAuthorHome(author, this));
            return true;
        }
        if (gallery.size() > 1) for (int i = 0; i < Math.min(8, gallery.size()); i++) {
            if (galleryBounds(g, i).contains(mouseX, mouseY)) { galleryIndex = i; return true; }
        }
        int top = WorkDetailLayout.BODY_TOP - scroll + p.header.itemTop();
        for (int i = 0; i < items.size(); i++) {
            Dtos.Item item = items.get(i);
            int rowY = top + i * WorkDetailLayout.ROW_HEIGHT;
            List<String> images = itemImages(item);
            if (images.size() > 1) for (boolean previous : new boolean[] { true, false }) {
                if (arrowBounds(g, rowY, previous).grow(2).contains(mouseX, mouseY)) {
                    itemImageIndex.put(item.itemId, Math.floorMod(itemImagePos(item, images.size())
                            + (previous ? -1 : 1), images.size()));
                    return true;
                }
            }
            if (previewBounds(g, rowY).contains(mouseX, mouseY)) { openPreview(item); return true; }
            if (downloadBounds(g, rowY).contains(mouseX, mouseY)) { fetchBlueprint(item); return true; }
        }
        return false;
    }

    // Existing 1.20.1 blueprint/native-preview pipeline; no changes to the preview renderer.
    private void fetchBlueprint(Dtos.Item item) {
        if (blueprintPending) return;
        if (!ModConfig.get().loggedIn()) { minecraft.setScreen(new GuiLogin(this)); return; }
        if (minecraft.player == null) {
            minecraft.setScreen(GuiMessage.info(this, "ltsmodelshare.detail.get_blueprint",
                    tr("ltsmodelshare.detail.world_required")));
            return;
        }
        String version = item.mcVersion == null ? "" : item.mcVersion.trim();
        if (!(version.startsWith("1.19") || version.startsWith("1.20"))) {
            minecraft.setScreen(new GuiMessage(this, "ltsmodelshare.detail.version_title",
                    tr("ltsmodelshare.detail.version_body", version.isEmpty() ? "?" : version),
                    List.of(new GuiMessage.Button("ltsmodelshare.common.cancel", null),
                            new GuiMessage.Button("ltsmodelshare.detail.continue", () -> startBlueprint(item)))));
            return;
        }
        startBlueprint(item);
    }

    private void startBlueprint(Dtos.Item item) {
        blueprintPending = true;
        pendingItemId = item.itemId;
        GuiMessage progress = GuiMessage.info(this, "ltsmodelshare.detail.get_blueprint", tr("ltsmodelshare.detail.requested"));
        blueprintProgress = progress;
        minecraft.setScreen(progress);
        ApiClient.downloadInfo(item.itemId, "1.19", accessCode, new ApiClient.Callback<Dtos.DownloadInfo>() {
            @Override public void ok(Dtos.DownloadInfo info) {
                if (info.version == null || !(info.version.startsWith("1.19") || info.version.startsWith("1.20"))) {
                    ApiClient.post(() -> failBlueprint(tr("ltsmodelshare.preview.version")));
                    return;
                }
                ApiClient.fetchText(info, new ApiClient.Callback<String>() {
                    @Override public void ok(String text) { ApiClient.post(() -> BlueprintClient.request(text, item.title, GuiWorkDetail.this::onServerBlueprint)); }
                    @Override public void fail(String message) { ApiClient.post(() -> failBlueprint(message)); }
                });
            }
            @Override public void fail(String message) { ApiClient.post(() -> failBlueprint(message)); }
        });
    }

    private void failBlueprint(String message) {
        onServerBlueprint(false, message);
    }

    public void onServerBlueprint(boolean ok, String text) {
        blueprintPending = false;
        if (ok && pendingItemId != null) madeBlueprint.add(pendingItemId);
        pendingItemId = null;
        if (minecraft.screen == this || minecraft.screen == blueprintProgress) {
            minecraft.setScreen(GuiMessage.info(this, "ltsmodelshare.detail.get_blueprint", text));
        }
        blueprintProgress = null;
    }

    private void openPreview(Dtos.Item item) {
        GuiMessage progress = new GuiMessage(this, "ltsmodelshare.detail.preview", tr("ltsmodelshare.preview.fetching"),
                List.of(new GuiMessage.Button("ltsmodelshare.common.cancel", null)));
        minecraft.setScreen(progress);
        ApiClient.downloadInfo(item.itemId, "1.19", accessCode, new ApiClient.Callback<Dtos.DownloadInfo>() {
            @Override public void ok(Dtos.DownloadInfo info) {
                ApiClient.fetchText(info, new ApiClient.Callback<String>() {
                    @Override public void ok(String text) {
                        ApiClient.post(() -> {
                            if (minecraft.screen != progress) return;
                            try {
                                LittleGroup group = LittleGroup.load(net.minecraft.nbt.TagParser.parseTag(text));
                                if (group == null || group.size() == 0) { fallbackObjPreview(item, progress); return; }
                                minecraft.setScreen(new GuiTilesPreview(GuiWorkDetail.this, group, item.title));
                            } catch (Throwable error) { fallbackObjPreview(item, progress); }
                        });
                    }
                    @Override public void fail(String message) {
                        ApiClient.post(() -> fallbackObjPreview(item, progress));
                    }
                });
            }
            @Override public void fail(String message) { ApiClient.post(() -> fallbackObjPreview(item, progress)); }
        });
    }

    private void fallbackObjPreview(Dtos.Item item, Screen progress) {
        if (minecraft.screen != progress) return;
        if (item.preview == null || !item.preview.available || item.preview.assetUrl == null || item.preview.assetUrl.isEmpty()) {
            minecraft.setScreen(GuiMessage.info(this, "ltsmodelshare.detail.preview", tr("ltsmodelshare.preview.native_failed")));
            return;
        }
        ApiClient.fetchObj(item.preview.assetUrl, 8L * 1024 * 1024, new ApiClient.Callback<String>() {
            @Override public void ok(String text) {
                try {
                    ObjModel model = ObjModel.parse(text);
                    ApiClient.post(() -> {
                        if (minecraft.screen == progress) minecraft.setScreen(new GuiObjPreview(GuiWorkDetail.this, model, item.title));
                    });
                } catch (Exception error) {
                    ApiClient.post(() -> previewFailure(progress, error.getMessage()));
                }
            }
            @Override public void fail(String message) { ApiClient.post(() -> previewFailure(progress, message)); }
        });
    }

    private void previewFailure(Screen progress, String message) {
        if (minecraft.screen == progress) minecraft.setScreen(GuiMessage.info(this, "ltsmodelshare.detail.preview",
                tr("ltsmodelshare.preview.obj_failed", "", message)));
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
