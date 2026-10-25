package net.spectre.client.gui;

/** Procedural character rig shared by the native UI and the animation preview. */
public final class GhostRig {
    public enum Role { IDLE, ACTIVE, MOVEMENT, MUSIC, FOOD, MINING, GUARD, COMBAT, SCOUT, BUILDER, ORDERS, FLIGHT, FREECAM }
    public interface Canvas {
        void polygon(float[] xy, int argb);
        void line(float[] xy, int argb, float width);
    }
    private static final int INK=0xff0c121b, WHITE=0xfff7f8fa, CYAN=WHITE;
    private final float[] weights = new float[Role.values().length];
    private final float[] start = new float[weights.length];
    private Role target = Role.IDLE;
    private final GhostCloth cloth=new GhostCloth();
    private long changed, origin, last = -1;
    public GhostRig() { weights[Role.IDLE.ordinal()]=start[Role.IDLE.ordinal()]=1; }

    /** Startup-only apparition; independent of the HUD's module and phone poses. */
    public void drawSplash(Canvas c,long now,float progress,boolean still) {
        if(origin==0)origin=now;
        float t=still?0:(now-origin)/1000f;
        float arrival=still?1:smooth(Math.min(1,t/1.3f));
        float bob=still?0:sin(t*1.7f)*3.5f;
        float bank=still?0:sin(t*.9f)*.035f;
        cloth.step(now,bank,still?0:sin(t)*4,0,still?0:sin(t*1.7f)*3,still);
        Canvas g=new Rotate(c,128,128+bob+(1-arrival)*18,1,bank);
        float[] sheet=cloth.deform(body());
        // Materialize upward from the hem, then gently billow in place.
        for(int i=0;i<sheet.length;i+=2){
            float lower=Math.max(0,(sheet[i+1]+15)/80);
            sheet[i]+=lower*(still?0:sin(t*2+sheet[i+1]*.07f)*2);
            sheet[i+1]=62+(sheet[i+1]-62)*(.75f+.25f*arrival);
        }
        int opacity=Math.round((.2f+.8f*arrival)*255);
        g.polygon(sheet,(opacity<<24)|(WHITE&0xffffff));
        float blink=still?1:1-.92f*blink(t%7,5.6f,.35f);
        float gaze=still?0:sin(t*.8f)*2;
        if(arrival>.35f){
            eye(g,-20+gaze,-20,0,-1,blink,1,0);
            eye(g,9+gaze,-20,0,1,blink,1,0);
        }
    }

