"""Run real LocalThumbs CPU/cache tests with tiny Minecraft API stubs (Python + JDK only).

Run from any directory: python tools/tests/upload_thumbnails_smoke.py
No Minecraft client is launched; this does not verify actual rendering or GL behavior.
"""
from pathlib import Path
import subprocess
import shutil
import tempfile

repo = Path(__file__).resolve().parents[2]
root = Path(tempfile.mkdtemp(prefix='lts-upload-thumbnails-')).resolve()
sources = {
'com/mojang/blaze3d/platform/NativeImage.java': '''package com.mojang.blaze3d.platform;
public class NativeImage implements AutoCloseable {
 public static NativeImage last; public final int w,h; public final int[] pixels; public boolean closed;
 public NativeImage(int w,int h,boolean unused){this.w=w;this.h=h;pixels=new int[w*h];last=this;}
 public void setPixelRGBA(int x,int y,int color){pixels[y*w+x]=color;}
 public void close(){closed=true;}
}''',
'net/minecraft/resources/ResourceLocation.java': '''package net.minecraft.resources;
public record ResourceLocation(String namespace,String path) {}''',
'net/minecraft/client/renderer/texture/DynamicTexture.java': '''package net.minecraft.client.renderer.texture;
import com.mojang.blaze3d.platform.NativeImage;
public class DynamicTexture implements AutoCloseable {
 public final NativeImage image; public DynamicTexture(NativeImage image){this.image=image;}
 public void close(){image.close();}
}''',
'net/minecraft/client/Minecraft.java': '''package net.minecraft.client;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.texture.DynamicTexture;
public class Minecraft {
 private static final Minecraft INSTANCE=new Minecraft(); public final Manager manager=new Manager();
 public static Minecraft getInstance(){return INSTANCE;} public Manager getTextureManager(){return manager;}
 public static class Manager {
 public final Map<ResourceLocation,DynamicTexture> textures=new HashMap<>(); public int released;
 public void register(ResourceLocation id,DynamicTexture texture){textures.put(id,texture);}
 public void release(ResourceLocation id){DynamicTexture old=textures.remove(id);if(old!=null){old.close();released++;}}
 }
}''',
'net/minecraft/client/gui/GuiGraphics.java': '''package net.minecraft.client.gui;
import net.minecraft.resources.ResourceLocation;
public class GuiGraphics {
 public int placeholders,sw,sh,tw,th; public float u,v;
 public void fill(int a,int b,int c,int d,int color){placeholders++;}
 public void blit(ResourceLocation id,int x,int y,int w,int h,float u,float v,int sw,int sh,int tw,int th){
 this.u=u;this.v=v;this.sw=sw;this.sh=sh;this.tw=tw;this.th=th;}
}''',
'work/inception/ltsmodelshare/LtsModelShare.java': '''package work.inception.ltsmodelshare;
public class LtsModelShare {
 public static final String MODID="ltsmodelshare"; public static final Log LOG=new Log();
 public static class Log { public int warnings; public void warn(String message,Object...args){warnings++;} }
}''',
'ThumbTest.java': r'''import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import com.mojang.blaze3d.platform.NativeImage;
import work.inception.ltsmodelshare.client.LocalThumbs;
import work.inception.ltsmodelshare.LtsModelShare;
public class ThumbTest {
 static void check(boolean condition,String name){if(!condition)throw new AssertionError(name);System.out.println("PASS "+name);}
 static File image(Path dir,String name,int w,int h,String format)throws Exception {
 BufferedImage image=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);
 for(int y=0;y<h;y++)for(int x=0;x<w;x++)image.setRGB(x,y,0xFFFF0000);
 File file=dir.resolve(name).toFile();ImageIO.write(image,format,file);image.flush();return file;
 }
 public static void main(String[]args)throws Exception {
 Path dir=Path.of(args[0]);Files.createDirectories(dir);
 LocalThumbs thumbs=new LocalThumbs();GuiGraphics g=new GuiGraphics();
 File wide=image(dir,"wide.png",1920,1080,"png");thumbs.draw(g,wide,0,0,60,34);
 check(g.tw<=240&&g.th<=240&&g.tw>0,"PNG downsample bounded to 240px");
 check(Math.abs(g.sw/(double)g.sh-60.0/34)<0.02,"wide screenshot center-crop aspect");
 check(NativeImage.last.pixels[0]==0xFF0000FF,"ARGB to native ABGR color conversion");
 NativeImage first=NativeImage.last;thumbs.draw(g,wide,0,0,60,34);
 check(NativeImage.last==first,"unchanged screenshot cache hit");
 wide.setLastModified(wide.lastModified()+5000);thumbs.draw(g,wide,0,0,60,34);
 check(first.closed&&NativeImage.last!=first,"modified file invalidates and releases old texture");
 File tall=image(dir,"tall.jpg",320,1280,"jpg");thumbs.draw(g,tall,0,0,60,34);
 check(g.v>0&&g.sh<g.th&&g.tw<=240&&g.th<=240,"JPEG decode and centered vertical crop");
 File bad=dir.resolve("bad.png").toFile();Files.writeString(bad.toPath(),"not an image");
 int warnings=LtsModelShare.LOG.warnings;thumbs.draw(g,bad,0,0,60,34);thumbs.draw(g,bad,0,0,60,34);
 check(LtsModelShare.LOG.warnings==warnings+1&&g.placeholders==2,"negative cache avoids repeated decode/logging");
 for(int i=0;i<70;i++){File f=image(dir,"small"+i+".png",8,8,"png");thumbs.draw(g,f,0,0,60,34);}
 check(Minecraft.getInstance().manager.textures.size()<=64,"LRU limits active texture count");
 check(Minecraft.getInstance().manager.released>0,"LRU eviction releases texture ownership");
 thumbs.retainFiles(List.of());check(Minecraft.getInstance().manager.textures.isEmpty(),"refresh releases deleted entries");
 thumbs.draw(g,wide,0,0,60,34);NativeImage last=NativeImage.last;thumbs.clear();thumbs.clear();
 check(last.closed&&Minecraft.getInstance().manager.textures.isEmpty(),"removed clear releases native memory; idempotent");
 System.out.println("All 11 thumbnail behavior checks passed (Minecraft rendering stubbed).");
 }
}'''
}
for name,content in sources.items():
 p=root/name
 p.parent.mkdir(parents=True,exist_ok=True)
 p.write_text(content,encoding='utf-8')
p=root/'work/inception/ltsmodelshare/client/LocalThumbs.java'
p.parent.mkdir(parents=True,exist_ok=True)
shutil.copyfile(repo/'src/main/java/work/inception/ltsmodelshare/client/LocalThumbs.java',p)
java_files=[str(p) for p in root.rglob('*.java')]
try:
    subprocess.run(['javac','-encoding','UTF-8','-d',str(root/'classes'),*java_files],check=True)
    subprocess.run(['java','-Djava.awt.headless=true','-cp',str(root/'classes'),'ThumbTest',str(root/'images')],check=True)
finally:
    # Verify the resolved generated-only directory before removing it.
    assert root.parent == Path(tempfile.gettempdir()).resolve()
    assert root.name.startswith('lts-upload-thumbnails-')
    print('Removing generated upload test directory:', root)
    shutil.rmtree(root)
