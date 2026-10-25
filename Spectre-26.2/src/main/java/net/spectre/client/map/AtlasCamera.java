package net.spectre.client.map;

/** Orthographic navigation shared by input, GPU projection and tests. */
public final class AtlasCamera {
    public double x,y,z,yaw=.72,pitch=.78,zoom=6;
    public void orbit(double dx,double dy){yaw+=dx*.008;pitch=Math.clamp(pitch+dy*.006,.25,1.50);}
    public void pan(double dx,double dy){
        double rx=-dx/zoom, rz=-dy/(zoom*Math.sin(pitch));
        x=Math.clamp(x+Math.cos(yaw)*rx+Math.sin(yaw)*rz,-29_999_984,29_999_984);
        z=Math.clamp(z-Math.sin(yaw)*rx+Math.cos(yaw)*rz,-29_999_984,29_999_984);
    }
    public void scroll(double wheel){zoom=Math.clamp(zoom*Math.pow(1.13,wheel),1.5,40);}
    public int chunkX(){return (int)Math.floor(x/16);}
    public int chunkZ(){return (int)Math.floor(z/16);}
}
