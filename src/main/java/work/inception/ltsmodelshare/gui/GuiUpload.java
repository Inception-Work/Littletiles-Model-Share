package work.inception.ltsmodelshare.gui;

import static work.inception.ltsmodelshare.client.Translations.tr;
import work.inception.ltsmodelshare.client.SiteTags;

import com.creativemd.littletiles.common.util.converation.StructureStringUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import org.lwjgl.input.Mouse;
import work.inception.ltsmodelshare.LtsModelShare;
import work.inception.ltsmodelshare.api.ApiClient;
import work.inception.ltsmodelshare.api.DependencyScan;
import work.inception.ltsmodelshare.api.Dtos;
import work.inception.ltsmodelshare.client.ImageCache;
import work.inception.ltsmodelshare.client.ImageDraw;
import work.inception.ltsmodelshare.client.TextUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 上传手持蓝图（草稿 or 直接提交审核）。
 *
 * <pre>
 *  上传手持蓝图
 *  已导出 6154 字符
 *  标题  [__________________________________]
 *  简介（Enter 换行）
 *        ┌──────────────────────────────────┐
 *        │                                  │
 *        └──────────────────────────────────┘
 *  截图（点选，最多 6）        标签（可多选）
 *  ┌───────────────────────┐   ┌────────────────┐
 *  │ [图][图][图]          │   │ ✔ 建筑         │
 *  │ [图][图][图]          │   │ · 家具         │
 *  └───────────────────────┘   └────────────────┘
 *  [提交草稿]  [提交审核]                              [取消]
 * </pre>
 *
 * <p>上传过程用**阻塞式进度弹窗**：期间所有操作被吞掉，弹窗显示当前步骤
 * （上传结构 / 截图 2/4 / 保存 / 提交审核），结束后显示结果，点一下关闭。</p>
 */
public class GuiUpload extends GuiScreen {

    private static final int MAX_SHOTS = 6;
    private static final int CELL_W = 60;
    private static final int CELL_H = 34;        // 约 16:9
    private static final int CELL_GAP = 4;
    private static final int TAG_ROW_H = 14;
    /** 当前模组构建固定支持 Forge 1.12.2，上传时自动带上，不需要玩家选择。 */
    private static final String SOURCE_LOADER = "forge";
    private static final String GAME_VERSION = "1.12.2";

    private final GuiScreen parent;

    private GuiTextField fieldTitle;
    private MultiLineText summary;

    private String snbt;
    private String exportError;

    // 截图
    private final List<File> shots = new ArrayList<>();
    private final Set<File> selectedShots = new LinkedHashSet<>();
    /** 截图网格的滚动偏移（像素，连续滚动而不是按行整跳） */
    private int shotScrollPx;
    private static final int ROW_H = CELL_H + CELL_GAP;

    // 标签
    private final List<Dtos.Tag> tags = new ArrayList<>();
    private final Set<String> tagCodes = new LinkedHashSet<>();
    private boolean loadingTags;
    private int tagScroll;

    // 上传进度（阻塞弹窗）
    private boolean busy;
    private boolean submitted;
    private String progress = "";
    private String resultText;
    private boolean resultOk;

    // 布局
    private int panelLeft;
    private int panelW;
    private int titleLabelY;
    private int summaryLabelY;
    private int summaryBoxX;
    private int summaryBoxY;
    private int summaryBoxW;
    private int summaryBoxH;
    private int listsLabelY;
    private int boxY;
    private int boxH;
    private int shotsBoxX;
    private int shotsBoxW;
    private int tagsBoxX;
    private int tagsBoxW;
    private int tagsBoxH;
    private int visibilityY;
    private int actionY;
    /** 可见性：默认公开；另一个选项是私有。 */
    private boolean visibilityPublic = true;
    /** 账号惯用语言；null = 没设置过，上传时回退到游戏语言。 */
    private String accountLocale;
    private boolean localeRequested;

