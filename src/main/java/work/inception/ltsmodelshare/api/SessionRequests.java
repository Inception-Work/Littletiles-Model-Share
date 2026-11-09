package work.inception.ltsmodelshare.api;

import java.io.IOException;
import java.util.Objects;
import java.util.function.ToIntFunction;

/** Session recovery policy shared by JSON, uploads and authenticated assets. */
public final class SessionRequests {
    public enum Refresh { UPDATED, REJECTED, UNAVAILABLE }
    public interface Session {
        String base();
        String token();
        Refresh refresh();
        void expired(String base, String token);
    }
    public interface Request<R> { R send(String base, String token) throws IOException; }
    private static final Object RECOVERY = new Object();
    private SessionRequests() { }

    private static void unchanged(Session session, String base, String token) throws IOException {
        if (!Objects.equals(base, session.base()) || !Objects.equals(token, session.token())) {
            throw new IOException("V2_SESSION_CHANGED");
        }
    }

    /** status must report 401 only for the original site's credential-bearing API routes. */
    public static <R> R execute(Session session, Request<R> request,
                                ToIntFunction<R> status, boolean authenticated) throws IOException {
        String base = session.base(), token = session.token();
        R response = request.send(base, authenticated ? token : null);
        unchanged(session, base, token);
        if (!authenticated || status.applyAsInt(response) != 401) return response;
        synchronized (RECOVERY) {
            unchanged(session, base, token);
            Refresh refresh = token == null || token.isEmpty() ? Refresh.REJECTED : session.refresh();
            if (refresh != Refresh.UPDATED) {
                unchanged(session, base, token);
                if (refresh == Refresh.REJECTED) session.expired(base, token);
                return response;
            }
            // A successful refresh may change only the token, never the website.
            if (!Objects.equals(base, session.base())) throw new IOException("V2_SESSION_CHANGED");
            token = session.token();
        }
        response = request.send(base, token);
        unchanged(session, base, token);
        if (status.applyAsInt(response) == 401) session.expired(base, token);
        return response;
    }
}
