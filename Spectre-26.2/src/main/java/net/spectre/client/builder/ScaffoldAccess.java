package net.spectre.client.builder;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;

/** Temporary access using the actual Scaffolding item and ordinary climbing inputs.
 * Ownership is limited to air cells confirmed after this helper's own placements. */
final class ScaffoldAccess {
    private record Tower(BlockPos base,BlockPos approach,int top){}
    private record Aim(float yaw,float pitch,boolean secondary){}
    private final GroundNavigator navigator=new GroundNavigator();
    private final LinkedHashSet<BlockPos> owned=new LinkedHashSet<>();
    private final List<Tower> towers=new ArrayList<>();
    private final Set<String> attempted=new HashSet<>();
    private final ArrayDeque<BlockPos> placing=new ArrayDeque<>();
    private Set<BlockPos> protectedCells=Set.of();
    private Tower tower;
    private BlockPos landing,bridgeStance,pendingCell;
    private Aim aim;
    private CompletableFuture<Boolean> confirmation;
    private int phase,wait,age,attempts,sinceProgress;
    private boolean cleanup;
    private String status="";
    boolean active(){return phase!=0;}
    int count(){return owned.size();}
    String status(){return status;}
    String diagnostic(){return "scaffoldPhase="+phase+" age="+age+" tower="+tower+" wanted="+placing.peek()+" stance="+bridgeStance+" landing="+landing+" "+navigator.diagnostic();}
    void reset(){phase=0;owned.clear();towers.clear();attempted.clear();placing.clear();confirmation=null;}
    void interrupt(Minecraft mc){phase=0;GroundNavigator.release(mc);mc.options.keyShift.setDown(false);}
    private void next(int value){phase=value;age=0;aim=null;attempts=0;}
    private void walk(Minecraft mc,BlockPos goal,int phase){
        var route=GroundNavigator.path(mc,goal);
        if(route.isEmpty())throw new IllegalStateException("No walking/climbing route to scaffold access at "+goal);
        navigator.follow(route);next(phase);
    }
    boolean request(Minecraft mc,Collection<BlockPos> targets,Set<BlockPos> protectedCells){
        if(active()||targets.isEmpty()||!hasItem(mc))return false;
        this.protectedCells=protectedCells;cleanup=false;sinceProgress=0;
        var target=targets.stream().min(Comparator.comparingInt((BlockPos p)->p.getY())
            .thenComparingDouble(p->Vec3.atCenterOf(p).distanceToSqr(mc.player.position()))).orElseThrow();
        int highest=targets.stream().mapToInt(BlockPos::getY).max().orElse(target.getY());
        var routes=GroundNavigator.routes(mc);Tower best=null;double score=Double.POSITIVE_INFINITY;
        var candidates=new ArrayList<Tower>(towers);
        for(int dx=-7;dx<=7;dx++)for(int dz=-7;dz<=7;dz++){
            if(Math.abs(dx)+Math.abs(dz)>7||Math.abs(dx)+Math.abs(dz)<2)continue;
            int x=target.getX()+dx,z=target.getZ()+dz;
            if(protectedCells.stream().anyMatch(p->p.getX()==x&&p.getZ()==z))continue;
            int start=Math.min(target.getY(),mc.player.blockPosition().getY()+2);
            for(int y=start;y>=Math.max(mc.level.getMinY()+1,start-128);y--){
                var base=new BlockPos(x,y,z);
                if(!mc.level.hasChunkAt(base)||!mc.level.getWorldBorder().isWithinBounds(base))break;
                if(!mc.level.getBlockState(base.below()).isFaceSturdy(mc.level,base.below(),Direction.UP))continue;
                if(!mc.level.getBlockState(base).isAir())break;
                for(var side:Direction.Plane.HORIZONTAL){var approach=base.relative(side,2);
                    if(GroundNavigator.standable(mc,approach)&&Double.isFinite(routes.cost(approach)))
                        candidates.add(new Tower(base,approach,Math.max(y+1,highest-2)));
                }
                break;
            }
        }
        for(var candidate:candidates){
            if(attempted.contains(target.toShortString()+"/"+candidate.base.toShortString()))continue;
            int separation=Math.abs(candidate.base.getX()-target.getX())+Math.abs(candidate.base.getZ()-target.getZ());
            if(separation>7)continue;
            int top=Math.max(candidate.top,highest-2);boolean clear=true;
            for(int y=candidate.base.getY();y<=top+1;y++){
                var p=new BlockPos(candidate.base.getX(),y,candidate.base.getZ());
                if(mc.level.isOutsideBuildHeight(p)||protectedCells.contains(p)||(!mc.level.getBlockState(p).isAir()&&!(owned.contains(p)&&mc.level.getBlockState(p).is(Blocks.SCAFFOLDING)))
                    ||!mc.level.getFluidState(p).isEmpty()){clear=false;break;}
            }
            if(!clear)continue;
            double distance=routes.cost(candidate.approach);
            // Reuse a tower already under the player without descending to its base.
            double fromColumn=Math.hypot(mc.player.getX()-candidate.base.getX()-.5,mc.player.getZ()-candidate.base.getZ()-.5);
            if(owned.contains(candidate.base)&&fromColumn<.3)distance=Math.min(distance,2);
            // Prefer access beside the work over a marginally shorter walk to a
            // distant tower that would need an unnecessary horizontal span.
            double value=distance+separation*4+(top-candidate.base.getY())*.03;
            if(owned.contains(candidate.base))value-=8;
            if(value<score){score=value;best=new Tower(candidate.base,candidate.approach,top);}
        }
        if(best==null)return false;
        tower=best;if(towers.stream().noneMatch(t->t.base.equals(tower.base)))towers.add(tower);
        attempted.add(target.toShortString()+"/"+tower.base.toShortString());
        int workY=Math.max(tower.base.getY()+1,target.getY()-1);
        int bridgeY=workY-1;
        var bridge=new ArrayList<BlockPos>();var end=new BlockPos(tower.base.getX(),bridgeY,tower.base.getZ());
        // Join another owned tower near the work when a supported span reaches
        // it. Otherwise stop once the work is reachable from this access column.
        var start=end;
        var link=towers.stream().filter(t->!t.base.equals(tower.base))
            .map(t->new BlockPos(t.base.getX(),bridgeY,t.base.getZ()))
            .filter(p->owned.contains(p)&&mc.level.getBlockState(p).is(Blocks.SCAFFOLDING)
                &&Math.abs(p.getX()-target.getX())+Math.abs(p.getZ()-target.getZ())<=2
                &&Math.abs(p.getX()-start.getX())+Math.abs(p.getZ()-start.getZ())<=6)
            .min(Comparator.comparingInt(p->Math.abs(p.getX()-start.getX())+Math.abs(p.getZ()-start.getZ())));
        var bridgeGoal=link.orElse(target);int stoppingDistance=link.isPresent()?0:2;
        for(int i=0;i<6&&Math.abs(end.getX()-bridgeGoal.getX())+Math.abs(end.getZ()-bridgeGoal.getZ())>stoppingDistance;i++){
            var following=end.offset(Integer.signum(bridgeGoal.getX()-end.getX()),0,0);
            if(following.equals(end))following=end.offset(0,0,Integer.signum(bridgeGoal.getZ()-end.getZ()));
            if(protectedCells.contains(following)||(!mc.level.getBlockState(following).isAir()&&!(owned.contains(following)&&mc.level.getBlockState(following).is(Blocks.SCAFFOLDING))))break;
            bridge.add(following);end=following;
        }
        landing=end.above();
        bridgeStance=bridgeY==tower.base.getY()?tower.approach:new BlockPos(tower.base.getX(),bridgeY-1,tower.base.getZ());
        placing.clear();for(int y=tower.base.getY();y<=tower.top;y++){
            var p=new BlockPos(tower.base.getX(),y,tower.base.getZ());if(!owned.contains(p))placing.add(p);
        }
        // Remember horizontal cells after the tower; placement checks the native
        // item context so an unsupported seventh span is never clicked.
        for(var cell:bridge)if(!owned.contains(cell))placing.add(cell);pendingCell=null;confirmation=null;wait=0;
        boolean atTower=owned.contains(tower.base)&&Math.hypot(mc.player.getX()-tower.base.getX()-.5,mc.player.getZ()-tower.base.getZ()-.5)<.3;
        if(atTower)next(2);else {navigator.follow(routes.path(tower.approach));next(1);}
        status="Scaffolding: building access tower";return true;
    }
    boolean beginCleanup(Minecraft mc){
        if(owned.isEmpty())return false;
        cleanup=true;placing.clear();pendingCell=null;confirmation=null;wait=0;sinceProgress=0;
        tower=towers.stream().min(Comparator.comparingDouble(t->Vec3.atCenterOf(t.base).distanceToSqr(mc.player.position()))).orElseThrow();
        walk(mc,tower.approach,5);status="Scaffolding: descending before cleanup";return true;
    }
    static boolean hasItem(Minecraft mc){for(int i=0;i<36;i++)if(mc.player.getInventory().getItem(i).is(Items.SCAFFOLDING))return true;return false;}
    private boolean select(Minecraft mc){
        var inv=mc.player.getInventory();if(inv.getSelectedItem().is(Items.SCAFFOLDING))return true;
        for(int i=0;i<36;i++)if(inv.getItem(i).is(Items.SCAFFOLDING)){
            if(i<9)inv.setSelectedSlot(i);else mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId,i,inv.getSelectedSlot(),ContainerInput.SWAP,mc.player);
            wait=3;return false;
        }
        throw new IllegalStateException("Scaffolding is missing from inventory");
    }
    private boolean safeCell(Minecraft mc,BlockPos p){
        if(protectedCells.contains(p)||!mc.level.hasChunkAt(p)||!mc.level.getWorldBorder().isWithinBounds(p)
            ||mc.level.isOutsideBuildHeight(p)||!mc.level.getBlockState(p).isAir()||!mc.level.getFluidState(p).isEmpty())return false;
        for(var direction:Direction.values())if(mc.level.getBlockState(p.relative(direction)).is(Blocks.SCAFFOLDING)&&!owned.contains(p.relative(direction)))return false;
        return ScaffoldingBlock.getDistance(mc.level,p)<7;
    }
    private BlockPos destination(Minecraft mc,BlockHitResult hit,boolean secondary){
        if(hit.getType()!=HitResult.Type.BLOCK)return null;
        var item=(ScaffoldingBlockItem)Items.SCAFFOLDING;
        var original=new BlockPlaceContext(mc.player,InteractionHand.MAIN_HAND,mc.player.getMainHandItem(),hit){
            @Override public boolean isSecondaryUseActive(){return secondary;}
        };
        var updated=item.updatePlacementContext(original);return updated==null?null:updated.getClickedPos();
    }
    private Aim aim(Minecraft mc,BlockPos wanted){
        float yaw=mc.player.getYRot(),pitch=mc.player.getXRot();Aim best=null;double score=Double.POSITIVE_INFINITY;
        var angles=new ArrayList<float[]>();
        for(var p:List.of(wanted,wanted.below(),tower.base)){
            var d=Vec3.atCenterOf(p).subtract(mc.player.getEyePosition());
            angles.add(new float[]{(float)Math.toDegrees(Math.atan2(-d.x,d.z)),(float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z)))});
        }
        var floor=Vec3.atBottomCenterOf(wanted).subtract(mc.player.getEyePosition());
        angles.add(new float[]{(float)Math.toDegrees(Math.atan2(-floor.x,floor.z)),(float)-Math.toDegrees(Math.atan2(floor.y,Math.hypot(floor.x,floor.z)))});
        for(float y:new float[]{0,90,180,-90,45,135,-45,-135})for(float p:new float[]{0,25,50,75,-25,-50,89})angles.add(new float[]{y,p});
        try{for(boolean secondary:new boolean[]{false,true})for(var angle:angles){
            mc.player.setYRot(angle[0]);mc.player.setXRot(angle[1]);var hit=CreativeBuilder.trace(mc);
            if(!wanted.equals(destination(mc,hit,secondary)))continue;
            double cost=Math.abs(net.minecraft.util.Mth.wrapDegrees(angle[0]-yaw))+Math.abs(angle[1]-pitch);
            // A top-face extension can avoid Sneak entirely. Prefer it over
            // descending through the platform merely to save a small aim turn.
            if(best==null||(!secondary&&best.secondary)||(secondary==best.secondary&&cost<score)){
                score=cost;best=new Aim(angle[0],angle[1],secondary);
            }
        }}finally{mc.player.setYRot(yaw);mc.player.setXRot(pitch);}
        return best;
    }
    void tick(Minecraft mc){
        if(!active())return;
        if(++sinceProgress>2400)throw new IllegalStateException("Scaffolding access made no durable progress: "+status);
        if(++age>1200)throw new IllegalStateException("Scaffolding access stopped making progress: "+status);
        if(wait-->0)return;
        if(phase==1||phase==3||phase==4||phase==5){
            if(!navigator.tick(mc))return;
            if(phase==4){finish(mc);return;}
            if(phase==5){next(6);return;}
            next(2);return;
        }
        GroundNavigator.release(mc);
        if(phase==6){
            mc.options.keyShift.setDown(false);
            if(!owned.contains(tower.base)||!mc.level.getBlockState(tower.base).is(Blocks.SCAFFOLDING)){next(7);return;}
            if(!select(mc))return;
            var d=Vec3.atCenterOf(tower.base).subtract(mc.player.getEyePosition());
            if(!CreativeBuilder.turn(mc,(float)Math.toDegrees(Math.atan2(-d.x,d.z)),(float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z)))))return;
            var hit=CreativeBuilder.trace(mc);
            if(!hit.getBlockPos().equals(tower.base))throw new IllegalStateException("Temporary scaffold base is obstructed; access left in place");
            mc.gameMode.startDestroyBlock(tower.base,hit.getDirection());mc.player.swing(InteractionHand.MAIN_HAND);next(7);wait=10;return;
        }
        if(phase==7){
            if(mc.level.getBlockState(tower.base).is(Blocks.SCAFFOLDING)){wait=5;return;}
            towers.removeIf(t->t.base.equals(tower.base));sinceProgress=0;
            if(towers.isEmpty()){next(8);return;}
            tower=towers.getFirst();walk(mc,tower.approach,5);return;
        }
        if(phase==8){
            if(owned.stream().anyMatch(p->mc.level.getBlockState(p).is(Blocks.SCAFFOLDING))){wait=5;return;}
            owned.clear();finish(mc);return;
        }
        if(pendingCell!=null&&confirmation==null){
            var dimension=mc.level.dimension();var cell=pendingCell;
            confirmation=mc.getSingleplayerServer().submit(()->mc.getSingleplayerServer().getLevel(dimension).getBlockState(cell).is(Blocks.SCAFFOLDING));return;
        }
        if(confirmation!=null){
            if(!confirmation.isDone())return;
            boolean placed=confirmation.join();confirmation=null;
            if(!placed){if(++attempts>=5)throw new IllegalStateException("Scaffolding placement was refused at "+pendingCell);pendingCell=null;wait=4;aim=null;return;}
            owned.add(pendingCell);placing.remove();pendingCell=null;aim=null;attempts=0;age=sinceProgress=0;mc.options.keyShift.setDown(false);wait=3;return;
        }
        if(placing.isEmpty()){
            mc.options.keyShift.setDown(false);walk(mc,landing,4);status="Scaffolding: climbing to the work level";return;
        }
        var wanted=placing.peek();
        boolean horizontal=wanted.getX()!=tower.base.getX()||wanted.getZ()!=tower.base.getZ();
        if(horizontal&&aim==null&&mc.player.position().distanceToSqr(Vec3.atBottomCenterOf(bridgeStance))>.07){
            mc.options.keyShift.setDown(false);walk(mc,bridgeStance,3);status="Scaffolding: preparing supported span";return;
        }
        if(!safeCell(mc,wanted))throw new IllegalStateException("Cannot safely place temporary scaffolding at "+wanted);
        if(!select(mc))return;
        if(aim==null)aim=aim(mc,wanted);
        if(aim==null)throw new IllegalStateException("No native scaffolding placement from this position for "+wanted);
        if(!CreativeBuilder.turn(mc,aim.yaw,aim.pitch))return;
        // Turn while standing still. Holding Sneak throughout a turn would
        // descend the tower before the horizontal click could be sent.
        if(mc.options.keyShift.isDown()!=aim.secondary){mc.options.keyShift.setDown(aim.secondary);return;}
        var hit=CreativeBuilder.trace(mc);
        if(!wanted.equals(destination(mc,hit,mc.player.isShiftKeyDown()))){aim=null;mc.options.keyShift.setDown(false);return;}
        mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,hit);mc.player.swing(InteractionHand.MAIN_HAND);
        mc.options.keyShift.setDown(false);
        pendingCell=wanted;
        // Delay the read until the native packet has had a tick to arrive.
        wait=4;
        status="Scaffolding: "+owned.size()+" supports placed";
    }
    private void finish(Minecraft mc){phase=0;GroundNavigator.release(mc);mc.options.keyShift.setDown(false);status=cleanup?"Temporary scaffolding removed":"Scaffolding access ready";}
}
