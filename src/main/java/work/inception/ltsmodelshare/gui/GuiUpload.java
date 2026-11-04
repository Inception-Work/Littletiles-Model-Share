package work.inception.ltsmodelshare.gui;

import static work.inception.ltsmodelshare.client.Translations.tr;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import team.creative.littletiles.common.block.little.tile.group.LittleGroup;
import team.creative.littletiles.common.item.ItemLittleBlueprint;
import work.inception.ltsmodelshare.LtsModelShare;
import work.inception.ltsmodelshare.api.ApiClient;
import work.inception.ltsmodelshare.api.DependencyScan;
import work.inception.ltsmodelshare.api.Dtos;
import work.inception.ltsmodelshare.client.LocalThumbs;
import work.inception.ltsmodelshare.client.SiteTags;
import work.inception.ltsmodelshare.client.TextUtil;

/** Forge 1.12.2 upload geometry, drawing order and interactions on 1.20.1. */
public class GuiUpload extends Screen {

    @Override
    public void renderBackground(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // No-op: Screen.render calls this *after* content; painting here dims the UI.
    }
    private static final int MAX_SHOTS = 6;
    private static final int CELL_W = 60;
    private static final int CELL_H = 34;
    private static final int CELL_GAP = 4;
    private static final int ROW_H = CELL_H + CELL_GAP;
    private static final int TAG_ROW_H = 14;
    private static final String SOURCE_LOADER = "forge";
    private static final String GAME_VERSION = "1.20.2";
    private final Screen parent;
    private final LocalThumbs thumbs = new LocalThumbs();
    private EditBox titleField;
    private final MultiLineText summary = new MultiLineText();
    private FitButton draftButton;
    private FitButton reviewButton;
    private String snbt;
    private String exportError;
    private boolean exported;
    private final List<File> shots = new ArrayList<>();
    // Click order determines the cover/image order, NOT directory order.
    private final Set<File> picked = new LinkedHashSet<>();
    private int shotScrollPx;
    private final List<Dtos.Tag> tags = new ArrayList<>();
    private final Set<String> tagCodes = new LinkedHashSet<>();
    private boolean loadingTags;
    private int tagScroll;
    private boolean busy;
    private boolean submitted;
    private String progress = "";
    private String resultText;
    private boolean resultOk;
    private boolean visibilityPublic = true;
    private String accountLocale;
    private boolean localeRequested;
    private int panelLeft, panelW, titleLabelY, summaryLabelY, listsLabelY;
    private int boxY, boxH, shotsBoxX, shotsBoxW, tagsBoxX, tagsBoxW, tagsBoxH, visibilityY, actionY;

    public GuiUpload(Screen parent) {
        super(Component.translatable("ltsmodelshare.upload.title"));
        this.parent = parent;
    }

