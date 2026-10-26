# MyClient (Fabric, Minecraft 1.21.x)

Starter for a Lunar-style client. Build: `./gradlew build` (needs Gradle wrapper + Java 21) -> `build/libs/myclient-*.jar`
into `.minecraft/mods` with Fabric Loader + Fabric API. Menu key: Right Shift.

Done in this skeleton: module system, Fullbright (works), menu with tabs (Mods / Cosmetics / Skies / Macros / Editor),
cosmetic data model with variants + animation types, macro engine (key -> chat/command, optional repeat).

Still to build (version-specific, needs a dev environment to test):
1. Cosmetic rendering: a FeatureRenderer on PlayerEntityRenderer drawing halo/headband/cape models, driven by Cosmetic.phase().
2. Motion blur: frame accumulation mixin at the end of GameRenderer.render (strength in MotionBlur).
3. Custom skies: mixin on WorldRenderer sky rendering using the chosen sky texture.
4. Editor: text fields, colour picker, preview, save/load as JSON in config/myclient/.
5. Persistence of equipped items / settings; multi-version builds (1.21 - 1.21.x) via Stonecutter.

## v0.2
- Rounded dark UI with real toggle switches, colour swatches, sliders, scrolling.
- Halos, headbands and capes are drawn on your player (open third person with F5). Animations: float, spin, pulse, wave, rainbow.
- Editor tab creates real accessories (colour, speed, animation) that show up in Cosmetics.
- Motion blur and custom skies are still UI-only ("coming soon").

## v0.3
- Menu drawn at real-pixel resolution with anti-aliased rounded shapes, smaller panel, scrolling on every tab.
- New cosmetics: wings, glasses (square/round), aura, particle trail, night-sky headbands and capes, much cooler halos (particles too).
- Thicker headbands/capes; capes use a small spring simulation (speed, jump, fall, strafing).
- Upload your own cape/sky PNG (button or drag a .png onto the window). Files are kept in config/myclient/.
- Textures tab: edit any block texture in-game, Apply writes + enables + reloads the pack resourcepacks/MyClientTextures.
- Builder tab: load a .litematic from .minecraft/schematics and build it from the hotbar (pauses when a block is missing).

## v0.4
- Meteor-style GUI: small floating windows (drag the title bar, collapse, close, open from the top dock). Window layout is saved.
- Smooth Poppins UI font (SIL OFL) and own text boxes, no vanilla widgets. No logo.
- Search window finds modules, cosmetics, skies, macros, schematics, block tracers and block textures.
- Tracers: pick any blocks, lines are drawn to them on the HUD (range 8-48).
- Trail is now a smooth glowing ribbon instead of particles (third person).
- Everything is saved to config/myclient/settings.json (equipped items, variants, custom items, modules, macros, sky, tracers, windows).

## v0.5 (Lumen)
- New compact flat GUI (default 75% size, adjustable in Effects), movable windows, dock at the top.
- Skies are rendered in the world: Lumen, Starry Night, Galaxy, Aurora, Nebula, Sunset, Blood Moon + your own PNGs.
  Moons, stars, twinkles, falling stars, aurora curtains and the Lumen logo.
- Own particle system (smooth glowing shapes, no Minecraft textures): halos, auras, trails, jump/hit/damage effects.
- Unique animations per cosmetic, live editor preview on your character.
- Tracers/X-Ray up to 512 blocks, Freecam, Auto Totem, Air Place, Reach.
- Effects window: low fire, low shield, small totem pop, remove vanilla particles, jump/hit/damage effects.

## v0.6
- Menu size fix: windows always fit the screen and scroll; +/- buttons in the dock and the - / + keys change the size.
- X-Ray: boxes through walls with its own block list (ores by default), Tracers keep theirs.
- Sus Chunk Finder: grown amethyst buds -> chunk marked red (world curtain + HUD outline + Finder window).
- Fake blocks (dirt=mace, stone=trident, deepslate=ancient debris, cobbled deepslate=emerald block, end stone=iron block, oak log=gold block, oak planks=diamond block) and butterfly knife via resourcepacks/MyClientFake.
- Texture editor: Copy / Paste between blocks.
- Schematic builder walks, looks and places like a player.
- Donut window: fake scoreboard and fake /pay (visual only).
