package work.inception.ltsmodelshare.gui;

/** Standalone regression checks: run with javac/java, without launching Minecraft. */
public final class WorkDetailLayoutTest {
    private static int checks;

    private static void check(boolean condition, String description) {
        checks++;
        if (!condition) throw new AssertionError(description);
    }

    public static void main(String[] args) {
        int[][] sizes = {{320, 240}, {427, 240}, {640, 360}, {854, 480}, {1280, 720}};
        for (int[] size : sizes) {
            WorkDetailLayout g = new WorkDetailLayout(size[0], size[1]);
            check(g.width == Math.min(320, size[0] - 20), "legacy content width");
            check(g.rightX == g.left + g.coverWidth + 8, "column gutter");
            check(g.bodyBottom == size[1] - 46, "fixed footer exclusion");
            check(g.coverHeight <= Math.max(48, Math.max(40, g.bodyBottom - 22) * 55 / 100), "cover height cap");
            check(g.inBody(g.left, 22), "body starts at 22");
            check(!g.inBody(g.left, 21), "header not clickable");
            check(!g.inBody(g.left, g.bodyBottom), "footer not clickable");
            check(!g.inBody(g.left, size[1] - 30), "footer buttons excluded from content hit tests");
            for (int titles : new int[]{1, 2}) for (int tags : new int[]{0, 1, 3}) {
                for (boolean author : new boolean[]{false, true}) for (int stats : new int[]{1, 2}) {
                    for (int deps : new int[]{0, 1, 6}) for (int summary : new int[]{0, 1, 4, 7, 200}) {
                        for (int images : new int[]{0, 1, 2, 8}) {
                            var h = g.header(titles, tags, author, stats, deps, summary, images);
                            check(h.tagsY() == titles * 10 + 2, "title before tags");
                            check(h.authorY() == h.tagsY() + tags * 10 + 2, "tags before author");
                            check(h.statsY() == h.authorY() + (author ? 20 : 0) + 6, "author before stats");
                            check(h.dependencyY() == h.statsY() + stats * 10 + (deps > 0 ? 6 : 0), "stats before dependencies");
                            check(h.summaryY() == h.dependencyY() + deps * 10 + (summary > 0 ? 6 : 0), "dependencies before summary");
                            int window = images > 1 ? 7 : 4;
                            check(h.summaryLines() == Math.min(summary, window), "actual summary height, no fixed blank reservation");
                            check(h.summaryMaxScroll(summary) == Math.max(0, summary - window), "description scroll range");
                            int rightBottom = h.summaryY() + Math.min(summary, window) * 10 + (summary > 0 ? 2 : 0);
                            int thumbRows = images > 1 ? (Math.min(8, images) + g.galleryColumns() - 1) / g.galleryColumns() : 0;
                            int leftBottom = g.coverHeight + thumbRows * (g.thumbSize + 4);
                            check(h.height() - Math.max(leftBottom, rightBottom) == 6, "exact six-pixel list gap");
                            check(h.itemTop() == h.height() + 14, "model-list heading height");
                            for (int items : new int[]{0, 1, 12}) {
                                int content = h.height() + 14 + items * 68 + 8;
                                check(g.maxScroll(h, items) == Math.max(0, content - (g.bodyBottom - 22)), "scroll uses relative height once");
                                int scrolledBottom = 22 + content - g.maxScroll(h, items);
                                check(scrolledBottom <= g.bodyBottom || g.maxScroll(h, items) == 0,
                                        "last row reachable without footer overlap");
                            }
                        }
                    }
                }
            }
            var noSummary = g.header(1, 0, true, 2, 0, 0, 1);
            var emptyAuthor = g.header(1, 0, false, 2, 0, 0, 1);
            check(noSummary.summaryLines() == 0, "empty description reserves no space");
            check(noSummary.summaryY() - emptyAuthor.summaryY() == 20, "absent author reserves no ghost bio box");
        }
        System.out.println("WorkDetailLayoutTest: " + checks + " checks passed across five GUI sizes.");
    }
}