    public GuiUpload(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int cx = width / 2;
        panelW = Math.min(440, width - 24);
        panelLeft = cx - panelW / 2;

        int y = 44;
        titleLabelY = y;
        y += 10;
        fieldTitle = new GuiTextField(0, fontRenderer, panelLeft, y, panelW, 18);
        fieldTitle.setMaxStringLength(64);
        fieldTitle.setFocused(true);
        y += 22;

        summaryLabelY = y;
        y += 10;
        summaryBoxX = panelLeft;
        summaryBoxY = y;
        summaryBoxW = panelW;
        int summaryLines = height >= 300 ? 3 : 2;
        summaryBoxH = summaryLines * 10 + 8;
        if (summary == null) {
            summary = new MultiLineText();
        }
        summary.setBox(summaryBoxX, summaryBoxY, summaryBoxW, summaryBoxH);
        y += summaryBoxH + 6;

        listsLabelY = y;
        y += 11;
        boxY = y;

        actionY = GuiNavigation.footerY(height);
        // 底部只留一个按钮高度的间距，让标签列表多出约一行按钮的高度。
        boxH = Math.max(CELL_H, actionY - 20 - boxY);
        // 标签列底部留一行可见性按钮。
        tagsBoxH = Math.max(TAG_ROW_H, boxH - 24);
        visibilityY = boxY + boxH - 20;

        // 左截图（更宽）/ 右标签（窄一些，标签本身不长）
        // 3 张/行需要 3*CELL_W + 2*CELL_GAP，再留 8px 余量（之前 -4 的算法会少算一格）
        int leftW = Math.min(3 * CELL_W + 2 * CELL_GAP + 8, panelW * 66 / 100);
        shotsBoxX = panelLeft;
        shotsBoxW = leftW;
        tagsBoxX = panelLeft + leftW + 8;
        tagsBoxW = Math.max(90, panelW - leftW - 8);

        int half = Math.max(30, (tagsBoxW - 6) / 2);
        buttonList.add(new TabButton(10, tagsBoxX, visibilityY, half, 18,
                tr("ltsmodelshare.upload.visibility_public"), visibilityPublic));
        buttonList.add(new TabButton(11, tagsBoxX + half + 6, visibilityY, tagsBoxW - half - 6, 18,
                tr("ltsmodelshare.upload.visibility_private"), !visibilityPublic));

        int bw = GuiNavigation.actionWidth(width, 2, 110);
        buttonList.add(new LocalizedButton(1, GuiNavigation.MARGIN, actionY, bw, 20, tr("ltsmodelshare.upload.draft")));
        buttonList.add(new LocalizedButton(4, GuiNavigation.MARGIN + bw + GuiNavigation.GAP, actionY, bw, 20,
                tr("ltsmodelshare.upload.review")));
        buttonList.add(GuiNavigation.backButton(2, width, height, tr("ltsmodelshare.common.back")));
        for (GuiButton button : buttonList) {
            if (button.id == 1 || button.id == 4) button.enabled = !submitted;
        }

        exportFromHand();
        if (shots.isEmpty()) {
            loadShots();
        }
        if (tags.isEmpty() && !loadingTags) {
            loadTags();
        }
        if (!localeRequested) {
            localeRequested = true;
            ApiClient.accountLocale(new ApiClient.Callback<String>() {
                @Override public void ok(String value) {
                    ApiClient.post(() -> accountLocale = value);
                }
                @Override public void fail(String message) { }
            });
        }
    }

    // ==================== 手持导出 ====================

    private void exportFromHand() {
        snbt = null;
        exportError = null;
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        if (player == null) {
            exportError = tr("ltsmodelshare.common.world_required");
            return;
        }
        ItemStack held = player.getHeldItemMainhand();
        if (held == null || held.isEmpty()) {
            exportError = tr("ltsmodelshare.upload.hold_blueprint");
            return;
        }
        try {
            String text = StructureStringUtils.exportStructure(held);
            if (text == null || text.trim().isEmpty()) {
                exportError = tr("ltsmodelshare.upload.not_blueprint");
                return;
            }
            snbt = text;
            if (fieldTitle.getText().isEmpty()) {
                String name = held.getDisplayName();
                fieldTitle.setText(name == null ? "" : name);
            }
        } catch (Throwable t) {
            exportError = tr("ltsmodelshare.upload.export_failed", t);
        }
    }

