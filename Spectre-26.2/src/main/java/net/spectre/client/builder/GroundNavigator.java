package net.spectre.client.builder;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;

/** Small, loaded-terrain walking planner. Uses ordinary movement/jump inputs, never flight or teleports. */
final class GroundNavigator {
    private final ArrayDeque<BlockPos> route=new ArrayDeque<>();
    private int arrivalTicks;
    private double closest=Double.POSITIVE_INFINITY;
    private int stalledTicks;
    private static Object jumpWorld;
    private static long lastJumpTick=-100;
    private static void jump(Minecraft mc,boolean down){
        if(jumpWorld!=mc.level){jumpWorld=mc.level;lastJumpTick=-100;}
        long now=mc.level.getGameTime();
        if(down&&!mc.options.keyJump.isDown()){
            // Creative interprets two rising edges inside seven ticks as flight.
            // A walking step followed by a scaffold climb must remain two ordinary
            // jumps, even when separate navigator instances own those routes.
            if(now-lastJumpTick<8)return;
            lastJumpTick=now;
        }
        mc.options.keyJump.setDown(down);
    }
    static boolean standable(Minecraft mc,BlockPos feet){
        if(!mc.level.hasChunkAt(feet)||!mc.level.getFluidState(feet).isEmpty())return false;
        if(scaffold(mc,feet)||scaffold(mc,feet.below())){
            // A horizontal span is walked on its top, not entered underneath.
            // Unlike a tower, an unsupported bottom scaffold cell has no floor
            // at its lower face to catch a player arriving from the side.
            if(scaffold(mc,feet)&&!scaffold(mc,feet.below())
                &&!mc.level.getBlockState(feet.below()).isFaceSturdy(mc.level,feet.below(),Direction.UP))return false;
            // Every level of a scaffold tower is a usable landing when jump and
            // descend are released. The cells themselves remain climbable.
            for(var p:List.of(feet,feet.above()))if(!scaffold(mc,p)
                &&!mc.level.getBlockState(p).getCollisionShape(mc.level,p).isEmpty())return false;
            return true;
        }
        return mc.level.getBlockState(feet.below()).isFaceSturdy(mc.level,feet.below(),Direction.UP)
            &&mc.level.noCollision(mc.player,new AABB(feet.getX()+.19,feet.getY()+.01,feet.getZ()+.19,feet.getX()+.81,feet.getY()+1.81,feet.getZ()+.81));
    }
    private static boolean scaffold(Minecraft mc,BlockPos p){return mc.level.getBlockState(p).is(Blocks.SCAFFOLDING);}
    /** One terrain search serves all placement poses, so a close tile behind a wall
     * does not win over a slightly farther tile with a short walking route. */
    static final class Routes {
        private final BlockPos start;
        private final Map<BlockPos,BlockPos> parent;
        private final Map<BlockPos,Double> costs;
        Routes(BlockPos start,Map<BlockPos,BlockPos> parent,Map<BlockPos,Double> costs){this.start=start;this.parent=parent;this.costs=costs;}
        double cost(BlockPos goal){return costs.getOrDefault(goal,Double.POSITIVE_INFINITY);}
        List<BlockPos> path(BlockPos goal){
            if(!parent.containsKey(goal))return List.of();
            var result=new ArrayList<BlockPos>();
            for(var n=goal;!n.equals(start);n=parent.get(n))result.add(n);
            Collections.reverse(result);return result.isEmpty()?List.of(goal):result;
        }
    }
    static Routes routes(Minecraft mc){return routes(mc,null);}
    private static Routes routes(Minecraft mc,BlockPos goal){
        BlockPos start=BlockPos.containing(mc.player.getX(),mc.player.getY()+.01,mc.player.getZ());
        for(int d=0;d<3&&!standable(mc,start);d++)start=start.below();
        var parent=new HashMap<BlockPos,BlockPos>();var costs=new HashMap<BlockPos,Double>();
        if(!standable(mc,start))return new Routes(start,parent,costs);
        record Visit(BlockPos pos,double cost){}
        var queue=new PriorityQueue<Visit>(Comparator.comparingDouble((Visit v)->v.cost+(goal==null?0:
                Math.abs(v.pos.getX()-goal.getX())+Math.abs(v.pos.getZ()-goal.getZ())+Math.abs(v.pos.getY()-goal.getY())*1.5))
            .thenComparingInt(v->v.pos.getX()).thenComparingInt(v->v.pos.getY()).thenComparingInt(v->v.pos.getZ()));
        queue.add(new Visit(start,0));parent.put(start,start);costs.put(start,0.0);
        while(!queue.isEmpty()&&parent.size()<8192){
            var visit=queue.remove();var p=visit.pos;
            if(visit.cost>costs.get(p))continue;
            if(p.equals(goal))break;
            for(var direction:List.of(Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST))for(int dy:new int[]{0,1,-1}){
                var n=p.relative(direction).offset(0,dy,0);
                if(Math.abs(n.getX()-start.getX())>24||Math.abs(n.getZ()-start.getZ())>24||!standable(mc,n))continue;
                // Enough headroom above the starting tile for a normal one-block jump.
                if(dy>0&&!mc.level.noCollision(mc.player,new AABB(p.getX()+.19,p.getY()+1,p.getZ()+.19,p.getX()+.81,p.getY()+2.81,p.getZ()+.81)))continue;
                double cost=visit.cost+(dy==0?1:dy>0?1.8:1.3);
                if(cost<costs.getOrDefault(n,Double.POSITIVE_INFINITY)){parent.put(n,p);costs.put(n,cost);queue.add(new Visit(n,cost));}
                break;
            }
            for(int dy:new int[]{1,-1}){
                var n=p.offset(0,dy,0);
                if(!standable(mc,n)||!(dy>0?scaffold(mc,p):scaffold(mc,n)))continue;
                double cost=visit.cost+2.5;
                if(cost<costs.getOrDefault(n,Double.POSITIVE_INFINITY)){parent.put(n,p);costs.put(n,cost);queue.add(new Visit(n,cost));}
            }
        }
        return new Routes(start,parent,costs);
    }
    static List<BlockPos> path(Minecraft mc,BlockPos goal){return routes(mc,goal).path(goal);}
    void follow(List<BlockPos> path){route.clear();route.addAll(path);arrivalTicks=stalledTicks=0;closest=Double.POSITIVE_INFINITY;}
    static boolean stopped(Minecraft mc){return mc.player.onGround()&&mc.player.getDeltaMovement().horizontalDistance()<.004;}
    String diagnostic(){return "next="+route.peek()+" remaining="+route.size();}
    boolean tick(Minecraft mc){
        while(!route.isEmpty()){
            var next=route.peek();var center=Vec3.atBottomCenterOf(next);var delta=center.subtract(mc.player.position());
            boolean last=route.size()==1;
            double remaining=delta.horizontalDistance()+Math.abs(delta.y);
            if(remaining<closest-.015){closest=remaining;stalledTicks=0;}
            else if(++stalledTicks>80)throw new IllegalStateException("Walking approach stopped making progress at "+next);
            mc.options.keyShift.setDown(false);
            boolean vertical=delta.horizontalDistance()<.24&&(scaffold(mc,next)||scaffold(mc,next.below())
                ||scaffold(mc,BlockPos.containing(mc.player.position())));
            if(vertical&&Math.abs(delta.y)>.18){
                releaseHorizontal(mc);
                if(delta.y>.18&&mc.player.onGround()&&mc.player.getDeltaMovement().horizontalDistance()>=.004){jump(mc,false);return false;}
                if(delta.y>.18)jump(mc,true);
                else {jump(mc,false);if(mc.player.getDeltaMovement().y<=0)mc.options.keyShift.setDown(true);}
                return false;
            }
            if(delta.horizontalDistance()<(last?.14:.20)&&Math.abs(delta.y)<.2){
                if(last){
                    release(mc);
                    if(!stopped(mc))return false;
                }
                route.remove();closest=Double.POSITIVE_INFINITY;stalledTicks=0;continue;
            }
            // Brake with released movement and normal ground friction. Sneaking is
            // a placement requirement, not a universal arrival/precision control.
            if(last&&Math.abs(delta.y)<.2&&delta.horizontalDistance()<mc.player.getDeltaMovement().horizontalDistance()*2.5+.04){release(mc);return false;}
            // Finish landing without turning back toward a waypoint underneath us.
            if(delta.horizontalDistance()<.10&&!mc.player.onGround()){release(mc);return false;}
            // Face the walking route. Quantized strafing while aiming elsewhere can
            // pin the body against the corner of an otherwise reachable narrow gap.
            float desired=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z));
            float angle=net.minecraft.util.Mth.wrapDegrees(desired-mc.player.getYRot());
            // Release forward and let ordinary ground friction brake before a sharp
            // turn. Rotating while coasting makes forward inputs look like strafing.
            if(Math.abs(angle)>25&&!stopped(mc)){release(mc);return false;}
            mc.player.setYRot(mc.player.getYRot()+net.minecraft.util.Mth.clamp(angle*.4F,-12,12));
            mc.player.setXRot(mc.player.getXRot()+net.minecraft.util.Mth.clamp((15-mc.player.getXRot())*.25F,-6,6));
            mc.options.keyUp.setDown(Math.abs(angle)<25);mc.options.keyDown.setDown(false);
            mc.options.keyRight.setDown(false);mc.options.keyLeft.setDown(false);
            // Enter the hollow scaffold at ground level, brake, then climb.
            // Jumping toward it while walking carries momentum out the far side.
            jump(mc,delta.y>.35&&mc.player.onGround()&&Math.abs(angle)<25
                &&!scaffold(mc,next)&&!scaffold(mc,next.below()));
            return false;
        }
        release(mc);
        // Also settle empty routes: a usable placement pose does not mean the
        // player has stopped moving. Keep the walking view until fully grounded.
        if(!stopped(mc)){arrivalTicks=0;return false;}
        if(++arrivalTicks<2)return false;
        mc.options.keyShift.setDown(false);return true;
    }
    private static void releaseHorizontal(Minecraft mc){mc.options.keyUp.setDown(false);mc.options.keyDown.setDown(false);mc.options.keyLeft.setDown(false);mc.options.keyRight.setDown(false);}
    static void release(Minecraft mc){releaseHorizontal(mc);mc.options.keyJump.setDown(false);}
}
