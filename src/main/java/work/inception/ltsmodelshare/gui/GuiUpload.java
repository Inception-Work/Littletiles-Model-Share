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
import work.inception.ltsmodelshare.ModConfig;
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
import java.util.List;

/**
 * 上传手持蓝图到站点（存成草稿，审核在网站上点）。
 *
 * <pre>
 *  上传手持蓝图
 *  已导出结构：6154 字符
 *  标题  [_____________________________]
 *  简介  ┌─────────────────────────────┐   ← 多行，Enter 换行
 *        │                             │
 *        └─────────────────────────────┘
 *  截图（点选，最多 6 张）   [刷新列表]
 *  ┌─────────────────────────────────────┐
 *  │ ☑ 2026-10-27_18.30.12.png   [缩略图]│
 *  │ ☐ 2026-10-27_18.28.03.png           │
 *  └─────────────────────────────────────┘
 *  [上传并保存草稿]  [取消]
 * </pre>
 *
 * <p>截图来自游戏自己的 {@code screenshots/} 目录：列出文件名（带缩略图），
 * 玩家自己点选要传的，然后一次性上传并把地址放进 save 的 images 里。</p>
 */
public class GuiUpload extends GuiScreen {

    private static final int MAX_SHOTS = 6;
    private static final int SHOT_ROW_H = 22;
    private static final int THUMB = 18;

    private final GuiScreen parent;

    private GuiTextField fieldTitle;
    private MultiLineText summary;
    private final List<String> summaryLines = new ArrayList<>();

    private String status = "";
    private boolean busy;

    private String snbt;
    private String exportError;

    // 截图
    private final List<File> shots = new ArrayList<>();
    private final List<File> selectedShots = new ArrayList<>();
    private int shotScroll;

    // 布局
    private int panelLeft;
    private int panelW;
    private int titleLabelY;
    private int summaryLabelY;
    private int summaryBoxX;
    private int summaryBoxY;
    private int summaryBoxW;
    private int summaryBoxH;
    private int shotsLabelY;
    private int shotsBoxX;
    private int shotsBoxY;
    private int shotsBoxW;
    private int shotsBoxH;
    private int actionY;

