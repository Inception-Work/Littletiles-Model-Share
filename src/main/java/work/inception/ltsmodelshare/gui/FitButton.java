package work.inception.ltsmodelshare.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import work.inception.ltsmodelshare.client.TextUtil;

/** Button that shrinks long translations to stay inside its bounds. */
public class FitButton extends Button {
    public FitButton(int x, int y, int width, int height, Component label, OnPress onPress) {
        // Keep Button's message as the single source of truth for drawing and narration.
        // setMessage() is used by follow/favorite/search-mode/session buttons.
        super(x, y, width, height, label, onPress, DEFAULT_NARRATION);
    }

    public FitButton(int x, int y, int width, int height, String translationKey, OnPress onPress) {
        this(x, y, width, height, Component.translatable(translationKey), onPress);
    }

    @Override
    public void renderString(PoseStack pose, Font font, int color) {
        TextUtil.drawCenteredFit(pose, font, getMessage().getString(),
                getX() + width / 2, getY() + (height - 8) / 2, width - 8, color);
    }

    public Component label() {
        return getMessage();
    }
}
