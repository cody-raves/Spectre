Spectre for Minecraft 26.2

The active Gradle project is exclusively Spectre-26.2. It targets Java 25,
Fabric Loader 0.19.3 and Fabric API 0.155.2+26.2. Historical reference
archives in a local workspace are not part of this public active project.
Original author and GPL attribution remain in the source/resources.

SPECTRE GUI AND BRANDING
Right Shift opens the complete module workspace, with Combat, Misc, Donut,
Render, Spectre Client, Movement, Base Raid, Base Builder and Visuals categories. The top
tabs open HUD editing, Friends, Appearance and Profiles.
Appearance uses one labelled sample beside fixed controls. Its color picker
stays inside the editor; Open Modules returns to the live module workspace.

MEDIA PLAYER
Spectre Client > Media Player > Open player opens YouTube or SoundCloud
inside Minecraft. Paste a site link or use the service buttons. Pin & play
keeps a small player visible in-game; reopen it from the same module.
Close or Stop player ends playback. The optional desktop-media HUD remains.
The first explicit open downloads Chromium into config/spectre/media/runtime;
the player uses its own profile. Windows playback has been tested. Site
availability, sign-in and individual video restrictions still apply.

SPECTRE SKY AND COSMETICS
Visuals > Spectre Sky offers Nebula, Aurora and Both. Sky palette > Follow GUI
matches Appearance's preset or custom accent; other palettes can be selected
independently. Stars, shooting stars, satellites, UFO sightings, cloud colors,
the spectral sun with an animated corona, and the glowing ghost moon have
individual controls. The sun follows the chosen sky palette. Sky objects are local
decoration and do not create server entities or requests.
Always night sky is optional and changes only the rendered sky. Leave it off
for the normal sun/day and moon/night cycle. Clouds also need to be enabled
in Minecraft's video options. Satellites drift slowly; UFOs and meteors are
intermittent. They are not guaranteed to be in view at any given moment.
Spectral Hood and Spectral Wisps replace the Egyptian cosmetic designs.
Existing profile IDs are retained for compatibility.

Startup and the main menu share a dedicated hovering ghost animation and
spectral landscape, separate from the HUD's phone or module poses. The animated
gameplay mascot lives in the top-center HUD bar; the bottom-right copy is
removed. HUD Bar Mascot controls the character in that bar.
All 115 modules map to their own animation. Sustained activities (building,
placing a schematic, the builder workspace, or an open Hive Map) retain their
animation until finished. Otherwise the most recently enabled module wins,
then falls back to the previous active module or idle. Builder pause keeps
the hard hat. Appearance's reduced-motion setting still applies.

BASE ACTIVITY CLUES
Base Raid > Sus Chunk Finder outlines actual objects instead of painting chunk
squares. Bright configurable colors distinguish containers, ender chests,
hoppers, machines, redstone, spawners, armor stands/item frames, amethyst,
growth/bee clues and ANBS light anomalies. Each category has its own switch.
Look toward an outline within Object / inspect range (128 blocks by default).
An anchored Spectre popup appears beside that object on screen, with real
inventory icons and counts. Its readable size does not shrink with distance.
Look away to dismiss it; the popup never captures the mouse. It uses saved
GUI accent, surface colors, rounding and UI scale. No floating world text.
Nearby objects within 12 blocks of the target's bounds are grouped in the
popup, including the number of different clue types found together. Growth,
storage and machines can therefore contribute to the same local discovery.
This is an observed combination, not a calibrated base probability.

Growth filters use geometry, not mere presence or kelp's age property:
- Kelp: at least 16 connected vertical blocks, by default.
- Vines: 16 blocks; cave vines: 20 blocks; bamboo: 14 blocks, by default.
- Amethyst: a connected formation with budding amethyst, at least 12 mature
  clusters, and at least 75% mature crystals among clusters plus smaller buds.
Cave vines use the vine texture and continuous vine length; glow berries do
not contribute evidence. Amethyst reports small, medium and large buds
separately from fully grown clusters, including individual icon counts.
All thresholds are adjustable. The popup reports actual plant length or
mature-cluster/bud counts and percentage. Amethyst formations connect across
chunk borders. Short plants and isolated crystals are hidden by default;
Include short / immature growth is an optional context display.
Bee clue points now count only honey-bearing nests and crafted beehives.
A short kelp tip with age 25 no longer contributes evidence by itself.
Cocoa alone remains optional natural context rather than a positive signal.

