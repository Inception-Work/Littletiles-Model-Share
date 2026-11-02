package work.inception.ltsmodelshare.gui;

/** Pure scaled-pixel geometry shared by detail drawing, scrolling and hit testing.
 * Values are ported from forge/1.12.2 GuiWorkDetail (22px body top, 320px column,
 * 46% 4:3 cover, 6px header gap, 68px item rows). No Minecraft dependency.
 */
final class WorkDetailLayout {
    static final int BODY_TOP = 22;
    static final int ROW_HEIGHT = 68;
    static final int THUMB_WIDTH = 64;
    static final int THUMB_HEIGHT = 48;

    final int left, width, bodyBottom, coverWidth, coverHeight, rightX, rightWidth, thumbSize;

    WorkDetailLayout(int screenWidth, int screenHeight) {
        width = Math.max(1, Math.min(320, screenWidth - 20));
        left = screenWidth / 2 - width / 2;
        bodyBottom = Math.max(BODY_TOP + 1, screenHeight - 46);
        int bodyHeight = Math.max(40, bodyBottom - BODY_TOP);
        int cw = Math.max(72, Math.min(170, width * 46 / 100));
        int ch = cw * 3 / 4;
        int maxCoverHeight = Math.max(48, bodyHeight * 55 / 100);
        if (ch > maxCoverHeight) {
            ch = maxCoverHeight;
            cw = ch * 4 / 3;
        }
        coverWidth = cw;
        coverHeight = ch;
        rightX = left + cw + 8;
        rightWidth = Math.max(60, width - cw - 8);
        thumbSize = Math.max(14, Math.min(24, cw / 5));
    }

    Header header(int titleLines, int tagRows, boolean author, int statsLines,
                  int dependencyLines, int summaryLines, int gallerySize) {
        int tagsY = titleLines * 10 + 2;
        int authorY = tagsY + tagRows * 10 + 2;
        int statsY = authorY + (author ? 20 : 0) + 6;
        int dependencyY = statsY + statsLines * 10 + (dependencyLines > 0 ? 6 : 0);
        int afterDependencies = dependencyY + dependencyLines * 10;
        int summaryY = afterDependencies + (summaryLines > 0 ? 6 : 0);
        int summaryWindow = gallerySize > 1 ? 7 : 4;
        int visibleSummaryLines = Math.min(summaryLines, summaryWindow);
        int rightBottom = summaryY + visibleSummaryLines * 10 + (summaryLines > 0 ? 2 : 0);
        int thumbRows = gallerySize > 1
                ? (Math.min(8, gallerySize) + galleryColumns() - 1) / galleryColumns() : 0;
        int leftBottom = coverHeight + thumbRows * (thumbSize + 4);
        return new Header(tagsY, authorY, statsY, dependencyY, summaryY,
                visibleSummaryLines, summaryWindow, Math.max(leftBottom, rightBottom) + 6);
    }

    int galleryColumns() {
        return Math.max(1, coverWidth / (thumbSize + 4));
    }

    int maxScroll(Header header, int itemCount) {
        return Math.max(0, header.height + 14 + itemCount * ROW_HEIGHT + 8 - (bodyBottom - BODY_TOP));
    }

    boolean inBody(double x, double y) {
        return x >= left - 4 && x < left + width + 8 && y >= BODY_TOP && y < bodyBottom;
    }

    record Header(int tagsY, int authorY, int statsY, int dependencyY, int summaryY,
                  int summaryLines, int summaryWindow, int height) {
        int itemTop() { return height + 14; }
        int summaryMaxScroll(int totalLines) { return Math.max(0, totalLines - summaryWindow); }
    }
}
