package net.spectre.client.gui;

/** Ear clipping keeps the concave cloth contour intact, including tiny rounded props. */
public final class GhostMesh {
    private GhostMesh() { }
    public static int[] triangles(float[] xy) {
        int count=xy.length/2;
        if(count<3)return new int[0];
        int[] vertices=new int[count];int n=0;
        for(int i=0;i<count;i++)if(n==0||distance(xy,vertices[n-1],i)>1e-10)vertices[n++]=i;
        if(n>1&&distance(xy,vertices[0],vertices[n-1])<=1e-10)n--;
        if(n<3)return new int[0];
        // Compute area relative to the first vertex so small props retain their winding.
        double area=0;for(int i=1;i<n-1;i++)area+=cross(xy,vertices[0],vertices[i],vertices[i+1]);
        if(Math.abs(area)<1e-10)return new int[0];
        double sign=area>=0?1:-1;
        int[] out=new int[(n-2)*3];int at=0,left=n;
        while(left>3){
            boolean found=false;
            for(int i=0;i<left;i++){
                int a=vertices[(i+left-1)%left],b=vertices[i],c=vertices[(i+1)%left];
                double bend=sign*cross(xy,a,b,c);
                if(Math.abs(bend)<1e-10){System.arraycopy(vertices,i+1,vertices,i,left-i-1);left--;found=true;break;}
                if(bend<0)continue;
                boolean occupied=false;
                for(int j=0;j<left;j++){
                    int v=vertices[j];if(v==a||v==b||v==c)continue;
                    if(sign*cross(xy,a,b,v)>=-1e-10&&sign*cross(xy,b,c,v)>=-1e-10&&sign*cross(xy,c,a,v)>=-1e-10){occupied=true;break;}
                }
                if(occupied)continue;
                out[at++]=a;out[at++]=b;out[at++]=c;
                System.arraycopy(vertices,i+1,vertices,i,left-i-1);left--;found=true;break;
            }
            if(!found)throw new IllegalArgumentException("Ghost contour cannot be triangulated");
        }
        if(left==3){out[at++]=vertices[0];out[at++]=vertices[1];out[at++]=vertices[2];}
        return java.util.Arrays.copyOf(out,at);
    }
    private static double cross(float[] xy,int a,int b,int c){return ((double)xy[b*2]-xy[a*2])*((double)xy[c*2+1]-xy[a*2+1])-((double)xy[b*2+1]-xy[a*2+1])*((double)xy[c*2]-xy[a*2]);}
    private static double distance(float[] xy,int a,int b){double dx=(double)xy[a*2]-xy[b*2],dy=(double)xy[a*2+1]-xy[b*2+1];return dx*dx+dy*dy;}
}
