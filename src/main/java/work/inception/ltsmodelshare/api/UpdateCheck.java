package work.inception.ltsmodelshare.api;

import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/** Public release checks never carry account credentials. Java 8 compatible. */
public final class UpdateCheck {
    public static final class Release {
        public final String version, changelog;
        Release(String version, String changelog) { this.version=version; this.changelog=changelog; }
    }
    private static final Properties BUILD = new Properties();
    static {
        try (InputStream input=UpdateCheck.class.getResourceAsStream("/lts-update.properties")) {
            if (input!=null) BUILD.load(input);
        } catch (IOException ignored) { }
    }
    public static String currentVersion() { return BUILD.getProperty("version", "1.0.0"); }
    public static String channel() { return BUILD.getProperty("channel", "stable"); }
    private UpdateCheck() { }
    private static String text(JsonObject object,String key) {
        JsonElement value=object.get(key); return value==null||value.isJsonNull()?"":value.getAsString();
    }
    public static int compare(String left,String right) {
        String[] a=left.replaceFirst("^[vV]", "").split("\\+",2)[0].split("-",2);
        String[] b=right.replaceFirst("^[vV]", "").split("\\+",2)[0].split("-",2);
        String[] x=a[0].split("\\."), y=b[0].split("\\.");
        for(int i=0;i<Math.max(x.length,y.length);i++) {
            java.math.BigInteger n=new java.math.BigInteger(i<x.length?x[i]:"0");
            java.math.BigInteger m=new java.math.BigInteger(i<y.length?y[i]:"0");
            int c=n.compareTo(m); if(c!=0)return c;
        }
        if(a.length!=b.length)return a.length==1?1:-1;
        if(a.length==1)return 0;
        x=a[1].split("\\.");y=b[1].split("\\.");
        for(int i=0;i<Math.min(x.length,y.length);i++) {
            boolean nx=x[i].matches("[0-9]+"),ny=y[i].matches("[0-9]+");
            int c=nx&&ny?new java.math.BigInteger(x[i]).compareTo(new java.math.BigInteger(y[i]))
                    :nx!=ny?(nx?-1:1):x[i].compareTo(y[i]);
            if(c!=0)return c;
        }
        return Integer.compare(x.length,y.length);
    }
    public static Release select(String json,String version,String channel,String loader,String game,String locale) {
        JsonObject envelope=new JsonParser().parse(json).getAsJsonObject();
        if(!envelope.has("success")||!envelope.get("success").getAsBoolean())return null;
        JsonArray items=envelope.getAsJsonObject("payload").getAsJsonArray("items");
        Release newest=null;
        for(JsonElement item:items) {
            JsonObject release=item.getAsJsonObject();
            if(!"model_share".equals(text(release,"product"))||!channel.equals(text(release,"channel"))
                    ||!loader.equals(text(release,"loader"))||!game.equals(text(release,"gameVersion")))continue;
            String candidate=text(release,"version");
            try { if(compare(candidate,version)<=0||(newest!=null&&compare(candidate,newest.version)<=0))continue; }
            catch(RuntimeException invalidVersion) { continue; }
            String changelog=text(release,"changelog");
            JsonElement translations=release.get("changelogI18n");
            if(translations!=null&&translations.isJsonObject()) {
                String translated=text(translations.getAsJsonObject(),locale);
                if(!translated.trim().isEmpty())changelog=translated;
            }
            newest=new Release(candidate,changelog.replaceAll("<[^>]*>", ""));
        }
        return newest;
    }
    public static String downloadPage(String base) throws IOException {
        try {
            URI uri=new URI(base);
            if(!("https".equals(uri.getScheme())||"http".equals(uri.getScheme()))||uri.getHost()==null||uri.getUserInfo()!=null)
                throw new IOException("Invalid website URL");
            return base.replaceAll("/+$", "")+"/lt-share-mod";
        } catch(URISyntaxException invalid) { throw new IOException(invalid); }
    }
    public static Release check(String base,String loader,String game,String locale) throws IOException {
        downloadPage(base);
        String query="product=model_share&channel="+channel()+"&loader="+loader+"&gameVersion="+game;
        HttpURLConnection connection=(HttpURLConnection)new URL(base.replaceAll("/+$", "")+"/api/v2/public/releases?"+query).openConnection();
        connection.setConnectTimeout(15000);connection.setReadTimeout(15000);connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept","application/json");
        connection.setRequestProperty("User-Agent","LittleTilesModelShare-update/"+currentVersion());
        try {
            if(connection.getResponseCode()!=200)throw new IOException("Update HTTP "+connection.getResponseCode());
            ByteArrayOutputStream output=new ByteArrayOutputStream();
            try(InputStream input=connection.getInputStream()) {
                byte[] bytes=new byte[8192];int length;
                while((length=input.read(bytes))!=-1) {
                    if(output.size()+length>2*1024*1024)throw new IOException("Release response too large");
                    output.write(bytes,0,length);
                }
            }
            return select(new String(output.toByteArray(),StandardCharsets.UTF_8),currentVersion(),channel(),loader,game,locale);
        } finally { connection.disconnect(); }
    }
}