    @Override protected void init() {
        // Resize must not discard edits or re-export a different held item during upload.
        EditBox oldTitle = titleField;
        boolean titleFocused = oldTitle == null || oldTitle.isFocused();
        clearWidgets();
        panelW = Math.min(440, width - 24);
        panelLeft = width / 2 - panelW / 2;
        int y = 44;
        titleLabelY = y;
        y += 10;
        Component titleLabel = Component.translatable("ltsmodelshare.upload.field_title");
        titleField = oldTitle == null ? new EditBox(font, panelLeft, y, panelW, 18, titleLabel)
                : new EditBox(font, panelLeft, y, panelW, 18, oldTitle, titleLabel);
        titleField.setMaxLength(64);
        addRenderableWidget(titleField);
        y += 22;
        summaryLabelY = y;
        y += 10;
        int summaryH = (height >= 300 ? 3 : 2) * 10 + 8;
        summary.setBox(panelLeft, y, panelW, summaryH);
        y += summaryH + 6;
        listsLabelY = y;
        boxY = y + 11;
        actionY = GuiNavigation.footerY(height);
        boxH = Math.max(CELL_H, actionY - 20 - boxY);
        tagsBoxH = Math.max(TAG_ROW_H, boxH - 24);
        visibilityY = boxY + boxH - 20;
        int leftW = Math.min(3 * CELL_W + 2 * CELL_GAP + 8, panelW * 66 / 100);
        shotsBoxX = panelLeft;
        shotsBoxW = leftW;
        tagsBoxX = panelLeft + leftW + 8;
        tagsBoxW = Math.max(90, panelW - leftW - 8);
        int half = Math.max(30, (tagsBoxW - 6) / 2);
        addRenderableWidget(new VisibilityButton(tagsBoxX, visibilityY, half, true));
        addRenderableWidget(new VisibilityButton(tagsBoxX + half + 6, visibilityY, tagsBoxW - half - 6, false));
        int bw = GuiNavigation.actionWidth(width, 2, 110);
        draftButton = addRenderableWidget(GuiNavigation.button(GuiNavigation.MARGIN, actionY, bw, 20,
                "ltsmodelshare.upload.draft", () -> startUpload(false)));
        reviewButton = addRenderableWidget(GuiNavigation.button(GuiNavigation.MARGIN + bw + GuiNavigation.GAP,
                actionY, bw, 20, "ltsmodelshare.upload.review", () -> startUpload(true)));
        addRenderableWidget(GuiNavigation.button(GuiNavigation.backX(width), actionY,
                GuiNavigation.BACK_WIDTH, 20, "ltsmodelshare.common.back", this::onClose));
        updateSubmitButtons();
        if (titleFocused && !summary.focused) setInitialFocus(titleField);
        else titleField.setFocused(false);
        if (!exported) {
            exported = true;
            exportFromHand();
        }
        // Refresh on reinitialization while preserving selection order and scroll.
        if (!busy) loadShots();
        shotScrollPx = Math.max(0, Math.min(shotScrollPx, maxShotScrollPx()));
        tagScroll = Math.max(0, Math.min(tagScroll, maxTagScroll()));
        if (tags.isEmpty() && !loadingTags) loadTags();
        if (!localeRequested) {
            localeRequested = true;
            ApiClient.accountLocale(new ApiClient.Callback<String>() {
                @Override public void ok(String value) { ApiClient.post(() -> accountLocale = value); }
                @Override public void fail(String message) { }
            });
        }
    }

    private void exportFromHand() {
        snbt = null;
        exportError = null;
        if (minecraft.player == null) { exportError = tr("ltsmodelshare.common.world_required"); return; }
        ItemStack held = minecraft.player.getMainHandItem();
        if (held.isEmpty()) { exportError = tr("ltsmodelshare.upload.hold_blueprint"); return; }
        if (!(held.getItem() instanceof ItemLittleBlueprint blueprint)) {
            exportError = tr("ltsmodelshare.upload.not_blueprint"); return;
        }
        try {
            LittleGroup group = blueprint.getTiles(held);
            if (group == null || group.size() == 0) { exportError = tr("ltsmodelshare.upload.no_structure"); return; }
            snbt = LittleGroup.save(group).toString();
            if (titleField.getValue().isEmpty()) titleField.setValue(held.getHoverName().getString());
        } catch (Throwable error) { exportError = tr("ltsmodelshare.upload.export_failed", error.toString()); }
    }

    private void loadShots() {
        shots.clear();
        File dir = net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().resolve("screenshots").toFile();
        File[] files = dir.listFiles();
        if (files != null) {
            Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            for (File file : files) {
                String name = file.getName().toLowerCase(Locale.ROOT);
                if (file.isFile() && (name.endsWith(".png") || name.endsWith(".jpg"))) shots.add(file);
            }
        }
        picked.removeIf(file -> !shots.contains(file));
        thumbs.retainFiles(shots);
    }

    private void loadTags() {
        loadingTags = true;
        ApiClient.listTags(new ApiClient.Callback<List<Dtos.Tag>>() {
            @Override public void ok(List<Dtos.Tag> value) {
                ApiClient.post(() -> {
                    loadingTags = false;
                    tags.clear();
                    if (value != null) for (Dtos.Tag tag : value) {
                        if (tag != null && tag.code != null && tag.name != null) tags.add(tag);
                    }
                    tagScroll = Math.max(0, Math.min(tagScroll, maxTagScroll()));
                });
            }
            @Override public void fail(String message) { ApiClient.post(() -> loadingTags = false); }
        });
    }

