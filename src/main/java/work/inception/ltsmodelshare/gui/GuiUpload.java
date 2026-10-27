package work.inception.ltsmodelshare.gui;

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
 * 上传手持蓝图到站点（存成草稿，审核在网站上点）。
 *
 * <pre>
 *  上传手持蓝图
 *  已导出 6154 字符
 *  标题  [________________________________]
 *  简介  ┌────────────────────────────────┐
 *        │ 多行，Enter 换行               │
 *        └────────────────────────────────┘
 *  截图（点选，最多 6）            标签（可多选）
 *  ┌─────────────────────┐      ┌────────────────────┐
 *  │ [图][图][图]        │      │ ✔ 建筑             │
 *  │ [图][图][图]        │      │ · 家具             │
 *  └─────────────────────┘      └────────────────────┘
 *  [上传并保存草稿]                          [取消]
 * </pre>
 *
 * <p>截图取自 {@code <gameDir>/screenshots}（游戏里 F2 生成）：一行三张、不显示文件名、
 * 选中项四周画绿色边框；缩略图**懒加载** —— 只给当前可见的格子请求贴图。</p>
 */
public class GuiUpload extends GuiScreen {

    private static final int MAX_SHOTS = 6;
    private static final int CELL_W = 52;
    private static final int CELL_H = 30;        // 约 16:9
    private static final int CELL_GAP = 4;
    private static final int TAG_ROW_H = 14;

    private final GuiScreen parent;

    private GuiTextField fieldTitle;
    private MultiLineText summary;

    private String status = "";
    private boolean busy;

    private String snbt;
    private String exportError;

    // 截图
    private final List<File> shots = new ArrayList<>();
    private final Set<File> selectedShots = new LinkedHashSet<>();
    private int shotScroll;

    // 标签
    private final List<Dtos.Tag> tags = new ArrayList<>();
    private final Set<String> tagCodes = new LinkedHashSet<>();
    private boolean loadingTags;
    private int tagScroll;

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
    private int actionY;

    public GuiUpload(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int cx = width / 2;
        panelW = Math.min(400, width - 24);
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
        // 简介框：正常 3 行，矮窗口（<300）用 2 行，给小面的截图/标签区留空间
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

        actionY = height - 30;
        boxH = Math.max(CELL_H, actionY - 36 - boxY);

        // 左截图 / 右标签
        int leftW = Math.min(3 * CELL_W + 2 * CELL_GAP + 6, panelW * 55 / 100);
        shotsBoxX = panelLeft;
        shotsBoxW = leftW;
        tagsBoxX = panelLeft + leftW + 8;
        tagsBoxW = panelW - leftW - 8;

        buttonList.add(new GuiButton(3, panelLeft + panelW - 78, listsLabelY - 3, 78, 18, "刷新截图"));
        buttonList.add(new GuiButton(1, panelLeft, actionY, 130, 20, "上传并保存草稿"));
        buttonList.add(new GuiButton(2, panelLeft + panelW - 90, actionY, 90, 20, "取消"));

        exportFromHand();
        if (shots.isEmpty()) {
            loadShots();
        }
        if (tags.isEmpty() && !loadingTags) {
            loadTags();
        }
    }

    // ==================== 手持导出 ====================

