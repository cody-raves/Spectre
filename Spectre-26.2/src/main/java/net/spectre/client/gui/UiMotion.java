package net.spectre.client.gui;

import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

/** Frame-rate independent transitions. One instance per open screen, at most 512 controls. */
public final class UiMotion {
    public enum Direction { FROM_BOTTOM,FROM_TOP,FROM_LEFT,FROM_RIGHT,FROM_BOTTOM_LEFT,FROM_BOTTOM_RIGHT,FROM_TOP_LEFT,FROM_TOP_RIGHT }
    public record Key(int family,int x,int y,int width,int height){}
    public record Region(int x,int y,int width,int height){}
    private static final class Control {float hover;long last,pressed=Long.MIN_VALUE;Control(long now){last=now;}}
    private final Map<Key,Control> controls=new HashMap<>();
    private final long opened;
    private final Direction direction;
    private final long seed;
    private long closed=-1;
    private float closeFrom;
    public UiMotion(long now){this(now,Direction.FROM_BOTTOM,0);}
    public UiMotion(long now,Direction direction,long seed){opened=now;this.direction=direction;this.seed=seed;}
    public static Direction randomDirection(Direction previous){
        var values=Direction.values();
        int index=java.util.concurrent.ThreadLocalRandom.current().nextInt(values.length-(previous==null?0:1));
        if(previous!=null&&index>=previous.ordinal())index++;
        return values[index];
    }
    private float progress(long now,float speed){
        if(speed<=0)return closed<0?1:0;
        return closed<0?Math.clamp((now-opened)*speed/480f,0,1):Math.clamp(closeFrom-(now-closed)*speed/320f,0,1);
    }
    public void startClosing(long now,float speed){closeFrom=progress(now,speed);closed=now;}
    public boolean finishedClosing(long now,float speed){return closed>=0&&progress(now,speed)<=0;}
    public boolean building(long now,float speed){return speed>0&&(closed>=0?!finishedClosing(now,speed):progress(now,speed)<1);}
    /** Each opaque cell grows from its centre; closing traverses the same mask in reverse. */
    public List<Region> reveal(int x,int y,int width,int height,long now,float speed){
        float t=progress(now,speed);
        if(t>=1)return List.of(new Region(x,y,width,height));
        if(t<=0)return List.of();
        int tile=Math.max(8,(width+15)/16),rows=(height+tile-1)/tile,columns=(width+tile-1)/tile;
        var result=new ArrayList<Region>();
        for(int col=0;col*tile<width;col++){
            int run=-1;
            for(int row=0;row<=rows;row++){
                int hash=(col*73428767)^(row*912931)^(int)seed^(int)(seed>>>32);hash^=hash>>>13;
                float noise=(hash&255)/255f*1.3f;
                int depth=switch(direction){
                    case FROM_BOTTOM -> row;
                    case FROM_TOP -> rows-1-row;
                    case FROM_LEFT -> col;
                    case FROM_RIGHT -> columns-1-col;
                    case FROM_BOTTOM_LEFT -> row+col;
                    case FROM_BOTTOM_RIGHT -> row+columns-1-col;
                    case FROM_TOP_LEFT -> rows-1-row+col;
                    case FROM_TOP_RIGHT -> rows-1-row+columns-1-col;
                };
                int span=switch(direction){
                    case FROM_BOTTOM,FROM_TOP -> rows-1;
                    case FROM_LEFT,FROM_RIGHT -> columns-1;
                    default -> rows+columns-2;
                };
                float cell=Math.clamp((t*(span+4.3f)-depth-noise)/3f,0,1);
                cell=cell*cell*(3-2*cell);
                boolean full=row<rows&&cell>=1;
                if(full&&run<0)run=row;
                if(!full&&run>=0){
                    int top=Math.max(y,y+height-row*tile),bottom=y+height-run*tile;
                    result.add(new Region(x+col*tile,top,Math.min(tile,width-col*tile),bottom-top));run=-1;
                }
                if(row<rows&&!full&&cell>0){
                    int cw=Math.min(tile,width-col*tile),ch=Math.min(tile,height-row*tile);
                    int rw=Math.round(cw*cell),rh=Math.round(ch*cell);
                    if(rw>0&&rh>0)result.add(new Region(x+col*tile+(cw-rw)/2,Math.max(y,y+height-(row+1)*tile)+(ch-rh)/2,rw,rh));
                }
            }
        }
        return result;
    }
    public float hover(Key key,boolean hovered,long now,float speed){
        var state=controls.get(key);
        if(state==null){if(controls.size()>=512)return hovered?1:0;state=new Control(now-16);controls.put(key,state);}
        float target=hovered?1:0;
        float dt=Math.clamp((now-state.last)/1000f,0,.1f);state.last=now;
        state.hover=speed<=0?target:state.hover+(target-state.hover)*(1-(float)Math.exp(-22*dt*speed));
        if(Math.abs(state.hover-target)<.001f)state.hover=target;
        return state.hover;
    }
    public void press(Key key,long now){
        var state=controls.get(key);if(state==null&&controls.size()<512){state=new Control(now);controls.put(key,state);}
        if(state!=null)state.pressed=now;
    }
    public float pulse(Key key,long now,float speed){
        var state=controls.get(key);
        if(speed<=0||state==null||state.pressed==Long.MIN_VALUE)return 0;
        float t=Math.clamp((now-state.pressed)*speed/160f,0,1);return (1-t)*(1-t);
    }
}
