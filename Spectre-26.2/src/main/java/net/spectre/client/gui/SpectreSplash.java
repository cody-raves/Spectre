package net.spectre.client.gui;

import imgui.ImDrawList;

/** Dedicated startup scene: apparition, drifting wisps and a progress halo. */
final class SpectreSplash {
    private SpectreSplash() {}
    static void draw(ImDrawList dl,float cx,float cy,float size,float time,float alpha,float progress) {
        boolean still=GuiPalette.animationSpeed()<=0;
        float t=still?0:time;
        SpectreDraw.glow(dl,cx,cy,size*.75f,SpectreBrand.ACCENT,.12f*alpha);
        float ringY=cy+size*.57f,rx=size*.65f,ry=size*.12f;
        ring(dl,cx,ringY,rx,ry,1,SpectreDraw.col(SpectreBrand.ACCENT,.18f*alpha),1);
        ring(dl,cx,ringY,rx,ry,progress<0?.25f:progress,SpectreDraw.col(SpectreBrand.ACCENT,.85f*alpha),2);
        // Rising wisps gather around the apparition, separately from HUD animation.
        for(int i=0;i<12;i++){
            float phase=(i/12f+t*.12f)%1;
            float side=i%2==0?-1:1;
            float x=cx+side*size*(.34f+.14f*(float)Math.sin(phase*3.14f+i));
            float y=ringY-phase*size*1.25f;
            float a=(float)Math.sin(phase*Math.PI)*alpha*.5f;
            dl.addLine(x,y+size*.04f,x+side*size*.008f,y,SpectreDraw.col(SpectreBrand.ACCENT,a),1.2f);
            dl.addCircleFilled(x,y,Math.max(1,size*.005f),SpectreDraw.col(SpectreBrand.TEXT,a),10);
        }
        WorkspaceMascot.drawSplash(dl,cx,cy,size,alpha,progress);
    }
    private static void ring(ImDrawList dl,float x,float y,float rx,float ry,float fraction,int color,float width){
        if(fraction<=0)return;
        dl.pathClear();
        int steps=Math.max(2,(int)(96*fraction));
        for(int i=0;i<=steps;i++){
            double a=Math.PI*.5+i/(double)steps*Math.PI*2*fraction;
            dl.pathLineTo(x+(float)Math.cos(a)*rx,y+(float)Math.sin(a)*ry);
        }
        dl.pathStroke(color,0,width);
    }
}
