package work.inception.ltsmodelshare.api;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/** Bounded HTTP transport. Redirects are explicit; account credentials stay on same-origin V2 APIs. */
public final class V2Http {
    interface Connections { HttpURLConnection open(URL url) throws IOException; }
    private final Connections connections;
    public V2Http() { this(url -> (HttpURLConnection) url.openConnection()); }
    V2Http(Connections connections) { this.connections = connections; }

    public static final class Response {
        public final int status;
        public final byte[] bytes;
        public final URL finalUrl;
        Response(int status, byte[] bytes, URL finalUrl) { this.status = status; this.bytes = bytes; this.finalUrl = finalUrl; }
        public String text() { return new String(bytes, StandardCharsets.UTF_8); }
    }
    public static URL resolve(String base, String path) throws IOException {
        URL u = new URL(new URL(base.endsWith("/") ? base : base + "/"), path);
        if (!("https".equalsIgnoreCase(u.getProtocol()) || "http".equalsIgnoreCase(u.getProtocol()))
                || u.getUserInfo() != null) throw new IOException("V2_INVALID_ASSET_URL");
        return u;
    }
    public static boolean sameOrigin(URL a, URL b) {
        int ap = a.getPort() < 0 ? a.getDefaultPort() : a.getPort();
        int bp = b.getPort() < 0 ? b.getDefaultPort() : b.getPort();
        return a.getProtocol().equalsIgnoreCase(b.getProtocol()) && a.getHost().equalsIgnoreCase(b.getHost()) && ap == bp;
    }
    public static boolean credentialTarget(String base, URL url) throws IOException {
        return sameOrigin(resolve(base, "/"), url) && url.getPath().startsWith("/api/v2/");
    }
    public Response request(String base, String path, String method, byte[] body, String contentType,
                            String token, int timeout, long maxBytes) throws IOException {
        return request(base, path, method, body, contentType, token, timeout, maxBytes, null);
    }
    public Response request(String base, String path, String method, byte[] body, String contentType,
                            String token, int timeout, long maxBytes, String locale) throws IOException {
        URL current = resolve(base, path);
        for (int redirects = 0; redirects <= 5; redirects++) {
            HttpURLConnection c = connections.open(current);
            try {
                c.setRequestMethod(method); c.setInstanceFollowRedirects(false);
                c.setConnectTimeout(15000); c.setReadTimeout(timeout);
                c.setRequestProperty("Accept", "application/json, */*");
                c.setRequestProperty("User-Agent", "ltsmodelshare-forge-1.12.2/v2");
                AssetHeaders.apply(c, base);
                if (locale != null && locale.matches("[A-Za-z-]{2,16}"))
                    c.setRequestProperty("Accept-Language", locale);
                if (token != null && !token.isEmpty() && credentialTarget(base, current))
                    c.setRequestProperty("Authorization", "Bearer " + token);
                if (body != null) {
                    c.setDoOutput(true); c.setRequestProperty("Content-Type", contentType);
                    try (OutputStream out = c.getOutputStream()) { out.write(body); }
                }
                int status = c.getResponseCode();
                if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                    // Never replay credentials or multipart/JSON writes to another route.
                    if (!"GET".equals(method) || body != null) throw new IOException("V2_WRITE_REDIRECT_REFUSED");
                    String location = c.getHeaderField("Location");
                    if (location == null) throw new IOException("V2_INVALID_REDIRECT");
                    URL next = resolve(current.toExternalForm(), location);
                    if ("https".equalsIgnoreCase(current.getProtocol()) && !"https".equalsIgnoreCase(next.getProtocol()))
                        throw new IOException("V2_INSECURE_REDIRECT");
                    // Share capabilities are as sensitive as Bearer credentials.
                    if (!sameOrigin(current, next) && next.getQuery() != null
                            && (next.getQuery().contains("access=") || next.getQuery().contains("media=")))
                        throw new IOException("V2_CAPABILITY_REDIRECT_REFUSED");
                    current = next;
                    continue;
                }
                long limit = status / 100 == 2 ? maxBytes : Math.min(maxBytes, 1024L * 1024);
                if (c.getContentLengthLong() > limit) throw new IOException("V2_RESPONSE_TOO_LARGE");
                InputStream stream = status / 100 == 2 ? c.getInputStream() : c.getErrorStream();
                return new Response(status, read(stream, limit), current);
            } finally { c.disconnect(); }
        }
        throw new IOException("V2_TOO_MANY_REDIRECTS");
    }
    private static byte[] read(InputStream in, long limit) throws IOException {
        if (in == null) return new byte[0];
        try (InputStream stream = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int n;
            while ((n = stream.read(buffer)) != -1) {
                if (out.size() + (long)n > limit) throw new IOException("V2_RESPONSE_TOO_LARGE");
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        }
    }
}