    private int cellsPerRow() { return Math.max(1, (shotsBoxW + CELL_GAP) / (CELL_W + CELL_GAP)); }
    private int shotRowCount() { return (shots.size() + cellsPerRow() - 1) / cellsPerRow(); }
    private int maxShotScrollPx() { return Math.max(0, shotRowCount() * ROW_H - boxH); }
    private int visibleTagRows() { return Math.max(1, tagsBoxH / TAG_ROW_H); }
    private int maxTagScroll() { return Math.max(0, tags.size() - visibleTagRows()); }
    private boolean inside(double x, double y, int left, int top, int w, int h) {
        return x >= left && x <= left + w && y >= top && y <= top + h;
    }
    private int shotIndexAt(double x, double y) {
        if (!inside(x, y, shotsBoxX, boxY, shotsBoxW, boxH)) return -1;
        int col = (int) (x - shotsBoxX) / (CELL_W + CELL_GAP);
        if (col >= cellsPerRow()) return -1;
        int row = ((int) (y - boxY) + shotScrollPx) / ROW_H;
        int index = row * cellsPerRow() + col;
        return index >= 0 && index < shots.size() ? index : -1;
    }
    private int tagIndexAt(double x, double y) {
        if (!inside(x, y, tagsBoxX, boxY, tagsBoxW, tagsBoxH)) return -1;
        int index = tagScroll + (int) (y - boxY) / TAG_ROW_H;
        return index >= 0 && index < tags.size() ? index : -1;
    }

    private void startUpload(boolean alsoSubmit) {
        if (busy || submitted) return;
        if (snbt == null) {
            finish(false, exportError == null ? tr("ltsmodelshare.upload.no_structure") : exportError); return;
        }
        String titleText = titleField.getValue().trim();
        if (titleText.isEmpty()) { finish(false, tr("ltsmodelshare.upload.title_required")); return; }
        if (alsoSubmit && picked.isEmpty()) {
            finish(false, tr("ltsmodelshare.upload.screenshot_required"));
            return;
        }
        busy = true;
        resultText = null;
        progress = tr("ltsmodelshare.upload.structure");
        String summaryText = summary.getText().trim();
        List<File> queue = new ArrayList<>(picked);
        ApiClient.uploadSnbt(snbt, titleText, new ApiClient.Callback<Dtos.SnbtUpload>() {
            @Override public void ok(Dtos.SnbtUpload blob) {
                ApiClient.post(() -> uploadNext(queue, 0, new ArrayList<>(), blob, titleText, summaryText, alsoSubmit));
            }
            @Override public void fail(String message) { finish(false, message); }
        });
    }

    private void uploadNext(List<File> queue, int index, List<String> urls, Dtos.SnbtUpload blob,
                            String titleText, String summaryText, boolean alsoSubmit) {
        if (index >= queue.size()) { saveWork(blob, titleText, summaryText, urls, alsoSubmit); return; }
        progress = tr("ltsmodelshare.upload.shot_progress", index + 1, queue.size());
        ApiClient.uploadImage(queue.get(index), new ApiClient.Callback<String>() {
            @Override public void ok(String url) {
                ApiClient.post(() -> {
                    if (url != null && !url.isEmpty()) urls.add(url);
                    uploadNext(queue, index + 1, urls, blob, titleText, summaryText, alsoSubmit);
                });
            }
            @Override public void fail(String message) {
                // Like the original, a failed screenshot does not discard the whole work.
                LtsModelShare.LOG.warn("Screenshot upload failed: {}", message);
                ApiClient.post(() -> uploadNext(queue, index + 1, urls, blob, titleText, summaryText, alsoSubmit));
            }
        });
    }

