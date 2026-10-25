package net.spectre.client.gui;

import net.spectre.client.config.ThemeStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.client.gui.screens.inventory.*;
import com.example.client.mixin.AccessorAbstractContainerScreen;

/** Shared visual skin only. Vanilla owns widgets, hitboxes, menus and packets. */
public final class VanillaUi {
    private static net.minecraft.client.gui.screens.Screen activeScreen;
    private static UiMotion motion;
    private static UiMotion.Direction lastDirection;
    private static long frameTime;
    private static java.util.List<UiMotion.Region> buildRegions=java.util.List.of();
    private static boolean buildPass;
    private static net.minecraft.client.gui.screens.Screen closingScreen;
    private static UiMotion closingMotion;
    private static net.minecraft.client.multiplayer.ClientLevel closingLevel;
    private static net.minecraft.client.player.LocalPlayer closingPlayer;
    private static boolean closingPass;
    private VanillaUi() {}
    public static void beginFrame(net.minecraft.client.gui.screens.Screen screen){
        if(!enabled())return;
        frameTime=System.nanoTime()/1_000_000;
        if(!closingPass&&activeScreen!=screen){
            activeScreen=screen;lastDirection=UiMotion.randomDirection(lastDirection);
            motion=new UiMotion(frameTime,lastDirection,java.util.concurrent.ThreadLocalRandom.current().nextLong());
        }
        buildRegions=java.util.List.of();
        if(screen instanceof AbstractContainerScreen<?> && motion!=null&&motion.building(frameTime,ThemeStore.get().animationSpeed.get())){
            var b=(AccessorAbstractContainerScreen<?>)screen;
            int extraLeft=screen instanceof AbstractRecipeBookScreen<?>?182:4;
            int extraVertical=screen instanceof CreativeModeInventoryScreen?32:4;
            buildRegions=motion.reveal(b.spectre$left()-extraLeft,b.spectre$top()-extraVertical,b.spectre$width()+extraLeft+4,b.spectre$height()+2*extraVertical,frameTime,ThemeStore.get().animationSpeed.get());
        }
    }
    /** Observe the transition without delaying vanilla's menu close, input or network handling. */
    public static void changingScreen(net.minecraft.client.gui.screens.Screen next){
        var mc=Minecraft.getInstance();
        // Container onClose closes the player menu and then calls Screen.onClose.
        // Both request null; the second request must not erase the afterimage.
        if(next==null&&mc.gui.screen()==null)return;
        closingScreen=null;closingMotion=null;closingLevel=null;closingPlayer=null;
        if(next==null&&enabled()&&activeScreen==mc.gui.screen()&&activeScreen instanceof AbstractContainerScreen<?>
            &&motion!=null&&ThemeStore.get().animationSpeed.get()>0&&mc.level!=null&&mc.player!=null&&!mc.player.isDeadOrDying()){
            motion.startClosing(System.nanoTime()/1_000_000,ThemeStore.get().animationSpeed.get());
            closingScreen=activeScreen;closingMotion=motion;closingLevel=mc.level;closingPlayer=mc.player;
        }
        activeScreen=null;motion=null;
    }
    public static boolean closingPass(){return closingPass;}
    public static boolean hasClosingVisual(){return closingScreen!=null;}
    /** Render-only afterimage. The removed screen is never ticked or given input. */
    public static void extractClosing(GuiGraphicsExtractor g,float tick){
        if(closingScreen==null)return;
        var mc=Minecraft.getInstance();
        if(mc.gui.screen()!=null||mc.gui.overlay()!=null||mc.level!=closingLevel||mc.player!=closingPlayer||mc.player==null||mc.player.isDeadOrDying()
            ||!ThemeStore.get().customVanillaUi.get()||closingScreen.width!=mc.getWindow().getGuiScaledWidth()||closingScreen.height!=mc.getWindow().getGuiScaledHeight()
            ||closingMotion.finishedClosing(System.nanoTime()/1_000_000,ThemeStore.get().animationSpeed.get())){
            closingScreen=null;closingMotion=null;closingLevel=null;closingPlayer=null;return;
        }
        closingPass=true;activeScreen=closingScreen;motion=closingMotion;
        try{closingScreen.extractRenderStateWithTooltipAndSubtitles(g,-10000,-10000,tick);}
        finally{closingPass=false;activeScreen=null;motion=null;buildRegions=java.util.List.of();}
    }
    public static boolean building(){return enabled()&&activeScreen instanceof AbstractContainerScreen<?> && motion!=null&&motion.building(frameTime,ThemeStore.get().animationSpeed.get());}
    public static boolean buildPass(){return buildPass;}
    public static void stableBackdrop(GuiGraphicsExtractor g,int width,int height){
        int a=Math.round(ThemeStore.get().backgroundDim.get()*255);
        g.fillGradient(0,0,width,height,alpha(surface(),a),alpha(card(),Math.min(210,a+45)));
    }
    public static void build(GuiGraphicsExtractor g,Runnable extract){
        buildPass=true;
        try{
            for(var region:buildRegions){
                g.enableScissor(region.x(),region.y(),region.x()+region.width(),region.y()+region.height());
                try{extract.run();}finally{g.disableScissor();}
            }
        }finally{buildPass=false;}
    }
    private static float hover(int family,int x,int y,int w,int h,boolean on){
        return motion==null?(on?1:0):motion.hover(new UiMotion.Key(family,x,y,w,h),on,frameTime,ThemeStore.get().animationSpeed.get());
    }
    public static void press(int x,int y,int w,int h){
        if(enabled()&&motion!=null)motion.press(new UiMotion.Key(1,x,y,w,h),System.nanoTime()/1_000_000);
    }
    private static int mix(int a,int b,float t){
        int r=Math.round(((a>>16)&255)*(1-t)+((b>>16)&255)*t),g=Math.round(((a>>8)&255)*(1-t)+((b>>8)&255)*t),blue=Math.round((a&255)*(1-t)+(b&255)*t);
        return 0xff000000|(r<<16)|(g<<8)|blue;
    }
    private static void button(GuiGraphicsExtractor g,int x,int y,int w,int h,boolean highlighted,boolean disabled,int opacity){
        float t=hover(1,x,y,w,h,highlighted);
        float pulse=motion==null?0:motion.pulse(new UiMotion.Key(1,x,y,w,h),frameTime,ThemeStore.get().animationSpeed.get());
        int radius=Math.min(5,Math.round(ThemeStore.get().rounding.get()));
        int a=disabled?opacity*2/3:opacity;
        rounded(g,x,y,w,h,radius,alpha(mix(0xff344355,accent(),t*.85f),a));
        int inset=1+Math.round(pulse);
        rounded(g,x+inset,y+inset,w-2*inset,h-2*inset,Math.max(0,radius-1),alpha(mix(card(),accent(),t*.11f+pulse*.16f),a));
        if(t>.01f)rounded(g,x+3,y+h-2,Math.round((w-6)*t),1,0,alpha(accent(),100));
    }
    public static void slot(GuiGraphicsExtractor g,int x,int y,boolean hovered){
        float t=hover(2,x,y,18,18,hovered);
        rounded(g,x,y,18,18,2,mix(0xff344355,accent(),t*.85f));
        rounded(g,x+1,y+1,16,16,1,mix(card(),accent(),t*.13f));
    }
    public static boolean enabled() {
        var mc=Minecraft.getInstance();
        var screen=renderScreen();
        return mc!=null && mc.gui!=null && screen!=null && (screen.getClass().getName().startsWith("net.minecraft.")||screen instanceof net.spectre.client.media.MediaScreen)
            && ThemeStore.get().customVanillaUi.get();
    }
    private static net.minecraft.client.gui.screens.Screen renderScreen(){
        var mc=Minecraft.getInstance();return closingPass?closingScreen:mc==null||mc.gui==null?null:mc.gui.screen();
    }
    public static int accent(){return ThemeStore.get().accent.argbWithAlpha(255);}
    public static int surface(){return ThemeStore.get().panels.color.argbWithAlpha(255);}
    public static int card(){return ThemeStore.get().cards.color.argbWithAlpha(255);}
    public static int alpha(int rgb,int alpha){return (rgb&0xffffff)|(Math.clamp(alpha,0,255)<<24);}
    public static void rounded(GuiGraphicsExtractor g,int x,int y,int w,int h,int radius,int color){
        if(w<=0||h<=0)return;
        int r=Math.clamp(radius,0,Math.min(w,h)/2);
        g.fill(x,y+r,x+w,y+h-r,color);
        for(int dy=0;dy<r;dy++){
            int inset=(int)Math.ceil(r-Math.sqrt(r*r-(r-dy-.5)*(r-dy-.5)));
            g.fill(x+inset,y+dy,x+w-inset,y+dy+1,color);
            g.fill(x+inset,y+h-dy-1,x+w-inset,y+h-dy,color);
        }
    }
    public static void panel(GuiGraphicsExtractor g,int x,int y,int w,int h,boolean selected,int opacity){
        int r=Math.min(6,Math.round(ThemeStore.get().rounding.get()));
        if(w<=24 || h<=22)r=Math.min(r,2);
        rounded(g,x,y,w,h,r,alpha(selected?accent():0xff435064,opacity));
        rounded(g,x+1,y+1,w-2,h-2,Math.max(0,r-1),alpha(selected?surface():card(),opacity));
    }
    /** Replace the big vanilla texture plates, while leaving all small stateful artwork in place. */
    public static boolean containerCanvas(GuiGraphicsExtractor g,Identifier texture,int drawnWidth,int drawnHeight){
        var mc=Minecraft.getInstance();
        if(!enabled() || mc.player==null || !(renderScreen() instanceof AbstractContainerScreen<?> screen)
            || !texture.getNamespace().equals("minecraft") || !texture.getPath().startsWith("textures/gui/container/"))return false;
        var bounds=(AccessorAbstractContainerScreen<?>)screen;
        int w=bounds.spectre$width(),h=bounds.spectre$height();
        if(drawnWidth<w*3/4 || drawnHeight<40)return false;
        int x=bounds.spectre$left(),y=bounds.spectre$top();
        int radius=Math.clamp(Math.round(ThemeStore.get().rounding.get()),0,10);
        rounded(g,x-3,y-2,w+6,h+7,radius+2,0x44000000);
        rounded(g,x,y,w,h,radius,0xff3b4b5a);
        rounded(g,x+1,y+1,w-2,h-2,Math.max(0,radius-1),surface());
        g.fillGradient(x+6,y+15,x+w-6,y+34,alpha(card(),180),alpha(surface(),0));
        g.fill(x+10,y+1,x+Math.min(w-10,42),y+2,accent());
        // Keep inventory groups readable and separate from the workstation above them.
        int minX=Integer.MAX_VALUE,minY=Integer.MAX_VALUE,maxX=0,maxY=0;
        for(var slot:screen.getMenu().slots){
            if(slot.isActive() && slot.container==mc.player.getInventory() && slot.getContainerSlot()>=9&&slot.getContainerSlot()<36){
                minX=Math.min(minX,slot.x);minY=Math.min(minY,slot.y);maxX=Math.max(maxX,slot.x+16);maxY=Math.max(maxY,slot.y+16);
            }
        }
        if(minX!=Integer.MAX_VALUE){
            rounded(g,x+minX-3,y+minY-3,maxX-minX+6,maxY-minY+6,3,alpha(card(),170));
            g.fill(x+minX,y+minY-14,x+maxX,y+minY-13,alpha(0xff8ba2b4,35));
        }
        if(screen instanceof MerchantScreen){
            rounded(g,x+4,y+16,89,143,3,card());
            g.fill(x+100,y+20,x+101,y+h-10,alpha(accent(),45));
            arrow(g,x+181,y+38,22,14,alpha(accent(),115));
        }else if(screen instanceof InventoryScreen){
            rounded(g,x+26,y+8,51,69,4,card());arrow(g,x+136,y+32,18,14,alpha(accent(),150));
        }else if(screen instanceof CraftingScreen || screen instanceof CrafterScreen){
            arrow(g,x+91,y+35,24,15,alpha(accent(),150));
        }else if(screen instanceof AbstractFurnaceScreen){
            arrow(g,x+79,y+34,24,16,alpha(accent(),85));
            rounded(g,x+56,y+36,14,14,2,card());
        }else if(screen instanceof AnvilScreen){
            plus(g,x+49,y+40);arrow(g,x+99,y+42,22,14,alpha(accent(),115));
        }else if(screen instanceof SmithingScreen){
            arrow(g,x+111,y+48,20,14,alpha(accent(),115));
        }else if(screen instanceof GrindstoneScreen){
            arrow(g,x+83,y+35,24,16,alpha(accent(),115));
        }else if(screen instanceof CartographyTableScreen){
            plus(g,x+40,y+36);arrow(g,x+68,y+39,20,14,alpha(accent(),115));
        }else if(screen instanceof BrewingStandScreen){
            g.fill(x+80,y+29,x+81,y+56,alpha(accent(),110));g.fill(x+58,y+55,x+103,y+56,alpha(accent(),110));
            rounded(g,x+60,y+44,18,4,1,card());
        }
        return true;
    }
    public static boolean auxiliaryCanvas(GuiGraphicsExtractor g,Identifier texture,int x,int y,int w,int h){
        if(!enabled()||!texture.getNamespace().equals("minecraft"))return false;
        String path=texture.getPath();
        if((path.equals("textures/gui/recipe_book.png")||path.equals("textures/gui/book.png"))&&w>100&&h>100){
            panel(g,x,y,w,h,false,255);
            g.fill(x+8,y+1,x+40,y+2,accent());
            return true;
        }
        return false;
    }
    private static void plus(GuiGraphicsExtractor g,int x,int y){g.fill(x,y+3,x+9,y+5,alpha(accent(),150));g.fill(x+3,y,x+5,y+9,alpha(accent(),150));}
    private static void arrow(GuiGraphicsExtractor g,int x,int y,int w,int h,int color){
        g.fill(x,y+h/2-1,x+w-h/2,y+h/2+2,color);
        for(int i=0;i<h/2;i++)g.fill(x+w-h/2+i,y+i,x+w-h/2+i+1,y+h-i,color);
    }
    /** Preserve original texture geometry and functional artwork (progress, costs, icons). */
    public static int textureTint(Identifier texture,int original){
        if(!enabled() || !texture.getNamespace().equals("minecraft"))return original;
        String p=texture.getPath();
        if(!p.startsWith("textures/gui/container/") && !p.startsWith("textures/gui/advancements/") && !p.equals("textures/gui/book.png") && !p.equals("textures/gui/recipe_book.png"))return original;
        // The unlit texture is ~198 grey. Compensate so its broad surface follows the palette.
        int c=surface(),r=Math.min(255,((c>>16)&255)*255/198),g=Math.min(255,((c>>8)&255)*255/198),b=Math.min(255,(c&255)*255/198);
        return (original&0xff000000)|(r<<16)|(g<<8)|b;
    }
    public static int textColor(int original){
        if(!enabled())return original;
        var screen=renderScreen();
        if(!(screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) && !screen.getClass().getSimpleName().startsWith("Book"))return original;
        return switch(original|0xff000000){
            case 0xff000000,0xff404040,0xff3f3f3f -> 0xffedf4fa;
            case 0xff606060,0xff808080 -> 0xffa6b4c5;
            default -> original;
        };
    }
    public static boolean sprite(GuiGraphicsExtractor g,Identifier id,int x,int y,int w,int h,int color){
        if(!enabled() || !id.getNamespace().equals("minecraft"))return false;
        String p=id.getPath();int opacity=color>>>24;
        if(p.startsWith("widget/button") || p.startsWith("widget/text_field") || p.startsWith("widget/slider") || p.startsWith("widget/tab") || p.startsWith("container/anvil/text_field")){
            boolean highlighted=p.contains("highlighted"),disabled=p.contains("disabled");
            button(g,x,y,w,h,highlighted||p.contains("_selected"),disabled,opacity);
            if(p.contains("slider_handle"))rounded(g,x+2,y+2,w-4,h-4,2,alpha(mix(0xff597685,accent(),hover(3,x,y,w,h,highlighted)),opacity));
            return true;
        }
        if(p.startsWith("widget/checkbox")){
            button(g,x,y,w,h,p.contains("highlighted")||p.contains("selected"),false,opacity);
            float checked=hover(4,x,y,w,h,p.contains("selected"));
            for(int i=0;i<Math.round(4*checked);i++)g.fill(x+4+i,y+h/2+i,x+6+i,y+h/2+i+2,alpha(accent(),opacity));
            for(int i=0;i<Math.round(7*checked);i++)g.fill(x+7+i,y+h/2+3-i,x+9+i,y+h/2+5-i,alpha(accent(),opacity));
            return true;
        }
        if(p.startsWith("container/enchanting_table/enchantment_slot")){
            button(g,x,y,w,h,p.contains("highlighted"),p.contains("disabled"),opacity);return true;
        }
        if(p.startsWith("recipe_book/slot_")||p.equals("container/slot")){
            rounded(g,x,y,w,h,2,alpha(p.endsWith("_craftable")?accent():0xff435064,opacity));
            rounded(g,x+1,y+1,w-2,h-2,1,alpha(card(),opacity));return true;
        }
        if(p.equals("container/slot_highlight_back")){
            return true;
        }
        if(p.equals("container/slot_highlight_front")){
            return true;
        }
        if(p.startsWith("container/creative_inventory/tab_") || p.equals("recipe_book/background") || p.equals("social_interactions/background") || p.equals("recipe_book/tab") || p.equals("recipe_book/tab_selected") || p.startsWith("recipe_book/slot_")){
            button(g,x,y,w,h,p.contains("_selected_")||p.endsWith("_selected")||p.endsWith("_craftable"),false,opacity);return true;
        }
        if(p.contains("scroller") || p.equals("widget/scroller")){
            rounded(g,x,y,w,h,Math.min(3,w/2),alpha(p.contains("disabled")?0xff536071:accent(),opacity));return true;
        }
        return false;
    }
}
