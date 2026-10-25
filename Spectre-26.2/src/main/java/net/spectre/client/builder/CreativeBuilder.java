package net.spectre.client.builder;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.*;

/** Creative-only placement executor. Never writes world blocks or creates inventory items. */
public final class CreativeBuilder {
    public static final CreativeBuilder INSTANCE=new CreativeBuilder();
    private record Step(BlockPos pos,BlockState state,Item item,boolean fluid) { }
    private record Aim(BlockHitResult hit,float yaw,float pitch) { }
    private record Approach(Vec3 position,Aim aim,List<BlockPos> route,double cost) { }
    private final GroundNavigator navigator=new GroundNavigator();
    private final ScaffoldAccess scaffolds=new ScaffoldAccess();
    private boolean automaticScaffolding=true,removeScaffolding=true,cleanupAttempted;
    private BlockState observed;
    private int cooldown,poseStable;
    private Approach approach;
    private List<Step> steps=List.of();
    private Map<BlockPos,BlockState> expected=Map.of();
    private Map<BlockPos,Integer> sections=Map.of();
    private int activeSection=-1;
    private boolean targetChosen;
    private ClientLevel world;
    private int index,wait,age,settle,oldSlot,travelStage,attempts;
    private boolean running,paused,oldFlying;
    private CompletableFuture<BlockState> confirmed;
    private String status="Ready. Creative single-player construction.";
    public boolean running(){return running;}
    public boolean paused(){return paused;}
    public String status(){return status;}
    public int completed(){return index;}
    public int total(){return steps.size();}
    public boolean automaticScaffolding(){return automaticScaffolding;}
    public void automaticScaffolding(boolean value){if(!running)automaticScaffolding=value;}
    public boolean removeScaffolding(){return removeScaffolding;}
    public void removeScaffolding(boolean value){if(!running)removeScaffolding=value;}
    public int temporaryScaffolding(){return scaffolds.count();}
    public String diagnostic(){var p=Minecraft.getInstance().player;return "target="+(index<steps.size()?steps.get(index).pos:"done")+" stage="+travelStage+" age="+age+" attempts="+attempts+" stable="+poseStable+" yaw="+p.getYRot()+" pitch="+p.getXRot()+" observed="+observed+" approach="+(approach==null?"none":approach.position)+" "+navigator.diagnostic()+" "+scaffolds.diagnostic();}
    public void pause(){if(running){paused=!paused;Minecraft.getInstance().player.setDeltaMovement(Vec3.ZERO);}}
    public void stop(){finish("Stopped. Placed blocks remain in the world.");}
    private void finish(String message){
        var mc=Minecraft.getInstance();GroundNavigator.release(mc);scaffolds.interrupt(mc);running=false;paused=false;status=message;
        if(mc.player!=null){mc.player.setDeltaMovement(Vec3.ZERO);mc.player.getInventory().setSelectedSlot(oldSlot);
            mc.player.getAbilities().flying=oldFlying;mc.player.onUpdateAbilities();mc.options.keyShift.setDown(false);mc.options.keyJump.setDown(false);}
    }
    public void start(List<Schematic.Block> blocks,Schematic.Pos origin,int rotation){
        var mc=Minecraft.getInstance();
        if(running)throw new IllegalStateException("A build is already running");
        if(mc.player==null||mc.level==null||mc.getSingleplayerServer()==null||!mc.player.getAbilities().instabuild)
            throw new IllegalStateException("This executor requires creative single-player");
        var planned=new ArrayList<Step>();var targets=new LinkedHashMap<BlockPos,BlockState>();
        Rotation turn=Rotation.values()[Math.floorMod(rotation,4)];
        for(var block:blocks){
            var p=block.pos().rotate(rotation).add(origin);var pos=new BlockPos(p.x(),p.y(),p.z());
            var state=BuilderMaterials.state(block.state()).rotate(turn);
            if(!mc.level.hasChunkAt(pos)||!mc.level.getWorldBorder().isWithinBounds(pos)||mc.level.isOutsideBuildHeight(pos))
                throw new IllegalArgumentException("Build position is outside loaded, buildable terrain: "+pos);
            targets.put(pos,state);
            if(state.is(Blocks.WATER)||state.is(Blocks.LAVA)){
                if(state.getValue(BlockStateProperties.LEVEL)!=0)throw new IllegalArgumentException("Flowing fluid cells must be produced by sources: "+pos);
                planned.add(new Step(pos,state,state.is(Blocks.WATER)?Items.WATER_BUCKET:Items.LAVA_BUCKET,true));continue;
            }
            if(state.getBlock() instanceof DoorBlock||state.getBlock() instanceof BedBlock||state.getBlock() instanceof DoublePlantBlock
                ||state.is(Blocks.PISTON_HEAD)||state.is(Blocks.MOVING_PISTON))throw new IllegalArgumentException("Unsupported multi-block placement: "+state);
            Item item=state.is(Blocks.REDSTONE_WIRE)?Items.REDSTONE:state.getBlock().asItem();
            if(!(item instanceof BlockItem))throw new IllegalArgumentException("No supported placement item for "+state);
            boolean wet=state.hasProperty(BlockStateProperties.WATERLOGGED)&&state.getValue(BlockStateProperties.WATERLOGGED);
            planned.add(new Step(pos,wet?state.setValue(BlockStateProperties.WATERLOGGED,false):state,item,false));
            if(wet)planned.add(new Step(pos,state,Items.WATER_BUCKET,true));
        }
        if(planned.isEmpty())throw new IllegalArgumentException("Schematic has no blocks");
        for(var entry:targets.entrySet()){
            var actual=mc.level.getBlockState(entry.getKey());
            if(!actual.isAir()&&!actual.equals(entry.getValue()))throw new IllegalArgumentException("Occupied build cell at "+entry.getKey()+": "+actual);
        }
        for(var step:planned)if(!hasItem(step.item))throw new IllegalArgumentException("Missing inventory item: "+step.item);
        // Separate machines are separate jobs, not rows across the entire print bed.
        // Within a connected structure we choose the next reachable local placement.
        var groups=new HashMap<BlockPos,Integer>();int group=0;
        for(var root:targets.keySet())if(!groups.containsKey(root)){
            var queue=new ArrayDeque<BlockPos>();queue.add(root);groups.put(root,group);
            while(!queue.isEmpty()){
                var pos=queue.remove();
                for(var direction:Direction.values()){
                    var neighbor=pos.relative(direction);
                    if(targets.containsKey(neighbor)&&groups.putIfAbsent(neighbor,group)==null)queue.add(neighbor);
                }
            }
            group++;
        }
        // Every entry point gets the same anchored construction hologram, including
        // direct starts. Preserve the GUI's schematic name when it is already loaded.
        var preview=SchematicPreview.INSTANCE;
        if(!preview.matches(blocks))preview.set("Active build",blocks,origin,rotation);
        else preview.transform(origin,rotation);
        preview.confirm();preview.visible(true);
        steps=new ArrayList<>(planned);expected=Map.copyOf(targets);sections=Map.copyOf(groups);activeSection=-1;targetChosen=false;world=mc.level;index=wait=age=settle=travelStage=attempts=0;approach=null;confirmed=null;scaffolds.reset();cleanupAttempted=false;
        oldSlot=mc.player.getInventory().getSelectedSlot();oldFlying=mc.player.getAbilities().flying;
        mc.player.getAbilities().flying=false;mc.player.onUpdateAbilities();observed=null;cooldown=0;running=true;paused=false;
        status="Building 0 / "+steps.size();mc.gui.setScreen(null);
    }
    private static boolean hasItem(Item item){var inv=Minecraft.getInstance().player.getInventory();for(int i=0;i<36;i++)if(inv.getItem(i).is(item))return true;return false;}
    private static int priority(Step step){return step.fluid?2:step.state.getBlock() instanceof RedStoneWireBlock||step.state.getBlock() instanceof DiodeBlock?1:0;}
    private boolean blocksDirectionalAccess(Step step){
        // Six-way machines need the player in front of their output to establish
        // their facing. Put those down before an adjacent output chest/payload
        // closes that approach. Four-way chests do not impose the reverse edge.
        for(var direction:Direction.values()){
            var pos=step.pos.relative(direction);var wanted=expected.get(pos);
            if(wanted==null||world.getBlockState(pos).getBlock()==wanted.getBlock())continue;
            Direction front=wanted.hasProperty(BlockStateProperties.FACING)?wanted.getValue(BlockStateProperties.FACING)
                :wanted.hasProperty(BlockStateProperties.ORIENTATION)?wanted.getValue(BlockStateProperties.ORIENTATION).front():null;
            if(front!=null&&pos.relative(front).equals(step.pos))return true;
        }
        return false;
    }
    private void chooseNext(Minecraft mc){
        var pending=new ArrayList<>(steps.subList(index,steps.size()));
        var groupDistance=new HashMap<Integer,Double>();
        for(var step:pending)groupDistance.merge(sections.get(step.pos),Vec3.atCenterOf(step.pos).distanceToSqr(mc.player.position()),Math::min);
        var groups=new ArrayList<>(groupDistance.keySet());
        groups.sort(Comparator.comparingInt((Integer g)->g==activeSection?0:1).thenComparingDouble(groupDistance::get).thenComparingInt(Integer::intValue));
        GroundNavigator.Routes routes=null;
        for(int group:groups){
            var local=new ArrayList<Step>();for(var s:pending)if(sections.get(s.pos)==group)local.add(s);
            // Fluids come last in this section; finish each supporting layer before
            // its upper blocks, and leave exposed wiring until the local solids exist.
            local.sort(Comparator.comparingInt((Step s)->s.fluid?1:0).thenComparingInt(s->s.pos.getY())
                .thenComparingInt(s->blocksDirectionalAccess(s)?1:0).thenComparingInt(CreativeBuilder::priority)
                .thenComparingDouble(s->Vec3.atCenterOf(s.pos).distanceToSqr(mc.player.position())));
            Step chosen=null;Approach best=null;
            for(int first=0;first<local.size()&&chosen==null;){
                var anchor=local.get(first);int end=first+1;
                while(end<local.size()&&sameBand(anchor,local.get(end)))end++;
                // Exhaust valid placements from the actual standing position first.
                // A nicer angle or centered tile never justifies moving when the
                // block can already be placed correctly from here.
                for(int pass=0;pass<2&&chosen==null;pass++){
                    boolean stationary=pass==0;int checked=0;
                    if(!stationary&&routes==null)routes=GroundNavigator.routes(mc);
                    for(int n=first;n<end;n++){
                        var s=local.get(n);var actual=world.getBlockState(s.pos);
                        if(actual.equals(s.state)){chosen=s;best=null;break;}
                        if(s.fluid&&s.state.hasProperty(BlockStateProperties.WATERLOGGED)&&actual.isAir())continue;
                        if(expected.containsKey(s.pos.below())&&world.getBlockState(s.pos.below()).isAir())continue;
                        if(!stationary&&best!=null&&checked>=8)break;
                        checked++;
                        var candidate=findApproach(mc,s,routes,stationary);
                        if(candidate!=null){
                            double distance=Vec3.atCenterOf(s.pos).distanceToSqr(mc.player.position());
                            double previous=chosen==null?Double.NEGATIVE_INFINITY:Vec3.atCenterOf(chosen.pos).distanceToSqr(mc.player.position());
                            // Fill the reachable back edge first, then work toward
                            // the player instead of putting an obstacle at our feet.
                            int facing=workDirection(candidate,mc.player.getYRot()),previousFacing=best==null?Integer.MAX_VALUE:workDirection(best,mc.player.getYRot());
                            boolean prefer=best==null||(stationary&&facing!=previousFacing?facing<previousFacing
                                :stationary&&Math.abs(distance-previous)>.01?distance>previous:candidate.cost<best.cost);
                            if(prefer){chosen=s;best=candidate;}
                        }
                    }
                }
                first=end;
            }
            if(chosen==null){
                if(automaticScaffolding&&scaffolds.request(mc,local.stream().map(Step::pos).toList(),expected.keySet())){
                    age=0;status=scaffolds.status();return;
                }
                continue;
            }
            int next=steps.indexOf(chosen);Collections.swap(steps,index,next);activeSection=group;targetChosen=true;
            approach=best;travelStage=best==null?0:1;poseStable=0;
            if(best!=null)navigator.follow(best.route);
            if(Boolean.getBoolean("spectre.smoke"))System.out.println("BUILDER_TARGET: section="+group+" pos="+chosen.pos+" route="+(best==null?0:best.route.size()));
            return;
        }
        throw new IllegalStateException("No reachable supported placement remains. Add access/scaffolding.");
    }
    private boolean sameBand(Step a,Step b){return a.fluid==b.fluid&&a.pos.getY()==b.pos.getY()
        &&priority(a)==priority(b)&&blocksDirectionalAccess(a)==blocksDirectionalAccess(b);}
    private static int workDirection(Approach approach,float yaw){
        double angle=Math.abs(net.minecraft.util.Mth.wrapDegrees(approach.aim.yaw-yaw));
        return angle<=45?0:angle<=100?1:2;
    }
    private boolean select(Item item){
        var mc=Minecraft.getInstance();var inv=mc.player.getInventory();
        if(inv.getSelectedItem().is(item))return true;
        for(int i=0;i<36;i++)if(inv.getItem(i).is(item)){
            if(i<9)inv.setSelectedSlot(i);
            else mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId,i,inv.getSelectedSlot(),ContainerInput.SWAP,mc.player);
            wait=3;return false;
        }
        throw new IllegalStateException("Inventory no longer contains "+item);
    }
    private static boolean directional(BlockState state){
        return state.getProperties().stream().anyMatch(p->Set.of("facing","axis","half","face","orientation").contains(p.getName()));
    }
    private static boolean configured(BlockState wanted,BlockState actual){
        if(wanted.getBlock()!=actual.getBlock())return false;
        for(var property:wanted.getProperties()){
            String name=property.getName();
            if(Set.of("powered","power","locked","extended","enabled","delay").contains(name))continue;
            // Comparator mode is configured by a right-click after placement.
            if(wanted.getBlock() instanceof ComparatorBlock&&name.equals("mode"))continue;
            if(wanted.getBlock() instanceof RedStoneWireBlock&&Set.of("north","south","east","west").contains(name))continue;
            if(!wanted.getValue(property).equals(actual.getValue(property)))return false;
        }
        return true;
    }
    public void tick(Minecraft mc){
        if(!running)return;
        if(mc.level!=world||mc.player==null||mc.getSingleplayerServer()==null||!mc.player.getAbilities().instabuild){finish("Stopped: world or game mode changed");return;}
        if(paused||mc.gui.screen()!=null){GroundNavigator.release(mc);mc.options.keyShift.setDown(false);return;}
        try{
            if(scaffolds.active()){scaffolds.tick(mc);status=scaffolds.status();return;}
            if(cooldown>0)cooldown--;
            if(++age>1500)throw new IllegalStateException("No progress: build needs a clear walking route or scaffolding");
            if(wait-->0)return;
            if(index==steps.size()){
                GroundNavigator.release(mc);
                if(!cleanupAttempted&&removeScaffolding&&scaffolds.count()>0){
                    cleanupAttempted=true;if(scaffolds.beginCleanup(mc)){status=scaffolds.status();return;}
                }
                if(++settle<60)return;
                var errors=new ArrayList<String>();
                for(var e:expected.entrySet())if(!world.getBlockState(e.getKey()).equals(e.getValue()))
                    errors.add(e.getKey()+" expected "+e.getValue()+"; got "+world.getBlockState(e.getKey()));
                finish(errors.isEmpty()?"Complete: verified "+expected.size()+" exact block states.":"Verification failed ("+errors.size()+" cells): "+errors.getFirst());return;
            }
            if(!targetChosen){if(!mc.player.onGround()||!GroundNavigator.stopped(mc))return;chooseNext(mc);if(!targetChosen)return;}
            Step step=steps.get(index);
            if(observed==null){
                GroundNavigator.release(mc);
                if(confirmed==null){var dimension=world.dimension();confirmed=mc.getSingleplayerServer().submit(()->mc.getSingleplayerServer().getLevel(dimension).getBlockState(step.pos));return;}
                if(!confirmed.isDone())return;observed=confirmed.join();confirmed=null;
            }
            var actual=observed;
            if(actual.equals(step.state)){advance();return;}
            // The integrated server's task queue can run before its placement packet arrives.
            // Do not plan around a stale air snapshot while the accepted block is appearing.
            if(actual.isAir()&&!world.getBlockState(step.pos).isAir()&&attempts>0){observed=null;wait=2;return;}
            if(!select(step.item))return;
            if(approach==null){
                if(!mc.player.onGround())return;
                approach=findApproach(mc,step);
                if(approach==null)throw new IllegalStateException("No visible walking approach for "+step.state+" at "+step.pos+". Add access/scaffolding.");
                navigator.follow(approach.route);travelStage=1;poseStable=0;
            }
            if(travelStage==1){if(navigator.tick(mc))travelStage=2;return;}
            GroundNavigator.release(mc);
            // Never lock onto a placement face (or repeater) while still walking,
            // landing, or being pushed. Navigation owns the view until settled.
            if(!GroundNavigator.stopped(mc)){poseStable=0;return;}
            if(configured(step.state,actual)&&!step.fluid){
                if(step.state.getBlock() instanceof ComparatorBlock&&actual.getValue(BlockStateProperties.MODE_COMPARATOR)!=step.state.getValue(BlockStateProperties.MODE_COMPARATOR)){
                    mc.options.keyShift.setDown(false);
                    if(!lookAt(mc,Vec3.atBottomCenterOf(step.pos).add(0,.1,0))||cooldown>0)return;
                    var hit=trace(mc);if(!hit.getBlockPos().equals(step.pos))throw new IllegalStateException("Comparator is obstructed");
                    mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,hit);observed=null;cooldown=5;wait=3;return;
                }
                if(step.state.getBlock() instanceof RepeaterBlock&&actual.getValue(BlockStateProperties.DELAY)!=step.state.getValue(BlockStateProperties.DELAY)){
                    mc.options.keyShift.setDown(false);
                    if(!lookAt(mc,Vec3.atBottomCenterOf(step.pos).add(0,.1,0))||cooldown>0)return;
                    var hit=trace(mc);if(!hit.getBlockPos().equals(step.pos))throw new IllegalStateException("Repeater is obstructed");
                    mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,hit);observed=null;cooldown=5;wait=3;return;
                }
                advance();return;
            }
            if(!actual.isAir()&&!(step.fluid&&step.state.getBlock()==actual.getBlock()))throw new IllegalStateException("Unexpected block at "+step.pos+": "+actual);
            if(mc.player.getBoundingBox().intersects(new AABB(step.pos).inflate(.08,0,.08))){approach=null;travelStage=0;return;}
            if(!mc.player.onGround()||cooldown>0||!lookAt(mc,approach.aim.hit.getLocation())){poseStable=0;return;}
            var visible=trace(mc);
            if(visible.getType()!=HitResult.Type.BLOCK||!visible.getBlockPos().equals(approach.aim.hit.getBlockPos())||visible.getDirection()!=approach.aim.hit.getDirection()){
                approach=null;return;
            }
            if(!step.fluid){
                var context=new BlockPlaceContext(mc.player,InteractionHand.MAIN_HAND,mc.player.getMainHandItem(),visible);
                var prediction=((BlockItem)step.item).getBlock().getStateForPlacement(context);
                if(!context.getClickedPos().equals(step.pos)||prediction==null||!configured(step.state,prediction)){approach=null;return;}
            }
            // Crouch only for interactive supports; no simultaneous jump or flight.
            var supportState=world.getBlockState(visible.getBlockPos());
            boolean interactive=supportState.hasBlockEntity()||supportState.getBlock() instanceof LeverBlock
                ||supportState.getBlock() instanceof ButtonBlock||supportState.getBlock() instanceof DoorBlock
                ||supportState.getBlock() instanceof TrapDoorBlock||supportState.getBlock() instanceof FenceGateBlock
                ||supportState.getBlock() instanceof DiodeBlock||supportState.getBlock() instanceof CraftingTableBlock
                ||supportState.getBlock() instanceof AnvilBlock||supportState.getBlock() instanceof LoomBlock
                ||supportState.getBlock() instanceof CartographyTableBlock||supportState.getBlock() instanceof StonecutterBlock
                ||supportState.getBlock() instanceof GrindstoneBlock||supportState.getBlock() instanceof SmithingTableBlock
                ||supportState.getBlock() instanceof ComposterBlock;
            if(!interactive&&mc.options.keyShift.isDown()){mc.options.keyShift.setDown(false);poseStable=0;wait=2;return;}
            if(interactive&&!mc.player.isShiftKeyDown()){mc.options.keyShift.setDown(true);poseStable=0;wait=2;return;}
            // END_CLIENT_TICK follows movement packet transmission. Keep the validated
            // pose for another tick so directional placement uses the server's updated yaw.
            boolean directional=directional(step.state);
            if(directional&&++poseStable<3)return;
            if(++attempts>5)throw new IllegalStateException("Server rejected five placement attempts at "+step.pos);
            if(step.fluid)mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);
            else mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,visible);
            if(Boolean.getBoolean("spectre.smoke"))System.out.println("BUILDER_PLACE: pos="+step.pos+" support="+supportState+" crouch="+mc.player.isShiftKeyDown());
            mc.player.swing(InteractionHand.MAIN_HAND);observed=null;cooldown=5;wait=3;
        }catch(Exception ex){finish("Stopped: "+ex.getMessage());}
    }
    private void advance(){
        index++;age=travelStage=attempts=0;approach=null;observed=null;targetChosen=false;
        Minecraft.getInstance().options.keyShift.setDown(false);
        status="Building "+index+" / "+steps.size();
    }
    private static boolean lookAt(Minecraft mc,Vec3 point){
        Vec3 d=point.subtract(mc.player.getEyePosition());
        return turn(mc,(float)Math.toDegrees(Math.atan2(-d.x,d.z)),(float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z))));
    }
    static boolean turn(Minecraft mc,float yaw,float pitch){
        float dy=net.minecraft.util.Mth.wrapDegrees(yaw-mc.player.getYRot()),dp=pitch-mc.player.getXRot();
        mc.player.setYRot(mc.player.getYRot()+net.minecraft.util.Mth.clamp(dy*.45F,-12,12));
        mc.player.setXRot(mc.player.getXRot()+net.minecraft.util.Mth.clamp(dp*.45F,-10,10));
        return Math.abs(dy)<.25F&&Math.abs(dp)<.25F;
    }
    static BlockHitResult trace(Minecraft mc){
        Vec3 eye=mc.player.getEyePosition();
        return mc.level.clip(new ClipContext(eye,eye.add(mc.player.getLookAngle().scale(mc.player.blockInteractionRange())),ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,mc.player));
    }
    private static BlockHitResult trace(Minecraft mc,Item held){
        var actual=net.minecraft.world.phys.shapes.CollisionContext.of(mc.player);
        var planned=new net.minecraft.world.phys.shapes.CollisionContext(){
            public boolean isDescending(){return actual.isDescending();}
            public boolean isAbove(net.minecraft.world.phys.shapes.VoxelShape shape,BlockPos p,boolean fallback){return actual.isAbove(shape,p,fallback);}
            public boolean isHoldingItem(Item item){return item==held;}
            public boolean alwaysCollideWithFluid(){return actual.alwaysCollideWithFluid();}
            public boolean canStandOnFluid(net.minecraft.world.level.material.FluidState a,net.minecraft.world.level.material.FluidState b){return actual.canStandOnFluid(a,b);}
            public net.minecraft.world.phys.shapes.VoxelShape getCollisionShape(BlockState state,net.minecraft.world.level.CollisionGetter level,BlockPos p){return actual.getCollisionShape(state,level,p);}
        };
        Vec3 eye=mc.player.getEyePosition();
        return mc.level.clip(new ClipContext(eye,eye.add(mc.player.getLookAngle().scale(mc.player.blockInteractionRange())),ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,planned));
    }
    private Approach findApproach(Minecraft mc,Step step){
        var here=findApproach(mc,step,null,true);
        return here!=null?here:findApproach(mc,step,GroundNavigator.routes(mc),false);
    }
    private Approach findApproach(Minecraft mc,Step step,GroundNavigator.Routes routes,boolean stationaryOnly){
        Vec3 original=mc.player.position();var originalBody=mc.player.getBoundingBox();float yaw=mc.player.getYRot(),pitch=mc.player.getXRot();
        int clear=0,visibleCount=0,predictedCount=0;
        var candidates=new ArrayList<Vec3>();
        // Reuse the player's real pose when it already offers a valid placement.
        // This also avoids endlessly retrying a block-center prediction after arriving slightly off-center.
        if(mc.player.onGround())candidates.add(original);
        if(!stationaryOnly)for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++)for(int y=-3;y<=4;y++){
            var feet=step.pos.offset(x,y,z);if(GroundNavigator.standable(mc,feet))candidates.add(Vec3.atBottomCenterOf(feet));
        }
        candidates.removeIf(v->!v.equals(original)&&!Double.isFinite(routes.cost(BlockPos.containing(v))));
        candidates.sort(Comparator.comparingDouble(v->v.equals(original)?0:routes.cost(BlockPos.containing(v))+.15));
        Approach best=null;
        try{
            for(Vec3 position:candidates){
                double walking=position.equals(original)?0:routes.cost(BlockPos.containing(position))+.15;
                if(best!=null&&best.route.isEmpty()&&!position.equals(original))break;
                if(best!=null&&walking>best.cost)break;
                var body=originalBody.move(position.subtract(original));
                mc.player.setPos(position);
                // Walking may settle within .14 of this pose. Reserve that error
                // here instead of asking navigation for unattainable precision.
                double clearance=position.equals(original)?.08:.30;
                if(body.intersects(new AABB(step.pos).inflate(clearance,0,clearance))||!world.noCollision(mc.player,body))continue;
                clear++;
                for(Direction face:Direction.values()){
                    BlockPos support=step.pos.relative(face.getOpposite());
                    if(step.fluid&&step.state.hasProperty(BlockStateProperties.WATERLOGGED))support=step.pos;
                    if(world.getBlockState(support).isAir())continue;
                    for(Vec3 hit:facePoints(support,face,position.equals(original))){
                        Vec3 delta=hit.subtract(mc.player.getEyePosition());
                        if(delta.lengthSqr()>mc.player.blockInteractionRange()*mc.player.blockInteractionRange())continue;
                        float y=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z));
                        float p=(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z)));
                        mc.player.setYRot(y);mc.player.setXRot(p);
                        var visible=trace(mc,step.item);
                        if(visible.getType()!=HitResult.Type.BLOCK||!visible.getBlockPos().equals(support)||visible.getDirection()!=face)continue;
                        visibleCount++;
                        if(step.fluid){
                            var route=position.equals(original)?List.<BlockPos>of():routes.path(BlockPos.containing(position));
                            double score=approachCost(original,yaw,pitch,position,route,walking,y,p);
                            if(best==null||score<best.cost)best=new Approach(position,new Aim(visible,y,p),route,score);
                            continue;
                        }
                        var context=new BlockPlaceContext(mc.player,InteractionHand.MAIN_HAND,new ItemStack(step.item),visible);
                        var predicted=((BlockItem)step.item).getBlock().getStateForPlacement(context);
                        if(context.getClickedPos().equals(step.pos)&&predicted!=null&&configured(step.state,predicted)&&predicted.canSurvive(world,step.pos)){
                            // A diagonal exactly between two facings can change direction after
                            // even a tiny walking offset. Reject such fragile candidate poses.
                            boolean robust=true;
                            float margin=position.equals(original)||!directional(step.state)?.5F:3F;
                            for(float[] jitter:new float[][]{{-margin,0},{margin,0},{0,-margin},{0,margin}}){
                                mc.player.setYRot(y+jitter[0]);mc.player.setXRot(p+jitter[1]);
                                var ray=trace(mc,step.item);
                                if(ray.getType()!=HitResult.Type.BLOCK||!ray.getBlockPos().equals(support)||ray.getDirection()!=face){robust=false;break;}
                                var check=((BlockItem)step.item).getBlock().getStateForPlacement(context);
                                if(check==null||!configured(step.state,check)){robust=false;break;}
                            }
                            mc.player.setYRot(y);mc.player.setXRot(p);
                            if(!robust)continue;
                            predictedCount++;
                            var route=position.equals(original)?List.<BlockPos>of():routes.path(BlockPos.containing(position));
                            double score=approachCost(original,yaw,pitch,position,route,walking,y,p);
                            if(best==null||score<best.cost)best=new Approach(position,new Aim(visible,y,p),route,score);
                        }
                    }
                }
                mc.player.setPos(original);
            }
            if(best==null&&(!stationaryOnly||index<2)&&Boolean.getBoolean("spectre.smoke"))System.out.println("BUILDER_APPROACH: stationary="+stationaryOnly+" feet="+original+" eye="+mc.player.getEyePosition()+" reach="+mc.player.blockInteractionRange()+" target="+step.pos+" candidates="+candidates.size()+" clear="+clear+" visible="+visibleCount+" predicted="+predictedCount+" startStandable="+GroundNavigator.standable(mc,BlockPos.containing(original)));
            return best;
        }finally{mc.player.setPos(original);mc.player.setYRot(yaw);mc.player.setXRot(pitch);}
    }
    private static List<Vec3> facePoints(BlockPos support,Direction face,boolean includeEdges){
        var points=new ArrayList<Vec3>();
        for(double a:new double[]{.5,.25,.75})for(double b:includeEdges?new double[]{.5,.25,.75}:new double[]{.5}){
            points.add(switch(face.getAxis()){
                case X->new Vec3(support.getX()+.5+face.getStepX()*.5,support.getY()+a,support.getZ()+b);
                case Y->new Vec3(support.getX()+a,support.getY()+.5+face.getStepY()*.5,support.getZ()+b);
                case Z->new Vec3(support.getX()+b,support.getY()+a,support.getZ()+.5+face.getStepZ()*.5);
            });
        }
        return points;
    }
    private static double approachCost(Vec3 original,float yaw,float pitch,Vec3 position,List<BlockPos> route,double walking,float placementYaw,float placementPitch){
        double turns=0;float heading=yaw;Vec3 previous=original;
        for(var tile:route){
            var point=Vec3.atBottomCenterOf(tile);var delta=point.subtract(previous);
            if(delta.horizontalDistance()>.14){
                float next=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z));
                turns+=Math.abs(net.minecraft.util.Mth.wrapDegrees(next-heading));heading=next;
            }
            previous=point;
        }
        turns+=Math.abs(net.minecraft.util.Mth.wrapDegrees(placementYaw-heading));
        // Account for the actual walking headings and the turn back to the block,
        // rather than comparing only the old view with the final placement view.
        return walking+turns*.012+Math.abs(placementPitch-pitch)*.002;
    }
}