    public GuiUpload(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int cx = width / 2;
        panelW = Math.min(360, width - 24);
        panelLeft = cx - panelW / 2;

        int y = 36;
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
        summaryBoxH = 3 * 10 + 8;
        if (summary == null) {
            summary = new MultiLineText();
        }
        summary.setBox(summaryBoxX, summaryBoxY, summaryBoxW, summaryBoxH);
        y += summaryBoxH + 4;

        shotsLabelY = y;
        buttonList.add(new GuiButton(3, panelLeft + panelW - 78, y - 3, 78, 18, "刷新截图"));
        y += 12;
        shotsBoxX = panelLeft;
        shotsBoxY = y;
        shotsBoxW = panelW;

        actionY = height - 30;
        shotsBoxH = Math.max(SHOT_ROW_H, actionY - 34 - shotsBoxY);

        buttonList.add(new GuiButton(1, panelLeft, actionY, 130, 20, "上传并保存草稿"));
        buttonList.add(new GuiButton(2, panelLeft + panelW - 90, actionY, 90, 20, "取消"));

        exportFromHand();
        if (shots.isEmpty()) {
            loadShots();
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

    // ==================== 截图列表 ====================

    /** 游戏截图目录：<gameDir>/screenshots */
    private File screenshotDir() {
        return new File(Minecraft.getMinecraft().gameDir, "screenshots");
    }

    private void loadShots() {
        shots.clear();
        File dir = screenshotDir();
        File[] files = dir.listFiles();
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
        // 清掉已经不存在的选择
        selectedShots.removeIf(f -> !f.isFile());
    }

    private int visibleShotRows() {
        return Math.max(1, shotsBoxH / SHOT_ROW_H);
    }

    private int maxShotScroll() {
        return Math.max(0, shots.size() - visibleShotRows());
    }

    private int shotIndexAt(int mouseX, int mouseY) {
        if (mouseX < shotsBoxX || mouseX > shotsBoxX + shotsBoxW
                || mouseY < shotsBoxY || mouseY > shotsBoxY + shotsBoxH) {
            return -1;
        }
        int index = shotScroll + (mouseY - shotsBoxY) / SHOT_ROW_H;
        return index >= 0 && index < shots.size() ? index : -1;
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
        if (mx >= shotsBoxX && mx <= shotsBoxX + shotsBoxW && my >= shotsBoxY && my <= shotsBoxY + shotsBoxH) {
            shotScroll = Math.max(0, Math.min(shotScroll - Integer.signum(wheel), maxShotScroll()));
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
            status = TextFormatting.GRAY + "截图列表已刷新（共 " + shots.size() + " 张）";
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
                setStatus(TextFormatting.YELLOW + "上传截图 0/" + selectedShots.size() + "…");
                uploadImagesThenSave(blob, finalTitle, summaryText);
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

    /** 依次上传选中的截图（拿到地址），最后统一保存作品 */
    private void uploadImagesThenSave(final Dtos.SnbtUpload blob, final String title, final String summaryText) {
        final List<String> urls = Collections.synchronizedList(new ArrayList<String>());
        final List<File> queue = new ArrayList<>(selectedShots);

        if (queue.isEmpty()) {
            saveWork(blob, title, summaryText, urls);
            return;
        }

        uploadNext(queue, 0, urls, blob, title, summaryText);
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
                // 截图失败不拦整体：记下来，继续保存作品
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
        req.tagCodes = Collections.emptyList();
        req.images = new ArrayList<>(imageUrls);
        req.items = Collections.singletonList(
                new Dtos.WorkSaveRequest.SaveItem(blob.blobId, title, "1.12.2"));

        ApiClient.saveWork(req, new ApiClient.Callback<Long>() {
            @Override
            public void ok(Long workId) {
                ApiClient.post(() -> {
                    busy = false;
                    status = TextFormatting.GREEN + "已保存为草稿：作品 #" + workId
                            + "（截图 " + imageUrls.size() + " 张）—— 去网站「创作台」提交审核后才会公开";
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
            if (keyCode == 15) {          // Tab：跳到简介
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
        int index = shotIndexAt(mouseX, mouseY);
        if (index >= 0) {
            File f = shots.get(index);
            if (selectedShots.contains(f)) {
                selectedShots.remove(f);
            } else if (selectedShots.size() >= MAX_SHOTS) {
                status = TextFormatting.RED + "最多选 " + MAX_SHOTS + " 张";
            } else {
                selectedShots.add(f);
            }
        }
    }

    // ==================== 绘制 ====================

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int cx = width / 2;

        Gui.drawRect(panelLeft - 10, 20, panelLeft + panelW + 10, height - 6, 0x70000000);

        drawCenteredString(fontRenderer, "上传手持蓝图", cx, 24, 0xFFFFFF);

        // 导出状态
        if (exportError != null) {
            drawCenteredString(fontRenderer, TextFormatting.RED + exportError, cx, 34, 0xFF5555);
        } else if (snbt != null) {
            drawCenteredString(fontRenderer, TextFormatting.GREEN + "已导出结构：" + snbt.length() + " 字符",
                    cx, 34, 0x55FF55);
        }

        drawString(fontRenderer, TextFormatting.GRAY + "标题", panelLeft, titleLabelY, 0xAAAAAA);
        fieldTitle.drawTextBox();

        drawString(fontRenderer, TextFormatting.GRAY + "简介（可多行，Enter 换行）",
                panelLeft, summaryLabelY, 0xAAAAAA);
        summary.draw(mouseX, mouseY);

        drawString(fontRenderer, TextFormatting.GRAY + "截图（点选，最多 " + MAX_SHOTS + " 张，已选 "
                + selectedShots.size() + "）", panelLeft, shotsLabelY, 0xAAAAAA);

        // 截图列表
        Gui.drawRect(shotsBoxX, shotsBoxY, shotsBoxX + shotsBoxW, shotsBoxY + shotsBoxH, 0x40000000);
        if (shots.isEmpty()) {
            drawString(fontRenderer, TextFormatting.DARK_GRAY
                            + "（screenshots 目录里没有图片 —— 游戏里按 F2 截图）",
                    shotsBoxX + 4, shotsBoxY + 4, 0x777777);
        }
        for (int row = 0; row < visibleShotRows(); row++) {
            int index = shotScroll + row;
            if (index >= shots.size()) {
                break;
            }
            File f = shots.get(index);
            int rowY = shotsBoxY + row * SHOT_ROW_H;
            boolean on = selectedShots.contains(f);
            boolean hover = shotIndexAt(mouseX, mouseY) == index;
            if (hover) {
                Gui.drawRect(shotsBoxX + 1, rowY, shotsBoxX + shotsBoxW - 5, rowY + SHOT_ROW_H - 1, 0x40FFFFFF);
            }

            // 勾选 + 缩略图 + 文件名
            drawString(fontRenderer, (on ? TextFormatting.GREEN + "☑ " : TextFormatting.GRAY + "☐ "),
                    shotsBoxX + 3, rowY + 6, on ? 0x55FF55 : 0xAAAAAA);
            ImageDraw.drawCropped(ImageCache.fileUrl(f), shotsBoxX + 14, rowY + 2, THUMB, THUMB);
            drawString(fontRenderer, trim(f.getName(), shotsBoxW - 40 - THUMB),
                    shotsBoxX + 18 + THUMB, rowY + 6, on ? 0xFFFFFF : 0xCCCCCC);
        }
        if (maxShotScroll() > 0) {
            int barX = shotsBoxX + shotsBoxW - 4;
            Gui.drawRect(barX, shotsBoxY, barX + 3, shotsBoxY + shotsBoxH, 0x40000000);
            int thumbH = Math.max(8, shotsBoxH * visibleShotRows() / Math.max(1, shots.size()));
            int thumbY = shotsBoxY + (shotsBoxH - thumbH) * shotScroll / maxShotScroll();
            Gui.drawRect(barX, thumbY, barX + 3, thumbY + thumbH, 0xAAFFFFFF);
        }

        if (!status.isEmpty()) {
            drawCenteredString(fontRenderer, status, cx, height - 20, 0xFFFFFF);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
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

    // ==================== 多行文本框 ====================

    /**
     * 极简多行输入框：支持 Enter 换行、Backspace 删字符、粘贴（Ctrl+V）。
     *
     * <p>MC 的 {@code GuiTextField} 是单行的，简介需要换行，所以这里自己存字符串；
     * 显示时用 {@link TextUtil} 折行，只画最后几行（滚动跟随光标）。</p>
     */
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
            if (keyCode == 28) {                 // Enter：换行
                text.append('\n');
                return;
            }
            if (keyCode == 14) {                 // Backspace
                if (text.length() > 0) {
                    text.setLength(text.length() - 1);
                }
                return;
            }
            if (keyCode == 15) {                 // Tab：回到标题
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
            // 光标（最后一行末尾画一条竖线）
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
