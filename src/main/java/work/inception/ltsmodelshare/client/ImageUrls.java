package work.inception.ltsmodelshare.client;

import java.net.URI;

/** Choose a bounded CDN variant through the same authorized resource endpoint. */
public final class ImageUrls {
    private ImageUrls() { }
    public static String thumb(String url) { return variant(url,"thumb",480); }
    public static String preview(String url) { return variant(url,"preview",1600); }
    private static String variant(String url,String suffix,int width) {
        if(url==null||url.trim().isEmpty())return url;
        try {
            URI uri=URI.create(url);
            String scheme=uri.getScheme(),host=uri.getHost(),path=uri.getRawPath();
            if(uri.getRawUserInfo()!=null)return url;
            if(scheme!=null&&!"https".equalsIgnoreCase(scheme)&&!"http".equalsIgnoreCase(scheme))return url;
            if(host!=null&&!ownHost(host))return url;
            if(path==null||path.isEmpty())return url;
            if(path.matches("/api/v2/resources/[1-9][0-9]*/(?:content|thumb)")) {
                StringBuilder query=new StringBuilder("w="+width);
                if(uri.getRawQuery()!=null)for(String part:uri.getRawQuery().split("&")) {
                    if(part.isEmpty())continue;
                    String key=part.split("=",2)[0];
                    if("w".equals(java.net.URLDecoder.decode(key,"UTF-8")))continue;
                    query.append('&').append(part);
                }
                return prefix(uri)+path.replaceFirst("/(?:content|thumb)$","/thumb")+"?"+query+fragment(uri);
            }
            if(host==null||path.startsWith("/api/"))return url;
            if(path.endsWith("/thumb")||path.endsWith("/preview"))return url;
            return prefix(uri)+path+"/"+suffix+(uri.getRawQuery()==null?"":"?"+uri.getRawQuery())+fragment(uri);
        } catch(Exception invalid) {return url;}
    }
    private static boolean ownHost(String host) {
        String lower=host.toLowerCase(java.util.Locale.ROOT);
        return lower.equals("inception.work")||lower.endsWith(".inception.work");
    }
    private static String prefix(URI uri) {
        if(uri.getRawAuthority()==null)return "";
        return (uri.getScheme()==null?"":uri.getScheme()+":")+"//"+uri.getRawAuthority();
    }
    private static String fragment(URI uri) {return uri.getRawFragment()==null?"":"#"+uri.getRawFragment();}
}
