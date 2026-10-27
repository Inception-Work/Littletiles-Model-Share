package work.inception.ltsmodelshare.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import work.inception.ltsmodelshare.LtsModelShare;
import work.inception.ltsmodelshare.gui.GuiWorkDetail;
import work.inception.ltsmodelshare.network.MsgBlueprintChunk;
import work.inception.ltsmodelshare.network.PacketHandler;
import work.inception.ltsmodelshare.network.SnbtCodec;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 客户端：请求服务端生成蓝图，并处理回执。
 *
 * <p>联机（专用服务端）时走这里；单人/局域网主机走 {@code GuiWorkDetail} 的本地路径。
 * 如果服务端没装本模组，包发出去没人应答 —— 6 秒后自动退回"复制 SNBT + /lt-import"
 * 的老办法，不至于让玩家干等。</p>
 */
public final class BlueprintClient {

    /** 等服务端回执的超时（毫秒） */
    /** 软超时：只提示"还在处理"，不抢剪贴板（大模型服务端解析可能要几十秒） */
    private static final long SOFT_TIMEOUT_MS = 20000L;
    /** 硬超时：到这一步才认为对方没装本模组，退回剪贴板方案 */
    private static final long HARD_TIMEOUT_MS = 60000L;
    private static final AtomicInteger TRANSFER_ID = new AtomicInteger(1);

    private static boolean waiting;
    private static boolean softWarned;
    private static long sentAt;
    private static String lastSnbt = "";
    private static String lastTitle = "";

    private BlueprintClient() {
    }

    /** 发出请求（由详情页在联机时调用） */
    public static void request(String snbt, String title) {
        lastSnbt = snbt == null ? "" : snbt;
        lastTitle = title == null ? "" : title;
        waiting = true;
        softWarned = false;
        sentAt = System.currentTimeMillis();
        if (PacketHandler.NET == null) {
            waiting = false;
            fallbackToClipboard("网络通道未初始化");
            return;
        }
        // gzip 后再切片：单包超过 32767 字节会被 Forge 直接拒绝
        byte[] packed = SnbtCodec.compress(lastSnbt);
        int total = Math.max(1, (packed.length + MsgBlueprintChunk.MAX_DATA - 1) / MsgBlueprintChunk.MAX_DATA);
        if (total > 64) {
            waiting = false;
            fallbackToClipboard("结构太大（压缩后 " + packed.length + " 字节），联机通道传不了");
            return;
        }
        int transferId = TRANSFER_ID.incrementAndGet();
        for (int i = 0; i < total; i++) {
            int from = i * MsgBlueprintChunk.MAX_DATA;
            int len = Math.min(MsgBlueprintChunk.MAX_DATA, packed.length - from);
            byte[] part = new byte[len];
            System.arraycopy(packed, from, part, 0, len);
            PacketHandler.NET.sendToServer(
                    new MsgBlueprintChunk(transferId, i, total, i == 0 ? lastTitle : "", part));
        }
        LtsModelShare.LOG.info("[联机] 已请求服务端生成蓝图（{} 字符 → 压缩 {} 字节 → {} 片）",
                lastSnbt.length(), packed.length, total);
    }

    /** 服务端回执 */
    public static void onResult(boolean ok, String text) {
        waiting = false;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen instanceof GuiWorkDetail) {
            ((GuiWorkDetail) mc.currentScreen).onServerBlueprint(ok, text);
            return;
        }
        if (work.inception.ltsmodelshare.DevEnv.isDev() && mc.player != null) {
            mc.player.sendMessage(new TextComponentString(
                    (ok ? TextFormatting.GREEN : TextFormatting.RED) + "[模型共享/调试] " + text));
        }
    }

    /** 每 tick 调一次：超时就退回剪贴板方案 */
    public static void tick() {
        if (!waiting) {
            return;
        }
        long waited = System.currentTimeMillis() - sentAt;
        // 第一段：只是慢（服务端要解析结构），提示一下别再点，但不抢剪贴板
        if (!softWarned && waited >= SOFT_TIMEOUT_MS) {
            softWarned = true;
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.currentScreen instanceof GuiWorkDetail) {
                ((GuiWorkDetail) mc.currentScreen)
                        .onServerBlueprint(false, "服务端还在处理（大模型解析会慢一些），请稍等…");
            }
            return;
        }
        // 第二段：真的没回音，才退回剪贴板
        if (waited >= HARD_TIMEOUT_MS) {
            waiting = false;
            fallbackToClipboard("服务端没有响应（对方可能没装本模组）");
        }
    }

    /** 老办法：SNBT 复制到剪贴板，让玩家用 LittleTiles 的导入框 */
    private static void fallbackToClipboard(String reason) {
        boolean copied = false;
        try {
            java.awt.datatransfer.StringSelection sel =
                    new java.awt.datatransfer.StringSelection(lastSnbt);
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, sel);
            copied = true;
        } catch (Throwable t) {
            LtsModelShare.LOG.warn("写剪贴板失败：{}", t.toString());
        }
        String text = copied
                ? reason + "；结构已复制到剪贴板，进 LittleTiles 的导入框 Paste + Import"
                : reason + "；而且写剪贴板也失败了，请重试";
        LtsModelShare.LOG.info("[联机] {}", text);
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen instanceof GuiWorkDetail) {
            ((GuiWorkDetail) mc.currentScreen).onServerBlueprint(false, text);
        } else if (work.inception.ltsmodelshare.DevEnv.isDev() && mc.player != null) {
            mc.player.sendMessage(new TextComponentString(
                    TextFormatting.YELLOW + "[模型共享/调试] " + text));
        }
    }
}
