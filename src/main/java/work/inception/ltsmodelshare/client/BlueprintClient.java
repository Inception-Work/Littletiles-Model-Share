package work.inception.ltsmodelshare.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import work.inception.ltsmodelshare.LtsModelShare;
import work.inception.ltsmodelshare.gui.GuiWorkDetail;
import work.inception.ltsmodelshare.network.MsgBlueprintRequest;
import work.inception.ltsmodelshare.network.PacketHandler;

/**
 * 客户端：请求服务端生成蓝图，并处理回执。
 *
 * <p>联机（专用服务端）时走这里；单人/局域网主机走 {@code GuiWorkDetail} 的本地路径。
 * 如果服务端没装本模组，包发出去没人应答 —— 6 秒后自动退回"复制 SNBT + /lt-import"
 * 的老办法，不至于让玩家干等。</p>
 */
public final class BlueprintClient {

    /** 等服务端回执的超时（毫秒） */
    private static final long TIMEOUT_MS = 6000L;

    private static boolean waiting;
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
        sentAt = System.currentTimeMillis();
        if (PacketHandler.NET == null) {
            waiting = false;
            fallbackToClipboard("网络通道未初始化");
            return;
        }
        PacketHandler.NET.sendToServer(new MsgBlueprintRequest(lastSnbt, lastTitle));
        LtsModelShare.LOG.info("[联机] 已向服务端请求生成蓝图（{} 字符）", lastSnbt.length());
    }

    /** 服务端回执 */
    public static void onResult(boolean ok, String text) {
        waiting = false;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen instanceof GuiWorkDetail) {
            ((GuiWorkDetail) mc.currentScreen).onServerBlueprint(ok, text);
            return;
        }
        if (mc.player != null) {
            mc.player.sendMessage(new TextComponentString(
                    (ok ? TextFormatting.GREEN : TextFormatting.RED) + "[模型共享] " + text));
        }
    }

    /** 每 tick 调一次：超时就退回剪贴板方案 */
    public static void tick() {
        if (!waiting || System.currentTimeMillis() - sentAt < TIMEOUT_MS) {
            return;
        }
        waiting = false;
        fallbackToClipboard("服务端没有响应（对方可能没装本模组）");
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
        } else if (mc.player != null) {
            mc.player.sendMessage(new TextComponentString(
                    TextFormatting.YELLOW + "[模型共享] " + text));
        }
    }
}
