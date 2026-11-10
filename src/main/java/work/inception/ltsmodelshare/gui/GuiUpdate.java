package work.inception.ltsmodelshare.gui;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiButton;
import org.lwjgl.input.Mouse;
import java.util.*;
import work.inception.ltsmodelshare.ModConfig;
import work.inception.ltsmodelshare.client.TextUtil;
import static work.inception.ltsmodelshare.client.Translations.tr;

/** Bounded, scrollable release notice for the legacy client. */
public final class GuiUpdate extends GuiScreen {
    private final String version,url,changelog;
    private List<String> titleLines=new ArrayList<>();
    private List<String> lines=new ArrayList<>();
    private int x,y,w,h,visible,scroll;
    public GuiUpdate(String version,String url,String changelog) {this.version=version;this.url=url;this.changelog=changelog;}
    @Override public void initGui() {
        w=Math.max(140,Math.min(280,width-24));
        titleLines=TextUtil.wrap(fontRenderer,tr("ltsmodelshare.update.title"),w-10);
        lines=TextUtil.wrap(fontRenderer,tr("ltsmodelshare.update.body",version,url)+"\n\n"+changelog,w-20);
        visible=Math.min(lines.size(),Math.max(1,(height-124)/10));
        scroll=Math.min(scroll,Math.max(0,lines.size()-visible));
        h=titleLines.size()*10+visible*10+64;x=(width-w)/2;y=(height-h)/2;
        int half=(w-26)/2;
        buttonList.clear();
        buttonList.add(new LocalizedButton(0,x+10,y+h-52,w-20,20,tr("ltsmodelshare.update.download")));
        buttonList.add(new LocalizedButton(1,x+10,y+h-28,half,20,tr("ltsmodelshare.update.later")));
        buttonList.add(new LocalizedButton(2,x+16+half,y+h-28,half,20,tr("ltsmodelshare.update.never")));
    }
    @Override public void drawScreen(int mouseX,int mouseY,float partial) {
        drawDefaultBackground();drawRect(x-2,y-2,x+w+2,y+h+2,0xFF000000);drawRect(x,y,x+w,y+h,0xFF2B2B2B);
        int ty=y+8;
        for(String line:titleLines) {drawCenteredString(fontRenderer,line,width/2,ty,0xFFFF55);ty+=10;}
        int bodyTop=ty+2;
        for(int i=0;i<visible;i++)drawString(fontRenderer,lines.get(scroll+i),x+10,bodyTop+i*10,0xDDDDDD);
        if(lines.size()>visible)drawRect(x+w-5,bodyTop+(visible*10-6)*scroll/Math.max(1,lines.size()-visible),x+w-3,bodyTop+6+(visible*10-6)*scroll/Math.max(1,lines.size()-visible),0xFFA0A0A0);
        super.drawScreen(mouseX,mouseY,partial);
    }
    @Override public void handleMouseInput() throws java.io.IOException {
        super.handleMouseInput();int wheel=Mouse.getEventDWheel();
        if(wheel!=0)scroll=Math.max(0,Math.min(Math.max(0,lines.size()-visible),scroll+(wheel>0?-3:3)));
    }
    @Override protected void actionPerformed(GuiButton button) {
        if(button.id==0)try { java.awt.Desktop.getDesktop().browse(java.net.URI.create(url)); }
        catch(Exception failure) { org.lwjgl.Sys.openURL(url); }
        else if(button.id==2) { ModConfig.get().updateNever=true; ModConfig.get().save(); }
        mc.displayGuiScreen(null);
    }
    @Override public boolean doesGuiPauseGame() {return false;}
}
