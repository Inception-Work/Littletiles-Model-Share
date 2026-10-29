package work.inception.ltsmodelshare.api;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;

/** Website source for CDN hotlink rules; contains no account credentials. */
public final class AssetHeaders {
    private AssetHeaders() { }

    public static void apply(HttpURLConnection connection, String siteUrl) {
        connection.setRequestProperty("Referer", referer(siteUrl));
    }

    public static String referer(String siteUrl) {
        try {
            URI site = new URI(siteUrl.trim());
            if (!("https".equalsIgnoreCase(site.getScheme()) || "http".equalsIgnoreCase(site.getScheme()))
                    || site.getHost() == null) {
                throw new IllegalArgumentException("Invalid website URL");
            }
            return new URI(site.getScheme(), null, site.getHost(), site.getPort(), "/", null, null).toASCIIString();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid website URL", e);
        }
    }
}