    private void exportFromHand() {
        snbt = null;
        exportError = null;
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        if (player == null) {
            exportError = "请先进入一个世界";
            return;
        }
        ItemStack held = player.getHeldItemMainhand();
        if (held == null || held.isEmpty()) {
            exportError = "手里没有东西 —— 请先拿着一张 LittleTiles 蓝图";
            return;
        }
        try {
            String text = StructureStringUtils.exportStructure(held);
            if (text == null || text.trim().isEmpty()) {
                exportError = "手里的物品不是 LittleTiles 蓝图";
                return;
            }
            snbt = text;
            if (fieldTitle.getText().isEmpty()) {
                String name = held.getDisplayName();
                fieldTitle.setText(name == null ? "" : name);
            }
        } catch (Throwable t) {
            exportError = "导出失败：" + t;
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
        shotScroll = 0;
        selectedShots.removeIf(f -> !f.isFile());
    }

    private int cellsPerRow() {
        return Math.max(1, (shotsBoxW - 4) / (CELL_W + CELL_GAP));
    }

    private int shotRowsVisible() {
        return Math.max(1, boxH / (CELL_H + CELL_GAP));
    }

    private int shotRowCount() {
        return (shots.size() + cellsPerRow() - 1) / Math.max(1, cellsPerRow());
    }

    private int maxShotScroll() {
        return Math.max(0, shotRowCount() - shotRowsVisible());
    }

    /** 鼠标落在第几张截图（-1 = 没命中） */
    private int shotIndexAt(int mouseX, int mouseY) {
        if (shotsBoxW <= 0 || mouseX < shotsBoxX || mouseX > shotsBoxX + shotsBoxW
                || mouseY < boxY || mouseY > boxY + boxH) {
            return -1;
        }
        int perRow = cellsPerRow();
        int col = (mouseX - shotsBoxX) / (CELL_W + CELL_GAP);
        int row = (mouseY - boxY) / (CELL_H + CELL_GAP) + shotScroll;
        if (col >= perRow) {
            return -1;
        }
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
                ApiClient.post(() -> {
                    loadingTags = false;
                    status = TextFormatting.RED + message;
                });
            }
        });
    }

    private int tagsVisible() {
        return Math.max(1, boxH / TAG_ROW_H);
    }

    private int maxTagScroll() {
        return Math.max(0, tags.size() - tagsVisible());
    }

    private int tagIndexAt(int mouseX, int mouseY) {
        if (tagsBoxW <= 0 || mouseX < tagsBoxX || mouseX > tagsBoxX + tagsBoxW
                || mouseY < boxY || mouseY > boxY + boxH) {
            return -1;
        }
        int index = tagScroll + (mouseY - boxY) / TAG_ROW_H;
        return index >= 0 && index < tags.size() ? index : -1;
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

        if (mx >= shotsBoxX && mx <= shotsBoxX + shotsBoxW && my >= boxY && my <= boxY + boxH) {
            shotScroll = Math.max(0, Math.min(shotScroll - Integer.signum(wheel), maxShotScroll()));
            return;
        }
        if (mx >= tagsBoxX && mx <= tagsBoxX + tagsBoxW && my >= boxY && my <= boxY + boxH) {
            tagScroll = Math.max(0, Math.min(tagScroll - Integer.signum(wheel), maxTagScroll()));
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 2) {
            mc.displayGuiScreen(parent);
            return;
        }
        if (button.id == 3) {
            loadShots();
            status = TextFormatting.GRAY + "截图已刷新（" + shots.size() + " 张）";
            return;
        }
        if (button.id != 1 || busy) {
            return;
        }
        startUpload();
    }

    private void startUpload() {
        if (snbt == null) {
            status = TextFormatting.RED + (exportError == null ? "没有可上传的结构" : exportError);
            return;
        }
        String title = fieldTitle.getText().trim();
        if (title.isEmpty()) {
            status = TextFormatting.RED + "标题不能为空";
            return;
        }
        busy = true;
        status = TextFormatting.YELLOW + "上传结构…";

        final String finalTitle = title;
        final String summaryText = summary.getText().trim();

        ApiClient.uploadSnbt(snbt, finalTitle, new ApiClient.Callback<Dtos.SnbtUpload>() {
            @Override
            public void ok(Dtos.SnbtUpload blob) {
                List<File> queue = new ArrayList<>(selectedShots);
                if (queue.isEmpty()) {
                    saveWork(blob, finalTitle, summaryText, new ArrayList<>());
                } else {
                    uploadNext(queue, 0, new ArrayList<>(), blob, finalTitle, summaryText);
                }
            }

            @Override
            public void fail(String message) {
                ApiClient.post(() -> {
                    busy = false;
                    status = TextFormatting.RED + message;
                });
            }
        });
    }

    private void uploadNext(final List<File> queue, final int index, final List<String> urls,
                            final Dtos.SnbtUpload blob, final String title, final String summaryText) {
        if (index >= queue.size()) {
            saveWork(blob, title, summaryText, urls);
            return;
        }
        setStatus(TextFormatting.YELLOW + "上传截图 " + (index + 1) + "/" + queue.size() + "…");
        ApiClient.uploadImage(queue.get(index), new ApiClient.Callback<String>() {
            @Override
            public void ok(String url) {
                urls.add(url);
                uploadNext(queue, index + 1, urls, blob, title, summaryText);
            }

            @Override
            public void fail(String message) {
                LtsModelShare.LOG.warn("截图上传失败：{}", message);
                uploadNext(queue, index + 1, urls, blob, title, summaryText);
            }
        });
    }

    private void saveWork(final Dtos.SnbtUpload blob, final String title, final String summaryText,
                          final List<String> imageUrls) {
        setStatus(TextFormatting.YELLOW + "保存作品…");
        Dtos.WorkSaveRequest req = new Dtos.WorkSaveRequest();
        req.kind = "single";
        req.title = title;
        req.summary = summaryText.isEmpty() ? null : summaryText;
        req.tagCodes = new ArrayList<>(tagCodes);
        req.images = new ArrayList<>(imageUrls);
        req.items = Collections.singletonList(
                new Dtos.WorkSaveRequest.SaveItem(blob.blobId, title, "1.12.2"));

        ApiClient.saveWork(req, new ApiClient.Callback<Long>() {
            @Override
            public void ok(Long workId) {
                ApiClient.post(() -> {
                    busy = false;
                    status = TextFormatting.GREEN + "已保存为草稿：作品 #" + workId
                            + "（截图 " + imageUrls.size() + " 张，标签 " + tagCodes.size() + " 个）"
                            + " —— 去网站「创作台」提交审核";
                });
            }

            @Override
            public void fail(String message) {
                ApiClient.post(() -> {
                    busy = false;
                    status = TextFormatting.RED + message;
                });
            }
        });
    }

    private void setStatus(String text) {
        ApiClient.post(() -> status = text);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
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
            } else if (selectedShots.size() >= MAX_SHOTS) {
                status = TextFormatting.RED + "最多选 " + MAX_SHOTS + " 张";
            } else {
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
        drawCenteredString(fontRenderer, "上传手持蓝图", cx, 22, 0xFFFFFF);

        String state = exportError != null ? TextFormatting.RED + exportError
                : snbt != null ? TextFormatting.GREEN + "已导出 " + snbt.length() + " 字符" : "";
        if (!state.isEmpty()) {
            drawCenteredString(fontRenderer, state, cx, 32, 0x55FF55);
        }

        drawString(fontRenderer, TextFormatting.GRAY + "标题", panelLeft, titleLabelY, 0xAAAAAA);
        fieldTitle.drawTextBox();

        drawString(fontRenderer, TextFormatting.GRAY + "简介（Enter 换行）", panelLeft, summaryLabelY, 0xAAAAAA);
        summary.draw(mouseX, mouseY);

        drawString(fontRenderer, TextFormatting.GRAY + "截图（点选，最多 " + MAX_SHOTS + "，已选 "
                + selectedShots.size() + "）", shotsBoxX, listsLabelY, 0xAAAAAA);
        drawString(fontRenderer, TextFormatting.GRAY + "标签（可多选"
                + (tags.isEmpty() ? "" : "，" + tags.size() + " 个") + "）", tagsBoxX, listsLabelY, 0xAAAAAA);

        // ---------- 左：截图网格（一行三张、无文件名、绿框选中、懒加载） ----------
        Gui.drawRect(shotsBoxX, boxY, shotsBoxX + shotsBoxW, boxY + boxH, 0x40000000);
        if (shots.isEmpty()) {
            drawString(fontRenderer, TextFormatting.DARK_GRAY + "（没有截图，游戏里按 F2）",
                    shotsBoxX + 4, boxY + 4, 0x777777);
        }
        int perRow = cellsPerRow();
        int firstRow = shotScroll;
        int lastRow = Math.min(shotRowCount(), shotScroll + shotRowsVisible());
        int hovered = shotIndexAt(mouseX, mouseY);

        for (int row = firstRow; row < lastRow; row++) {
            for (int col = 0; col < perRow; col++) {
                int index = row * perRow + col;
                if (index >= shots.size()) {
                    break;
                }
                File f = shots.get(index);
                int cellX = shotsBoxX + col * (CELL_W + CELL_GAP);
                int cellY = boxY + (row - firstRow) * (CELL_H + CELL_GAP);
                boolean on = selectedShots.contains(f);

                // 懒加载：只有循环到"当前可见行"时才请求这张图的贴图
                ImageDraw.drawCropped(ImageCache.fileUrl(f), cellX, cellY, CELL_W, CELL_H);

                if (on) {
                    drawFrame(cellX - 2, cellY - 2, CELL_W + 4, CELL_H + 4, 0xFF2ECC40);
                } else if (hovered == index) {
                    drawFrame(cellX, cellY, CELL_W, CELL_H, 0x90FFFFFF);
                }
            }
        }
        if (maxShotScroll() > 0) {
            drawScrollbar(shotsBoxX + shotsBoxW - 4, boxY, boxH, shotScroll, maxShotScroll(),
                    shotRowCount(), shotRowsVisible());
        }

        // ---------- 右：标签列表 ----------
        Gui.drawRect(tagsBoxX, boxY, tagsBoxX + tagsBoxW, boxY + boxH, 0x40000000);
        if (tags.isEmpty() && !loadingTags) {
            drawString(fontRenderer, TextFormatting.DARK_GRAY + "（没有标签）", tagsBoxX + 4, boxY + 4, 0x777777);
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
                    + tag.name, tagsBoxX + 4, rowY + 3, on ? 0x55FF55 : 0xCCCCCC);
        }
        if (maxTagScroll() > 0) {
            drawScrollbar(tagsBoxX + tagsBoxW - 4, boxY, boxH, tagScroll, maxTagScroll(),
                    tags.size(), tagsVisible());
        }

        if (!status.isEmpty()) {
            drawCenteredString(fontRenderer, status, cx, height - 20, 0xFFFFFF);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
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

    /** 极简多行输入框：Enter 换行、Backspace 删除、Tab 在标题/简介间切换 */
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
