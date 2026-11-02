package work.inception.ltsmodelshare.client;

import static work.inception.ltsmodelshare.client.Translations.tr;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import work.inception.ltsmodelshare.LtsModelShare;
import work.inception.ltsmodelshare.gui.GuiWorkDetail;
import work.inception.ltsmodelshare.network.MsgBlueprintChunk;
import work.inception.ltsmodelshare.network.SnbtCodec;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

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
    private static BiConsumer<Boolean, String> resultHandler;

    private BlueprintClient() { }

    public static void request(String snbt, String title) {
        Minecraft mc = Minecraft.getInstance();
        request(snbt, title, mc.screen instanceof GuiWorkDetail detail ? detail::onServerBlueprint : null);
    }

    /** Bind the response to its initiating page, not whichever screen happens to be open.
     * A progress modal is a different Screen, so instanceof GuiWorkDetail loses every response.
     */
    public static void request(String snbt, String title, BiConsumer<Boolean, String> onResult) {
        if (waiting) {
            if (onResult != null) onResult.accept(false, tr("ltsmodelshare.blueprint.processing"));
            return;
        }
        resultHandler = onResult;
        lastSnbt = snbt == null ? "" : snbt;
        lastTitle = title == null ? "" : title;
        waiting = true;
        softWarned = false;
        sentAt = System.currentTimeMillis();
        try {
            byte[] packed = SnbtCodec.compress(lastSnbt);
            int total = Math.max(1, (packed.length + MsgBlueprintChunk.MAX_DATA - 1) / MsgBlueprintChunk.MAX_DATA);
            if (total > 64) {
                fallbackToClipboard(tr("ltsmodelshare.blueprint.compressed_large", packed.length));
                return;
            }
            int transferId = TRANSFER_ID.incrementAndGet();
            for (int i = 0; i < total; i++) {
                int from = i * MsgBlueprintChunk.MAX_DATA;
                int len = Math.min(MsgBlueprintChunk.MAX_DATA, packed.length - from);
                byte[] part = new byte[len];
                System.arraycopy(packed, from, part, 0, len);
                PacketDistributor.sendToServer(new MsgBlueprintChunk(transferId, i, total, i == 0 ? lastTitle : "", part));
            }
            LtsModelShare.LOG.info("[联机] 已请求服务端生成蓝图（{} 字符 → 压缩 {} 字节 → {} 片）",
                    lastSnbt.length(), packed.length, total);
        } catch (RuntimeException error) {
            fallbackToClipboard(error.getMessage() == null ? error.toString() : error.getMessage());
        }
    }

    /** Called on the client thread by the result packet. */
    public static void onResult(boolean ok, Component message) {
        if (!waiting) return;
        finish(ok, message == null ? "" : message.getString());
    }

    private static void finish(boolean ok, String text) {
        waiting = false;
        BiConsumer<Boolean, String> callback = resultHandler;
        resultHandler = null;
        if (callback != null) callback.accept(ok, text);
        lastSnbt = "";
        lastTitle = "";
    }

    /** Call every client tick. A soft timeout is not a terminal failure. */
    public static void tick() {
        if (!waiting) return;
        long waited = System.currentTimeMillis() - sentAt;
        if (!softWarned && waited >= SOFT_TIMEOUT_MS) {
            softWarned = true;
            LtsModelShare.LOG.info("[联机] {}", tr("ltsmodelshare.blueprint.processing"));
        }
        if (waited >= HARD_TIMEOUT_MS) fallbackToClipboard(tr("ltsmodelshare.blueprint.timeout"));
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
        String text = copied ? tr("ltsmodelshare.blueprint.clipboard_ok", reason)
                : tr("ltsmodelshare.blueprint.clipboard_failed", reason);
        LtsModelShare.LOG.info("[联机] {}", text);
        finish(false, text);
    }
}
