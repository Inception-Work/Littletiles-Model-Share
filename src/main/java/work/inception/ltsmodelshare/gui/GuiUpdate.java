package work.inception.ltsmodelshare.gui;

import java.util.List;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import work.inception.ltsmodelshare.ModConfig;
import static work.inception.ltsmodelshare.client.Translations.tr;

/** Wrapped title and bounded changelog with the same three actions as the legacy update dialog. */
public final class GuiUpdate extends Screen {
    private final String version,url,changelog;
    private List<FormattedCharSequence> titles=List.of(),lines=List.of();
    private int x,y,w,h,visible,scroll,bodyTop;
    public GuiUpdate(String version,String url,String changelog) {
        super(Component.translatable("ltsmodelshare.update.title"));
        this.version=version;this.url=url;this.changelog=changelog;
    }
    @Override protected void init() {
        clearWidgets();
        w=Math.max(140,Math.min(280,width-24));
        titles=font.split(title,w-20);
        lines=font.split(Component.literal(tr("ltsmodelshare.update.body",version,url)+"\n\n"+changelog),w-20);
        visible=Math.min(lines.size(),Math.max(1,(height-32-titles.size()*10-72)/10));
        scroll=Math.max(0,Math.min(scroll,Math.max(0,lines.size()-visible)));
        h=titles.size()*10+visible*10+72;x=(width-w)/2;y=Math.max(4,(height-h)/2);bodyTop=y+titles.size()*10+14;
        int half=(w-26)/2;
        addRenderableWidget(GuiNavigation.button(x+10,y+h-52,w-20,20,"ltsmodelshare.update.download",() -> {
            net.minecraft.Util.getPlatform().openUri(java.net.URI.create(url));onClose();
        }));
        addRenderableWidget(GuiNavigation.button(x+10,y+h-28,half,20,"ltsmodelshare.update.later",this::onClose));
        addRenderableWidget(GuiNavigation.button(x+16+half,y+h-28,half,20,"ltsmodelshare.update.never",() -> {
            ModConfig.get().updateNever=true;ModConfig.get().save();onClose();
        }));
    }
    @Override public void render(PoseStack graphics,int mouseX,int mouseY,float partialTick) {
        GuiComponent.fill(graphics,0,0,width,height,0xA0000000);
        GuiComponent.fill(graphics,x-2,y-2,x+w+2,y+h+2,0xFF000000);
        GuiComponent.fill(graphics,x,y,x+w,y+h,0xFF2B2B2B);
        int top=y+8;
        for(FormattedCharSequence line:titles) {
            font.drawShadow(graphics,line,x+(w-font.width(line))/2,top,0xFFFF55);top+=10;
        }
        GuiComponent.enableScissor(x+10,bodyTop,x+w-10,bodyTop+visible*10);
        try {
            for(int i=0;i<visible;i++)font.drawShadow(graphics,lines.get(scroll+i),x+10,bodyTop+i*10,0xDDDDDD);
        } finally {GuiComponent.disableScissor();}
        if(lines.size()>visible) {
            int thumb=bodyTop+(visible*10-6)*scroll/Math.max(1,lines.size()-visible);
            GuiComponent.fill(graphics,x+w-5,thumb,x+w-3,thumb+6,0xFFA0A0A0);
        }
        super.render(graphics,mouseX,mouseY,partialTick);
    }
    @Override public boolean mouseScrolled(double mouseX,double mouseY,double delta) {
        if(mouseX>=x&&mouseX<x+w&&mouseY>=y&&mouseY<y+h) {
            scroll=Math.max(0,Math.min(Math.max(0,lines.size()-visible),scroll-(int)Math.signum(delta)*3));return true;
        }
        return super.mouseScrolled(mouseX,mouseY,delta);
    }
    @Override public void onClose() {minecraft.setScreen(null);}
    @Override public boolean isPauseScreen() {return false;}
}
