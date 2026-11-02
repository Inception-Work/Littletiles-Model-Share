package work.inception.ltsmodelshare.gui;

/** Geometry in scaled Minecraft GUI pixels; the cards API accepts at most 48 items. */
public final class ModelGridLayout {
    public final int cardWidth, imageHeight, cardHeight, columns, rows, left, top;

    public ModelGridLayout(int width, int height) {
        int availableHeight = Math.max(1, height - 62 - 50);
        int preferredWidth = width >= 300 ? 78 : 66;
        int twoRowImage = (availableHeight - 6) / 2 - 28;
        cardWidth = Math.max(44, Math.min(width - 16,
                Math.min(preferredWidth, Math.max(60, twoRowImage * 16 / 9 + 6))));
        imageHeight = Math.max(18, (cardWidth - 6) * 9 / 16);
        cardHeight = imageHeight + 28;
        columns = Math.max(1, Math.min(48, (width - 16 + 6) / (cardWidth + 6)));
        rows = Math.max(1, Math.min(48 / columns, (availableHeight + 6) / (cardHeight + 6)));
        left = Math.max(8, (width - (columns * cardWidth + (columns - 1) * 6)) / 2);
        top = 50 + Math.max(0, (availableHeight - (rows * cardHeight + (rows - 1) * 6)) / 2);
    }
}
