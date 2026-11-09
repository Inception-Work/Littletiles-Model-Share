package work.inception.ltsmodelshare.api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** In-memory sessions; no player credentials, live network or Minecraft runtime. */
public final class SessionChecks {
    private static int count;
    private static void check(boolean value, String message) {
        count++; if (!value) throw new AssertionError(message);
    }
    private static final class Fake implements SessionRequests.Session {
        String base = "https://site.test", token = "old";
        int refreshed, expired;
        SessionRequests.Refresh result = SessionRequests.Refresh.REJECTED;
        public String base() { return base; }
        public String token() { return token; }
        public SessionRequests.Refresh refresh() {
            refreshed++;
            if (result == SessionRequests.Refresh.UPDATED) token = "new";
            return result;
        }
        public void expired(String b, String t) {
            check(base.equals(b) && token.equals(t), "Only the rejected session is cleared");
            expired++; token = "";
        }
    }
    public static void run() throws Exception {
        Fake valid = new Fake();
        check(SessionRequests.execute(valid,(b,t)->200, x->x,true)==200
                && valid.refreshed==0 && valid.expired==0,"Valid session unchanged");
        Fake stale = new Fake();
        SessionRequests.execute(stale,(b,t)->401,x->x,true);
        check(stale.expired==1 && stale.token.isEmpty(),"Rejected credentials request login");
        Fake empty = new Fake(); empty.token="";
        SessionRequests.execute(empty,(b,t)->401,x->x,true);
        check(empty.refreshed==0 && empty.expired==1,"Missing credentials request login without refresh");
        Fake renewed = new Fake(); renewed.result=SessionRequests.Refresh.UPDATED;
        List<String> attempts = new ArrayList<>();
        int result=SessionRequests.execute(renewed,(b,t)->{attempts.add(t); return "old".equals(t)?401:200;},x->x,true);
        check(result==200 && attempts.size()==2 && "new".equals(attempts.get(1))
                && renewed.refreshed==1 && renewed.expired==0,"Refresh once and retry with new credentials");
        Fake retryDenied = new Fake(); retryDenied.result=SessionRequests.Refresh.UPDATED;
        SessionRequests.execute(retryDenied,(b,t)->401,x->x,true);
        check(retryDenied.refreshed==1 && retryDenied.expired==1,"Rejected retry returns to login");
        for (int status : new int[]{403,404,410,429,500,503}) {
            Fake other = new Fake();
            SessionRequests.execute(other,(b,t)->status,x->x,true);
            check(other.expired==0 && other.refreshed==0,"Non-auth failure preserves session: "+status);
        }
        Fake offline = new Fake(); offline.result=SessionRequests.Refresh.UNAVAILABLE;
        SessionRequests.execute(offline,(b,t)->401,x->x,true);
        check(offline.expired==0 && "old".equals(offline.token),"Refresh network outage preserves credentials");
        Fake network = new Fake();
        try { SessionRequests.execute(network,(b,t)->{throw new IOException("offline");},x->(Integer)x,true); throw new AssertionError(); }
        catch(IOException expected){check(network.expired==0,"Request network outage preserves credentials");}
        Fake login = new Fake();
        SessionRequests.execute(login,(b,t)->{check(t==null,"Login request carries no old token");return 401;},x->x,false);
        check(login.expired==0 && login.refreshed==0,"Bad password stays on login without invalidation");
        Fake switched = new Fake();
        try { SessionRequests.execute(switched,(b,t)->{switched.token="another-user";return 401;},x->x,true); throw new AssertionError(); }
        catch(IOException expected){check("V2_SESSION_CHANGED".equals(expected.getMessage())
                && switched.expired==0 && "another-user".equals(switched.token),"Late old response cannot clear a new login");}
        Fake external = new Fake();
        SessionRequests.execute(external,(b,t)->401,x->0,true);
        check(external.expired==0 && external.refreshed==0,"CDN denial cannot expire the account");
        System.out.println("Session checks passed: " + count);
    }
}