    private void saveWork(Dtos.SnbtUpload blob, String titleText, String summaryText,
                          List<String> urls, boolean alsoSubmit) {
        progress = tr("ltsmodelshare.upload.saving");
        Dtos.WorkSaveRequest request = new Dtos.WorkSaveRequest();
        request.kind = "single";
        request.visibility = visibilityPublic ? "public" : "private";
        request.sourceLoader = SOURCE_LOADER;
        request.sourceGameVersion = GAME_VERSION;
        request.sourceLocale = accountLocale != null && !accountLocale.trim().isEmpty()
                ? accountLocale : work.inception.ltsmodelshare.client.Translations.siteLocale();
        request.title = titleText;
        request.summary = summaryText.isEmpty() ? null : summaryText;
        request.tagCodes = new ArrayList<>(tagCodes);
        request.images = new ArrayList<>(urls);
        DependencyScan.Result deps = DependencyScan.scan(snbt);
        Dtos.WorkSaveRequest.SaveItem item = new Dtos.WorkSaveRequest.SaveItem(blob.blobId, titleText, GAME_VERSION);
        if (deps.hasModBlocks) item.hasModBlocks = 1;
        if (deps.usesOtherTextures) item.usesOtherTextures = 1;
        if (!deps.dependencies.isEmpty()) item.dependencies = deps.dependencies;
        request.items = Collections.singletonList(item);
        ApiClient.saveWork(request, new ApiClient.Callback<Long>() {
            @Override public void ok(Long workId) {
                ApiClient.post(() -> {
                    if (!alsoSubmit) {
                        finish(true, tr("ltsmodelshare.upload.draft_saved", workId, urls.size(), tagCodes.size())); return;
                    }
                    if (urls.isEmpty()) {
                        finish(false, tr("ltsmodelshare.upload.screenshot_required")); return;
                    }
                    progress = tr("ltsmodelshare.upload.consent");
                    ApiClient.agreeUploadRights(new ApiClient.Callback<Long>() {
                        @Override public void ok(Long consentId) {
                            ApiClient.post(() -> {
                                progress = tr("ltsmodelshare.upload.submitting");
                                ApiClient.submitReview(workId, false, consentId, new ApiClient.Callback<Dtos.SubmitReview>() {
                                    @Override public void ok(Dtos.SubmitReview value) {
                                        finish(true, value.submitted ? tr("ltsmodelshare.upload.submitted", workId)
                                                + (value.message == null ? "" : "\n" + value.message)
                                                : tr("ltsmodelshare.upload.auto_submit", workId));
                                    }
                                    @Override public void fail(String message) {
                                        finish(false, message != null && message.contains("M05-069")
                                                ? tr("ltsmodelshare.upload.screenshot_required")
                                                : tr("ltsmodelshare.upload.review_error", workId, message));
                                    }
                                });
                            });
                        }
                        @Override public void fail(String message) {
                            finish(false, tr("ltsmodelshare.upload.consent_error", workId, message));
                        }
                    });
                });
            }
            @Override public void fail(String message) { finish(false, message); }
        });
    }

