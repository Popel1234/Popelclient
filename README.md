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
