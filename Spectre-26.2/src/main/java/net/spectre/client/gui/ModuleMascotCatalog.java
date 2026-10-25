package net.spectre.client.gui;

import java.util.*;
/** One explicit mascot identity for each built-in module. No category inference. */
public final class ModuleMascotCatalog {
    public record Entry(String id,String name,String prop,String action) { }
    private static final Map<String,Entry> entries=new LinkedHashMap<>();
    static {
        add("aim-assist","Aim Assist","Dart and target","Tracks the target, then throws a dart");
        add("anchor-macro","Anchor Macro","Charged anchor cube","Loads a glowing charge into an anchor");
        add("crystal-macro","Crystal Macro","Crystal placement tile","Lowers a crystal onto a marked tile");
        add("crystal-aura","Crystal Aura","Orbiting crystal trio","Conducts three crystals around one hand");
        add("triggerbot","Triggerbot","Trigger remote","Squeezes a trigger after its indicator lights");
        add("kill-aura","Kill Aura","Boxing gloves","Alternates short left and right jabs");
        add("shield-breaker","Shield Breaker","Axe and cracked shield","Taps an axe into a shield seam");
        add("mace","Mace","Heavy mace","Raises the mace, then lets its weight settle");
        add("hovertotem","Hover Totem","Totem and pointer","Moves a pointer onto a floating totem");
        add("fastxp","FastXP","Experience bottle","Tilts a bottle and pours glowing droplets");
        add("autoinvtotem","Auto Inventory Totem","Totem backpack","Pulls a spare totem out of a backpack");
        add("w-tap-recode","W Tap Recode","W keycap","Presses and releases a large W key");
        add("crystal-optimizer","Crystal Optimizer","Crystal wrench","Turns a wrench around a crystal");
        add("friend-guard","Friend Guard","Friend shield","Raises a green shield to guard a friend");
        add("spear-swap","Spear Swap","Spear","Rolls the spear grip between his hands");
        add("legit-auto-tool","Legit Auto Tool","Tool belt","Selects a hammer from a compact tool belt");
        add("auto-clicker","Auto Clicker","Mouse","Alternates presses on the mouse buttons");
        add("auto-eat","Auto Eat","Cookie","Brings a cookie toward his face");
        add("auto-replenish","Auto Replenish","Refill crate","Transfers a small cube into an open crate");
        add("auto-reconnect","Auto Reconnect","Disconnected plug","Guides two cable ends back together");
        add("anti-vanish","Anti Vanish","Ghost detector lens","Reveals a faint silhouette under a lens");
        add("player-detection","Player Detection","Antenna receiver","Raises a receiver as its signal bars pulse");
        add("auto-relog","Auto Relog","Revolving doorway","Turns a little doorway through a full cycle");
        add("anti-trap","Anti Trap","Chain cutters","Closes pliers on a chain link");
        add("air-stuck","Air Stuck","Ice clamp","Holds perfectly still inside an icy clamp");
        add("spawner-protect","Spawner Protect","Locked spawner cage","Clicks a padlock onto the cage");
        add("staff-list","Staff List","Staff badge roster","Reads a badge roster and checks its rows");
        add("player-bypass","Player Bypass","Visitor pass","Slides a visitor pass through a gate");
        add("netherite-finder","Netherite Finder","Metal detector","Sweeps a detector coil near the hem");
        add("render-distance-toggle","Render Distance Toggle","Extendable telescope","Extends and retracts its tube");
        add("home-meta","Home Meta","House keychain","Turns a key beneath its little house charm");
        add("trident-util","Trident Util","Trident sling","Tightens a sling around a trident");
        add("quantum","Quantum","Split prism","Separates and rejoins two halves of a prism");
        add("render-util","Render Util","Block caliper","Measures a block with sliding caliper jaws");
        add("donut-nofall","Donut NoFall","Parachute","Opens a small canopy above his head");
        add("speed-mine","Donut Speed Mine","Pickaxe","Makes short rhythmic mining strokes");
        add("amethyst-bypass","Amethyst Bypass","Geode and chisel","Chips the edge of a purple geode");
        add("freecam","Freecam","Selfie camera","Extends the selfie stick, poses and winks");
        add("spam","Spam","Megaphone","Pulses small speech rings from a megaphone");
        add("coord-snapper","Coord Snapper","GPS recorder","Pins a position on a handheld coordinate display");
        add("chat-macro","Chat Macro","Speech keyboard","Taps a key and releases a speech bubble");
        add("name-protect","Name Protect","Identity mask","Draws a mask over an identification card");
        add("no-inv-leak","No Inv Leak","Zipped inventory pouch","Closes a zipper and secures a small lock");
        add("fake-stats","Fake Stats","Score medal","Flips a medal beside a changing score panel");
        add("weather-notifier","Weather Notifier","Umbrella","Opens the umbrella beneath a tiny rain cloud");
        add("fly","Fly","Aviator goggles","Adjusts the goggles and stretches an arm forward");
        add("auto-walk","Auto Walk","Hiking boots and trail map","Steps slowly while checking a trail map");
        add("auto-jump","Auto Jump","Pogo stick","Bounces with the pogo spring");
        add("click-tp","Click TP","Portal pointer","Points a wand through a small portal");
        add("boat-fly","Boat Fly","Flying boat","Banks a little boat with paper wings");
        add("boat-noclip","Boat Noclip","Ghost boat bottle","Guides a tiny boat through a transparent bottle");
        add("elytra-fly","Elytra Fly","Elytra wings","Opens and flaps two elytra panels");
        add("elytra-swap","Elytra Swap","Wing hanger","Unhooks a wing from a clothes hanger");
        add("entity-control","Entity Control","Reins","Tugs alternating sides of a pair of reins");
        add("entity-speed","Entity Speed","Carrot on a stick","Dangles a carrot forward and back");
        add("jesus","Jesus","Surfboard","Balances a small surfboard over ripples");
        add("sneak","Sneak","Soft slippers","Makes slow alternating tiptoe steps");
        add("speed","Speed","Red running shoes","Jogs rapidly with alternating foot and arm movement");
        add("spider","Spider","Suction cups","Alternates attaching and peeling wall cups");
        add("sprint","Sprint","Relay baton and stopwatch","Pumps a relay baton while checking a stopwatch");
        add("no-jump-delay","No Jump Delay","Coil spring","Compresses a spring and releases it quickly");
        add("trident-boost","Trident Boost","Rocket trident","Pulses a small jet from the trident tail");
        add("slow-swing","Slow Swing","Pendulum","Guides a slow pendulum through a wide arc");
        add("swing-animation","Swing Animation","Conductor baton","Conducts a smooth figure-eight gesture");
        add("hand-pose","Hand Pose","Pose mannequin hand","Folds and unfolds its articulated fingers");
        add("hand-aura","Hand Aura","Magic gauntlet","Rolls a glowing ring around a gauntlet");
        add("skin-change","Skin Change","Theater mask pair","Alternates two small character masks");
        add("spawner-notifier","Spawner Notifier","Cage bell","Rings a bell hanging from a spawner cage");
        add("sus-chunk-finder","Sus Chunk Finder","Binoculars","Raises binoculars and scans for a base");
        add("chunk-scanner","Chunk Scanner","Sonar tablet","Sweeps a scan line across a chunk grid");
        add("prime-chunk-finder","Prime Chunk Finder","Amethyst compass","Rotates a compass needle toward an amethyst shard");
        add("block-entity-debug","Block Entity Debug","Block stethoscope","Listens to a block with a stethoscope");
        add("light-debug","Light Debug","Bulb meter","Turns a light bulb beside a measuring gauge");
        add("fullbright","Fullbright","Flashlight","Sweeps a soft cone of light");
        add("esp","ESP","Tracking visor","Tracks a little player silhouette with a visor reticle");
        add("glow-esp","Glow ESP","Neon frame","Traces a glowing frame around a small silhouette");
        add("nametags","Nametags","Label maker","Feeds a name strip from a label printer");
        add("spawner-nametags","Spawner Nametags","Cage flag","Unrolls a name pennant above a cage");
        add("block-esp","Block ESP","Wire specimen cube","Rotates a cube inside a wire outline");
        add("block-overlay","Block Overlay","Block stencil","Slides a translucent stencil over a block");
        add("storage-esp","Storage ESP","Treasure chest","Opens a chest and peeks inside");
        add("freelook","Freelook","Periscope","Turns the periscope head while his body stays still");
        add("hole-esp","Hole ESP","Inspection ladder","Lowers a tiny ladder into a marked hole");
        add("pearl-trajectory","Pearl Trajectory","Pearl slingshot","Pulls back a pearl and previews its arc");
        add("trajectories","Trajectories","Bow and arrow","Draws a bow along a dotted trajectory");
        add("item-esp","Item ESP","Loot magnet","Pulls a small item toward a horseshoe magnet");
        add("xray","Xray","Xray plate","Slides a bone silhouette into an Xray viewer");
        add("no-render","No Render","Eraser","Wipes a small cube from a display");
        add("custom-fov","Custom Fov","Fisheye lens","Expands and narrows the lens aperture");
        add("zoom","Zoom","Magnifying glass","Moves the lens nearer and farther");
        add("custom-crosshair","Custom Crosshair","Reticle jig","Adjusts four reticle arms with a small dial");
        add("custom-glint","Custom Glint","Polishing gem","Buffs a jewel to reveal a traveling glint");
        add("hit-particles","Hit Particles","Confetti popper","Pops short colorful bursts from a small tube");
        add("target-esp","Target ESP","Laser target marker","Moves a laser dot around a target dummy");
        add("totem-spirit","Totem Spirit","Winged totem","Lifts a totem as its small wings unfold");
        add("particles","Particles","Snow globe","Shakes a globe and watches its motes settle");
        add("motion-blur","Motion Blur","Pinwheel","Spins a pinwheel with translucent trailing spokes");
        add("atmosphere","Atmosphere","Cloud jar","Turns a jar holding a tiny cloud");
        add("color-grading","Color Grading","Color swatch fan","Fans colored swatches open and closed");
        add("jump-circles","Jump Circles","Hula hoop","Raises and lowers a hoop beneath the hem");
        add("pharaoh-headdress","Spectral Hood","Ghost hood","Pulls on a ghost hood with glowing eyes");
        add("character-3d","3D Character","Marionette","Moves a tiny figure with puppet strings");
        add("floating-relics","Spectral Wisps","Ghost wisps","Ghosts and stars drift around the mascot");
        add("time-changer","Time Changer","Pocket watch","Turns the hands of an open pocket watch");
        add("low-health-warning","Low Health Warning","First-aid kit","Opens a medical kit and lifts a bandage");
        add("hud","HUD","HUD monitor","Moves small widgets around a monitor");
        add("spotify-hud","Media Player","Headphones","Nods to a rhythm with a floating music note");
        add("region-map","Region Map","Folded map","Unfolds a map and traces its route");
        add("hive-map","Hive Map","Terrain layers","Rotates a floating chunk and peels back its underground layers");
        add("radar","Radar","Radar dish","Turns a radar dish through a slow sweep");
        add("hotkeys","Hotkeys","Shortcut keyboard","Presses a short sequence of three keys");
        add("builder","Builder","Construction hard hat","Lifts the hard hat from his hand onto his head");
        add("tracers","Tracers","Thread spool","Draws a bright thread from a spool to a marker");
        add("performance","Performance","Chip cooling fan","Spins a cooling fan above a microchip");
        add("discord-presence","Discord Presence","Gamepad chat badge","Taps a gamepad as a chat badge pops up");
    }
    private static void add(String id,String name,String prop,String action){
        if(entries.put(id,new Entry(id,name,prop,action))!=null)throw new IllegalStateException("Duplicate mascot: "+id);
    }
    public static Collection<Entry> all(){return Collections.unmodifiableCollection(entries.values());}
    public static Entry get(String id){return entries.get(id);}
    public static GhostRig.Role coreRole(String id){
        return switch(id){
            case "idle"->GhostRig.Role.IDLE;
            case "freecam"->GhostRig.Role.FREECAM;
            case "builder"->GhostRig.Role.BUILDER;
            case "orders"->GhostRig.Role.ORDERS;
            case "fly"->GhostRig.Role.FLIGHT;
            case "speed"->GhostRig.Role.MOVEMENT;
            case "kill-aura"->GhostRig.Role.COMBAT;
            case "friend-guard"->GhostRig.Role.GUARD;
            case "speed-mine"->GhostRig.Role.MINING;
            case "auto-eat"->GhostRig.Role.FOOD;
            case "spotify-hud"->GhostRig.Role.MUSIC;
            case "sus-chunk-finder"->GhostRig.Role.SCOUT;
            default->GhostRig.Role.ACTIVE;
        };
    }
    public static boolean custom(String id){return coreRole(id)==GhostRig.Role.ACTIVE&&entries.containsKey(id);}
}
