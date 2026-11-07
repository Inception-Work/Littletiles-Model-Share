package work.inception.ltsmodelshare.api;

import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

/** No player config or accounts. Local real HTTP plus optional anonymous public website GET. */
public final class V2Checks {
    static int count;
    static void check(boolean v,String message){count++;if(!v)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        check(work.inception.ltsmodelshare.client.ImageUrls.thumb("https://www.inception.work/api/v2/resources/8/content").endsWith("/content"),"API image URL unchanged");
        JsonObject a=V2Api.decode(200,"{\"success\":true,\"payload\":[{\"code\":\"building\",\"name\":\"Building\"}]}");
        check(a.getAsJsonArray("items").size()==1,"Array payload retained");
        var method=V2Api.class.getDeclaredMethod("card",JsonObject.class);method.setAccessible(true);
        Dtos.WorkCard c=(Dtos.WorkCard)method.invoke(null,JsonParser.parseString("{\"id\":4,\"coverResourceId\":8,\"authorId\":9,\"authorName\":\"Author\",\"authorUsername\":\"author\"}").getAsJsonObject());
        Dtos.WorkCard bookmark=(Dtos.WorkCard)method.invoke(null,JsonParser.parseString(
                "{\"workId\":7,\"cover\":\"/api/v2/resources/10/content\",\"author\":{\"userId\":9,\"displayName\":\"Author\"}}").getAsJsonObject());
        check(bookmark.cover.equals("/api/v2/resources/10/content"),"Bookmark and author-work cover alias");
        check(bookmark.author.userId==9,"Bookmark nested author retained");
        check(c.cover.equals("/api/v2/resources/8/content"),"Cover mapped");
        check(c.author.userId==9&&c.author.displayName.equals("Author"),"Flat author mapped");
        try{V2Api.decode(401,"{\"success\":false,\"error_code\":\"F01-001\"}");throw new AssertionError();}
        catch(java.io.IOException e){check(e.getMessage().equals("F01-001"),"Canonical error identity");}
        HttpServer origin=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        HttpServer cdn=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        AtomicReference<String> auth=new AtomicReference<>(),cdnAuth=new AtomicReference<>(),referer=new AtomicReference<>();
        cdn.createContext("/asset",e->{cdnAuth.set(e.getRequestHeaders().getFirst("Authorization"));referer.set(e.getRequestHeaders().getFirst("Referer"));byte[] b="asset".getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(200,b.length);e.getResponseBody().write(b);e.close();});
        origin.createContext("/api/v2/file",e->{auth.set(e.getRequestHeaders().getFirst("Authorization"));e.getResponseHeaders().set("Location","http://127.0.0.1:"+cdn.getAddress().getPort()+"/asset");e.sendResponseHeaders(302,-1);e.close();});
        origin.createContext("/large",e->{byte[] b=new byte[100];e.sendResponseHeaders(200,b.length);e.getResponseBody().write(b);e.close();});
        origin.start();cdn.start();
        try{
            String base="http://127.0.0.1:"+origin.getAddress().getPort();
            var response=V2Transport.request(base,"/api/v2/file","GET",null,null,"fixture-only",100);
            check(response.text().equals("asset"),"Real redirect bytes");
            check("Bearer fixture-only".equals(auth.get())&&cdnAuth.get()==null,"Bearer stays on origin API");
            check("https://www.inception.work/".equals(referer.get()),"CDN public referer");
            try{V2Transport.request(base,"/large","GET",null,null,null,10);throw new AssertionError();}
            catch(java.io.IOException expected){check(expected.getMessage().equals("V2_TOO_LARGE"),"Bounded actual HTTP");}
        }finally{origin.stop(0);cdn.stop(0);}
        if(args.length>0&&args[0].equals("public")){
            String base="https://www.inception.work";
            var response=V2Transport.request(base,"/api/v2/works?page=1&size=2&locale=en","GET",null,null,null,4*1024*1024);
            JsonObject page=V2Api.decode(response.status(),response.text());
            check(page.has("items")&&page.has("total"),"Public deployed V2 list");
            for(var row:page.getAsJsonArray("items")){
                var card=(Dtos.WorkCard)method.invoke(null,row.getAsJsonObject());
                check(card.author!=null&&card.author.userId>0,"Real public author mapping");
            }
            int images=0;
            for(var row:page.getAsJsonArray("items")){
                var card=(Dtos.WorkCard)method.invoke(null,row.getAsJsonObject());
                if(card.cover!=null){
                    var image=V2Transport.request(base,card.cover,"GET",null,null,null,16L*1024*1024);
                    check(image.status()==200&&javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(image.bytes()))!=null,"Public cover actual decode");
                    images++;
                }
                var profile=V2Transport.request(base,"/api/v2/authors/"+card.author.userId,"GET",null,null,null,1024*1024);
                var author=V2Api.decode(profile.status(),profile.text());
                if(author.has("avatarUrl")&&!author.get("avatarUrl").isJsonNull()){
                    var image=V2Transport.request(base,author.get("avatarUrl").getAsString(),"GET",null,null,null,16L*1024*1024);
                    check(image.status()==200&&javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(image.bytes()))!=null,"Public avatar actual decode");
                }
            }
            check(images>0,"Public images checked");
            var tags=V2Transport.request(base,"/api/v2/tags","GET",null,null,null,1024*1024);
            check(V2Api.decode(tags.status(),tags.text()).has("items"),"Real public tags array");
            System.out.println("Public anonymous GET checks completed; no login or writes.");
        }
        System.out.println("V2 checks passed: "+count);
    }
}