    private void finish(boolean ok, String text) {
        ApiClient.post(() -> {
            busy = true;
            resultOk = ok;
            submitted = ok;
            resultText = text == null ? "" : text;
            progress = "";
        });
    }
    private void updateSubmitButtons() { draftButton.active = !submitted; reviewButton.active = !submitted; }
    private void dismissResult() { busy = false; resultText = null; updateSubmitButtons(); }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xE0101010);
        graphics.fill(panelLeft - 10, 20, panelLeft + panelW + 10, height - 6, 0x70000000);
        graphics.drawCenteredString(font, title, width / 2, 22, 0xFFFFFF);
        String state = exportError != null ? exportError : snbt != null ? tr("ltsmodelshare.upload.exported", snbt.length()) : "";
        if (!state.isEmpty()) graphics.drawCenteredString(font, state, width / 2, 32, exportError != null ? 0xFF5555 : 0x55FF55);
        graphics.drawString(font, tr("ltsmodelshare.upload.field_title"), panelLeft, titleLabelY, 0xAAAAAA);
        graphics.drawString(font, tr("ltsmodelshare.upload.summary"), panelLeft, summaryLabelY, 0xAAAAAA);
        summary.draw(graphics);
        graphics.drawString(font, tr("ltsmodelshare.upload.screenshots", MAX_SHOTS, picked.size()), shotsBoxX, listsLabelY, 0xAAAAAA);
        graphics.drawString(font, tags.isEmpty() ? tr("ltsmodelshare.common.tags")
                : tr("ltsmodelshare.common.tags_count", tags.size()), tagsBoxX, listsLabelY, 0xAAAAAA);
        drawShots(graphics, mouseX, mouseY);
        drawTags(graphics, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (busy) {
            graphics.pose().pushPose();
            try { graphics.pose().translate(0, 0, 300); drawProgressOverlay(graphics); }
            finally { graphics.pose().popPose(); }
        }
    }

    private void drawShots(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(shotsBoxX, boxY, shotsBoxX + shotsBoxW, boxY + boxH, 0x40000000);
        if (shots.isEmpty()) graphics.drawString(font, tr("ltsmodelshare.upload.no_shots"), shotsBoxX + 4, boxY + 4, 0x555555);
        int perRow = cellsPerRow();
        int hovered = shotIndexAt(mouseX, mouseY);
        // Partial rows must not paint over labels/buttons, unlike the old unscissored renderer.
        graphics.enableScissor(shotsBoxX, boxY, shotsBoxX + shotsBoxW, boxY + boxH);
        try {
            for (int index = (shotScrollPx / ROW_H) * perRow; index < shots.size(); index++) {
                int cellX = shotsBoxX + index % perRow * (CELL_W + CELL_GAP);
                int cellY = boxY + index / perRow * ROW_H - shotScrollPx;
                if (cellY >= boxY + boxH) break;
                if (cellY + CELL_H <= boxY) continue;
                File file = shots.get(index);
                thumbs.draw(graphics, file, cellX, cellY, CELL_W, CELL_H);
                if (picked.contains(file)) drawFrame(graphics, cellX - 2, cellY - 2, CELL_W + 4, CELL_H + 4, 0xFF2ECC40);
                else if (hovered == index) drawFrame(graphics, cellX, cellY, CELL_W, CELL_H, 0x90FFFFFF);
            }
        } finally { graphics.disableScissor(); }
        if (maxShotScrollPx() > 0) drawScrollbarPx(graphics, shotsBoxX + shotsBoxW - 4, boxY, boxH, shotScrollPx, maxShotScrollPx());
    }

    private void drawTags(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(tagsBoxX, boxY, tagsBoxX + tagsBoxW, boxY + tagsBoxH, 0x40000000);
        graphics.enableScissor(tagsBoxX, boxY, tagsBoxX + tagsBoxW - 5, boxY + tagsBoxH);
        try {
            if (tags.isEmpty() && !loadingTags) graphics.drawString(font, tr("ltsmodelshare.search.no_tags"), tagsBoxX + 4, boxY + 4, 0x555555);
            for (int row = 0; row < visibleTagRows(); row++) {
                int index = tagScroll + row;
                if (index >= tags.size()) break;
                Dtos.Tag tag = tags.get(index);
                int rowY = boxY + row * TAG_ROW_H;
                boolean on = tagCodes.contains(tag.code);
                if (tagIndexAt(mouseX, mouseY) == index) graphics.fill(tagsBoxX + 1, rowY, tagsBoxX + tagsBoxW - 5, rowY + TAG_ROW_H - 1, 0x40FFFFFF);
                graphics.drawString(font, (on ? "✔ " : "·  ") + SiteTags.label(tag), tagsBoxX + 4, rowY + 3, on ? 0x55FF55 : 0xAAAAAA);
            }
        } finally { graphics.disableScissor(); }
        if (maxTagScroll() > 0) {
            int thumbH = Math.max(8, tagsBoxH * visibleTagRows() / Math.max(visibleTagRows(), tags.size()));
            drawScrollbar(graphics, tagsBoxX + tagsBoxW - 4, boxY, tagsBoxH, tagScroll, maxTagScroll(), thumbH);
        }
    }

    private void drawProgressOverlay(GuiGraphics graphics) {
        int cx = width / 2, cy = height / 2, w = Math.min(300, width - 40);
        List<String> lines = wrap(resultText != null ? resultText : progress, w - 16);
        int h = 26 + lines.size() * 10 + 16;
        graphics.fill(0, 0, width, height, 0xA0000000);
        graphics.fill(cx - w / 2 - 2, cy - h / 2 - 2, cx + w / 2 + 2, cy + h / 2 + 2, 0xFF000000);
        graphics.fill(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2, 0xFF2B2B2B);
        drawFrame(graphics, cx - w / 2, cy - h / 2, w, h, 0xFF8A8A8A);
        String heading = resultText == null ? tr("ltsmodelshare.upload.busy") : tr(resultOk ? "ltsmodelshare.upload.done" : "ltsmodelshare.upload.failed");
        int color = resultText == null ? 0xFFFF55 : resultOk ? 0x55FF55 : 0xFF5555;
        graphics.drawCenteredString(font, heading, cx, cy - h / 2 + 6, color);
        int y = cy - h / 2 + 20;
        for (String line : lines) { graphics.drawCenteredString(font, line, cx, y, 0xDDDDDD); y += 10; }
        String footer = resultText == null ? ".".repeat((int) ((System.currentTimeMillis() / 300L) % 4L)) : tr("ltsmodelshare.upload.dismiss");
        graphics.drawCenteredString(font, footer, cx, cy + h / 2 - 12, 0xAAAAAA);
    }
    private void drawFrame(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 2, color); g.fill(x, y + h - 2, x + w, y + h, color);
        g.fill(x, y, x + 2, y + h, color); g.fill(x + w - 2, y, x + w, y + h, color);
    }
    private void drawScrollbarPx(GuiGraphics g, int x, int y, int h, int scroll, int max) {
        drawScrollbar(g, x, y, h, scroll, max, Math.max(10, (int) ((long) h * h / (h + max))));
    }
    private void drawScrollbar(GuiGraphics g, int x, int y, int h, int scroll, int max, int thumbH) {
        g.fill(x, y, x + 3, y + h, 0x40000000);
        int thumbY = y + (int) ((long) (h - thumbH) * scroll / max);
        g.fill(x, thumbY, x + 3, thumbY + thumbH, 0xAAFFFFFF);
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (busy) { if (resultText != null && button == 0) dismissResult(); return true; }
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (titleField.isFocused()) summary.focused = false;
        if (summary.mouseClicked(mouseX, mouseY, button)) {
            setFocused(null); titleField.setFocused(false); handled = true;
        }
        if (button != 0 || busy) return handled;
        int shotIndex = shotIndexAt(mouseX, mouseY);
        if (shotIndex >= 0) {
            File file = shots.get(shotIndex);
            if (!picked.remove(file) && picked.size() < MAX_SHOTS) picked.add(file);
            return true;
        }
        int tagIndex = tagIndexAt(mouseX, mouseY);
        if (tagIndex >= 0) {
            String code = tags.get(tagIndex).code;
            if (!tagCodes.remove(code)) tagCodes.add(code);
            return true;
        }
        return handled;
    }
    @Override public boolean mouseScrolled(double x, double y, double deltaX, double delta) {
        if (busy) return true;
        if (inside(x, y, shotsBoxX, boxY, shotsBoxW, boxH)) {
            shotScrollPx = Math.max(0, Math.min(maxShotScrollPx(), shotScrollPx - (int) Math.signum(delta) * (ROW_H / 2)));
            return true;
        }
        if (inside(x, y, tagsBoxX, boxY, tagsBoxW, tagsBoxH)) {
            tagScroll = Math.max(0, Math.min(maxTagScroll(), tagScroll - (int) Math.signum(delta)));
            return true;
        }
        return super.mouseScrolled(x, y, deltaX, delta);
    }
    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (busy) { if (key == GLFW.GLFW_KEY_ESCAPE && resultText != null) dismissResult(); return true; }
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        if (summary.focused) return summary.keyPressed(key);
        if (titleField.isFocused()) {
            if (key == GLFW.GLFW_KEY_TAB) { setFocused(null); titleField.setFocused(false); summary.focused = true; }
            else titleField.keyPressed(key, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }
    @Override public boolean charTyped(char character, int modifiers) {
        if (busy) return true;
        if (summary.focused) { if (character >= ' ') summary.text.append(character); return true; }
        return super.charTyped(character, modifiers);
    }
    @Override public boolean keyReleased(int key, int scan, int modifiers) { return busy || super.keyReleased(key, scan, modifiers); }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) { return busy || super.mouseDragged(x, y, button, dx, dy); }
    @Override public boolean mouseReleased(double x, double y, int button) { return busy || super.mouseReleased(x, y, button); }
    @Override public void tick() { /* EditBox.tick() removed in 1.20.2. */ }
    @Override public void onClose() { if (!busy) minecraft.setScreen(parent); }
    @Override public void removed() { thumbs.clear(); super.removed(); }
    @Override public boolean isPauseScreen() { return false; }

    /** Original character wrapping, preserving blank and trailing newline rows. */
    private List<String> wrap(String text, int maxWidth) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty()) return out;
        for (String raw : text.replace("\r", "").split("\n", -1)) {
            if (raw.isEmpty()) { out.add(""); continue; }
            StringBuilder line = new StringBuilder();
            for (int i = 0; i < raw.length(); i++) {
                char c = raw.charAt(i);
                if (font.width(line.toString() + c) > maxWidth && line.length() > 0) { out.add(line.toString()); line.setLength(0); }
                line.append(c);
            }
            if (line.length() > 0) out.add(line.toString());
        }
        return out;
    }

    /** Append/backspace/Enter/Tab editing matches the original, not a single-line EditBox. */
    private final class MultiLineText {
        private final StringBuilder text = new StringBuilder();
        private boolean focused;
        private int x, y, w, h;
        void setBox(int x, int y, int w, int h) { this.x = x; this.y = y; this.w = w; this.h = h; }
        String getText() { return text.toString(); }
        boolean mouseClicked(double mx, double my, int button) {
            if (button != 0 || !inside(mx, my, x, y, w, h)) return false;
            focused = true; return true;
        }
        boolean keyPressed(int key) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) text.append('\n');
            else if (key == GLFW.GLFW_KEY_BACKSPACE && text.length() > 0) text.setLength(text.length() - 1);
            else if (key == GLFW.GLFW_KEY_TAB) { focused = false; setFocused(titleField); titleField.setFocused(true); }
            return true;
        }
        void draw(GuiGraphics g) {
            g.fill(x, y, x + w, y + h, 0x60000000);
            g.fill(x, y, x + w, y + 1, focused ? 0xFFFFFFFF : 0x60FFFFFF);
            List<String> lines = wrap(text.toString(), w - 6);
            int visible = Math.max(1, (h - 4) / 10), from = Math.max(0, lines.size() - visible), lineY = y + 3;
            for (int i = from; i < lines.size(); i++) { g.drawString(font, lines.get(i), x + 3, lineY, 0xDDDDDD); lineY += 10; }
            if (focused) {
                int caretY = y + 3 + Math.max(0, Math.min(lines.size(), visible) - 1) * 10;
                String last = lines.isEmpty() ? "" : lines.get(lines.size() - 1);
                int caretX = x + 3 + font.width(last);
                g.fill(caretX + 1, caretY, caretX + 2, caretY + 9, 0xFFFFFFFF);
            }
        }
    }

    private final class VisibilityButton extends FitButton {
        private final boolean publicTab;
        VisibilityButton(int x, int y, int w, boolean publicTab) {
            super(x, y, w, 18, Component.translatable(publicTab ? "ltsmodelshare.upload.visibility_public"
                    : "ltsmodelshare.upload.visibility_private"), button -> { if (!busy) visibilityPublic = publicTab; });
            this.publicTab = publicTab;
        }
        @Override public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            if (visibilityPublic == publicTab) {
                g.fill(getX(), getY(), getX() + width, getY() + height, 0xFF3F6E8C);
                g.fill(getX(), getY() + height - 1, getX() + width, getY() + height, 0xFFFFFFFF);
                TextUtil.drawCenteredFit(g, font, label().getString(), getX() + width / 2, getY() + (height - 8) / 2, width - 8, 0xFFFFFFFF);
            } else super.renderWidget(g, mouseX, mouseY, partialTick);
        }
    }
}
