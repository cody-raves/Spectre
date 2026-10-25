package net.spectre.client.gui;

/** Small, semantic props with a dedicated drawing and gesture for each exact module. */
public final class ModuleMascotArt {
    private static final int INK=0xff0c121b,WHITE=0xfff7f8fa,BLUE=0xff7bbbcf,GOLD=0xffe5bc72,
        GREEN=0xff70aa96,RED=0xffdf7481,PURPLE=0xffaa8bc9;
    private ModuleMascotArt() { }
    public static void draw(GhostRig.Canvas c,String id,float time,float amount,boolean still){
        if(amount<.02f)return;
        float t=still?0:time,w=(float)Math.sin(t*1.7)*amount,v=(float)Math.sin(t*2.5)*amount,q=(1+w)*.5f;
        boolean head=id.equals("esp")||id.equals("pharaoh-headdress")||id.equals("donut-nofall");
        if(!head)GhostRig.arm(c,25,1,42,12,amount);
        float arc=(float)Math.sin(amount*Math.PI)*10;
        P p=new P(c,head?35-39*amount:18+25*amount,head?14-49*amount-arc:20-12*amount-arc,amount,0);
        switch(id){
            case "aim-assist"->{target(p,0,0,15);p.line(RED,2,-24+q*10,-13,0,0);arrow(p,-20+q*8,-10,-.35f,RED);}
            case "anchor-macro"->{cube(p,0,4,16,PURPLE);p.rect(-6,-3,12,10,INK);p.circle(0,-5-q*17,4,BLUE);p.line(BLUE,2,-4,11,4,11);}
            case "crystal-macro"->{p.rect(-19,14,38,7,INK);p.rect(-16,15,32,3,BLUE);diamond(p,0,-5+q*15,9,PURPLE);}
            case "crystal-aura"->{for(int i=0;i<3;i++){float a=t*.8f+i*2.094f;diamond(p,cos(a)*18,sin(a)*18,6,PURPLE);}p.circle(0,0,3,BLUE);}
            case "triggerbot"->{p.rect(-10,-18,20,37,INK);p.rect(-7,-15,14,28,PURPLE);p.circle(0,-9,3,q>.75?GREEN:RED);p.circle(0,6+q,5,INK);p.line(WHITE,4,11,3,5,6+q);}
            case "shield-breaker"->{shield(p,-6,3,BLUE);p.line(INK,2,-6,-9,-1,-3,-9,3,-3,13);axe(p,13-q*6,-12+q*5,.3f);}
            case "mace"->{P g=p.at(0,-w*4,-.3f+w*.18f);g.line(GOLD,5,0,19,0,-12);g.rect(-12,-25,24,18,INK);g.rect(-9,-22,18,12,BLUE);g.line(WHITE,2,-5,-21,-5,-12);}
            case "hovertotem"->{totem(p,-5,0,GOLD);arrow(p,15-q*14,-15+q*12,.5f,WHITE);}
            case "fastxp"->{P g=p.at(-3,-4,-q*.8f);bottle(g,0,0,GREEN);for(int i=0;i<3;i++)p.circle(15+i*3,8+((t*12+i*6)%20),2,GREEN);}
            case "autoinvtotem"->{p.rect(-17,-14,31,35,INK);p.rect(-14,-11,25,28,BLUE);p.rect(-10,6,17,9,INK);totem(p,8,-8-q*17,GOLD);}
            case "w-tap-recode"->{key(p,-13,-12+q*4,26,WHITE);p.line(INK,2,-7,-6+q*4,-4,4+q*4,0,-1+q*4,4,4+q*4,7,-6+q*4);}
            case "crystal-optimizer"->{diamond(p,-5,-5,11,PURPLE);wrench(p,9,7,w*.28f);}
            case "spear-swap"->{P g=p.at(0,0,w*.3f);g.line(GOLD,4,0,25,0,-20);g.poly(BLUE,-6,-16,0,-30,6,-16);g.line(WHITE,3,-8,7,7,7);}
            case "legit-auto-tool"->{p.rect(-20,6,40,14,INK);p.rect(-17,8,34,8,GOLD);wrench(p,-11,-2-q*8,-.3f);hammer(p,11,-3+(1-q)*5,.2f);}
            case "auto-clicker"->{p.rect(-13,-22,26,42,INK);p.rect(-10,-19,20,34,WHITE);p.line(INK,2,0,-18,0,-2);p.circle(-5,-11+v,4,RED);p.circle(5,-11-v,4,BLUE);p.line(BLUE,2,0,-22,0,-29,8,-32);}
            case "auto-replenish"->{crate(p,0,9);cube(p,-10+q*15,-20+q*17,8,GREEN);p.line(GOLD,2,-16,2,-16,15,16,15);}
            case "auto-reconnect"->{plug(p,-19+q*10,0,1);plug(p,19-q*10,0,-1);p.line(BLUE,2,-30,0,-22,0);p.line(BLUE,2,22,0,30,0);}
            case "anti-vanish"->{person(p,0,5,0xff8291a4);lens(p,-12+q*24,-2,11,GREEN);p.line(GREEN,2,-17,20,17,20);}
            case "player-detection"->{p.rect(-10,-15,20,32,INK);p.rect(-7,-12,14,26,GREEN);p.line(INK,3,-6,-16,-6,-29);for(int i=0;i<3;i++)p.line(WHITE,2,-4+i*4,9,-4+i*4,4-i*3*q);}
            case "auto-relog"->{p.rect(-16,-23,32,44,INK);p.rect(-12,-20,24,37,BLUE);P g=p.at(0,0,w*.8f);g.line(WHITE,3,0,-17,0,15);g.line(GOLD,2,0,0,14,0);person(p,-5+q*10,9,GREEN);}
            case "anti-trap"->{ring(p,-9,3,8,BLUE);ring(p,8,3,8,BLUE);p.line(GOLD,4,-12,-18,0,-4,12,-18);p.line(RED,4,-13,-18,-18,-29);p.line(RED,4,13,-18,18,-29+q*5);}
            case "air-stuck"->{p.poly(BLUE,-15,-24,15,-24,23,0,13,23,-13,23,-23,0);person(p,0,0,WHITE);p.line(WHITE,2,-16,-13,-7,-18);p.line(WHITE,2,8,10,15,5);}
            case "spawner-protect"->{cage(p,0,0);lock(p,8,10+q*2);}
            case "staff-list"->{card(p,0,1,18,24,GOLD);star(p,0,-10,7,WHITE);for(int i=0;i<3;i++)p.line(INK,2,-9,3+i*6,6+q*3,3+i*6);}
            case "player-bypass"->{p.line(BLUE,3,-17,-24,-17,23);p.line(BLUE,3,17,-24,17,23);p.line(GOLD,3,-17,-13,17,-13);card(p,-15+q*30,4,9,14,WHITE);person(p,-15+q*30,2,GREEN);}
            case "netherite-finder"->{p.line(GOLD,4,8,-19,-6,12);P g=p.at(-9+w*12,17,0);ring(g,0,0,14,PURPLE);g.line(PURPLE,3,0,-13,0,-22);p.rect(1,-24,15,10,INK);p.circle(8,-19,3,GREEN);}
            case "render-distance-toggle"->{P g=p.at(0,0,-.4f);g.rect(-22,-8,21+q*15,16,INK);g.rect(-18,-5,17+q*15,10,GOLD);g.rect(2+q*15,-10,8,20,BLUE);g.circle(7+q*15,0,6,WHITE);}
            case "home-meta"->{p.line(GOLD,4,-10,4,13,4);ring(p,-15,4,8,GOLD);p.line(GOLD,4,7,4,7,12,13,12,13,4);house(p,3,-13+w*2);}
            case "trident-util"->{trident(p,-6,0,GOLD);p.line(BLUE,3,-17,-7,14,7,-8,19);p.circle(13,6+w*2,4,BLUE);}
            case "quantum"->{diamond(p,-7-q*6,0,13,PURPLE);diamond(p,7+q*6,0,13,BLUE);p.line(WHITE,1,-3,0,3,0);}
            case "render-util"->{cube(p,0,4,14,GREEN);p.line(INK,4,-21,-17,21,-17);p.line(BLUE,3,-18,-18,-18,7);p.line(BLUE,3,17-q*6,-18,17-q*6,7);}
            case "donut-nofall"->{P g=p.at(0,-34,0);g.poly(INK,-40,9,-25,-10,0,-18,25,-10,40,9);g.poly(RED,-35,6,-22,-7,0,-14,22,-7,35,6);g.line(WHITE,2,-34,7,-15,43);g.line(WHITE,2,34,7,15,43);g.line(WHITE,2,0,-14,0,8);}
            case "amethyst-bypass"->{diamond(p,-7,7,16,PURPLE);hammer(p,9,-12+q*6,-.4f);p.circle(-18,-5+q*5,2,PURPLE);}
            case "spam"->{P g=p.at(-6,0,-.2f);g.poly(INK,-10,-8,10,-18,10,18,-10,8);g.poly(GOLD,-6,-5,6,-12,6,12,-6,5);g.line(INK,5,-5,8,-5,21);for(int i=0;i<3;i++)p.arc(14+i*6,0,5+i*3,-.8f,.8f,BLUE,1+q);}
            case "coord-snapper"->{card(p,0,0,16,24,BLUE);p.rect(-12,-16,24,24,INK);grid(p,-10,-14,20,20,4,BLUE);p.circle(-5+q*10,-5,3,RED);p.line(WHITE,2,-5,13,5,13);}
            case "chat-macro"->{keyboard(p,-1,13,3);bubble(p,0,-15+q*3,BLUE);p.line(INK,2,-6,-17,6,-17);}
            case "name-protect"->{card(p,0,5,19,15,WHITE);p.line(BLUE,2,-10,9,10,9);mask(p,w*5,-5,PURPLE);}
            case "no-inv-leak"->{p.rect(-18,-18,36,36,INK);p.rect(-14,-14,28,28,BLUE);p.line(WHITE,2,-12,-3,12,-3);p.circle(-12+q*24,-3,2,GOLD);lock(p,0,10);}
            case "fake-stats"->{card(p,9,-8,12,16,BLUE);for(int i=0;i<3;i++)p.line(INK,2,2,-15+i*6,10+q*5,-15+i*6);p.poly(RED,-20,-19,-14,-2,-8,-19);p.circle(-14,5+w*2,10,GOLD);star(p,-14,5+w*2,5,WHITE);}
            case "weather-notifier"->{cloud(p,0,-23);for(int i=0;i<3;i++)p.line(BLUE,1,-12+i*12,-16+(t*6+i*3)%9,-14+i*12,-12+(t*6+i*3)%9);p.poly(INK,-23,-2,-12,-15,0,-20,12,-15,23,-2);p.poly(PURPLE,-19,-4,-10,-12,0,-16,10,-12,19,-4);p.line(GOLD,3,0,-3,0,23,6,23);}
            case "auto-walk"->{shoe(p,-11,12-w*3,GOLD);shoe(p,11,12+w*3,GOLD);map(p,0,-15,10);}
            case "auto-jump"->{p.line(GOLD,4,0,-25,0,19-w*5);p.line(BLUE,3,-11,-20,11,-20);spring(p,0,7,10,12+w*4);p.line(WHITE,3,-8,22,8,22);}
            case "click-tp"->{ring(p,5,0,20,PURPLE);p.circle(5,0,13,0xff2b263c);p.line(GOLD,3,-23,15,-3,-3);star(p,-3,-3,5+q*2,BLUE);}
            case "boat-fly"->{P g=p.at(0,w*4,w*.1f);boat(g,0,7);g.poly(WHITE,-9,3,-26,-12,-10,-6);g.poly(WHITE,9,3,26,-12,10,-6);}
            case "boat-noclip"->{bottle(p,0,0,0xff527e8e);P g=p.at(w*5,7,0);boat(g,0,0);p.line(WHITE,1,-15,-13,-15,12);}
            case "elytra-fly"->{P g=p.at(0,0,0);g.poly(INK,-2,-20,-25+q*5,-13,-23,24,-3,8);g.poly(PURPLE,-5,-17,-21+q*5,-11,-20,17,-6,6);g.poly(INK,2,-20,25-q*5,-13,23,24,3,8);g.poly(PURPLE,5,-17,21-q*5,-11,20,17,6,6);}
            case "elytra-swap"->{p.line(GOLD,2,0,-25,0,-19,-20,-2,20,-2,0,-19);p.poly(PURPLE,-17,0,0,6,-8,23);p.poly(BLUE,17,0,0,6,8,23-q*5);}
            case "entity-control"->{ring(p,0,0,14,GOLD);p.line(INK,3,-13,0,-21,-18-w*4);p.line(INK,3,13,0,21,-18+w*4);p.line(WHITE,3,-22,-17-w*4,-15,-19-w*4);p.line(WHITE,3,22,-17+w*4,15,-19+w*4);}
            case "entity-speed"->{p.line(GOLD,3,-16,21,-5,-22);p.line(WHITE,1,-5,-22,15,-5+w*5);p.poly(RED,10,-3+w*5,19,-3+w*5,14,12+w*5);p.line(GREEN,2,13,-4+w*5,13,-11+w*5,18,-7+w*5);}
            case "jesus"->{P g=p.at(0,w*2,w*.07f);g.poly(INK,-24,0,-15,-7,18,-7,27,0,18,7,-15,7);g.poly(BLUE,-20,0,-13,-4,16,-4,22,0,16,4,-13,4);g.line(WHITE,2,-15,0,14,0);p.arc(0,17,20,.1f,3f,BLUE,2);}
            case "sneak"->{shoe(p,-12,12-Math.max(0,w)*4,PURPLE);shoe(p,12,12-Math.max(0,-w)*4,PURPLE);p.line(WHITE,1,-20,-6,-13,-6);p.line(WHITE,1,13,-6,20,-6);}
            case "spider"->{ring(p,-13,-5-w*8,10,RED);ring(p,13,-5+w*8,10,BLUE);p.line(WHITE,3,-13,5-w*8,-13,22);p.line(WHITE,3,13,5+w*8,13,22);}
            case "sprint"->{p.line(RED,6,-15,22,-6,-7-w*5);watch(p,10,-9,11,t*5);p.line(WHITE,2,-18,19,-13,19);}
            case "no-jump-delay"->{spring(p,0,0,17,16+v*5);p.line(WHITE,3,-15,22,15,22);p.circle(0,-22-v*5,4,BLUE);}
            case "trident-boost"->{trident(p,0,-5,BLUE);p.poly(GOLD,-6,20,0,28+q*10,6,20);p.poly(RED,-3,20,0,26+q*6,3,20);}
            case "slow-swing"->{p.line(WHITE,2,0,-24,w*17,14);p.circle(w*17,14,9,GOLD);p.line(BLUE,3,-17,-25,17,-25);}
            case "swing-animation"->{float a=t*.9f;P g=p.at(10,0,0);g.line(INK,6,-8,14,sin(a)*18,-10+sin(a*2)*9);g.line(BLUE,3,-8,14,sin(a)*18,-10+sin(a*2)*9);g.circle(-8,14,4,GOLD);}
            case "hand-pose"->{p.rect(-12,-2,24,23,INK);p.rect(-9,1,18,17,WHITE);for(int i=0;i<4;i++){p.line(INK,6,-7+i*5,0,-7+i*5,-13-q*(i+1));p.line(WHITE,3,-7+i*5,0,-7+i*5,-13-q*(i+1));}p.line(INK,6,-9,5,-16,1-q*5);p.line(WHITE,3,-9,5,-16,1-q*5);}
            case "hand-aura"->{p.rect(-11,-13,22,28,PURPLE);for(int i=0;i<3;i++)p.line(GOLD,2,-7+i*7,-18,-7+i*7,-8);P g=p.at(0,0,w*.3f);ring(g,0,0,21,BLUE);}
            case "skin-change"->{mask(p,-8-w*3,-7,WHITE);mask(p,10+w*3,9,PURPLE);}
            case "spawner-notifier"->{cage(p,0,5);bell(p,0,-19,w*.25f);}
            case "chunk-scanner"->{card(p,0,0,20,23,GREEN);grid(p,-16,-17,32,32,4,INK);p.line(WHITE,2,-15,-15+q*30,15,-15+q*30);}
            case "prime-chunk-finder"->{watch(p,0,4,18,t*.7f);diamond(p,0,-20,7,PURPLE);p.line(PURPLE,2,0,-14,0,-7);}
            case "block-entity-debug"->{cube(p,-4,-6,15,GREEN);ring(p,6,9,6,WHITE);p.line(INK,3,6,15,15,21,24,12,19,-11);p.circle(18,-12+w*2,4,RED);}
            case "light-debug"->{bulb(p,-9,-9);card(p,12,9,10,13,BLUE);p.line(INK,2,5,13,13-w*4,4);}
            case "fullbright"->{P g=p.at(0,0,w*.2f);g.rect(-9,-12,18,31,INK);g.rect(-6,-9,12,24,GOLD);g.rect(-12,-15,24,8,BLUE);g.poly(0x55ffe7a3,-10,-15,-23,-36,23,-36,10,-15);}
            case "esp"->{p.rect(-36,-7,72,14,INK);p.rect(-32,-4,64,8,0xff315267);person(p,5+w*8,0,GREEN);target(p,5+w*8,0,9);}
            case "glow-esp"->{person(p,0,0,WHITE);p.rect(-15,-25,30,50,BLUE);p.rect(-12,-22,24,44,INK);person(p,0,0,WHITE);p.circle(-14,-24+q*48,3,GREEN);}
            case "nametags"->{p.rect(-18,-10,36,29,INK);p.rect(-15,-7,30,22,BLUE);p.rect(-10,-24,20,17+q*9,WHITE);p.line(INK,2,-6,-17,6,-17);p.line(INK,2,-6,-12,3,-12);}
            case "spawner-nametags"->{cage(p,0,9);p.line(GOLD,2,-12,-24,-12,0);p.poly(WHITE,-12,-24,18,-24,14,-13,-12,-13);p.line(INK,2,-8,-19,8+q*5,-19);}
            case "block-esp"->{P g=p.at(0,0,w*.12f);cube(g,0,2,14,BLUE);g.line(GREEN,2,-23,-20,23,-20,23,22,-23,22,-23,-20);}
            case "block-overlay"->{cube(p,-3,3,16,GOLD);p.rect(-23+q*10,-21,33,35,0x887bbbcf);p.line(WHITE,1,-17+q*10,-15,4+q*10,7);}
            case "storage-esp"->{p.rect(-21,0,42,24,INK);p.rect(-17,3,34,17,GOLD);p.line(INK,2,-9,3,-9,20,9,20,9,3);P g=p.at(0,-2,-q*.45f);g.rect(-22,-10,44,11,INK);g.rect(-18,-7,36,5,GOLD);p.rect(-3,4,6,8,BLUE);}
            case "freelook"->{p.line(INK,13,-6,20,-6,-17,15,-17);p.line(GREEN,8,-6,20,-6,-17,15,-17);p.circle(16,-17+w*3,9,INK);p.circle(16,-17+w*3,6,BLUE);}
            case "hole-esp"->{ring(p,0,14,22,INK);p.line(GOLD,3,-7,-20+q*9,-7,18);p.line(GOLD,3,7,-20+q*9,7,18);for(int i=0;i<5;i++)p.line(GOLD,2,-7,-17+i*7+q*4,7,-17+i*7+q*4);}
            case "pearl-trajectory"->{p.line(GOLD,4,-12,-17,0,-3,12,-17);p.line(GOLD,4,0,-3,0,21);p.line(INK,2,-12,-17,0,-14-q*8,12,-17);p.circle(0,-14-q*8,6,GREEN);dottedArc(p,8,-26,20,BLUE);}
            case "trajectories"->{p.arc(2,0,23,-1.3f,1.3f,GOLD,3);p.line(WHITE,1,8,-22,-10-q*5,0,8,22);arrow(p,-10-q*5,0,0,BLUE);dottedArc(p,17,-12,18,BLUE);}
            case "item-esp"->{p.arc(0,0,14,0,3.14f,RED,7);p.rect(-18,-20,8,22,RED);p.rect(10,-20,8,22,RED);p.rect(-18,-20,8,7,WHITE);p.rect(10,-20,8,7,WHITE);diamond(p,0,-26+q*11,5,GOLD);}
            case "xray"->{card(p,0,0,18,25,BLUE);p.rect(-13,-20,26,39,INK);p.line(WHITE,5,-7+w*3,-10,7+w*3,10);for(int i=0;i<2;i++){p.circle(-8+w*3+i*4,-12+i*2,3,WHITE);p.circle(5+w*3+i*4,9+i*2,3,WHITE);}}
            case "no-render"->{card(p,0,0,22,22,BLUE);cube(p,-4,0,11,WHITE);P g=p.at(-15+q*30,0,.3f);g.rect(-7,-10,14,20,RED);g.rect(-7,-10,14,7,WHITE);}
            case "custom-fov"->{ring(p,0,0,23,INK);ring(p,0,0,18,BLUE);for(int i=0;i<6;i++){float a=i*1.047f+q;p.line(WHITE,2,cos(a)*7,sin(a)*7,cos(a+.4f)*16,sin(a+.4f)*16);}}
            case "zoom"->{lens(p,w*7,w*3,17,BLUE);p.line(GOLD,5,11+w*7,12+w*3,23+w*7,25+w*3);}
            case "custom-crosshair"->{target(p,0,0,20);p.circle(0,0,3,RED);gear(p,13,15,8,t*.5f,GOLD);}
            case "custom-glint"->{diamond(p,-4,0,18,BLUE);p.line(WHITE,5,-17+q*30,10,-7+q*30,-7);star(p,-9+q*17,-7,4,GOLD);}
            case "hit-particles"->{P g=p.at(-8,12,-.5f);g.rect(-6,-11,12,24,INK);g.rect(-3,-8,6,18,PURPLE);for(int i=0;i<5;i++){float a=-2.6f+i*.3f;g.circle(cos(a)*(14+q*17),-11+sin(a)*(14+q*17),2,i%2==0?GOLD:BLUE);}}
            case "target-esp"->{person(p,5,0,WHITE);p.line(RED,2,-24,15,5+w*5,-3);p.circle(5+w*5,-3,3,RED);p.rect(-27,12,10,8,INK);}
            case "totem-spirit"->{totem(p,0,0,GOLD);p.poly(WHITE,-8,-8,-25,-17+q*5,-22,8,-10,4);p.poly(WHITE,8,-8,25,-17+q*5,22,8,10,4);ring(p,0,-26,8,GOLD);}
            case "particles"->{p.circle(0,-4,22,INK);p.circle(0,-4,18,BLUE);p.rect(-17,16,34,9,GOLD);for(int i=0;i<6;i++){float a=i*1.047f+t*.5f;p.circle(cos(a)*12,-4+sin(a)*12,1.8f,WHITE);}}
            case "motion-blur"->{P g=p.at(0,-5,t*2);for(int i=0;i<4;i++){P blade=g.at(0,0,i*1.57f);blade.poly(BLUE,0,0,17,-4,18,11,5,6);blade.poly(0x667bbbcf,0,0,20,7,14,16);}p.line(GOLD,3,0,-4,0,24);p.circle(0,-5,3,GOLD);}
            case "atmosphere"->{bottle(p,0,0,0xff528292);P g=p.at(w*3,-2,0);cloud(g,0,0);p.line(WHITE,1,-15,-13,-15,10);}
            case "color-grading"->{for(int i=0;i<5;i++){P g=p.at(0,14,-.65f+q*.3f+i*(.1f+q*.16f));g.rect(-5,-38,10,40,new int[]{RED,GOLD,GREEN,BLUE,PURPLE}[i]);}p.circle(0,14,3,WHITE);}
            case "jump-circles"->{p.arc(0,9+w*8,24,0,6.283f,PURPLE,3);p.circle(-10,9+w*8,3,WHITE);}
            case "pharaoh-headdress"->{p.poly(WHITE,-34,18,-30,-19,-17,-32,17,-32,30,-19,34,18,24,11,13,22,0,13,-13,22,-24,11);p.rect(-17,-18,34,26,INK);p.rect(-12,-12,7,12,BLUE);p.rect(5,-12,7,12,BLUE);}
            case "character-3d"->{p.line(GOLD,3,-21,-22,21,-22);p.line(WHITE,1,-14,-22,-9,0+w*3);p.line(WHITE,1,14,-22,9,0-w*3);person(p,0,6,BLUE);p.line(WHITE,2,-9,0+w*3,9,0-w*3);}
            case "floating-relics"->{for(int i=0;i<3;i++){float a=t*.7f+i*2.094f;P g=p.at(cos(a)*19,sin(a)*16,0);if(i==0){g.poly(WHITE,-7,7,-7,-3,-3,-8,3,-8,7,-3,7,7,3,4,0,7,-3,4);g.rect(-4,-3,2,4,INK);g.rect(2,-3,2,4,INK);}else if(i==1)diamond(g,0,0,6,BLUE);else star(g,0,0,7,PURPLE);}p.circle(0,0,2,WHITE);}
            case "time-changer"->{watch(p,0,4,20,t*.8f);ring(p,0,-22,5,GOLD);p.arc(-22,-7,10,1.8f,4.5f,GOLD,2);}
            case "low-health-warning"->{p.rect(-22,-13,44,33,INK);p.rect(-18,-10,36,26,WHITE);p.line(RED,5,0,-5,0,9);p.line(RED,5,-7,2,7,2);p.rect(-9,-19,18,6,INK);p.rect(13,-20-q*7,11,7,GOLD);}
            case "hud"->{card(p,0,-4,23,18,BLUE);p.line(INK,5,0,14,0,24);p.line(INK,3,-13,24,13,24);p.rect(-16,-15,13,8,WHITE);p.rect(2,-15,13,14,INK);p.line(GREEN,2,-15,6,-4+q*8,6);}
            case "region-map"->{map(p,0,0,23);p.line(RED,2,-14,-9,-3,5,9,-5,16,8);p.circle(-14+q*30,-9+q*17,2,RED);}
            case "hive-map"->{for(int layer=0;layer<3;layer++){float y=-10+layer*10+q*layer*3;p.line(layer==0?GREEN:BLUE,2,-20,y,0,y-9,20,y,0,y+9,-20,y);}p.line(WHITE,2,0,-22,0,-12);p.circle(0,-23,3,RED);}
            case "radar"->{P g=p.at(0,-2,w*.35f);g.arc(0,-8,22,0,3.14f,BLUE,4);g.line(GOLD,3,0,-9,0,14);g.circle(0,-10,4,GOLD);p.line(INK,4,0,10,0,25,-14,25,14,25);p.arc(0,-16,16,3.8f,5.6f,GREEN,1+q);}
            case "hotkeys"->{keyboard(p,0,0,4);p.circle(-11+q*22,-3,4,RED);p.line(WHITE,2,-20,15,20,15);}
            case "tracers"->{p.rect(-14,-21,28,42,INK);p.rect(-18,-18,36,5,GOLD);p.rect(-18,13,36,5,GOLD);for(int i=0;i<5;i++)p.line(RED,2,-12,-11+i*5,12,-11+i*5);p.line(RED,2,13,1,24+w*6,-18);p.circle(24+w*6,-18,3,WHITE);}
            case "performance"->{p.rect(-16,-16,32,32,INK);p.rect(-12,-12,24,24,BLUE);for(int i=0;i<4;i++){p.line(GOLD,2,-21,-10+i*7,-16,-10+i*7);p.line(GOLD,2,16,-10+i*7,21,-10+i*7);}P g=p.at(0,0,t*3);for(int i=0;i<4;i++){P blade=g.at(0,0,i*1.57f);blade.poly(WHITE,0,0,9,-3,9,5);}p.circle(0,0,3,GOLD);}
            case "discord-presence"->{p.rect(-23,-4,46,24,INK);p.rect(-19,-1,38,17,PURPLE);p.line(WHITE,2,-13,3,-13,11);p.line(WHITE,2,-17,7,-9,7);p.circle(12,5+w,2,BLUE);p.circle(16,10-w,2,RED);bubble(p,7,-19+q*3,BLUE);}
            default->throw new IllegalArgumentException("No exact-module mascot artwork for "+id);
        }
    }
    private static float sin(float t){return (float)Math.sin(t);}private static float cos(float t){return (float)Math.cos(t);}
    private static void cube(P p,float x,float y,float s,int color){p.poly(INK,x-s,y-s*.6f,x,y-s,x+s,y-s*.6f,x+s,y+s*.6f,x,y+s,x-s,y+s*.6f);p.poly(color,x-s+2,y-s*.5f,x,y-s+2,x+s-2,y-s*.5f,x+s-2,y+s*.5f,x,y+s-2,x-s+2,y+s*.5f);p.line(INK,1.5f,x-s,y-s*.5f,x,y,x+s,y-s*.5f);p.line(INK,1.5f,x,y,x,y+s);}
    private static void diamond(P p,float x,float y,float s,int color){p.poly(INK,x,y-s-2,x+s+2,y,x,y+s+2,x-s-2,y);p.poly(color,x,y-s,x+s,y,x,y+s,x-s,y);p.line(WHITE,1,x,y-s+3,x+s-3,y);}
    private static void star(P p,float x,float y,float s,int color){float[] xy=new float[20];for(int i=0;i<10;i++){float a=-1.57f+i*.6283f,r=i%2==0?s:s*.43f;xy[i*2]=x+cos(a)*r;xy[i*2+1]=y+sin(a)*r;}p.poly(color,xy);}
    private static void ring(P p,float x,float y,float r,int color){p.arc(x,y,r,0,6.283f,color,3);}
    private static void target(P p,float x,float y,float r){ring(p,x,y,r,BLUE);p.line(BLUE,2,x-r-4,y,x-r+4,y);p.line(BLUE,2,x+r-4,y,x+r+4,y);p.line(BLUE,2,x,y-r-4,x,y-r+4);p.line(BLUE,2,x,y+r-4,x,y+r+4);}
    private static void arrow(P p,float x,float y,float a,int color){P g=p.at(x,y,a);g.line(color,2,-10,0,9,0);g.poly(color,6,-4,13,0,6,4);}
    private static void card(P p,float x,float y,float w,float h,int color){p.rect(x-w-2,y-h-2,w*2+4,h*2+4,INK);p.rect(x-w,y-h,w*2,h*2,color);}
    private static void bottle(P p,float x,float y,int color){p.rect(x-6,y-24,12,9,INK);p.rect(x-15,y-17,30,39,INK);p.rect(x-12,y-14,24,33,color);p.rect(x-4,y-21,8,6,GOLD);p.line(WHITE,1,x-8,y-10,x-8,y+9);}
    private static void totem(P p,float x,float y,int color){p.rect(x-8,y-16,16,32,INK);p.rect(x-6,y-14,12,27,color);p.line(color,5,x-14,y-1,x+14,y-1);p.circle(x-3,y-9,1.6f,GREEN);p.circle(x+3,y-9,1.6f,GREEN);}
    private static void wrench(P p,float x,float y,float a){P g=p.at(x,y,a);g.line(INK,7,0,19,0,-12);g.line(BLUE,4,0,19,0,-12);g.line(BLUE,4,-8,-19,-6,-10,6,-10,8,-19);}
    private static void hammer(P p,float x,float y,float a){P g=p.at(x,y,a);g.line(GOLD,4,0,20,0,-13);g.rect(-12,-20,24,11,INK);g.rect(-9,-17,18,5,BLUE);}
    private static void axe(P p,float x,float y,float a){P g=p.at(x,y,a);g.line(GOLD,4,0,22,0,-20);g.poly(BLUE,0,-19,14,-23,17,-8,0,-7);}
    private static void crate(P p,float x,float y){p.rect(x-19,y-10,38,27,INK);p.rect(x-16,y-7,32,21,GOLD);p.line(INK,2,x-16,y-7,x+16,y+14);p.line(INK,2,x+16,y-7,x-16,y+14);}
    private static void plug(P p,float x,float y,int side){p.rect(x-7,y-9,14,18,INK);p.rect(x-5,y-6,10,12,BLUE);p.line(GOLD,2,x+side*5,y-4,x+side*11,y-4);p.line(GOLD,2,x+side*5,y+4,x+side*11,y+4);}
    private static void person(P p,float x,float y,int color){p.circle(x,y-11,5,color);p.line(color,7,x,y-3,x,y+7);p.line(color,3,x-9,y+1,x+9,y+1);p.line(color,3,x,y+6,x-7,y+17);p.line(color,3,x,y+6,x+7,y+17);}
    private static void lens(P p,float x,float y,float r,int color){p.circle(x,y,r+3,INK);p.circle(x,y,r,color);p.line(WHITE,2,x-r*.5f,y-r*.3f,x-r*.15f,y-r*.6f);}
    private static void cage(P p,float x,float y){cube(p,x,y,21,BLUE);for(int i=0;i<4;i++)p.line(INK,2,x-15+i*10,y-12,x-15+i*10,y+12);p.line(INK,2,x-18,y,x+18,y);}
    private static void lock(P p,float x,float y){p.arc(x,y-7,6,3.14f,6.283f,INK,3);p.rect(x-9,y-7,18,15,INK);p.rect(x-6,y-4,12,9,GOLD);p.circle(x,y,1.5f,INK);}
    private static void house(P p,float x,float y){p.poly(INK,x-13,y-3,x,y-15,x+13,y-3,x+10,y-3,x+10,y+11,x-10,y+11,x-10,y-3);p.rect(x-7,y-3,14,11,BLUE);p.rect(x-2,y+1,4,7,INK);}
    private static void trident(P p,float x,float y,int color){p.line(GOLD,3,x,y+25,x,y-24);p.line(color,3,x-10,y-27,x-10,y-12,x+10,y-12,x+10,y-27);p.line(color,3,x,y-29,x,y-11);}
    private static void cloud(P p,float x,float y){p.circle(x-10,y,9,WHITE);p.circle(x,y-6,12,WHITE);p.circle(x+11,y,9,WHITE);p.rect(x-12,y,26,8,WHITE);}
    private static void keyboard(P p,float x,float y,int cols){p.rect(x-22,y-12,44,24,INK);p.rect(x-19,y-9,38,18,BLUE);for(int i=0;i<cols;i++)for(int j=0;j<2;j++)p.rect(x-16+i*(30f/cols),y-6+j*7,5,4,WHITE);}
    private static void bubble(P p,float x,float y,int color){p.rect(x-13,y-9,26,17,color);p.poly(color,x-7,y+6,x-7,y+14,x,y+6);}
    private static void mask(P p,float x,float y,int color){p.poly(INK,x-17,y-10,x+17,y-10,x+14,y+12,x,y+20,x-14,y+12);p.poly(color,x-14,y-7,x+14,y-7,x+11,y+10,x,y+16,x-11,y+10);p.circle(x-6,y,3,INK);p.circle(x+6,y,3,INK);}
    private static void shoe(P p,float x,float y,int color){p.poly(INK,x-12,y-7,x+3,y-7,x+8,y-2,x+16,y+2,x+16,y+7,x-12,y+7);p.poly(color,x-9,y-4,x+2,y-4,x+7,y+1,x+12,y+3,x-9,y+3);p.line(WHITE,2,x-10,y+5,x+14,y+5);}
    private static void map(P p,float x,float y,float r){p.poly(INK,x-r,y-r,x,y-r+4,x+r,y-r,x+r,y+r,x,y+r-4,x-r,y+r);p.poly(GREEN,x-r+2,y-r+3,x,y-r+7,x+r-2,y-r+3,x+r-2,y+r-3,x,y+r-7,x-r+2,y+r-3);p.line(WHITE,1,x,y-r+7,x,y+r-7);}
    private static void boat(P p,float x,float y){p.poly(INK,x-22,y-7,x+22,y-7,x+15,y+7,x-15,y+7);p.poly(GOLD,x-17,y-4,x+17,y-4,x+12,y+4,x-12,y+4);p.line(INK,2,x,y-5,x,y+4);}
    private static void spring(P p,float x,float y,float w,float h){float[] xy=new float[18];for(int i=0;i<9;i++){xy[i*2]=x+(i%2==0?-w:w);xy[i*2+1]=y-h+i*h/4;}p.line(BLUE,2,xy);}
    private static void watch(P p,float x,float y,float r,float angle){p.circle(x,y,r+3,INK);p.circle(x,y,r,GOLD);p.circle(x,y,r-3,WHITE);p.line(INK,2,x,y,x+sin(angle)*r*.65f,y-cos(angle)*r*.65f);p.line(INK,2,x,y,x+sin(angle*.2f)*r*.4f,y-cos(angle*.2f)*r*.4f);}
    private static void bell(P p,float x,float y,float angle){P g=p.at(x,y,angle);g.poly(INK,-11,6,-8,-8,0,-12,8,-8,11,6);g.poly(GOLD,-8,3,-6,-6,0,-9,6,-6,8,3);g.circle(0,8,3,GOLD);}
    private static void bulb(P p,float x,float y){p.circle(x,y,11,INK);p.circle(x,y,8,GOLD);p.rect(x-5,y+8,10,9,BLUE);p.line(WHITE,1,x-3,y+2,x+3,y+2);}
    private static void dottedArc(P p,float x,float y,float r,int color){for(int i=0;i<7;i++){float a=-2.7f+i*.22f;p.circle(x+cos(a)*r,y+sin(a)*r,1,color);}}
    private static void gear(P p,float x,float y,float r,float t,int color){ring(p,x,y,r*.6f,color);for(int i=0;i<8;i++){float a=t+i*.785f;p.line(color,3,x+cos(a)*r*.5f,y+sin(a)*r*.5f,x+cos(a)*r,y+sin(a)*r);}}
    private static void grid(P p,float x,float y,float w,float h,int count,int color){for(int i=0;i<=count;i++){p.line(color,1,x+i*w/count,y,x+i*w/count,y+h);p.line(color,1,x,y+i*h/count,x+w,y+i*h/count);}}
    private static void shield(P p,float x,float y,int color){p.poly(INK,x-15,y-20,x+15,y-20,x+13,y+7,x,y+20,x-13,y+7);p.poly(color,x-12,y-17,x+12,y-17,x+10,y+5,x,y+15,x-10,y+5);}
    private static void key(P p,float x,float y,float s,int color){p.rect(x,y,s,s,INK);p.rect(x+3,y+3,s-6,s-7,color);}
    private static final class P {
        final GhostRig.Canvas c;final float x,y,s,cos,sin;
        P(GhostRig.Canvas c,float x,float y,float s,float a){this.c=c;this.x=x;this.y=y;this.s=s;cos=ModuleMascotArt.cos(a);sin=ModuleMascotArt.sin(a);}
        P at(float x,float y,float a){return new P(new GhostRig.Canvas(){public void polygon(float[] xy,int col){P.this.poly(col,xy);}public void line(float[] xy,int col,float w){P.this.line(col,w,xy);}},x,y,1,a);}
        float[] map(float[] xy){float[] out=xy.clone();for(int i=0;i<out.length;i+=2){out[i]=x+s*(xy[i]*cos-xy[i+1]*sin);out[i+1]=y+s*(xy[i]*sin+xy[i+1]*cos);}return out;}
        void poly(int col,float...xy){c.polygon(map(xy),col);}void line(int col,float width,float...xy){c.line(map(xy),col,width*s);}
        void rect(float x,float y,float w,float h,int col){poly(col,x,y,x+w,y,x+w,y+h,x,y+h);}
        void circle(float x,float y,float r,int col){float[] xy=new float[48];for(int i=0;i<24;i++){float a=i/24f*6.283185f;xy[i*2]=x+ModuleMascotArt.cos(a)*r;xy[i*2+1]=y+ModuleMascotArt.sin(a)*r;}poly(col,xy);}
        void arc(float x,float y,float r,float from,float to,int col,float width){float[] xy=new float[50];for(int i=0;i<25;i++){float a=from+(to-from)*i/24f;xy[i*2]=x+ModuleMascotArt.cos(a)*r;xy[i*2+1]=y+ModuleMascotArt.sin(a)*r;}line(col,width,xy);}
    }
}

