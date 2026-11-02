package work.inception.ltsmodelshare.client;

import static work.inception.ltsmodelshare.client.Translations.tr;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import work.inception.ltsmodelshare.LtsModelShare;
import work.inception.ltsmodelshare.gui.GuiWorkDetail;
import work.inception.ltsmodelshare.network.MsgBlueprintChunk;
import work.inception.ltsmodelshare.network.Network;
import work.inception.ltsmodelshare.network.SnbtCodec;

import java.util.concurrent.atomic.AtomicInteger;

/** Client blueprint request with soft/hard timeout and clipboard fallback. */
public final class BlueprintClient {
    private static final long SOFT_TIMEOUT_MS = 20_000L;
    private static final long HARD_TIMEOUT_MS = 60_000L;
    private static final AtomicInteger TRANSFER_ID = new AtomicInteger(1);

    private static boolean waiting;
    private static boolean softWarned;
    private static long sentAt;
    private static String lastSnbt = "";
    private static String lastTitle = "";

    private BlueprintClient() { }

    public static void request(String snbt, String title) {
        lastSnbt = snbt == null ? "" : snbt;
        lastTitle = title == null ? "" : title;
        waiting = true;
        softWarned = false;
        sentAt = System.currentTimeMillis();
        if (Network.INSTANCE == null) {
            waiting = false;
            fallbackToClipboard(tr("ltsmodelshare.blueprint.channel_missing"));
            return;
        }
        byte[] packed = SnbtCodec.compress(lastSnbt);
        int total = Math.max(1, (packed.length + MsgBlueprintChunk.MAX_DATA - 1) / MsgBlueprintChunk.MAX_DATA);
        if (total > 64) {
            waiting = false;
            fallbackToClipboard(tr("ltsmodelshare.blueprint.compressed_large", packed.length));
            return;
        }
        int transferId = TRANSFER_ID.incrementAndGet();
        for (int i = 0; i < total; i++) {
            int from = i * MsgBlueprintChunk.MAX_DATA;
            int len = Math.min(MsgBlueprintChunk.MAX_DATA, packed.length - from);
            byte[] part = new byte[len];
            System.arraycopy(packed, from, part, 0, len);
            Network.INSTANCE.sendToServer(new MsgBlueprintChunk(transferId, i, total,
                    i == 0 ? lastTitle : "", part));
        }
        LtsModelShare.LOG.info("[联机] 已请求服务端生成蓝图（{} 字符 → 压缩 {} 字节 → {} 片）",
                lastSnbt.length(), packed.length, total);
    }

    /** Called on the client thread by the result packet. */
    public static void onResult(boolean ok, String text) {
        waiting = false;
        Component message = decode(text);
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof GuiWorkDetail detail) {
            detail.onServerBlueprint(ok, message.getString());
        }
    }

    private static Component decode(String text) {
        try {
            Component parsed = Component.Serializer.fromJson(text);
            if (parsed != null) return parsed;
        } catch (Exception ignored) {
        }
        return Component.literal(text == null ? "" : text);
    }

    /** Call every client tick. */
    public static void tick() {
        if (!waiting) return;
        long waited = System.currentTimeMillis() - sentAt;
        if (!softWarned && waited >= SOFT_TIMEOUT_MS) {
            softWarned = true;
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof GuiWorkDetail detail) {
                detail.onServerBlueprint(false, tr("ltsmodelshare.blueprint.processing"));
            }
            return;
        }
        if (waited >= HARD_TIMEOUT_MS) {
            waiting = false;
            fallbackToClipboard(tr("ltsmodelshare.blueprint.timeout"));
        }
    }

    private static void fallbackToClipboard(String reason) {
        boolean copied = false;
        try {
            java.awt.datatransfer.StringSelection selection = new java.awt.datatransfer.StringSelection(lastSnbt);
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
            copied = true;
        } catch (Throwable error) {
            LtsModelShare.LOG.warn("写剪贴板失败：{}", error.toString());
        }
        String text = copied
                ? tr("ltsmodelshare.blueprint.clipboard_ok", reason)
                : tr("ltsmodelshare.blueprint.clipboard_failed", reason);
        LtsModelShare.LOG.info("[联机] {}", text);
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof GuiWorkDetail detail) {
            detail.onServerBlueprint(false, text);
        }
    }
}
