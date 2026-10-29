package work.inception.ltsmodelshare.gui;

/** Shared page footer geometry, measured in Minecraft's scaled GUI pixels. */
public final class GuiNavigation {
    public static final int MARGIN = 10;
    public static final int GAP = 6;
    public static final int BACK_WIDTH = 80;
    public static final int BUTTON_HEIGHT = 20;

    private GuiNavigation() { }

    public static int backX(int screenWidth) {
        return screenWidth - MARGIN - BACK_WIDTH;
    }

    public static int footerY(int screenHeight) {
        return screenHeight - MARGIN - BUTTON_HEIGHT;
    }

    public static LocalizedButton backButton(int id, int screenWidth, int screenHeight, String label) {
        return new LocalizedButton(id, backX(screenWidth), footerY(screenHeight), BACK_WIDTH, BUTTON_HEIGHT, label);
    }

    /** Equal-width action buttons to the left, with a reserved gap before the back button. */
    public static int actionWidth(int screenWidth, int count, int preferred) {
        return Math.min(preferred, Math.max(1, (backX(screenWidth) - MARGIN - count * GAP) / count));
    }
}