    // ==================== 截图 ====================

    private void loadShots() {
        shots.clear();
        File[] files = new File(Minecraft.getMinecraft().gameDir, "screenshots").listFiles();
        if (files != null) {
            Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            for (File f : files) {
                String n = f.getName().toLowerCase();
                if (f.isFile() && (n.endsWith(".png") || n.endsWith(".jpg"))) {
                    shots.add(f);
                }
            }
        }
        shotScrollPx = 0;
        selectedShots.removeIf(f -> !f.isFile());
    }

    /** 一行放几张：(宽 + 间距) / (格宽 + 间距) —— 这样 3 张刚好放得下时就会算出 3 */
    private int cellsPerRow() {
        return Math.max(1, (shotsBoxW + CELL_GAP) / (CELL_W + CELL_GAP));
    }

    private int shotRowsVisible() {
        return Math.max(1, boxH / ROW_H);
    }

    private int maxShotScrollPx() {
        return Math.max(0, shotRowCount() * ROW_H - boxH);
    }

    private int shotRowCount() {
        return (shots.size() + cellsPerRow() - 1) / Math.max(1, cellsPerRow());
    }

    private int shotIndexAt(int mouseX, int mouseY) {
        if (shotsBoxW <= 0 || mouseX < shotsBoxX || mouseX > shotsBoxX + shotsBoxW
                || mouseY < boxY || mouseY > boxY + boxH) {
            return -1;
        }
        int perRow = cellsPerRow();
        int col = (mouseX - shotsBoxX) / (CELL_W + CELL_GAP);
        if (col >= perRow) {
            return -1;
        }
        int contentY = mouseY - boxY + shotScrollPx;
        int row = contentY / ROW_H;
        int index = row * perRow + col;
        return index >= 0 && index < shots.size() ? index : -1;
    }

