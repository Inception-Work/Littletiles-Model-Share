package work.inception.ltsmodelshare.gui;

/**
 * Pure geometry for the author page footer, mirroring the favourites layout.
 *
 * <pre>
 *            [上一页]            第 n / m 页          [下一页][关注]   (pagY)
 * [单个作品][合集]                                          [返回]   (footerY)
 * </pre>
 *
 * The next + follow group is right aligned with the back button and its total width equals the
 * back button's width, so the two rows end on the same edge.
 */
final class AuthorFooterLayout {
    static final int MARGIN = 10;
    static final int GAP = 6;
    static final int KIND_W = 64;
    static final int PAGE_W = 40;
    static final int FOLLOW_W = 46;
    /** PAGE_W + GAP + FOLLOW_W == BACK_W keeps both rows the same width. */
    static final int BACK_W = PAGE_W + GAP + FOLLOW_W;
    static final int ROW_H = 20;
    static final int PAG_OFFSET = 26;

    final int right;
    final int footerY;
    final int pagY;
    final int listBottom;

    AuthorFooterLayout(int screenWidth, int screenHeight) {
        right = screenWidth - MARGIN;
        footerY = screenHeight - MARGIN - ROW_H;
        pagY = footerY - PAG_OFFSET;
        listBottom = pagY - GAP;
    }

    int kindX(int index) { return MARGIN + index * (kindWidth() + GAP); }
    /** Tabs shrink before they can reach the back button on narrow windows. */
    int kindWidth() {
        int available = backX() - MARGIN - GAP;
        return Math.max(28, Math.min(KIND_W, (available - GAP) / 2));
    }
    int backX() { return right - BACK_W; }
    int previousX() { return MARGIN; }
    int nextX() { return right - BACK_W; }
    int followX() { return right - FOLLOW_W; }
}
