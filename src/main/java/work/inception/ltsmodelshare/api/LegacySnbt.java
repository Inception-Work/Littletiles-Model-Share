package work.inception.ltsmodelshare.api;

import java.io.IOException;
import java.nio.charset.*;
import java.nio.ByteBuffer;
import java.util.*;

/** Bounded dialect gate only; LittleTiles still parses and imports the unchanged text on the game thread. */
public final class LegacySnbt {
    private LegacySnbt() {}
    public static String utf8(byte[] bytes) throws IOException {
        try { return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); }
        catch (CharacterCodingException invalid) { throw new IOException("V2_LEGACY_REQUIRED"); }
    }
    public static void requireLegacy(String text) throws IOException {
        if (text == null || text.length() > 10 * 1024 * 1024) throw failure();
        Parser p = new Parser(text); Object root = p.value(0); p.space();
        if (p.at != text.length() || !(root instanceof Map) || !legacy((Map<?,?>)root)) throw failure();
    }
    private static boolean legacy(Map<?,?> root) throws IOException {
        if (root.containsKey("t") || root.containsKey("c") || root.containsKey("s")) throw failure();
        Object tiles = root.get("tiles"), children = root.get("children");
        boolean found = tiles instanceof List || root.get("structure") instanceof Map;
        if (tiles != null && !(tiles instanceof List)) throw failure(); // 1.10 compact is not a 1.12 list
        if (children instanceof List) for (Object child : (List<?>)children) {
            if (!(child instanceof Map)) throw failure();
            found |= legacy((Map<?,?>)child);
        }
        return found;
    }
    private static IOException failure() { return new IOException("V2_LEGACY_REQUIRED"); }
    private static final Object SCALAR = new Object();
    private static final class Parser {
        final String input; int at, nodes;
        Parser(String input) { this.input=input; }
        void space() { while(at<input.length() && Character.isWhitespace(input.charAt(at))) at++; }
        boolean eat(char c) { space(); if(at<input.length() && input.charAt(at)==c){at++;return true;}return false; }
        void expect(char c) throws IOException { if(!eat(c))throw failure(); }
        Object value(int depth) throws IOException {
            space(); if(depth>64 || ++nodes>1000000 || at>=input.length())throw failure();
            char c=input.charAt(at);
            if(c=='{') {
                at++; Map<String,Object> out=new HashMap<>();
                if(eat('}'))return out;
                do { String key=token(true);expect(':');Object v=value(depth+1);
                    if(out.put(key,v)!=null)throw failure();
                    if(eat('}'))return out;
                    expect(',');
                }while(true);
            }
            if(c=='[') {
                at++; space();
                if(at+1<input.length() && "BILbil".indexOf(input.charAt(at))>=0 && input.charAt(at+1)==';') {
                    at+=2; // Typed primitive arrays must not be mistaken for a legacy tiles list.
                    if(eat(']'))return SCALAR;
                    do { token(false); if(eat(']'))return SCALAR;expect(','); }while(true);
                }
                List<Object> out=new ArrayList<>();if(eat(']'))return out;
                do {out.add(value(depth+1));if(eat(']'))return out;expect(',');}while(true);
            }
            token(false);return SCALAR;
        }
        String token(boolean key) throws IOException {
            space();if(at>=input.length())throw failure();
            char quote=input.charAt(at);
            if(quote=='"' || quote=='\'') {
                at++;StringBuilder out=new StringBuilder();
                while(at<input.length()){
                    char c=input.charAt(at++);
                    if(c==quote)return out.toString();
                    if(c=='\\'){if(at>=input.length())throw failure();c=input.charAt(at++);}
                    out.append(c);
                }
                throw failure();
            }
            int start=at;
            while(at<input.length()){
                char c=input.charAt(at);
                if(Character.isWhitespace(c)||c==','||c==']'||c=='}'||(key&&c==':'))break;
                if(c=='{'||c=='['||c=='"'||c=='\'')throw failure();
                at++;
            }
            if(at==start)throw failure();
            return input.substring(start,at);
        }
    }
}