Other objects use selection bounding boxes; armor stands use entity bounds.
Counts mean received world blocks/entities, not container contents. Double
chests count as two chest blocks. The nearest 2,048 object/formation markers
are displayed; the popup shows up to 28 distinct item types and states when
item types or detail are omitted. Scans update from received block changes,
and unloaded evidence is cleared. No additional terrain is requested.
Classic and Activity clues retain their scan data; ANBS adds light anomalies.

The mod remains 26.2. Your 1.21.11 server controls growth even when it accepts
newer clients. Long plants and mature formations can occur naturally; other
chunk-loading sources, server plugins, visitors and your own presence also
confound the inference. These measurements do not establish a player's AFK
time or prove a nearby base. Java 1.21.5 random-tick changes are documented at:
https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21-5

HIVE MAP
Open Client > Hive Map, or press H while in a world (rebindable).
The map shares Spectre's workspace frame, fonts, theme accents and rounded
controls. Modules returns to the main module workspace.
Left drag pans, right drag rotates, the mouse wheel zooms, and Esc closes.
Your position and current chunk are marked. Locate me recenters the map.
Surface shows the terrain top. Underground caves hides the roof and exposes
known cavities; Y slice / cutaway clips terrain to a chosen ceiling and depth.
Use the Y slider or +/- controls to browse underground. Radius 1..4 selects
up to 9x9 surrounding chunks, within the terrain already loaded by the client.
Browsing never moves your player or requests server chunks beyond normal play.

The map uses cached GPU geometry. Orbiting and zooming change camera uniforms;
they do not rebuild meshes or rasterize terrain on the CPU. One bounded worker
builds meshes from copied chunk sections. Client-thread capture and model
preparation run in small batches. Nearby chunks appear first; existing terrain
refreshes progressively after five seconds. GPU buffers/textures are evicted
outside the selected radius and released on close. Resource reloads invalidate
model and texture caches. All map modes operate on the current dimension.

This is the local Spectre integration of HiveMap's model extraction, with a
new GPU renderer and local chunk pipeline. The separate HiveMap-POC project
is unchanged. No backend, remote chunk database or persistent survey is needed.
Block tints use neutral biome colors, animated textures use a still frame,
and entity/block-entity animations are not rendered in the terrain preview.
Spectre's ImGui renderer and this map require the OpenGL graphics backend.

VANILLA UI AND DONUT NAME PRIVACY
Appearance > Interface > Custom vanilla UI applies Spectre's panel/card colors,
accent, corner radius and Geist typeface to vanilla screens. Container plates,
rectangular slots, trade rows, recipe panels, buttons, sliders, text fields and
tooltips share the theme. Villager discounts, stock indicators, progress sprites,
item/entity previews and vanilla slot coordinates remain functional. Turning
the option off restores vanilla rendering immediately. Non-default server fonts
and item-specific tooltip styles are retained. GUI size still follows Minecraft's
GUI scale; no inventory clicks or networking are replaced.

Container screens assemble in square pixel patches with an irregular edge and
no opacity fade. Each opening chooses one of eight directions (four sides and
four corners), avoids the previous direction, and uses a new patch pattern.
Each opaque patch grows smoothly into place instead of popping on. Closing
shrinks the same pattern in reverse, including when interrupted mid-opening.
The world stays steady. Menu closure and player input are never delayed by the
visual afterimage, which is discarded on another screen, resize or world change.
Hover, selection, checkbox and click-feedback transitions animate the controls
without moving their hitboxes. Appearance's Animation speed controls the timing;
zero skips the build effect and applies control states immediately.

Appearance > Interface > Scramble my name on Donut replaces the local account
name with one obfuscated glyph in the sidebar and the local player's vanilla
third-person name tag. It applies only to donutsmp.net and its subdomains, and
does not alter server data, other players' names or the account identity sent
to the server. This is a visual mask for captures, not removal from logs/data.

Run build.ps1 -VanillaUiTest with Java 25 for the disposable-world screen tour,
slot/trade interaction checks, name-mask checks and PNG evidence in validation.
The smoke initializer is excluded from normal builds.

