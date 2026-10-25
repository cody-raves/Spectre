package net.spectre.client.media;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.Component;
import net.spectre.client.gui.VanillaUi;

public final class MediaScreen extends Screen {
    private final Screen parent;
    private EditBox address;
    private int left,top,viewW,viewH;
    private boolean browserFocus,mouseHeld;
    private String message;
    public MediaScreen(Screen parent){super(Component.literal("Spectre Media Player"));this.parent=parent;}
    @Override protected void init(){
        left=12;top=78;viewW=Math.max(100,width-24);viewH=Math.max(80,height-top-24);
        address=addRenderableWidget(new EditBox(font,12,48,Math.max(80,width-84),20,Component.literal("YouTube or SoundCloud link")));
        address.setMaxLength(2048);address.setValue(MediaPlayer.url());
        addRenderableWidget(Button.builder(Component.literal("Go"),b->navigate()).bounds(width-66,48,54,20).build());
        addRenderableWidget(Button.builder(Component.literal("YouTube"),b->navigate("https://www.youtube.com/")).bounds(12,20,78,20).build());
        addRenderableWidget(Button.builder(Component.literal("SoundCloud"),b->navigate("https://soundcloud.com/")).bounds(96,20,92,20).build());
        addRenderableWidget(Button.builder(Component.literal("Pin & play"),b->{MediaPlayer.pin();minecraft.gui.setScreen(null);}).bounds(width-178,20,90,20).build());
        addRenderableWidget(Button.builder(Component.literal("Close"),b->onClose()).bounds(width-82,20,70,20).build());
        MediaPlayer.resume();
    }
    private void navigate(){navigate(address.getValue());}
    private void navigate(String url){try{MediaPlayer.load(url);address.setValue(url);message=null;}catch(IllegalArgumentException bad){message=bad.getMessage();}}
    @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float tick){g.fill(0,0,width,height,0xF008101A);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float tick){
        if(!address.isFocused()&&MediaPlayer.browser()!=null&&!MediaPlayer.url().equals("about:blank"))address.setValue(MediaPlayer.url());
        VanillaUi.rounded(g,left-2,top-2,viewW+4,viewH+4,4,0xFF293C4D);
        g.fill(left,top,left+viewW,top+viewH,0xFF101823);
        if(MediaPlayer.browser()==null)g.text(font,MediaPlayer.status(),left+12,top+20,0xFFCDD8E5,false);
        else{MediaPlayer.draw(g,left,top,viewW,viewH);if(inside(mx,my))g.requestCursor(MediaPlayer.browser().getCursorType());}
        if(message!=null)g.text(font,message,12,height-14,0xFFFF9C9C,false);
        super.extractRenderState(g,mx,my,tick);
    }
    private boolean inside(double x,double y){return x>=left&&x<left+viewW&&y>=top&&y<top+viewH;}
    private MouseButtonEvent translate(MouseButtonEvent e){return new MouseButtonEvent(MediaPlayer.pixelX(e.x()-left,viewW),MediaPlayer.pixelY(e.y()-top,viewH),e.buttonInfo());}
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean doubled){
        browserFocus=inside(e.x(),e.y())&&MediaPlayer.browser()!=null;
        if(MediaPlayer.browser()!=null)MediaPlayer.browser().setFocus(browserFocus);
        if(browserFocus){address.setFocused(false);mouseHeld=true;MediaPlayer.browser().onMouseClicked(translate(e),doubled);return true;}
        return super.mouseClicked(e,doubled);
    }
    @Override public boolean mouseReleased(MouseButtonEvent e){if(mouseHeld&&MediaPlayer.browser()!=null){mouseHeld=false;MediaPlayer.browser().onMouseReleased(translate(e));return true;}return super.mouseReleased(e);}
    @Override public void mouseMoved(double x,double y){if(MediaPlayer.browser()!=null)MediaPlayer.browser().onMouseMoved(MediaPlayer.pixelX(x-left,viewW),MediaPlayer.pixelY(y-top,viewH));super.mouseMoved(x,y);}
    @Override public boolean mouseDragged(MouseButtonEvent e,double dx,double dy){if(mouseHeld){mouseMoved(e.x(),e.y());return true;}return super.mouseDragged(e,dx,dy);}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){if(inside(x,y)&&MediaPlayer.browser()!=null){MediaPlayer.browser().onMouseScrolled(MediaPlayer.pixelX(x-left,viewW),MediaPlayer.pixelY(y-top,viewH),vertical);return true;}return super.mouseScrolled(x,y,horizontal,vertical);}
    @Override public boolean keyPressed(KeyEvent e){
        if(e.key()==256){onClose();return true;}
        if(address.isFocused()&&e.key()==257){navigate();return true;}
        if(browserFocus&&MediaPlayer.browser()!=null){MediaPlayer.browser().onKeyPressed(e);return true;}return super.keyPressed(e);
    }
    @Override public boolean keyReleased(KeyEvent e){if(browserFocus&&MediaPlayer.browser()!=null){MediaPlayer.browser().onKeyReleased(e);return true;}return super.keyReleased(e);}
    @Override public boolean charTyped(CharacterEvent e){if(browserFocus&&MediaPlayer.browser()!=null){MediaPlayer.browser().onCharTyped(e);return true;}return super.charTyped(e);}
    @Override public void onClose(){MediaPlayer.stop();minecraft.gui.setScreen(parent);}
    @Override public void removed(){MediaPlayer.screenClosed();}
    @Override public boolean isPauseScreen(){return false;}
}
