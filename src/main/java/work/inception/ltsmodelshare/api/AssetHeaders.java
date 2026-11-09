package work.inception.ltsmodelshare.api;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;

/** Website source for CDN hotlink rules; contains no account credentials. */
public final class AssetHeaders {
    private AssetHeaders() { }

    public static void apply(HttpURLConnection connection, String siteUrl) {
        // CDN hotlink allowlist is the public website, even for a local development API.
        String source = V2Http.sameOrigin(connection.getURL(), url(siteUrl))
                ? referer(siteUrl) : "https://www.inception.work/";
        connection.setRequestProperty("Referer", source);
    }

    private static java.net.URL url(String siteUrl) {
        try { return new java.net.URL(siteUrl); }
        catch (java.net.MalformedURLException e) { throw new IllegalArgumentException("Invalid website URL"); }
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
