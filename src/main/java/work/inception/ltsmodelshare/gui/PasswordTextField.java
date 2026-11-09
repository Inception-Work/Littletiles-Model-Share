package work.inception.ltsmodelshare.gui;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.ChatAllowedCharacters;

/** Login password model; the native widget stores and renders only the masking characters. */
final class PasswordTextField extends GuiTextField {
    private String password = "";

    PasswordTextField(int id, FontRenderer font, int x, int y, int width, int height) {
        super(id, font, x, y, width, height);
    }

    @Override
    public String getText() {
        return password;
    }

    @Override
    public void setText(String text) {
        String value = text == null ? "" : text;
        replace(value.substring(0, Math.min(value.length(), getMaxStringLength())), value.length());
    }

    @Override
    public void setMaxStringLength(int length) {
        super.setMaxStringLength(length);
        if (password.length() > length) setText(password.substring(0, length));
    }

    @Override
    public void writeText(String input) {
        String filtered = ChatAllowedCharacters.filterAllowedCharacters(input);
        int start = Math.min(getCursorPosition(), getSelectionEnd());
        int end = Math.max(getCursorPosition(), getSelectionEnd());
        int available = Math.max(0, getMaxStringLength() - password.length() + end - start);
        String inserted = filtered.substring(0, Math.min(filtered.length(), available));
        replace(password.substring(0, start) + inserted + password.substring(end), start + inserted.length());
    }

    @Override
    public void deleteFromCursor(int count) {
        if (getCursorPosition() != getSelectionEnd()) {
            writeText("");
            return;
        }
        int cursor = getCursorPosition();
        int start = Math.max(0, Math.min(cursor, cursor + count));
        int end = Math.min(password.length(), Math.max(cursor, cursor + count));
        replace(password.substring(0, start) + password.substring(end), start);
    }

    private void replace(String value, int cursor) {
        password = value;
        char[] mask = new char[value.length()];
        java.util.Arrays.fill(mask, '*');
        // Native painting, selection, clicks and horizontal scrolling all see this same mask.
        super.setText(new String(mask));
        setCursorPosition(Math.min(cursor, value.length()));
    }
}
