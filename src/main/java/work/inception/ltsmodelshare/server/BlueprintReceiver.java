package work.inception.ltsmodelshare.server;

import net.minecraft.entity.player.EntityPlayerMP;
import work.inception.ltsmodelshare.LtsModelShare;
import work.inception.ltsmodelshare.network.MsgBlueprintChunk;
import work.inception.ltsmodelshare.network.SnbtCodec;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * 服务端：把分片拼回完整 SNBT，再交给 {@link BlueprintServer} 生成蓝图。
 *
 * <p>服务端主线程单线程访问，所以用普通 HashMap 就够；每条传输带时间戳，
 * 超过 2 分钟没凑齐就丢掉（客户端断线/发包失败时不会永久占内存）。</p>
 */
public final class BlueprintReceiver {

    /** 一次传输的存活时间 */
    private static final long TIMEOUT_MS = 2 * 60 * 1000L;
    /** 一次传输允许的最大分片数（30KB × 64 ≈ 1.9MB） */
    private static final int MAX_CHUNKS = 64;

    private static final Map<String, Transfer> ACTIVE = new HashMap<>();

    private BlueprintReceiver() {
    }

    public static void accept(EntityPlayerMP player, MsgBlueprintChunk msg) {
        if (player == null || msg == null) {
            return;
        }
        evictStale();

        String key = player.getUniqueID() + "#" + msg.transferId();
        if (msg.total() <= 0 || msg.total() > MAX_CHUNKS || msg.index() < 0 || msg.index() >= msg.total()) {
            LtsModelShare.LOG.warn("收到非法分片：total={} index={}", msg.total(), msg.index());
            return;
        }

        Transfer t = ACTIVE.computeIfAbsent(key, k -> new Transfer(msg.total()));
        if (t.total != msg.total()) {
            // 同一 transferId 的分片总数不一致 → 当新传输处理
            t = new Transfer(msg.total());
            ACTIVE.put(key, t);
        }
        t.title = msg.title().isEmpty() ? t.title : msg.title();
        t.parts[msg.index()] = msg.data();
        t.received++;

        if (t.received < t.total) {
            return;
        }
        ACTIVE.remove(key);

        // 拼起来 → 解压 → 生成
        try {
            int size = 0;
            for (byte[] p : t.parts) {
                if (p == null) {
                    BlueprintServer.give(player, null, t.title);
                    return;
                }
                size += p.length;
            }
            byte[] all = new byte[size];
            int off = 0;
            for (byte[] p : t.parts) {
                System.arraycopy(p, 0, all, off, p.length);
                off += p.length;
            }
            String snbt = SnbtCodec.decompress(all);
            LtsModelShare.LOG.info("收到结构：player={} 分片={} 压缩后={}B 解压后={} 字符",
                    player.getName(), t.total, size, snbt.length());
            BlueprintServer.give(player, snbt, t.title);
        } catch (Throwable e) {
            LtsModelShare.LOG.warn("拼装结构失败：{}", e.toString());
            BlueprintServer.give(player, null, t.title);
        }
    }

    /** 清理超时 / 玩家已下线的半成品传输 */
    private static void evictStale() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Transfer>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Transfer> e = it.next();
            if (now - e.getValue().startedAt > TIMEOUT_MS) {
                it.remove();
            }
        }
    }

    private static final class Transfer {
        final byte[][] parts;
        final int total;
        final long startedAt = System.currentTimeMillis();
        int received;
        String title = "";

        Transfer(int total) {
            this.total = total;
            this.parts = new byte[total][];
        }
    }

    /** 玩家下线时丢掉他的半成品（由 BlueprintServer 之外的钩子调用也可以不调，靠超时兜底） */
    public static void drop(UUID playerId) {
        ACTIVE.keySet().removeIf(k -> k.startsWith(playerId.toString() + "#"));
    }
}
