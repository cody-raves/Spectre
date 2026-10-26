package net.spectre.client.map;

import java.nio.file.*;
import java.util.*;
import java.util.function.Supplier;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.inventory.*;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.trading.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.spectre.client.config.ThemeStore;
import net.spectre.client.gui.*;

/** Real vanilla screens, disposable integrated world, no external server or credentials. */
public final class VanillaUiSmokeClient implements ClientModInitializer {
    private int ticks,phase,index,captureX,captureY,captureW,captureH;private long started=System.nanoTime();
    private final List<String> checks=new ArrayList<>();
    private final List<Preview> previews=new ArrayList<>();
    private record Preview(String name,Supplier<Screen> create){}
    private ClickGuiRenderer appearance;
    private int appearanceStep;
    private FeaturePreview features;
    private final Map<String,Boolean> enabledBefore=new HashMap<>();
    private Object rendererField(String name)throws Exception{var f=ClickGuiRenderer.class.getDeclaredField(name);f.setAccessible(true);return f.get(appearance);}
    private void rendererField(String name,Object value)throws Exception{var f=ClickGuiRenderer.class.getDeclaredField(name);f.setAccessible(true);f.set(appearance,value);}
    private void showAppearance(Minecraft mc)throws Exception{
        var screen=new ImGuiScreen();mc.gui.setScreen(screen);
        var f=ImGuiScreen.class.getDeclaredField("clickGui");f.setAccessible(true);appearance=(ClickGuiRenderer)f.get(screen);
        Class<?> tab=Class.forName("net.spectre.client.gui.ClickGuiRenderer$Tab");
        var switcher=ClickGuiRenderer.class.getDeclaredMethod("switchTab",tab);switcher.setAccessible(true);
        switcher.invoke(appearance,java.util.Arrays.stream(tab.getEnumConstants()).filter(v->v.toString().equals("THEME")).findFirst().orElseThrow());
        for(var module:net.spectre.client.module.ModuleRegistry.all())enabledBefore.put(module.id(),module.isEnabled());
        phase=200;ticks=0;
    }
    private void appearanceFrame(Minecraft mc)throws Exception{
        if(++ticks<30)return;ticks=0;
        if(appearanceStep<5){
            String[] names={"global","panels","cards","navigation","color"};
            AtlasSmokeClient.captureBack(mc,"appearance-"+names[appearanceStep]+".png");
            float[] r=(float[])rendererField("inspectorRect");
            float wx=(float)rendererField("workspaceX"),wy=(float)rendererField("workspaceY"),ww=(float)rendererField("workspaceW"),wh=(float)rendererField("workspaceH");
            require(r!=null&&r[0]>=wx&&r[1]>=wy+76&&r[0]+r[2]<=wx+ww&&r[1]+r[3]<=wy+wh,"Appearance controls stay inside the workspace: "+names[appearanceStep]);
            require(((List<?>)rendererField("panelRects")).isEmpty(),"No legacy category preview windows: "+names[appearanceStep]);
            for(var module:net.spectre.client.module.ModuleRegistry.all())if(enabledBefore.get(module.id())!=module.isEnabled())throw new AssertionError("Preview changed module "+module.id());
            var theme=ThemeStore.get();appearanceStep++;
            rendererField("themeSurface",appearanceStep==1?theme.panels:appearanceStep==2?theme.cards:appearanceStep==3?theme.tabBar:null);
            if(appearanceStep==4){rendererField("activeColorSetting",theme.accent);((imgui.type.ImBoolean)rendererField("colorPickerOpen")).set(true);}
            if(appearanceStep<5)return;
            rendererField("activeColorSetting",null);((imgui.type.ImBoolean)rendererField("colorPickerOpen")).set(false);
            Files.writeString(Path.of("appearance-preview-ready.txt"),String.join("\n",checks));
            phase=210;
        }
    }
    public void onInitializeClient(){ThemeStore.get().applyPreset(ThemeStore.PRESETS.getFirst());ThemeStore.get().customVanillaUi.set(true);ClientTickEvents.END_CLIENT_TICK.register(this::tick);}
    private void require(boolean ok,String label){if(!ok)throw new AssertionError(label);checks.add(label);}
    private void privacyChecks(){
        var key=new UiMotion.Key(1,0,0,100,20);var motion=new UiMotion(0);
        float a=motion.hover(key,true,0,1),b=motion.hover(key,true,50,1),c=motion.hover(key,false,100,1);
        require(a>0&&a<b&&b<1&&c<b&&c>0,"Hover transitions ease in and out without snapping");
        require(motion.hover(key,true,101,0)==1&&motion.hover(key,false,102,0)==0,"Reduced-motion mode applies hover state immediately");
        motion.press(key,200);require(motion.pulse(key,200,1)==1&&motion.pulse(key,360,1)==0,"Button feedback decays without moving its hitbox");
        var reveal=new UiMotion(0);boolean[] previous=new boolean[176*166];boolean monotonic=true,bounded=true,nonoverlap=true;
        for(int time=0;time<=480;time+=40){
            boolean[] current=new boolean[previous.length];
            for(var r:reveal.reveal(0,0,176,166,time,1)){
                bounded&=r.x()>=0&&r.y()>=0&&r.x()+r.width()<=176&&r.y()+r.height()<=166;
                for(int y=r.y();y<r.y()+r.height();y++)for(int x=r.x();x<r.x()+r.width();x++){int at=y*176+x;nonoverlap&=!current[at];current[at]=true;}
            }
            for(int i=0;i<previous.length;i++)monotonic&=!previous[i]||current[i];previous=current;
        }
        require(monotonic&&bounded&&nonoverlap,"Pixel build adds cells monotonically without overlap or leaving panel bounds");
        boolean complete=true;for(boolean visible:previous)complete&=visible;
        require(complete,"Pixel build finishes with the entire panel visible");
        require(!reveal.building(0,0)&&reveal.reveal(0,0,176,166,0,0).size()==1,"Reduced-motion mode renders the complete panel immediately");
        for(var direction:UiMotion.Direction.values()){
            var pattern=new UiMotion(0,direction,93746);int lastArea=0;boolean partial=false;
            for(int time=0;time<=480;time+=8){
                int area=pattern.reveal(0,0,176,166,time,1).stream().mapToInt(r->r.width()*r.height()).sum();
                if(area<lastArea)throw new AssertionError("Opening shrank: "+direction);lastArea=area;
                partial|=pattern.reveal(0,0,176,166,time,1).stream().anyMatch(r->r.width()<11&&r.width()>0);
            }
            require(partial&&lastArea==176*166,"Opaque cells grow smoothly to completion: "+direction);
            pattern.startClosing(500,1);
            for(int time=500;time<=820;time+=8){
                int area=pattern.reveal(0,0,176,166,time,1).stream().mapToInt(r->r.width()*r.height()).sum();
                if(area>lastArea)throw new AssertionError("Closing grew: "+direction);lastArea=area;
            }
            require(lastArea==0&&pattern.finishedClosing(820,1),"Closing dissolves completely in the matching direction: "+direction);
            require(UiMotion.randomDirection(direction)!=direction,"Consecutive opening directions differ: "+direction);
        }
        var interrupted=new UiMotion(0);var before=interrupted.reveal(0,0,176,166,150,1);interrupted.startClosing(150,1);
        require(before.equals(interrupted.reveal(0,0,176,166,150,1)),"Closing during opening preserves the current mask without a flash");
        require(interrupted.finishedClosing(151,0),"Reduced motion closes immediately");
        var original=Component.literal("Balance: ").withStyle(ChatFormatting.GOLD).append(Component.literal("Long").withStyle(ChatFormatting.RED)).append(Component.literal("PlayerName").withStyle(ChatFormatting.BLUE)).append(Component.literal(" | 20 coins"));
        var masked=DonutNamePrivacy.mask(original,"LongPlayerName");
        require(masked.getString().equals("Balance: X | 20 coins"),"Split styled name replaced by exactly one glyph");
        require(original.getString().equals("Balance: LongPlayerName | 20 coins"),"Original scoreboard component untouched");
        require(DonutNamePrivacy.mask(Component.literal("Name Name NamePlus"),"Name").getString().equals("X X NamePlus"),"Repeated names collapsed; longer account names unchanged");
        require(DonutNamePrivacy.mask(masked,"LongPlayerName")==masked,"Masking is idempotent");
        var glyph=DonutNamePrivacy.mask(Component.literal("LongPlayerName"),"LongPlayerName");
        require(glyph.getString().equals("X"),"Third-person name uses a single glyph");
        boolean[] scramble={false};glyph.visit((style,text)->{if(text.equals("X"))scramble[0]=style.isObfuscated();return Optional.empty();},Style.EMPTY);
        require(scramble[0],"Glyph uses Minecraft obfuscated text style");
        require(DonutNamePrivacy.donutAddress("west.donutsmp.net:25565")&&DonutNamePrivacy.donutAddress("donutsmp.net")&&!DonutNamePrivacy.donutAddress("evildonutsmp.net")&&!DonutNamePrivacy.donutAddress("donutsmp.net.example.org"),"Donut host matching is bounded");
    }
    private void plan(Minecraft mc){
        var inv=mc.player.getInventory();var title=Component.literal("Spectre preview");
        previews.add(new Preview("inventory",()->new InventoryScreen(mc.player)));
        previews.add(new Preview("chest",()->new ContainerScreen(ChestMenu.threeRows(1,inv),inv,Component.literal("Chest"))));
        previews.add(new Preview("double-chest",()->new ContainerScreen(ChestMenu.sixRows(1,inv),inv,Component.literal("Large Chest"))));
        previews.add(new Preview("villager",()->{
            var menu=new MerchantMenu(1,inv);var offers=new MerchantOffers();
            for(int i=0;i<9;i++){
                var item=new ItemStack(new net.minecraft.world.item.Item[]{Items.DIAMOND_PICKAXE,Items.BREAD,Items.ENDER_PEARL,Items.EXPERIENCE_BOTTLE,Items.APPLE,Items.LANTERN,Items.DIAMOND,Items.GLASS,Items.COMPASS}[i]);
                var offer=new MerchantOffer(new ItemCost(Items.EMERALD,12+i),item,12,3,.05f);
                if(i==0)offer.addToSpecialPriceDiff(-4);if(i==2)offer.setToOutOfStock();offers.add(offer);
            }
            menu.setOffers(offers);menu.setMerchantLevel(3);menu.setShowProgressBar(true);menu.setXp(90);menu.setCanRestock(true);
            return new MerchantScreen(menu,inv,Component.literal("Villager trades"));
        }));
        previews.add(new Preview("recipe-book",()->{
            var screen=new InventoryScreen(mc.player);return screen;
        }));
        previews.add(new Preview("crafting",()->new CraftingScreen(new CraftingMenu(1,inv),inv,title)));
        previews.add(new Preview("furnace",()->{var menu=new FurnaceMenu(1,inv);menu.setData(0,100);menu.setData(1,200);menu.setData(2,60);menu.setData(3,200);return new FurnaceScreen(menu,inv,title);}));
        previews.add(new Preview("blast-furnace",()->new BlastFurnaceScreen(new BlastFurnaceMenu(1,inv),inv,title)));
        previews.add(new Preview("smoker",()->new SmokerScreen(new SmokerMenu(1,inv),inv,title)));
        previews.add(new Preview("anvil",()->new AnvilScreen(new AnvilMenu(1,inv),inv,title)));
        previews.add(new Preview("enchanting",()->new EnchantmentScreen(new EnchantmentMenu(1,inv),inv,title)));
        previews.add(new Preview("brewing",()->new BrewingStandScreen(new BrewingStandMenu(1,inv),inv,title)));
        previews.add(new Preview("beacon",()->new BeaconScreen(new BeaconMenu(1,inv),inv,title)));
        previews.add(new Preview("loom",()->new LoomScreen(new LoomMenu(1,inv),inv,title)));
        previews.add(new Preview("cartography",()->new CartographyTableScreen(new CartographyTableMenu(1,inv),inv,title)));
        previews.add(new Preview("grindstone",()->new GrindstoneScreen(new GrindstoneMenu(1,inv),inv,title)));
        previews.add(new Preview("smithing",()->new SmithingScreen(new SmithingMenu(1,inv),inv,title)));
        previews.add(new Preview("stonecutter",()->new StonecutterScreen(new StonecutterMenu(1,inv),inv,title)));
        previews.add(new Preview("crafter",()->new CrafterScreen(new CrafterMenu(1,inv),inv,title)));
        previews.add(new Preview("hopper",()->new HopperScreen(new HopperMenu(1,inv),inv,title)));
        previews.add(new Preview("dispenser",()->new DispenserScreen(new DispenserMenu(1,inv),inv,title)));
        previews.add(new Preview("shulker",()->new ShulkerBoxScreen(new ShulkerBoxMenu(1,inv),inv,title)));
        previews.add(new Preview("book",BookViewScreen::new));
        previews.add(new Preview("creative",()->{mc.gameMode.setLocalMode(GameType.CREATIVE);return new CreativeModeInventoryScreen(mc.player,mc.player.connection.enabledFeatures(),false);}));
        previews.add(new Preview("horse",()->{
            mc.gameMode.setLocalMode(GameType.SURVIVAL);
            var horse=new net.minecraft.world.entity.animal.equine.Horse(net.minecraft.world.entity.EntityTypes.HORSE,mc.level);
            horse.setId(99002);
            return new HorseInventoryScreen(new HorseInventoryMenu(1,inv,new net.minecraft.world.SimpleContainer(2),horse,0),inv,horse,0);
        }));
        previews.add(new Preview("pause",()->new PauseScreen(true)));
        previews.add(new Preview("options",()->new OptionsScreen(new PauseScreen(true),mc.options,false)));
        previews.add(new Preview("vanilla-off",()->{ThemeStore.get().customVanillaUi.set(false);return new MerchantScreen(new MerchantMenu(1,inv),inv,title);}));
        previews.add(new Preview("teal-theme",()->{ThemeStore.get().customVanillaUi.set(true);ThemeStore.get().applyPreset(ThemeStore.PRESETS.getFirst());return new InventoryScreen(mc.player);}));
    }
    private void interactions(Minecraft mc,String preview)throws Exception{
        if(preview.equals("chest")){
            var menu=((ContainerScreen)mc.gui.screen()).getMenu();
            menu.getSlot(0).set(new ItemStack(Items.EMERALD,12));
            menu.clicked(0,0,ContainerInput.PICKUP,mc.player);
            require(menu.getCarried().getCount()==12&&menu.getSlot(0).getItem().isEmpty(),"Vanilla pickup retains stack contents");
            menu.clicked(1,0,ContainerInput.PICKUP,mc.player);
            require(menu.getCarried().isEmpty()&&menu.getSlot(1).getItem().getCount()==12,"Vanilla placement keeps the target slot aligned");
            menu.clicked(1,0,ContainerInput.QUICK_MOVE,mc.player);
            require(menu.getSlot(1).getItem().isEmpty(),"Vanilla shift-click quick move remains active");
        }
        if(preview.equals("villager")){
            var screen=(MerchantScreen)mc.gui.screen();var menu=screen.getMenu();
            var buttons=net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(screen).stream().filter(w->w.getClass().getSimpleName().equals("TradeOfferButton")).toList();
            require(buttons.size()==7,"All seven visible trade buttons remain available");
            ((net.minecraft.client.gui.components.Button)buttons.get(1)).onPress(null);
            var selected=MerchantScreen.class.getDeclaredField("shopItem");selected.setAccessible(true);
            require(selected.getInt(screen)==1,"Original villager trade selection callback remains active");
            require(menu.getOffers().get(0).getCostA().getCount()==8&&menu.getOffers().get(2).isOutOfStock(),"Discounts and out-of-stock state remain intact");
            screen.mouseScrolled(0,0,0,-1);
            var scroll=MerchantScreen.class.getDeclaredField("scrollOff");scroll.setAccessible(true);
            require(scroll.getInt(screen)==1,"Villager offer scrolling remains active");
        }
    }
    private void tick(Minecraft mc){try{
        if(phase==99)return;
        if(features!=null){features.tick(mc);return;}
        if(phase==210)return;
        if(phase==200){appearanceFrame(mc);return;}
        if(phase==4&&ticks==0)require(VanillaUi.hasClosingVisual()==ThemeStore.get().customVanillaUi.get(),"Closing retains a visual only with the theme enabled: "+previews.get(index).name());
        if(System.nanoTime()-started>240_000_000_000L)throw new IllegalStateException("UI test timed out at "+phase+" / "+index);
        if(phase==3&&ticks<20&&previews.get(index).name().equals("inventory")){
            var bounds=(com.example.client.mixin.AccessorAbstractContainerScreen<?>)mc.gui.screen();
            double sx=(double)mc.getWindow().getWidth()/mc.gui.screen().width,sy=(double)mc.getWindow().getHeight()/mc.gui.screen().height;
            int x=Math.max(0,(int)((bounds.spectre$left()-32)*sx)),y=Math.max(0,(int)((bounds.spectre$top()-32)*sy));
            int w=Math.min(mc.getWindow().getWidth()-x,(int)((bounds.spectre$width()+64)*sx)),h=Math.min(mc.getWindow().getHeight()-y,(int)((bounds.spectre$height()+64)*sy));
            captureX=x;captureY=y;captureW=w;captureH=h;
            AtlasSmokeClient.captureRegionBack(mc,String.format("vanilla-ui-build-%03d.png",ticks),x,y,w,h);
        }
        if(phase==4&&ticks<20&&previews.get(index).name().equals("inventory"))AtlasSmokeClient.captureRegionBack(mc,String.format("vanilla-ui-close-%03d.png",ticks),captureX,captureY,captureW,captureH);
        if(mc.gui.overlay()!=null||++ticks<40)return;
        if(phase==0){privacyChecks();mc.options.pauseOnLostFocus=false;mc.options.guiScale().set(2);mc.resizeGui();mc.options.renderDistance().set(3);mc.options.simulationDistance().set(5);mc.createWorldOpenFlows().createFreshLevel("vanilla-ui-test-"+System.currentTimeMillis(),new LevelSettings("Spectre UI disposable test",GameType.SURVIVAL,new LevelSettings.DifficultySettings(net.minecraft.world.Difficulty.PEACEFUL,false,false),true,WorldDataConfiguration.DEFAULT),new WorldOptions(2611262L,false,false),registry->registry.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.gui.screen());phase=1;ticks=0;return;}
        if(mc.player==null||mc.level==null)return;
        if(phase==1){if(Boolean.getBoolean("spectre.featurePreview")){features=new FeaturePreview(mc);return;}if(Boolean.getBoolean("spectre.appearancePreview")){showAppearance(mc);return;}plan(mc);mc.player.getInventory().setItem(0,new ItemStack(Items.EMERALD,64));mc.player.getInventory().setItem(1,new ItemStack(Items.DIAMOND_PICKAXE));phase=2;ticks=0;return;}
        if(phase==2){var preview=previews.get(index);mc.gui.setScreen(preview.create().get());if(mc.gui.screen() instanceof AbstractContainerScreen<?> container)mc.player.containerMenu=container.getMenu();if(preview.name().equals("recipe-book")){for(var widget:net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(mc.gui.screen()))if(widget instanceof net.minecraft.client.gui.components.ImageButton button){button.onPress(null);break;}}phase=3;ticks=0;return;}
        if(phase==3){var preview=previews.get(index);require(mc.gui.screen()!=null,"Screen remains open: "+preview.name());require(VanillaUi.enabled()!=preview.name().equals("vanilla-off"),"Theme toggle respected: "+preview.name());AtlasSmokeClient.captureBack(mc,"vanilla-ui-"+preview.name()+".png");interactions(mc,preview.name());if(mc.gui.screen() instanceof AbstractContainerScreen<?>){mc.gui.screen().onClose();require(mc.gui.screen()==null&&mc.player.containerMenu==mc.player.inventoryMenu,"Menu and input close immediately: "+preview.name());phase=4;ticks=0;return;}next(mc);}
        else if(phase==4){require(mc.gui.screen()==null&&!VanillaUi.hasClosingVisual(),"Closing visual ends without reopening the menu: "+previews.get(index).name());next(mc);}
    }catch(Throwable failure){failure.printStackTrace();try{Files.writeString(Path.of("vanilla-ui-failure.txt"),failure.toString()+"\n"+String.join("\n",checks));}catch(Exception ignored){}phase=99;mc.stop();}}
    private void next(Minecraft mc)throws Exception{index++;if(index<previews.size()){mc.player.containerMenu=mc.player.inventoryMenu;phase=2;ticks=15;}else{Files.writeString(Path.of("vanilla-ui-pass.txt"),String.join("\n",checks));phase=99;mc.stop();}}
}
