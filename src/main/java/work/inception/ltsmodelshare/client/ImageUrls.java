package work.inception.ltsmodelshare.client;

import java.net.URI;

/**
 * DogeCloud image suffixes: {@code /thumb} (<=480px) for cards, {@code /preview} (<=1600px)
 * for large covers. Only applied to our own domains.
 */
public final class ImageUrls {
    private ImageUrls() { }

    public static String thumb(String url) {
        return variant(url, "thumb");
    }

    public static String preview(String url) {
        return variant(url, "preview");
    }

    private static String variant(String url, String suffix) {
        if (url == null || url.trim().isEmpty()) return url;
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null) return url;
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) return url;
            String lower = host.toLowerCase(java.util.Locale.ROOT);
            if (!(lower.equals("inception.work") || lower.endsWith(".inception.work"))) return url;
            String path = uri.getRawPath();
            if (path == null || path.isEmpty() || path.startsWith("/api/")) return url;
            if (path.endsWith("/thumb") || path.endsWith("/preview")) return url;
            String processed = scheme + "://" + uri.getRawAuthority() + path + "/" + suffix;
            String query = uri.getRawQuery();
            return query == null || query.isEmpty() ? processed : processed + "?" + query;
        } catch (Exception ignored) {
            return url;
        }
    }
}
