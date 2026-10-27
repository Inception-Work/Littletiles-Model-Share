package work.inception.ltsmodelshare.gui;

import com.creativemd.littletiles.common.util.converation.StructureStringUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import work.inception.ltsmodelshare.ModConfig;
import work.inception.ltsmodelshare.api.ApiClient;
import work.inception.ltsmodelshare.api.Dtos;

import java.io.IOException;
import java.util.Collections;

/**
 * 上传手持蓝图到站点（先存成草稿，审核在网站上点）。
 *
 * <p>流程：</p>
 * <ol>
 *   <li>手里必须拿着一张 LittleTiles 蓝图（{@code StructureStringUtils.exportStructure} 能导出）；</li>
 *   <li>导出 SNBT 文本 → {@code POST /api/studio/work/upload-snbt}（multipart，txt）；
 *       服务端算哈希、去重、生成白模，返回 blobId / grid / boxCount；</li>
 *   <li>填标题/简介 → {@code POST /api/studio/work/save} 存成作品（单个作品、一个条目）；</li>
 *   <li>告诉玩家作品编号，让他去网站提交审核。</li>
 * </ol>
 *
 * <p>为什么不直接提交审核：审核需要同意协议（consentId）等网页端交互，
 * 模组里硬做容易和站点规则漂移，所以到「草稿」为止，剩下的在网站上点最稳。</p>
 */
public class GuiUpload extends GuiScreen {

    private final GuiScreen parent;

    private GuiTextField fieldTitle;
    private GuiTextField fieldSummary;

    private String status = "";
    private boolean busy;

    /** 导出好的 SNBT 与统计（打开界面时就导出一次，方便显示能不能传） */
    private String snbt;
    private String exportError;

    public GuiUpload(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int cx = width / 2;
        int cy = height / 2;

        fieldTitle = new GuiTextField(0, fontRenderer, cx - 130, cy - 46, 260, 18);
        fieldTitle.setMaxStringLength(64);
        fieldTitle.setFocused(true);

        fieldSummary = new GuiTextField(1, fontRenderer, cx - 130, cy - 16, 260, 18);
        fieldSummary.setMaxStringLength(120);

        buttonList.add(new GuiButton(1, cx - 130, cy + 14, 124, 20, "上传并保存草稿"));
        buttonList.add(new GuiButton(2, cx + 6, cy + 14, 124, 20, "取消"));

        exportFromHand();
    }

    /** 从手上导出 SNBT（也顺便当作"这是不是蓝图"的校验） */
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
            String name = held.getDisplayName();
            if (fieldTitle.getText().isEmpty()) {
                fieldTitle.setText(name == null ? "" : name);
            }
        } catch (Throwable t) {
            exportError = "导出失败：" + t;
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 2) {
            mc.displayGuiScreen(parent);
            return;
        }
        if (button.id != 1 || busy) {
            return;
        }
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
        final String summary = fieldSummary.getText().trim();

        ApiClient.uploadSnbt(snbt, finalTitle, new ApiClient.Callback<Dtos.SnbtUpload>() {
            @Override
            public void ok(Dtos.SnbtUpload blob) {
                setStatus(TextFormatting.YELLOW + "保存作品…"
                        + (blob.dedup ? "（这份结构已存在，直接复用）" : ""));

                Dtos.WorkSaveRequest req = new Dtos.WorkSaveRequest();
                req.kind = "single";
                req.title = finalTitle;
                req.summary = summary.isEmpty() ? null : summary;
                req.tagCodes = Collections.emptyList();
                req.items = java.util.Collections.singletonList(
                        new Dtos.WorkSaveRequest.SaveItem(blob.blobId, finalTitle, "1.12.2"));

                ApiClient.saveWork(req, new ApiClient.Callback<Long>() {
                    @Override
                    public void ok(Long workId) {
                        ApiClient.post(() -> {
                            busy = false;
                            status = TextFormatting.GREEN + "已保存为草稿：作品 #" + workId
                                    + " —— 去网站「创作台」提交审核后才会公开";
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
        fieldTitle.textboxKeyTyped(typedChar, keyCode);
        fieldSummary.textboxKeyTyped(typedChar, keyCode);
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        fieldTitle.mouseClicked(mouseX, mouseY, mouseButton);
        fieldSummary.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int cx = width / 2;
        int cy = height / 2;

        drawCenteredString(fontRenderer, "上传手持蓝图", cx, cy - 84, 0xFFFFFF);
        drawCenteredString(fontRenderer, TextFormatting.GRAY
                + "手里的蓝图会导出成 SNBT 传到站点，先存成草稿", cx, cy - 72, 0xAAAAAA);

        // 导出状态
        if (exportError != null) {
            drawCenteredString(fontRenderer, TextFormatting.RED + exportError, cx, cy - 60, 0xFF5555);
        } else if (snbt != null) {
            drawCenteredString(fontRenderer, TextFormatting.GREEN
                    + "已导出结构：" + snbt.length() + " 字符", cx, cy - 60, 0x55FF55);
        }

        drawString(fontRenderer, "标题", cx - 130, cy - 58, 0xCCCCCC);
        drawString(fontRenderer, "简介（可空）", cx - 130, cy - 28, 0xCCCCCC);
        fieldTitle.drawTextBox();
        fieldSummary.drawTextBox();

        if (!status.isEmpty()) {
            drawCenteredString(fontRenderer, status, cx, cy + 42, 0xFFFFFF);
        }
        if (!ModConfig.get().loggedIn()) {
            drawCenteredString(fontRenderer, TextFormatting.RED + "未登录，先去面板登录", cx, cy + 54, 0xFF5555);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
