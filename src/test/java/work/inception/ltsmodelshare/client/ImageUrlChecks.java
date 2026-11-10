package work.inception.ltsmodelshare.client;

public final class ImageUrlChecks {
    private static int checks;
    private static void check(boolean result) {checks++;if(!result)throw new AssertionError("Image URL check "+checks);}
    public static void main(String[] args) {
        String api="https://www.inception.work/api/v2/resources/8/content";
        check(ImageUrls.thumb(api).equals("https://www.inception.work/api/v2/resources/8/thumb?w=480"));
        check(ImageUrls.preview(api).endsWith("/thumb?w=1600"));
        String privateUrl="/api/v2/resources/8/content?media=fixture%2Bcap&access=fixture-grant";
        String thumb=ImageUrls.thumb(privateUrl);
        check(thumb.equals("/api/v2/resources/8/thumb?w=480&media=fixture%2Bcap&access=fixture-grant"));
        check(ImageUrls.thumb(thumb).equals(thumb));
        check(ImageUrls.preview(thumb).equals("/api/v2/resources/8/thumb?w=1600&media=fixture%2Bcap&access=fixture-grant"));
        check(ImageUrls.thumb("/api/v2/resources/8/thumb?w=1600&media=x").equals("/api/v2/resources/8/thumb?w=480&media=x"));
        check(ImageUrls.thumb("https://elsewhere.example/api/v2/resources/8/content").endsWith("/content"));
        check(ImageUrls.thumb("https://std-cdn.inception.work/picture.png").endsWith("picture.png/thumb"));
        check(ImageUrls.preview("https://std-cdn.inception.work/picture.png?key=x").endsWith("picture.png/preview?key=x"));
        check(ImageUrls.thumb("https://www.inception.work/api/v2/works/1").endsWith("/works/1"));
        check(ImageUrls.thumb(null)==null);
        check(ImageUrls.thumb("/api/v2/resources/8/content#image").endsWith("/thumb?w=480#image"));
        System.out.println("Authorized image thumbnail URL checks passed: "+checks);
    }
}