    private final java.util.Map<String,Float> moduleWeights=new java.util.LinkedHashMap<>();
    private final java.util.Map<String,Float> moduleStart=new java.util.LinkedHashMap<>();
    private String moduleTarget="idle";
    private long moduleChanged;
    private boolean moduleMode;
    /** Transitions are keyed by the exact module, including changes within a category. */
    public void draw(Canvas c,String id,long now,boolean reducedMotion){
        if(moduleWeights.isEmpty()){moduleWeights.put("idle",1f);moduleStart.put("idle",1f);moduleChanged=now;}
        updateModules(now,reducedMotion);
        if(!id.equals(moduleTarget)){
            moduleStart.clear();moduleStart.putAll(moduleWeights);
            moduleWeights.putIfAbsent(id,0f);moduleStart.putIfAbsent(id,0f);
            moduleTarget=id;moduleChanged=now;
            if(reducedMotion)updateModules(now,true);
        }
        moduleMode=true;
        try{draw(c,ModuleMascotCatalog.coreRole(id),now,reducedMotion);}finally{moduleMode=false;}
    }
    private void updateModules(long now,boolean instant){
        float p=instant?1:smooth(Math.min(1,Math.max(0,(now-moduleChanged)/1450f)));
        for(var entry:moduleWeights.entrySet())entry.setValue(lerp(moduleStart.getOrDefault(entry.getKey(),0f),entry.getKey().equals(moduleTarget)?1:0,p));
        moduleWeights.entrySet().removeIf(e->e.getValue()<.00001f&&!e.getKey().equals(moduleTarget));
    }
    public void draw(Canvas c, Role role, long now, boolean reducedMotion) {
        if(last<0){last=now;changed=now;origin=now;}
        update(now,reducedMotion);
        if(role!=target){System.arraycopy(weights,0,start,0,weights.length);target=role;changed=now;if(reducedMotion)update(now,true);}
        last=now;
        float t=reducedMotion?0:(now-origin)/1000f;
        float cam=weights[Role.FREECAM.ordinal()],hat=weights[Role.BUILDER.ordinal()];
        float orders=weights[Role.ORDERS.ordinal()],fly=weights[Role.FLIGHT.ordinal()];
        float evil=1-weights[Role.IDLE.ordinal()];
        float w=(float)(Math.PI/2);
        float drift=(sin(t*w)*18+sin(t*w*2)*6)*evil;
        float bob=(sin(t*w*2)*12+sin(t*w)*4)*evil
            +weights[Role.MUSIC.ordinal()]*sin(t*3)*2
            +weights[Role.MOVEMENT.ordinal()]*sin(t*25.13f)*2.6f;
        float vx=(float)Math.cos(t*w)*18*w+(float)Math.cos(t*w*2)*12*w;
        float ax=-sin(t*w)*18*w*w-sin(t*w*2)*24*w*w;
        float ay=-sin(t*w*2)*48*w*w-sin(t*w)*4*w*w;
        float bank=0; // Head and face remain upright; only the sheet bends.
        cloth.step(now,bank,reducedMotion?0:vx*evil,reducedMotion?0:ax*evil,reducedMotion?0:ay*evil,reducedMotion);
        float seated=1-evil;
        if(seated>.001f)ellipse(c,128,185,49,4,((int)(seated*65)<<24)|0x506574);
        Canvas g=new Rotate(c,128+drift,137+bob+22*seated,1+seated*.005f*sin(t*.8),bank);
        float propCam=propPhase(cam),propHat=propPhase(hat),propOrders=propPhase(orders);
        float armCam=smooth(Math.min(1,Math.max(0,(cam-.15f)/.35f)));
        float handX=42+armCam*(12+sin(t*(float)(Math.PI/4))*5),handY=12-armCam*(26+sin(t*(float)(Math.PI/2))*3);
        float hatX=lerp(39,-4,propHat),hatY=lerp(13,-66,propHat)-sin(propHat*Math.PI)*23;
        float lift=sin(propHat*Math.PI);
        float rightX=lerp(handX,hatX,lift),rightY=lerp(handY,hatY+10,lift);
        float extra=0;
        for(Role r:new Role[]{Role.SCOUT,Role.MINING,Role.FOOD,Role.GUARD})extra=Math.max(extra,propPhase(weights[r.ordinal()]));
        float reach=Math.max(extra,Math.max(armCam,Math.max(propOrders,lift)));
        // No detached noodle arms: short tapered extensions belong to the silhouette.
        if(reach>.001f) arm(g,25,1,rightX,rightY,reach);
        float fighting=propPhase(weights[Role.COMBAT.ordinal()]);
        if(fighting>.001f){
            float jab=reducedMotion?0:sin(t*2.4f);
            arm(g,-27,2,-43-Math.max(0,jab)*6,-4,fighting);
            arm(g,25,2,43+Math.max(0,-jab)*6,-4,fighting);
        }
        if(fly>.001f) arm(g,-29,2,-50,-7,fly);
        float running=propPhase(weights[Role.MOVEMENT.ordinal()]);
        if(running>.001f){arm(g,-28,3,-47,4+sin(t*12.57f)*13,running);arm(g,25,3,46,4-sin(t*12.57f)*13,running);}
        float[] sheet=cloth.deform(body());
        // Pool the lower sheet into a grounded sitting pose, with the head upright.
        for(int i=0;i<sheet.length;i+=2){
            float lower=Math.max(0,Math.min(1,(sheet[i+1]+22)/84));
            sheet[i]*=1+seated*lower*.24f;
            if(sheet[i+1]>-22)sheet[i+1]=-22+(sheet[i+1]+22)*(1-seated*.44f);
        }
        g.polygon(sheet,WHITE);
        if(running>.001f){
            float stride=reducedMotion?0:sin(t*12.57f);
            float[] feet=cloth.deform(new float[]{-18,60,11,62});
            sneaker(g,feet[0]-stride*10,feet[1]+4-Math.max(0,stride)*9,running,-1,stride*.22f);
            sneaker(g,feet[2]+stride*10,feet[3]+4-Math.max(0,-stride)*9,running,1,-stride*.22f);
        }
        float phone=propPhase(seated);
        float lookUp=reducedMotion?0:beat(t%10,4,6.3f);
        if(phone>.001f){
            // Soft local screen light remains inside the face silhouette.
            for(int i=7;i>0;i--)ellipse(g,-4,-6,14+i*2,14+i*2,
                ((int)(phone*(lookUp>.5f?2:4))<<24)|0x62cfe8);
            phone(g,-4,16,phone,t,reducedMotion);
        }
        // The eyes carry the entire expression; no mouth, brows, cheeks or outlines.
        // Held expression beats have eased entrances/exits, rather than restless jitter.
        float curious=reducedMotion?0:beat(t%16, .3f,2.6f);
        float suspicious=reducedMotion?0:beat(t%16,8.2f,10.5f);
        float delight=reducedMotion?0:cam*beat(t%4,2.4f,3.5f);
        float scan=reducedMotion?0:orders*(beat(t%8,1,3.3f)-beat(t%8,4.6f,6.8f));
        float gazeX=cam*(3+sin(t*(float)(Math.PI/4))*2)+orders*2+curious*3*(1-evil)-evil*suspicious*2;
        float gazeY=orders*(4+scan*2)-cam*2+seated*lerp(5,-1,lookUp);
        float blink=reducedMotion?1:Math.max(.055f,1-.96f*lerp(blink(t%10,7.4f,.7f),Math.max(blink(t%8f,4.25f,.30f),blink(t%8f,4.72f,.22f)*.75f),evil));
        float wink=reducedMotion?0:cam*blink(t%4,3.03f,.55f);
        float narrow=evil*(hat*.17f+orders*.12f+suspicious*.20f);
        float openness=lerp(.80f,1,evil)-narrow+delight*.12f;
        eye(g,-20+gazeX,-19+gazeY,evil,-1,
            blink*openness,1,suspicious*evil*.06f);
        eye(g,9+gazeX,-19+gazeY,evil,1,
            Math.max(.055f,blink*(1-wink*.94f))*openness,
            1,-suspicious*evil*.10f);
        extraProps(g,t,reducedMotion);
        if(moduleMode)for(var entry:moduleWeights.entrySet()){
            float amount=propPhase(entry.getValue());
            if(amount>.001f&&ModuleMascotCatalog.custom(entry.getKey()))
                ModuleMascotArt.draw(g,entry.getKey(),t,amount,reducedMotion);
        }
        if(propCam>.001f){
            float extension=propCam;
            float cameraX=rightX+extension*(12+sin(t*(float)(Math.PI/4))*15);
            float cameraY=rightY-extension*(57+sin(t*(float)(Math.PI/4)+.8f)*6);
            g.line(new float[]{rightX,rightY,cameraX,cameraY},WHITE,2.7f);
            camera(g,cameraX,cameraY,propCam*.78f,-.10f+sin(t*(float)(Math.PI/4))*.18f);
            if(!reducedMotion&&cam>.98f){float flash=blink(t%4,3.1f,.13f);if(flash>0){
                int color=((int)(flash*255)<<24)|0xffffff;
                g.line(new float[]{cameraX-10,cameraY,cameraX+10,cameraY},color,1.4f);
                g.line(new float[]{cameraX,cameraY-10,cameraX,cameraY+10},color,1.4f);
            }}
        }
        if(propHat>.001f)hardhat(g,hatX,hatY,propHat*.8f);
        if(propOrders>.001f)clipboard(g,lerp(29,38,propOrders),lerp(0,13,propOrders),propOrders*.78f,-.07f+sin(t*(float)(Math.PI/2))*.025f);
    }
    private void update(long now,boolean instant){
        float p=instant?1:smooth(Math.min(1,Math.max(0,(now-changed)/1450f)));
        for(int i=0;i<weights.length;i++)weights[i]=lerp(start[i],i==target.ordinal()?1:0,p);
    }
    private static float propPhase(float weight){
        // Outgoing props retract completely before the next prop enters.
        return smooth(Math.min(1,Math.max(0,(weight-.55f)/.45f)));
    }
    private static float blink(float t,float at,float duration){
        float p=(t-at)/duration;if(p<0||p>1)return 0;
        return (float)Math.pow(Math.sin(p*Math.PI),2);
    }
    private static float beat(float t,float from,float to){
        if(t<=from||t>=to)return 0;
        float edge=Math.min(.65f,(to-from)*.35f);
        return smooth(Math.min(1,(t-from)/edge))*smooth(Math.min(1,(to-t)/edge));
    }
    static void arm(Canvas c,float x,float y,float ex,float ey,float amount){
        if(amount<.025f)return;
        float dx=ex-x,dy=ey-y;
        float length=(float)Math.sqrt(dx*dx+dy*dy);
        if(length<3)return;
        float nx=-dy/length,ny=dx/length;
        float bend=Math.min(4,length*.12f);
        float mx=(x+ex)/2+nx*bend,my=(y+ey)/2+ny*bend;
        float[] xy=new float[66];int at=0;
        for(int side=0;side<2;side++)for(int i=0;i<=15;i++){
            float p=side==0?i/15f:1-i/15f,a=1-p;
            float px=a*a*x+2*a*p*mx+p*p*ex,py=a*a*y+2*a*p*my+p*p*ey;
            float tx=2*a*(mx-x)+2*p*(ex-mx),ty=2*a*(my-y)+2*p*(ey-my);
            float norm=(float)Math.sqrt(tx*tx+ty*ty),width=lerp(Math.min(8,length*.25f),Math.min(4,length*.18f),p)*(side==0?1:-1);
            xy[at++]=x+(px-ty/norm*width-x)*amount;
            xy[at++]=y+(py+tx/norm*width-y)*amount;
        }
        float[] out=java.util.Arrays.copyOf(xy,at);c.polygon(out,WHITE);
        ellipse(c,x+(ex-x)*amount,y+(ey-y)*amount,4*amount,4*amount,WHITE);
    }
    private static float[] body(){
        // A continuous sheet with three broad, rounded hem folds.
        Path p=new Path(-4,-67);
        p.curve(22,-67,40,-48,40,-22);
        p.curve(40,-4,38,13,43,32);
        p.curve(47,43,53,45,52,51);
        p.curve(51,59,43,59,37,54);
        p.curve(30,49,29,47,24,48);
        p.curve(18,48,20,63,11,62);
        p.curve(3,62,3,50,-4,50);
        p.curve(-12,49,-10,62,-20,60);
        p.curve(-32,57,-36,43,-39,32);
        p.curve(-45,16,-48,-4,-48,-22);
        p.curve(-48,-48,-30,-67,-4,-67);
        return p.points();
    }
    private static void eye(Canvas c,float x,float y,float evil,int side,float blink,float width,float tilt){
        Canvas g=new Rotate(c,x,y,1,side*evil*-.12f+tilt);
        Path p=new Path(-10,lerp(0,-8,evil)*blink);
        p.curve(lerp(-10,-13,evil),lerp(-17,-13,evil)*blink,10,lerp(-17,0,evil)*blink,11,lerp(0,6,evil)*blink);
        p.curve(lerp(10,5,evil),lerp(17,15,evil)*blink,-10,lerp(17,10,evil)*blink,-10,lerp(0,-8,evil)*blink);
        float[] xy=p.points();
        for(int i=0;i<xy.length;i+=2)xy[i]*=width*(side==1?-1:1);
        g.polygon(xy,INK);
    }
    private static final class Path {
        final java.util.ArrayList<Float> xy=new java.util.ArrayList<>();float x,y;
        Path(float x,float y){this.x=x;this.y=y;xy.add(x);xy.add(y);}
        void curve(float ax,float ay,float bx,float by,float ex,float ey){
            for(int i=1;i<=12;i++){float p=i/12f,a=1-p;xy.add(a*a*a*x+3*a*a*p*ax+3*a*p*p*bx+p*p*p*ex);xy.add(a*a*a*y+3*a*a*p*ay+3*a*p*p*by+p*p*p*ey);}
            x=ex;y=ey;
        }
        float[] points(){float[] out=new float[xy.size()-2];for(int i=0;i<out.length;i++)out[i]=xy.get(i);return out;}
    }
    private static void camera(Canvas c,float x,float y,float scale,float angle) {
        Canvas g=new Rotate(c,x,y,scale,angle);
        rounded(g,-15,-10,30,20,4,WHITE);
        ellipse(g,2,0,8,8,INK);ellipse(g,2,0,5,5,CYAN);ellipse(g,2,0,3,3,INK);ellipse(g,0,-2,1.5f,1.5f,WHITE);
        rounded(g,-12,-13,8,4,1,INK);rounded(g,-10,-5,4,3,1,WHITE);
    }
    private static void phone(Canvas c,float x,float y,float scale,float t,boolean still){
        float tap=still?0:sin(t*7)*.9f;
        Canvas g=new Rotate(c,x,y,scale,-.08f+tap*.015f);
        // Hands cradle the phone; thumbs and screen face the ghost behind the handset.
        ellipse(g,-9,7+tap,4,5,WHITE);ellipse(g,9,6-tap,4,5,WHITE);
        ellipse(g,-5,3+tap,2.7f,3.4f,WHITE);ellipse(g,5,3-tap,2.7f,3.4f,WHITE);
        rounded(g,-10,-15,20,30,3,INK);
        rounded(g,-8.5f,-13.5f,17,27,2,0xff365263);
        rounded(g,-7,-12,7,11,2,0xff172732);
        ellipse(g,-3.5f,-9,2,2,0xff7595a4);ellipse(g,-3.5f,-4,2,2,0xff7595a4);
        ellipse(g,3,-9,1.1f,1.1f,0xffd2d6c5);
        g.line(new float[]{-2,10,3,10},0xff698492,1);
    }
    private static void sneaker(Canvas c,float x,float y,float amount,int side,float angle){
        Canvas g=new Rotate(c,x,y,amount,angle);
        rounded(g,-6,-10,12,9,2,WHITE);
        // The toes point outward; foot lifts alternate beneath the flowing hem.
        float[] outline={-12,-7,2,-7,6,-2,15,1,17,7,-13,7};
        for(int i=0;i<outline.length;i+=2)outline[i]*=side;
        g.polygon(outline,INK);
        float[] upper={-10,-5,1,-5,5,0,13,3,14,4,-10,4};
        for(int i=0;i<upper.length;i+=2)upper[i]*=side;
        g.polygon(upper,0xffe95864);
        g.line(new float[]{-11*side,5,15*side,5},WHITE,3);
        g.line(new float[]{-6*side,-2,1*side,1},WHITE,3);
        g.line(new float[]{-1*side,-4,2*side,-2},INK,1);
        g.line(new float[]{2*side,-2,5*side,0},INK,1);
    }
    private static void boxingGlove(Canvas c,float x,float y,float amount,int side,float jab){
        Canvas g=new Rotate(c,x+side*jab*6,y,amount,side*.12f);
        rounded(g,-8,6,16,9,2,INK);rounded(g,-5,8,10,5,1,0xffc3cdd2);
        ellipse(g,0,-2,12,13,INK);ellipse(g,0,-2,9.5f,10.5f,0xffcf5965);
        ellipse(g,-side*9,3,5,7,INK);ellipse(g,-side*9,3,3.2f,5,0xffe17880);
        g.line(new float[]{-4,-8,2,-10,5,-8},0xffed9ba2,1.5f);
    }
    private void extraProps(Canvas c,float t,boolean still){
        float scout=propPhase(weights[Role.SCOUT.ordinal()]),mine=propPhase(weights[Role.MINING.ordinal()]);
        float combat=propPhase(weights[Role.COMBAT.ordinal()]),food=propPhase(weights[Role.FOOD.ordinal()]);
        float music=propPhase(weights[Role.MUSIC.ordinal()]),guard=propPhase(weights[Role.GUARD.ordinal()]);
        float fly=propPhase(weights[Role.FLIGHT.ordinal()]);
        float wave=still?0:sin(t*2);
        if(scout>.001f){
            float raised=still?1:beat(t%8,1,6.6f);
            Canvas g=new Rotate(c,-4+wave*2,lerp(8,-20,raised),scout,wave*.035f);
            rounded(g,-21,-13,15,25,5,INK);rounded(g,6,-13,15,25,5,INK);
            rounded(g,-18,-10,9,18,3,0xff4d746a);rounded(g,9,-10,9,18,3,0xff4d746a);
            g.line(new float[]{-7,-3,7,-3},INK,7);rounded(g,-5,-7,10,7,2,0xff819f95);
            ellipse(g,-14,8,10,10,INK);ellipse(g,14,8,10,10,INK);
            ellipse(g,-14,8,7,7,0xff70aabd);ellipse(g,14,8,7,7,0xff70aabd);
            ellipse(g,-16,6,2.5f,2.5f,0xffd2f2f5);ellipse(g,12,6,2.5f,2.5f,0xffd2f2f5);
            ellipse(g,-23,5,4,6,WHITE);ellipse(g,23,5,4,6,WHITE);
        }
        if(mine>.001f){Canvas g=new Rotate(c,48,-3,mine,-.3f+wave*.22f);
            g.line(new float[]{-7,22,5,-16},INK,7);g.line(new float[]{-7,22,5,-16},0xffb3915e,4);
            Path head=new Path(-14,-14);head.curve(-3,-23,17,-21,25,-9);head.curve(11,-15,2,-14,-14,-10);head.curve(-15,-11,-15,-13,-14,-14);
            g.polygon(head.points(),0xff83b9c9);
        }
        if(combat>.001f){float jab=still?0:sin(t*2.4f);
            boxingGlove(c,-43,-4,combat,-1,Math.max(0,jab));
            boxingGlove(c,43,-4,combat,1,Math.max(0,-jab));
        }
        if(guard>.001f){Canvas g=new Rotate(c,41,9,guard,wave*.035f);
            Path shield=new Path(-17,-21);shield.curve(-9,-24,9,-24,17,-21);shield.curve(18,-4,13,14,0,22);shield.curve(-13,14,-18,-4,-17,-21);
            g.polygon(shield.points(),INK);
            Path inset=new Path(-13,-18);inset.curve(-6,-20,6,-20,13,-18);inset.curve(14,-3,10,10,0,17);inset.curve(-10,10,-14,-3,-13,-18);
            g.polygon(inset.points(),0xff538d83);
            g.line(new float[]{0,-12,0,9},0xffddcfed,3);g.line(new float[]{-8,-3,8,-3},0xffddcfed,3);
        }
        if(food>.001f){Canvas g=new Rotate(c,38,-1-wave*4,food,0);
            ellipse(g,0,0,12,12,INK);ellipse(g,0,0,10,10,0xffdcb478);
            for(float[] spot:new float[][]{{-4,-4},{4,-3},{0,4},{6,5},{-6,3}})ellipse(g,spot[0],spot[1],1.6f,1.6f,0xff694b37);
            ellipse(g,-8,7,4,4,WHITE);
        }
        if(music>.001f){Canvas g=new Rotate(c,-4,-48,music,0);
            float[] arc=new float[34];for(int i=0;i<17;i++){float a=(float)Math.PI+i/16f*(float)Math.PI;arc[i*2]=38*(float)Math.cos(a);arc[i*2+1]=25*(float)Math.sin(a);}
            g.line(arc,INK,7);g.line(arc,0xff8a7db9,4);
            rounded(g,-42,-3,12,21,4,INK);rounded(g,30,-3,12,21,4,INK);
            rounded(g,-39,0,6,15,2,0xffb5a3e2);rounded(g,33,0,6,15,2,0xffb5a3e2);
            g.line(new float[]{48,8+wave*2,48,-3+wave*2,54,-5+wave*2},0xffb5a3e2,2);ellipse(g,46,9+wave*2,3,2,0xffb5a3e2);
        }
        if(fly>.001f){Canvas g=new Rotate(c,-4,-41,fly,0);
            g.line(new float[]{-36,0,36,0},0xffba9668,4);
            rounded(g,-21,-7,19,14,5,INK);rounded(g,2,-7,19,14,5,INK);
            rounded(g,-18,-4,13,8,3,0xff80bfd3);rounded(g,5,-4,13,8,3,0xff80bfd3);
            g.line(new float[]{-2,0,2,0},0xffba9668,3);
        }
    }
    private static void hardhat(Canvas c,float x,float y,float scale){
        Canvas g=new Rotate(c,x,y,scale,0);
        Path outer=new Path(-31,7);
        outer.curve(-31,-16,-18,-27,0,-27);outer.curve(19,-27,31,-16,31,7);outer.curve(14,8,-14,8,-31,7);
        g.polygon(outer.points(),INK);
        Path dome=new Path(-28,6);
        dome.curve(-28,-14,-16,-24,0,-24);dome.curve(17,-24,28,-14,28,6);dome.curve(14,7,-14,7,-28,6);
        g.polygon(dome.points(),0xffffc443);
        g.line(new float[]{-4,-23,-4,5},0xffffe99e,4);
        g.line(new float[]{-20,-12,-22,4},0xffbd7b18,2);
        g.line(new float[]{20,-12,22,4},0xffbd7b18,2);
        rounded(g,-35,5,70,11,4,INK);rounded(g,-32,7,64,7,3,0xffffd365);
        rounded(g,-24,-1,6,2,1,0xff98671d);rounded(g,18,-1,6,2,1,0xff98671d);
    }
    private static void clipboard(Canvas c,float x,float y,float scale,float angle){
        Canvas g=new Rotate(c,x,y,scale,angle);
        rounded(g,-20,-28,40,56,4,INK);
        rounded(g,-17.5f,-25.5f,35,51,2,0xffc59a58);
        rounded(g,-14,-21,28,43,1,0xfffff9ec);
        rounded(g,-10,-31,20,10,3,INK);rounded(g,-7.5f,-29,15,6,2,0xffa6b5c2);
        for(int i=0;i<4;i++){
            float row=-11+i*8;
            rounded(g,-10,row,4.5f,4.5f,.5f,0xff405363);
            rounded(g,-9,row+1,2.5f,2.5f,.2f,0xfffff9ec);
            g.line(new float[]{-2,row+2,9,row+2},0xff405363,1.4f);
            if(i<3)g.line(new float[]{-10,row+2,-8,row+4,-5,row-.5f},0xff258f7e,1.8f);
        }
    }
    private static void rounded(Canvas c,float x,float y,float w,float h,float radius,int color) {
        float[] xy=new float[40*2];int at=0;
        for(int corner=0;corner<4;corner++)for(int i=0;i<10;i++){float a=(float)(-Math.PI/2+corner*Math.PI/2+i/9f*Math.PI/2);float cx=corner<2?x+w-radius:x+radius,cy=corner==0||corner==3?y+radius:y+h-radius;xy[at++]=cx+radius*(float)Math.cos(a);xy[at++]=cy+radius*(float)Math.sin(a);}
        c.polygon(xy,color);
    }
    private static void triangle(Canvas c,float a,float b,float d,float e,float f,float g,int color){c.polygon(new float[]{a,b,d,e,f,g},color);}
    private static void ellipse(Canvas c,float x,float y,float rx,float ry,int color) {
        float[] xy=new float[64];for(int i=0;i<32;i++){float a=i/32f*(float)Math.PI*2;xy[i*2]=x+rx*(float)Math.cos(a);xy[i*2+1]=y+ry*(float)Math.sin(a);}c.polygon(xy,color);
    }
    private static float sin(double t){return (float)Math.sin(t);}
    private static float pulse(float t,float center,float half){return Math.max(0,1-Math.abs(t-center)/half);}
    private static float smooth(float p){return p*p*p*(p*(p*6-15)+10);}
    private static float lerp(float a,float b,float p){return a+(b-a)*p;}
    private static int lerpColor(int a,int b,float p){int out=0;for(int shift=0;shift<32;shift+=8)out|=((int)lerp((a>>>shift)&255,(b>>>shift)&255,p))<<shift;return out;}
    private static class Transform implements Canvas {
        final Canvas canvas;final float x,y,s;
        Transform(Canvas c,float x,float y){this(c,x,y,1);}
        Transform(Canvas c,float x,float y,float s){canvas=c;this.x=x;this.y=y;this.s=s;}
        float[] map(float[] xy){float[] out=xy.clone();for(int i=0;i<out.length;i+=2){out[i]=s==1?out[i]+x:x+(out[i]-x)*s;out[i+1]=s==1?out[i+1]+y:y+(out[i+1]-y)*s;}return out;}
        public void polygon(float[] xy,int color){if(s>=.02f)canvas.polygon(map(xy),color);}
        public void line(float[] xy,int color,float width){if(s>=.02f)canvas.line(map(xy),color,width*s);}
    }
    private static final class Rotate implements Canvas {
        final Canvas canvas;final float x,y,s,cos,sin;
        Rotate(Canvas c,float x,float y,float s,float a){canvas=c;this.x=x;this.y=y;this.s=s;cos=(float)Math.cos(a);sin=(float)Math.sin(a);}
        float[] map(float[] xy){float[] out=xy.clone();for(int i=0;i<out.length;i+=2){out[i]=x+s*(xy[i]*cos-xy[i+1]*sin);out[i+1]=y+s*(xy[i]*sin+xy[i+1]*cos);}return out;}
        public void polygon(float[] xy,int color){if(s>=.02f)canvas.polygon(map(xy),color);}
        public void line(float[] xy,int color,float width){if(s>=.02f)canvas.line(map(xy),color,width*s);}
    }
}
