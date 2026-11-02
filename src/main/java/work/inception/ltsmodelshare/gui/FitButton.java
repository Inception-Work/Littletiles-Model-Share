package work.inception.ltsmodelshare.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import work.inception.ltsmodelshare.client.TextUtil;

/** Button that shrinks long translations to stay inside its bounds. */
public class FitButton extends Button {
    private final Component label;

    public FitButton(int x, int y, int width, int height, Component label, OnPress onPress) {
        super(x, y, width, height, Component.empty(), onPress, DEFAULT_NARRATION);
        this.label = label;
    }

    public FitButton(int x, int y, int width, int height, String translationKey, OnPress onPress) {
        this(x, y, width, height, Component.translatable(translationKey), onPress);
    }

    @Override
    public void renderString(GuiGraphics graphics, Font font, int color) {
        TextUtil.drawCenteredFit(graphics, font, label.getString(),
                getX() + width / 2, getY() + (height - 8) / 2, width - 8, color);
    }

    public Component label() {
        return label;
    }
}
