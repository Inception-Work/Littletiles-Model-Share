package work.inception.ltsmodelshare.api;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Java 21 transport: bounded bodies, explicit redirects and origin-scoped credentials. */
public final class V2Transport {
    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
            .version(HttpClient.Version.HTTP_2).followRedirects(HttpClient.Redirect.NEVER).build();
    public record Response(int status, byte[] bytes) {
        public String text() { return new String(bytes, StandardCharsets.UTF_8); }
    }
    private V2Transport() {}
    static URI resolve(String base,String path) throws IOException {
        try {
            URI uri=URI.create(base.endsWith("/")?base:base+"/").resolve(path).normalize();
            if(!("https".equalsIgnoreCase(uri.getScheme())||"http".equalsIgnoreCase(uri.getScheme()))
                    ||uri.getHost()==null||uri.getUserInfo()!=null)throw new IllegalArgumentException();
            return uri;
        }catch(IllegalArgumentException e){throw new IOException("V2_BAD_URL");}
    }
    static boolean sameOrigin(URI a,URI b){
        return a.getScheme().equalsIgnoreCase(b.getScheme())&&a.getHost().equalsIgnoreCase(b.getHost())
                &&port(a)==port(b);
    }
    static int port(URI u){return u.getPort()<0?("https".equalsIgnoreCase(u.getScheme())?443:80):u.getPort();}
    public static Response request(String base,String path,String method,byte[] body,String contentType,
                                   String token,long limit) throws IOException {
        URI origin=resolve(base,"/"),current=resolve(base,path);
        for(int hop=0;hop<=5;hop++){
            HttpRequest.Builder b=HttpRequest.newBuilder(current).timeout(Duration.ofSeconds(120))
                    .header("Accept","application/json, */*").header("User-Agent","ltsmodelshare-forge-1.20.2/v2")
                    .header("Referer",sameOrigin(origin,current)?AssetHeaders.referer(base):"https://www.inception.work/");
            if(token!=null&&!token.isEmpty()&&sameOrigin(origin,current)&&current.getPath().startsWith("/api/v2/"))
                b.header("Authorization","Bearer "+token);
            if(body!=null)b.header("Content-Type",contentType);
            b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofByteArray(body));
            try {
                HttpResponse<InputStream> r=CLIENT.send(b.build(),HttpResponse.BodyHandlers.ofInputStream());
                try(InputStream in=r.body()){
                    int status=r.statusCode();
                    if(status==301||status==302||status==303||status==307||status==308){
                        if(!method.equals("GET")||body!=null)throw new IOException("V2_WRITE_REDIRECT");
                        URI next=resolve(current.toString(),r.headers().firstValue("Location").orElseThrow(()->new IOException("V2_BAD_REDIRECT")));
                        if(current.getScheme().equals("https")&&!next.getScheme().equals("https"))throw new IOException("V2_INSECURE_REDIRECT");
                        if(!sameOrigin(current,next)&&next.getQuery()!=null&&
                                (next.getQuery().contains("access=")||next.getQuery().contains("media=")))throw new IOException("V2_CAPABILITY_REDIRECT");
                        current=next;continue;
                    }
                    long budget=status/100==2?limit:Math.min(limit,1024*1024);
                    ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;
                    while((n=in.read(buf))!=-1){if(out.size()+(long)n>budget)throw new IOException("V2_TOO_LARGE");out.write(buf,0,n);}
                    return new Response(status,out.toByteArray());
                }
            }catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("V2_INTERRUPTED");}
        }
        throw new IOException("V2_TOO_MANY_REDIRECTS");
    }
}
