package work.inception.ltsmodelshare.network;

import work.inception.ltsmodelshare.LtsModelShare;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * SNBT 的压缩/解压。
 *
 * <p>为什么要压：结构 SNBT 里全是对称的数字和重复键名，压缩比很夸张
 * （几十万字符 → 十几 KB），压完再切片才不容易撞上"单包 32767 字节"的限制。
 * （Forge 的 SimpleNetworkWrapper 不会替我们分片，超了直接
 * {@code Payload may not be larger than 32767 bytes}。）</p>
 */
public final class SnbtCodec {

    /** 解压后允许的最大字符数（防解压炸弹） */
    public static final int MAX_CHARS = 4 * 1024 * 1024;

    private SnbtCodec() {
    }

    public static byte[] compress(String text) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream(Math.max(64, text.length() / 8));
            try (GZIPOutputStream gz = new GZIPOutputStream(bos)) {
                gz.write(text.getBytes(StandardCharsets.UTF_8));
            }
            return bos.toByteArray();
        } catch (Exception e) {
            LtsModelShare.LOG.warn("压缩 SNBT 失败，退回原文：{}", e.toString());
            return text.getBytes(StandardCharsets.UTF_8);
        }
    }

    /**
     * 解压。为了兼容"压缩失败退回原文"的情况：先试 gzip，失败就当纯文本。
     */
    public static String decompress(byte[] data) {
        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(data);
            ByteArrayOutputStream bos = new ByteArrayOutputStream(data.length * 4);
            try (GZIPInputStream gz = new GZIPInputStream(bis)) {
                byte[] buf = new byte[8192];
                int n;
                int total = 0;
                while ((n = gz.read(buf)) > 0) {
                    total += n;
                    if (total > MAX_CHARS * 4) {
                        throw new IllegalStateException("结构解压后过大");
                    }
                    bos.write(buf, 0, n);
                }
            }
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception notGzip) {
            return new String(data, StandardCharsets.UTF_8);
        }
    }
}