BUILD (Windows)
Install a Java 25 JDK, set JAVA_HOME, then run:
  .\build.ps1
Or pass -JavaHome <JDK directory>. Gradle 9.5.1 is downloaded with checksum
verification, or supply an existing installation with -GradleHome <directory>.
The script stages current sources outside OneDrive to avoid Cloud Files
snapshot failures, then copies the normal mod and sources JARs to dist/.
Install the normal JAR, Fabric Loader, and Fabric API for 26.2; do not install
the sources JAR. A standard Gradle build also works outside synced folders:
  gradle :Spectre-26.2:build

VALIDATION
  .\build.ps1 -BuilderPlay
Opens a fresh creative practice world for hands-on testing. Loads the
functional schematic, supplies materials, and opens Base Builder with its
ghost position confirmed. Click Build in creative, then close the GUI.
The setup releases control and leaves the game open; this is not an
automated pass/fail run. Reopen with Right Shift to pause or stop building.
  .\build.ps1 -BuilderTest
Runs the opt-in builder test in a disposable creative single-player world.
It imports a functional schematic, supplies inventory items, builds using
normal walking/jumping and placement interactions, and compares server block
states. The test also exercises a redstone/repeater/piston circuit, hopper
transfers, rotation, and contained water/lava sources. An external access
scaffold is part of the test terrain. On failure the world stays open for
inspection; diagnostics are written in the staging run directory.
The six pistons on the raised rack deliberately face different directions
(north, south, east, west, up and down). Each must match its schematic state.
Successful reports and captures are copied into validation/.
  .\build.ps1 -BuilderInteractions
Runs a shorter regression for placing repeaters in four directions and
adjusting their delay, with a 25-second construction time limit.
Also checks forward travel, stationary placement aiming, textured ghosts,
mascot activity, and a loaded-chunk activity-clue fixture.
  .\build.ps1 -BuilderScaffoldTest
Runs native Scaffolding access tests in a disposable creative world: missing
inventory, four isolated 12-high columns sharing access towers, climbing,
elevated spans between towers, support stability, and cleanup that preserves
pre-existing scaffolding. No target or temporary-access blocks are supplied by
server writes. The report is copied to validation/scaffold-smoke-pass.txt.
  .\build.ps1 -BuilderRedstoneDemo
Records the complete first-person redstone build and functional checks at 1x.
The six fixtures include hoppers/furnace, crafter, dispenser, dropper,
repeater/piston and subtract-comparator/sticky-piston circuits. The capture
and independent server-state report are saved in validation/builder-redstone.

BASE BUILDER
Open Right Shift > Base Builder and load a .schem or .litematic from
spectre/schematics. Choose Place in world to position its ghost blocks:
aim at terrain (up to 64 blocks away), R rotates 90 degrees, Page Up/Down
adjusts height, and Enter confirms the origin. Escape returns to the menu.
The confirmed schematic stays anchored while you walk around it. You can
also enter exact origin coordinates in the panel and Confirm position.
Changing the origin or rotation requires confirmation again before building.
Translucent block textures with cyan edges show missing blocks; red marks occupied cells.
Matching blocks disappear from the overlay as construction progresses.
Show ghost blocks toggles visibility. Previewing never changes the world.
Nearby preview detail is limited to 96 blocks and 24,000 faces per frame;
the full placement bounds remain visible. Native models and live atlas textures
show hoppers, directional blocks and source fluids. Chests use their native
model/texture too; other special block entities retain a shape fallback.
Starting any build enables and anchors its ghost preview automatically.
Select Build in creative after confirming the placement.
The construction executor currently requires creative single-player and
the needed items in inventory. It walks and jumps; it does not use flight.
It faces the walking route, brakes before sharp turns, and waits until
grounded and stopped before turning toward a placement face.
Disconnected schematic sections are completed locally, with supporting layers
first and reachable nearby placements chosen using walking-route cost and view
changes. Machine orientation access takes priority over closing it with an
output chest or piston payload. This is a local planner, not a guarantee of a
globally shortest tour. Normal arrivals use ground friction instead of crouch;
crouching is reserved for placing against interactive supports.
For each ready local layer, it checks all placements from the player's actual
position before considering walking. A better viewing angle or a centered
standing tile never outranks an already valid stationary placement. Face-edge
aim points are checked too. Future poses for directional blocks use a larger
orientation margin; plain blocks are not rejected by that directional guard.
Every actual interaction is aimed precisely and checked again before clicking.
Within the current working direction, farther reachable blocks are preferred
so the structure grows toward the player without needless walking or turns.
Opening the GUI suspends movement; Pause/Resume and Stop are available there.
Use scaffolding for access builds temporary towers from the actual Minecraft
Scaffolding item in inventory when existing walking routes cannot reach the
work. It reuses columns at different heights, climbs with Jump, descends with
Sneak, and uses supported horizontal spans, including connections to owned
towers near the work. Each generated span is limited to six horizontal steps
and checked against native support distance. Additional towers are used when
needed; no flight, teleportation or world-block writes provide access.
Remove after build descends first, then breaks only the builder's own access
bases and waits for their connected scaffolding to disappear. Pre-existing
scaffolding is not owned or removed. Stopping or failing a build leaves its
access in place. Supply scaffolding separately from schematic materials.
Obstructed or unsupported placements stop with a reason. Survival logistics,
material orders, shulker resupply, and restoring block-entity contents are
not implemented.
Multi-block placement recipes such as doors/beds and explicit flowing-fluid
cells are rejected. Fluids must be placed from source buckets into prepared
containment. A successful build verifies exact block states after settling;
that does not imply every possible machine or schematic has been tested.