    // ==================== 标签 ====================

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
                ApiClient.post(() -> loadingTags = false);
            }
        });
    }

    private int tagsVisible() {
        return Math.max(1, tagsBoxH / TAG_ROW_H);
    }

    private int maxTagScroll() {
        return Math.max(0, tags.size() - tagsVisible());
    }

    private int tagIndexAt(int mouseX, int mouseY) {
        if (tagsBoxW <= 0 || mouseX < tagsBoxX || mouseX > tagsBoxX + tagsBoxW
                || mouseY < boxY || mouseY > boxY + tagsBoxH) {
            return -1;
        }
        int index = tagScroll + (mouseY - boxY) / TAG_ROW_H;
        return index >= 0 && index < tags.size() ? index : -1;
    }

    // ==================== 交互 ====================

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        if (busy) {
            Mouse.getEventDWheel();   // 消费滚轮，避免背景跟着滚
            return;
        }
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }
        int mx = Mouse.getEventX() * width / Math.max(1, mc.displayWidth);
        int my = height - Mouse.getEventY() * height / Math.max(1, mc.displayHeight) - 1;

        if (mx >= shotsBoxX && mx <= shotsBoxX + shotsBoxW && my >= boxY && my <= boxY + boxH) {
            // 连续滚动：每格滚半行（17px），不再整行跳，看起来不会"一页一页翻"
            int step = ROW_H / 2;
            shotScrollPx = Math.max(0, Math.min(shotScrollPx - Integer.signum(wheel) * step, maxShotScrollPx()));
            return;
        }
        if (mx >= tagsBoxX && mx <= tagsBoxX + tagsBoxW && my >= boxY && my <= boxY + tagsBoxH) {
            tagScroll = Math.max(0, Math.min(tagScroll - Integer.signum(wheel), maxTagScroll()));
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (busy) {
            return;
        }
        if (button.id == 2) {
            mc.displayGuiScreen(parent);
            return;
        }
        if (button.id == 10 || button.id == 11) {
            visibilityPublic = button.id == 10;
            for (GuiButton tab : buttonList) {
                if (tab instanceof TabButton && (tab.id == 10 || tab.id == 11)) {
                    ((TabButton) tab).setSelected((tab.id == 10) == visibilityPublic);
                }
            }
            return;
        }
        if (button.id == 1) {
            startUpload(false);
        } else if (button.id == 4) {
            startUpload(true);
        }
    }

    /** @param alsoSubmit true = 保存后立即提交审核；false = 只存草稿 */
    private void startUpload(final boolean alsoSubmit) {
        if (submitted) return;
        if (snbt == null) {
            finish(false, exportError == null ? tr("ltsmodelshare.upload.no_structure") : exportError);
            return;
        }
        String title = fieldTitle.getText().trim();
        if (title.isEmpty()) {
            finish(false, tr("ltsmodelshare.upload.title_required"));
            return;
        }

        busy = true;
        resultText = null;
        progress = tr("ltsmodelshare.upload.structure");

        final String finalTitle = title;
        final String summaryText = summary.getText().trim();

        ApiClient.uploadSnbt(snbt, finalTitle, new ApiClient.Callback<Dtos.SnbtUpload>() {
            @Override
            public void ok(Dtos.SnbtUpload blob) {
                List<File> queue = new ArrayList<>(selectedShots);
                if (queue.isEmpty()) {
                    saveWork(blob, finalTitle, summaryText, new ArrayList<>(), alsoSubmit);
                } else {
                    uploadNext(queue, 0, new ArrayList<>(), blob, finalTitle, summaryText, alsoSubmit);
                }
            }

            @Override
            public void fail(String message) {
                finish(false, message);
            }
        });
    }

    private void uploadNext(final List<File> queue, final int index, final List<String> urls,
                            final Dtos.SnbtUpload blob, final String title, final String summaryText,
                            final boolean alsoSubmit) {
        if (index >= queue.size()) {
            saveWork(blob, title, summaryText, urls, alsoSubmit);
            return;
        }
        setProgress(tr("ltsmodelshare.upload.shot_progress", index + 1, queue.size()));
        ApiClient.uploadImage(queue.get(index), new ApiClient.Callback<String>() {
            @Override
            public void ok(String url) {
                urls.add(url);
                uploadNext(queue, index + 1, urls, blob, title, summaryText, alsoSubmit);
            }

            @Override
            public void fail(String message) {
                LtsModelShare.LOG.warn("截图上传失败：{}", message);
                uploadNext(queue, index + 1, urls, blob, title, summaryText, alsoSubmit);
            }
        });
    }

    private void saveWork(final Dtos.SnbtUpload blob, final String title, final String summaryText,
                          final List<String> imageUrls, final boolean alsoSubmit) {
        setProgress(tr("ltsmodelshare.upload.saving"));
        final Dtos.WorkSaveRequest req = new Dtos.WorkSaveRequest();
        req.kind = "single";
        // 站点保存时不带 visibility 会默认存成 private；界面默认公开。
        // 草稿（status=draft）仍然不会对外显示，只是把可见性先定好。
        req.visibility = visibilityPublic ? "public" : "private";
        // 来源加载器/游戏版本由当前模组构建决定，不需要玩家选择。
        req.sourceLoader = SOURCE_LOADER;
        req.sourceGameVersion = GAME_VERSION;
        // 目标语言：账号设置优先，没有设置则跟随游戏当前语言。
        req.sourceLocale = accountLocale != null && !accountLocale.trim().isEmpty()
                ? accountLocale : work.inception.ltsmodelshare.client.Translations.siteLocale();
        req.title = title;
        req.summary = summaryText.isEmpty() ? null : summaryText;
        req.tagCodes = new ArrayList<>(tagCodes);
        req.images = new ArrayList<>(imageUrls);
        // 扫一遍 SNBT，把「用了哪些模组方块 / 其它材质」一并上报 ——
        // 不带这些字段的话，详情页的依赖会一直显示「原版」。
        final DependencyScan.Result deps = DependencyScan.scan(snbt);
        final Dtos.WorkSaveRequest.SaveItem item =
                new Dtos.WorkSaveRequest.SaveItem(blob.blobId, title, GAME_VERSION);
        if (deps.hasModBlocks) {
            item.hasModBlocks = 1;
        }
        if (deps.usesOtherTextures) {
            item.usesOtherTextures = 1;
        }
        if (!deps.dependencies.isEmpty()) {
            item.dependencies = deps.dependencies;
        }
        req.items = Collections.singletonList(item);

        ApiClient.saveWork(req, new ApiClient.Callback<Long>() {
            @Override
            public void ok(final Long workId) {
                if (!alsoSubmit) {
                    finish(true, tr("ltsmodelshare.upload.draft_saved", workId, imageUrls.size(), tagCodes.size()));
                    return;
                }
                setProgress(tr("ltsmodelshare.upload.consent"));
                ApiClient.agreeUploadRights(new ApiClient.Callback<Long>() {
                    @Override
                    public void ok(Long consentId) {
                        setProgress(tr("ltsmodelshare.upload.submitting"));
                        ApiClient.submitReview(workId, false, consentId,
                                new ApiClient.Callback<Dtos.SubmitReview>() {
                                    @Override
                                    public void ok(Dtos.SubmitReview value) {
                                        finish(true, value.submitted
                                                        ? tr("ltsmodelshare.upload.submitted", workId)
                                                        + (value.message == null ? "" : "\n" + value.message)
                                                        : tr("ltsmodelshare.upload.auto_submit", workId));
                                    }

                                    @Override
                                    public void fail(String message) {
                                        finish(false, tr("ltsmodelshare.upload.review_error", workId, message));
                                    }
                                });
                    }

                    @Override
                    public void fail(String message) {
                        finish(false, tr("ltsmodelshare.upload.consent_error", workId, message));
                    }
                });
            }

            @Override
            public void fail(String message) {
                finish(false, message);
            }
        });
    }

    private void setProgress(String text) {
        ApiClient.post(() -> progress = text);
    }

    private void finish(boolean ok, String text) {
        ApiClient.post(() -> {
            busy = true;              // 保持阻塞，等玩家点一下看结果
            resultOk = ok;
            submitted = ok;
            resultText = text;
            progress = "";
        });
    }

    /** Dismiss only this overlay; a second ESC leaves the upload page. */
    private void dismissResult() {
        busy = false;
        resultText = null;
        for (GuiButton button : buttonList) {
            if (button.id == 1 || button.id == 4) button.enabled = !submitted;
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (busy) {
            if (keyCode == 1 && resultText != null) {
                dismissResult();
            }
            return;
        }
        if (keyCode == 1) {
            mc.displayGuiScreen(parent);
            return;
        }
        if (summary.isFocused()) {
            summary.keyTyped(typedChar, keyCode);
            return;
        }
        if (fieldTitle.isFocused()) {
            fieldTitle.textboxKeyTyped(typedChar, keyCode);
            if (keyCode == 15) {
                fieldTitle.setFocused(false);
                summary.setFocused(true);
            }
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (busy) {
            if (resultText != null && mouseButton == 0) {
                dismissResult();
            }
            return;
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
        fieldTitle.mouseClicked(mouseX, mouseY, mouseButton);
        if (fieldTitle.isFocused()) {
            summary.setFocused(false);
        }
        if (summary.mouseClicked(mouseX, mouseY, mouseButton)) {
            fieldTitle.setFocused(false);
        }
        if (mouseButton != 0) {
            return;
        }

        int shotIndex = shotIndexAt(mouseX, mouseY);
        if (shotIndex >= 0) {
            File f = shots.get(shotIndex);
            if (selectedShots.contains(f)) {
                selectedShots.remove(f);
            } else if (selectedShots.size() < MAX_SHOTS) {
                selectedShots.add(f);
            }
            return;
        }

        int tagIndex = tagIndexAt(mouseX, mouseY);
        if (tagIndex >= 0) {
            String code = tags.get(tagIndex).code;
            if (!tagCodes.remove(code)) {
                tagCodes.add(code);
            }
        }
    }

    // ==================== 绘制 ====================

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int cx = width / 2;

        Gui.drawRect(panelLeft - 10, 20, panelLeft + panelW + 10, height - 6, 0x70000000);
        drawCenteredString(fontRenderer, tr("ltsmodelshare.upload.title"), cx, 22, 0xFFFFFF);

        String state = exportError != null ? TextFormatting.RED + exportError
                : snbt != null ? TextFormatting.GREEN + tr("ltsmodelshare.upload.exported", snbt.length()) : "";
        if (!state.isEmpty()) {
            drawCenteredString(fontRenderer, state, cx, 32, 0x55FF55);
        }

        drawString(fontRenderer, TextFormatting.GRAY + tr("ltsmodelshare.upload.field_title"), panelLeft, titleLabelY, 0xAAAAAA);
        fieldTitle.drawTextBox();

        drawString(fontRenderer, TextFormatting.GRAY + tr("ltsmodelshare.upload.summary"), panelLeft, summaryLabelY, 0xAAAAAA);
        summary.draw(mouseX, mouseY);

        drawString(fontRenderer, TextFormatting.GRAY + tr("ltsmodelshare.upload.screenshots", MAX_SHOTS, selectedShots.size()), shotsBoxX, listsLabelY, 0xAAAAAA);
        drawString(fontRenderer, TextFormatting.GRAY + (tags.isEmpty() ? tr("ltsmodelshare.common.tags") : tr("ltsmodelshare.common.tags_count", tags.size())), tagsBoxX, listsLabelY, 0xAAAAAA);

        // ---------- 左：截图网格 ----------
        Gui.drawRect(shotsBoxX, boxY, shotsBoxX + shotsBoxW, boxY + boxH, 0x40000000);
        if (shots.isEmpty()) {
            drawString(fontRenderer, TextFormatting.DARK_GRAY + tr("ltsmodelshare.upload.no_shots"),
                    shotsBoxX + 4, boxY + 4, 0x777777);
        }
        int perRow = cellsPerRow();
        int firstRow = shotScrollPx / ROW_H;
        int offset = shotScrollPx % ROW_H;
        int hovered = shotIndexAt(mouseX, mouseY);

        for (int row = firstRow; row < shotRowCount(); row++) {
            int rowTop = boxY + (row - firstRow) * ROW_H - offset;
            if (rowTop >= boxY + boxH) {
                break;   // 已经画到可视区外
            }
            for (int col = 0; col < perRow; col++) {
                int index = row * perRow + col;
                if (index >= shots.size()) {
                    break;
                }
                File f = shots.get(index);
                int cellX = shotsBoxX + col * (CELL_W + CELL_GAP);
                int cellY = rowTop;
                boolean on = selectedShots.contains(f);

                // 懒加载：只给当前可见行请求贴图
                ImageDraw.drawCropped(ImageCache.fileUrl(f), cellX, cellY, CELL_W, CELL_H);

                if (on) {
                    drawFrame(cellX - 2, cellY - 2, CELL_W + 4, CELL_H + 4, 0xFF2ECC40);
                } else if (hovered == index) {
                    drawFrame(cellX, cellY, CELL_W, CELL_H, 0x90FFFFFF);
                }
            }
        }
        if (maxShotScrollPx() > 0) {
            drawScrollbarPx(shotsBoxX + shotsBoxW - 4, boxY, boxH, shotScrollPx, maxShotScrollPx());
        }

        // ---------- 右：标签列表 ----------
        Gui.drawRect(tagsBoxX, boxY, tagsBoxX + tagsBoxW, boxY + tagsBoxH, 0x40000000);
        if (tags.isEmpty() && !loadingTags) {
            drawString(fontRenderer, TextFormatting.DARK_GRAY + tr("ltsmodelshare.search.no_tags"), tagsBoxX + 4, boxY + 4, 0x777777);
        }
        for (int row = 0; row < tagsVisible(); row++) {
            int index = tagScroll + row;
            if (index >= tags.size()) {
                break;
            }
            Dtos.Tag tag = tags.get(index);
            int rowY = boxY + row * TAG_ROW_H;
            boolean on = tagCodes.contains(tag.code);
            if (tagIndexAt(mouseX, mouseY) == index) {
                Gui.drawRect(tagsBoxX + 1, rowY, tagsBoxX + tagsBoxW - 5, rowY + TAG_ROW_H - 1, 0x40FFFFFF);
            }
            drawString(fontRenderer, (on ? TextFormatting.GREEN + "✔ " : TextFormatting.GRAY + "·  ")
                    + SiteTags.label(tag), tagsBoxX + 4, rowY + 3, on ? 0x55FF55 : 0xCCCCCC);
        }
        if (maxTagScroll() > 0) {
            drawScrollbar(tagsBoxX + tagsBoxW - 4, boxY, tagsBoxH, tagScroll, maxTagScroll(),
                    tags.size(), tagsVisible());
        }

        super.drawScreen(mouseX, mouseY, partialTicks);

        // ---------- 阻塞式进度弹窗（最后画，盖住一切） ----------
        if (busy) {
            drawProgressOverlay();
        }
    }

    private void drawProgressOverlay() {
        int cx = width / 2;
        int cy = height / 2;
        int w = Math.min(300, width - 40);
        String text = resultText != null ? resultText : progress;
        List<String> lines = TextUtil.wrap(fontRenderer, text == null ? "" : text, w - 16);
        int h = 26 + lines.size() * 10 + 16;

        Gui.drawRect(0, 0, width, height, 0xA0000000);
        Gui.drawRect(cx - w / 2 - 2, cy - h / 2 - 2, cx + w / 2 + 2, cy + h / 2 + 2, 0xFF000000);
        Gui.drawRect(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2, 0xFF2B2B2B);
        drawFrame(cx - w / 2, cy - h / 2, w, h, 0xFF8A8A8A);

        String title = resultText == null ? tr("ltsmodelshare.upload.busy") : (resultOk ? tr("ltsmodelshare.upload.done") : tr("ltsmodelshare.upload.failed"));
        int titleColor = resultText == null ? 0xFFFF55 : (resultOk ? 0x55FF55 : 0xFF5555);
        drawCenteredString(fontRenderer, title, cx, cy - h / 2 + 6, titleColor);

        int y = cy - h / 2 + 20;
        for (String line : lines) {
            drawCenteredString(fontRenderer, line, cx, y, 0xDDDDDD);
            y += 10;
        }

        if (resultText == null) {
            int dots = (int) ((System.currentTimeMillis() / 300L) % 4L);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < dots; i++) {
                sb.append('.');
            }
            drawCenteredString(fontRenderer, TextFormatting.GRAY + sb.toString(),
                    cx, cy + h / 2 - 12, 0xAAAAAA);
        } else {
            drawCenteredString(fontRenderer, TextFormatting.GRAY + tr("ltsmodelshare.upload.dismiss"),
                    cx, cy + h / 2 - 12, 0xAAAAAA);
        }
    }

    /** 像素滚动条：thumb 高度按 可视/总 比例，位置按 偏移/最大偏移 */
    private void drawScrollbarPx(int barX, int boxTop, int boxHeight, int scrollPx, int maxPx) {
        if (maxPx <= 0) {
            return;
        }
        Gui.drawRect(barX, boxTop, barX + 3, boxTop + boxHeight, 0x40000000);
        int thumbH = Math.max(10, (int) ((long) boxHeight * boxHeight / (boxHeight + maxPx)));
        int thumbY = boxTop + (int) ((long) (boxHeight - thumbH) * scrollPx / maxPx);
        Gui.drawRect(barX, thumbY, barX + 3, thumbY + thumbH, 0xAAFFFFFF);
    }

    private void drawFrame(int x, int y, int w, int h, int color) {
        Gui.drawRect(x, y, x + w, y + 2, color);
        Gui.drawRect(x, y + h - 2, x + w, y + h, color);
        Gui.drawRect(x, y, x + 2, y + h, color);
        Gui.drawRect(x + w - 2, y, x + w, y + h, color);
    }

    private void drawScrollbar(int barX, int boxTop, int boxHeight, int scroll, int maxScroll,
                               int total, int visible) {
        if (maxScroll <= 0) {
            return;
        }
        Gui.drawRect(barX, boxTop, barX + 3, boxTop + boxHeight, 0x40000000);
        int thumbH = Math.max(8, boxHeight * visible / Math.max(visible, total));
        int thumbY = boxTop + (boxHeight - thumbH) * scroll / maxScroll;
        Gui.drawRect(barX, thumbY, barX + 3, thumbY + thumbH, 0xAAFFFFFF);
    }

    // ==================== 多行文本框 ====================

    private final class MultiLineText {
        private final StringBuilder text = new StringBuilder();
        private boolean focused;
        private int boxX;
        private int boxY;
        private int boxW;
        private int boxH;

        void setBox(int x, int y, int w, int h) {
            this.boxX = x;
            this.boxY = y;
            this.boxW = w;
            this.boxH = h;
        }

        String getText() {
            return text.toString();
        }

        void setFocused(boolean focused) {
            this.focused = focused;
        }

        boolean isFocused() {
            return focused;
        }

        boolean mouseClicked(int mx, int my, int button) {
            boolean inside = mx >= boxX && mx <= boxX + boxW && my >= boxY && my <= boxY + boxH;
            if (inside && button == 0) {
                focused = true;
                return true;
            }
            return false;
        }

        void keyTyped(char typedChar, int keyCode) {
            if (!focused) {
                return;
            }
            if (keyCode == 28) {
                text.append('\n');
                return;
            }
            if (keyCode == 14) {
                if (text.length() > 0) {
                    text.setLength(text.length() - 1);
                }
                return;
            }
            if (keyCode == 15) {
                focused = false;
                fieldTitle.setFocused(true);
                return;
            }
            if (typedChar >= ' ') {
                text.append(typedChar);
            }
        }

        void draw(int mouseX, int mouseY) {
            Gui.drawRect(boxX, boxY, boxX + boxW, boxY + boxH, 0x60000000);
            Gui.drawRect(boxX, boxY, boxX + boxW, boxY + 1, focused ? 0xFFFFFFFF : 0x60FFFFFF);

            List<String> lines = TextUtil.wrap(fontRenderer, text.toString(), boxW - 6);
            int visible = Math.max(1, (boxH - 4) / 10);
            int from = Math.max(0, lines.size() - visible);
            int y = boxY + 3;
            for (int i = from; i < lines.size(); i++) {
                drawString(fontRenderer, lines.get(i), boxX + 3, y, 0xDDDDDD);
                y += 10;
            }
            if (focused) {
                int caretRow = Math.max(0, Math.min(lines.size(), visible) - 1);
                int caretY = boxY + 3 + caretRow * 10;
                String last = lines.isEmpty() ? "" : lines.get(lines.size() - 1);
                int caretX = boxX + 3 + fontRenderer.getStringWidth(last);
                Gui.drawRect(caretX + 1, caretY, caretX + 2, caretY + 9, 0xFFFFFFFF);
            }
        }
    }
}
