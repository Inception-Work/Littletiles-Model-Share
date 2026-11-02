package work.inception.ltsmodelshare.gui;

/** Standalone checks for the author footer geometry. Run with javac/java, no Minecraft needed. */
public final class AuthorFooterLayoutTest {
    private static int checks;

    private static void check(boolean condition, String description) {
        checks++;
        if (!condition) throw new AssertionError(description);
    }

    public static void main(String[] args) {
        int[][] sizes = {{320, 240}, {427, 240}, {640, 360}, {854, 480}, {1280, 720}, {2560, 1440}};
        for (int[] size : sizes) {
            AuthorFooterLayout f = new AuthorFooterLayout(size[0], size[1]);
            // Bottom row: two kind tabs on the left, back on the right.
            check(f.kindX(0) == AuthorFooterLayout.MARGIN, "single tab left aligned at margin");
            check(f.kindX(1) == AuthorFooterLayout.MARGIN + f.kindWidth() + AuthorFooterLayout.GAP,
                    "collection tab follows the single tab");
            check(f.backX() + AuthorFooterLayout.BACK_W == f.right, "back right aligned to margin");
            check(f.kindX(1) + f.kindWidth() <= f.backX(), "kind tabs never overlap back");

            // Upper row: previous left, next + follow right, total width equal to back.
            check(f.previousX() == AuthorFooterLayout.MARGIN, "previous left aligned at margin");
            check(f.nextX() == f.backX(), "next starts where back starts");
            check(f.followX() + AuthorFooterLayout.FOLLOW_W == f.right, "follow right aligned to margin");
            check(f.nextX() + AuthorFooterLayout.PAGE_W + AuthorFooterLayout.GAP == f.followX(),
                    "next and follow separated by one gap");
            check(AuthorFooterLayout.PAGE_W + AuthorFooterLayout.GAP + AuthorFooterLayout.FOLLOW_W
                    == AuthorFooterLayout.BACK_W, "next + gap + follow equals back width");
            check(f.nextX() + AuthorFooterLayout.BACK_W == f.backX() + AuthorFooterLayout.BACK_W,
                    "rows share the same right edge");
            check(f.previousX() + AuthorFooterLayout.PAGE_W < f.nextX(), "previous never overlaps next");

            // Rows do not overlap the content and sit above the very bottom.
            check(f.pagY + AuthorFooterLayout.ROW_H == f.footerY - AuthorFooterLayout.GAP,
                    "pagination row is one gap above the footer row");
            check(f.listBottom < f.pagY, "content stops above the pagination row");
            check(f.footerY + AuthorFooterLayout.ROW_H == size[1] - AuthorFooterLayout.MARGIN,
                    "footer row sits on the bottom margin");
        }

        // Very narrow windows must still keep the two footer rows usable.
        AuthorFooterLayout narrow = new AuthorFooterLayout(200, 200);
        check(narrow.kindX(1) + narrow.kindWidth() <= narrow.backX(),
                "narrow window keeps tabs clear of back");
        System.out.println("AuthorFooterLayoutTest: " + checks + " checks passed across six GUI sizes.");
    }
}