MAP VALIDATION
  .\build.ps1 -CheckMap
Runs geometry/camera checks and a real OpenGL cache/render/cleanup benchmark.
  .\build.ps1 -SmokeTest
Launches an isolated development client, creates a disposable test world,
checks surface/caves/slice and camera changes, captures the map, then exits.
The smoke entrypoint is opt-in and excluded from the normal distribution.
Development runs and disposable worlds remain in the local staging folder.

SUS CHUNK VALIDATION
  .\build.ps1 -SusChunkTest
Creates a disposable creative world and checks natural-growth controls,
evidence scores, category/threshold settings, real block update packets,
teleport unload/reload, Classic amethyst growth/removal/support changes,
radius/sensitivity changes, and an ordinary-light negative ANBS fixture.
Also checks all four amethyst stages and live maturity threshold crossings,
berry-independent cave-vine length and correct vine icons, exact base-object
counts, connected amethyst maturity, measured
plant lengths, short-growth rejection, mixed nearby clues, category toggles,
look-away/range dismissal and anchored icons at 57 blocks.
Writes validation/sus-chunk-pass.txt and seven unedited game captures.
Random plant growth is frozen; server-side fixture edits drive the changes.
This does not test a remote 1.21.11 proxy, the DonutFolia-specific branch,
positive ANBS anti-xray anomalies, or the accuracy of predicting a base.

CONTINUOUS GUI RECORDING
  .\build.ps1 -BuilderDemo
  .\build.ps1 -BuilderDemoFirstPerson
Records a real starter-base construction in a disposable creative world.
An external filming camera leaves the builder's player movement and placement
unchanged. The target starts empty and completion verifies every block state.
The raw continuous recording and scene timestamps are copied to validation.
Run validation/Render-BuilderDemo.ps1 to make the captioned real-time demo.
Normal walking, aiming, placement timing and pauses are preserved by default.
An optional -Timelapse switch explicitly creates a labelled speed-up instead.
Requires ffmpeg on PATH.

  .\build.ps1 -RecordShowcase
Requires ffmpeg on PATH. Records a continuous, silent 1920x1080 / 30 fps
tour from the actual game framebuffer: startup, menu, all eight module
categories, HUD/appearance/profiles/friends, and surface/caves/cutaway maps.
Output: validation/spectre-continuous-tour.mp4, with scene timestamps in
validation/spectre-tour-chapters.tsv. The recording uses a disposable local
world and opt-in demonstration code, excluded from the normal mod JAR.
Keep the recording client's window size unchanged until the run finishes.

The public handbook is in docs/ and is hosted on GitHub Pages. Its module
reference is regenerated with python tools/generate-docs.py.
