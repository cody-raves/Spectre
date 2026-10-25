package net.spectre.client.gui;

/** Three damped spine segments carry the sheet; their equilibrium is world down. */
public final class GhostCloth {
    private final float[] angle=new float[3], velocity=new float[3];
    private float bank;
    private long last=-1;
    public void step(long now,float bodyBank,float vx,float ax,float ay,boolean still){
        bank=bodyBank;
        float forceX=-ax*.8f-vx*.9f,forceY=Math.max(40,85-ay*.7f);
        float forceAngle=(float)Math.atan2(forceX,forceY);
        float target=.48f*(float)Math.tanh(forceAngle/.48f);
        if(last<0||still){for(int i=0;i<3;i++){angle[i]=target;velocity[i]=0;}last=now;return;}
        float dt=Math.min(.1f,Math.max(0,(now-last)/1000f));last=now;
        int steps=Math.max(1,(int)Math.ceil(dt*240));float h=dt/steps;
        for(int step=0;step<steps;step++)for(int i=0;i<3;i++){
            float equilibrium=i==0?target:angle[i-1];
            float stiffness=22-i*6,damping=5.8f-i*.8f;
            velocity[i]+=(stiffness*(equilibrium-angle[i])-damping*velocity[i])*h;
            angle[i]+=velocity[i]*h;
        }
    }
    public float[] deform(float[] contour){
        // Integrate a spine with a vertical tangent at the attachment. A wide first
        // bend avoids folding the outline back over itself at either shoulder.
        final int steps=168;final float ds=.5f;
        float[] nx=new float[steps+1],ny=new float[steps+1],theta=new float[steps+1];ny[0]=-22;
        for(int i=1;i<=steps;i++){
            float desired=direction(i*ds),limit=.014f*ds;
            // Bound curvature below the inverse sheet width to prevent shoulder folds.
            theta[i]=theta[i-1]+limit*(float)Math.tanh((desired-theta[i-1])/limit);
            float a=(theta[i-1]+theta[i])*.5f;
            nx[i]=nx[i-1]+ds*(float)Math.sin(a);
            ny[i]=ny[i-1]+ds*(float)Math.cos(a);
        }
        float[] xy=contour.clone();
        for(int i=0;i<xy.length;i+=2){
            float x=xy[i],y=xy[i+1];if(y<=-22)continue;
            float u=(y+22)/ds,extra=Math.max(0,u-steps)*ds;
            int at=Math.min(steps-1,(int)u);float p=Math.min(1,u-at);
            float cx=nx[at]+(nx[at+1]-nx[at])*p,cy=ny[at]+(ny[at+1]-ny[at])*p;
            float a=theta[at]+(theta[at+1]-theta[at])*p;
            float tx=(float)Math.sin(a),ty=(float)Math.cos(a);
            xy[i]=cx+ty*x+tx*extra;xy[i+1]=cy-tx*x+ty*extra;
        }
        return xy;
    }
    private float direction(float distance){
        float[] knot={0,44,68,84},a={0,angle[0]-bank,angle[1]-bank,angle[2]-bank};
        int segment=distance<44?0:distance<68?1:2;
        float p=Math.min(1,(distance-knot[segment])/(knot[segment+1]-knot[segment]));
        p=p*p*p*(p*(p*6-15)+10);
        return a[segment]+(a[segment+1]-a[segment])*p;
    }
    private static float ease(float p){return p*p*(3-2*p);}
}
