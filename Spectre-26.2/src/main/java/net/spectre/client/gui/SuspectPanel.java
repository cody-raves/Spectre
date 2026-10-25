package net.spectre.client.gui;

import java.util.*;
import java.util.function.ToIntFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.*;
import net.spectre.client.config.ThemeStore;
import net.spectre.client.util.SuspectObjects;
import net.spectre.client.util.SuspectObjects.Category;

/** Screen-projected look card; readable size does not shrink with world distance. */
public final class SuspectPanel {
    public static void draw(GuiGraphicsExtractor g,SuspectObjects.Focus focus,ToIntFunction<Category> colors,boolean limited){
        var mc=Minecraft.getInstance();var theme=ThemeStore.get();var font=mc.font;
        var counts=new EnumMap<Category,Map<SuspectObjects.Icon,Integer>>(Category.class);
        for(var marker:focus.nearby())marker.icons().forEach((item,n)->counts.computeIfAbsent(marker.category(),k->new HashMap<>()).merge(item,n,Integer::sum));
        record Slot(SuspectObjects.Icon icon,int count,Category category){}
        var slots=new ArrayList<Slot>();
        counts.forEach((category,items)->items.entrySet().stream().sorted(Comparator.comparing(e->e.getKey().label())).forEach(e->slots.add(new Slot(e.getKey(),e.getValue(),category))));
        int width=254,columns=7,shown=Math.min(28,slots.size()),rows=(shown+columns-1)/columns;
        var detailLines=focus.target().detail().split("\n");int extraDetail=(detailLines.length-1)*12;
        int legendRows=(counts.size()+1)/2,height=78+extraDetail+rows*32+legendRows*14+25;
        float scale=Math.min(Math.max(1,2F/mc.getWindow().getGuiScale())*theme.uiScale.get(),Math.min(g.guiWidth()/280F,g.guiHeight()/(height+50F)));
        var box=focus.target().bounds();
        var anchor=WorldLabel.project(mc,new net.minecraft.world.phys.Vec3(box.getCenter().x,box.maxY+.35,box.getCenter().z),g.guiWidth(),g.guiHeight());
        if(anchor==null)anchor=WorldLabel.project(mc,box.getCenter(),g.guiWidth(),g.guiHeight());
        if(anchor==null){
            var camera=mc.gameRenderer.mainCamera();var direction=camera.forwardVector();
            var hit=box.clip(camera.position(),camera.position().add(direction.x()*512,direction.y()*512,direction.z()*512));
            if(hit.isPresent())anchor=WorldLabel.project(mc,hit.get(),g.guiWidth(),g.guiHeight());
        }
        if(anchor==null)return;
        float x,y;
        if(anchor[0]+20+width*scale<g.guiWidth()-8){x=anchor[0]+20;y=anchor[1]-height*scale*.35F;}
        else if(anchor[0]-20-width*scale>8){x=anchor[0]-20-width*scale;y=anchor[1]-height*scale*.35F;}
        else{x=anchor[0]-width*scale*.5F;y=anchor[1]-height*scale-12;if(y<36)y=anchor[1]+12;}
        x=Math.clamp(x,8,g.guiWidth()-width*scale-8);y=Math.clamp(y,36,g.guiHeight()-height*scale-8);
        int accent=theme.accent.argb(),panel=0xF5000000|(theme.panels.color.argb()&0xFFFFFF),card=0xFF000000|(theme.cards.color.argb()&0xFFFFFF);
        int ax=Math.round(anchor[0]),ay=Math.round(anchor[1]);
        int edgeX=Math.round(Math.clamp(anchor[0],x,x+width*scale)),edgeY=Math.round(Math.clamp(anchor[1],y,y+height*scale));
        g.fill(Math.min(ax,edgeX),ay,Math.max(ax,edgeX)+1,ay+1,accent);
        g.fill(edgeX,Math.min(ay,edgeY),edgeX+1,Math.max(ay,edgeY)+1,accent);g.fill(ax-2,ay-2,ax+2,ay+2,accent);
        var pose=g.pose();pose.pushMatrix();pose.translate(x,y);pose.scale(scale,scale);
        int radius=Math.round(theme.rounding.get()*.6F);
        rounded(g,0,2,width,height+2,0x66000000,radius);rounded(g,0,0,width,height,accent,radius);rounded(g,1,1,width-1,height-1,panel,Math.max(0,radius-1));
        g.fill(12,12,15,25,accent);g.text(font,"SPECTRE / DISCOVERIES",21,14,0xFFF0F5F7,false);
        String distance=String.format(Locale.ROOT,"%.0fm",focus.distance());
        g.text(font,distance,width-12-font.width(distance),14,accent,false);
        String label=font.plainSubstrByWidth(focus.target().label(),width-24);
        g.text(font,label,12,33,colors.applyAsInt(focus.target().category()),false);
        for(int i=0;i<detailLines.length;i++)g.text(font,font.plainSubstrByWidth(detailLines[i],width-24),12,46+i*12,0xFFD8E5EE,false);
        var signals=new HashSet<String>();for(var m:focus.nearby())signals.add(m.category()==Category.GROWTH?m.label():m.category().label);
        g.text(font,signals.size()+" clue types together / nearby objects",12,60+extraDetail,0xFFADBCC9,false);
        for(int i=0;i<shown;i++){
            var slot=slots.get(i);int sx=12+(i%columns)*33,sy=76+extraDetail+(i/columns)*32;
            rounded(g,sx,sy,sx+30,sy+29,card,3);g.fill(sx+3,sy+26,sx+27,sy+28,colors.applyAsInt(slot.category));
            if(slot.icon.texture()!=null)g.blit(slot.icon.texture(),sx+6,sy+2,sx+22,sy+18,0,1,0,1);
            else g.item(new ItemStack(slot.icon.item()),sx+6,sy+2);
            String count=String.valueOf(slot.count);g.text(font,count,sx+28-font.width(count),sy+16,0xFFFFFFFF,true);
        }
        int ly=79+extraDetail+rows*32,n=0;
        for(var entry:counts.entrySet()){
            int lx=12+(n%2)*118,yy=ly+(n/2)*14;n++;
            g.fill(lx,yy+2,lx+4,yy+8,colors.applyAsInt(entry.getKey()));
            int count=entry.getValue().values().stream().mapToInt(Integer::intValue).sum();
            String text=count+" "+entry.getKey().label;
            g.text(font,font.plainSubstrByWidth(text,109),lx+8,yy,0xFFE3EBF2,false);
        }
        String footer=slots.size()>shown?"+"+(slots.size()-shown)+" more item types":limited?"Detail limit reached - move closer":"Loaded objects / not proof of a base";
        g.text(font,footer,12,height-15,0xFF99AEBF,false);pose.popMatrix();
    }
    private static void rounded(GuiGraphicsExtractor g,int x,int y,int right,int bottom,int color,int radius){
        for(int dy=0;dy<radius;dy++){
            int inset=(int)Math.ceil(radius-Math.sqrt(radius*radius-Math.pow(radius-dy-.5,2)));
            g.fill(x+inset,y+dy,right-inset,y+dy+1,color);g.fill(x+inset,bottom-dy-1,right-inset,bottom-dy,color);
        }g.fill(x,y+radius,right,bottom-radius,color);
    }
}
