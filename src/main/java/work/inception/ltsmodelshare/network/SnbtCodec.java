package work.inception.ltsmodelshare.network;

import work.inception.ltsmodelshare.LtsModelShare;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Gzip helpers for structure payloads; keeps single packets small. */
public final class SnbtCodec {
    /** Maximum decompressed character count (zip-bomb guard). */
    public static final int MAX_CHARS = 4 * 1024 * 1024;

    private SnbtCodec() { }

    public static byte[] compress(String text) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, text.length() / 8));
            try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
                gzip.write(text.getBytes(StandardCharsets.UTF_8));
            }
            return out.toByteArray();
        } catch (Exception error) {
            LtsModelShare.LOG.warn("压缩 SNBT 失败，退回原文：{}", error.toString());
            return text.getBytes(StandardCharsets.UTF_8);
        }
    }

    public static String decompress(byte[] data) {
        try {
            ByteArrayInputStream input = new ByteArrayInputStream(data);
            ByteArrayOutputStream out = new ByteArrayOutputStream(data.length * 4);
            try (GZIPInputStream gzip = new GZIPInputStream(input)) {
                byte[] buffer = new byte[8192];
                int n;
                int total = 0;
                while ((n = gzip.read(buffer)) > 0) {
                    total += n;
                    if (total > MAX_CHARS * 4) {
                        throw new IllegalStateException("Decompressed structure exceeds size limit");
                    }
                    out.write(buffer, 0, n);
                }
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception notGzip) {
            return new String(data, StandardCharsets.UTF_8);
        }
    }
}
