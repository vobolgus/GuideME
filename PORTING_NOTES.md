# GuideME Fabric Port — Notes

Branch: `fabric-26.1` (based on `v26.1.10-alpha`). Goal: dual-loader Gradle structure
mirroring the recipe proven in the sibling AE2 repo (`Applied-Energistics-2`, branch with
`loader/neoforge` + `loader/fabric`).

**STATUS: code port COMPLETE.** `src/main/java` has zero `net.neoforged` imports; both
loaders build green (minus the pre-existing `javaImmaculateCheck` failure, see below);
`org.appliedenergistics:guideme-fabric:26.1.10-alpha` (shaded jar) publishes to mavenLocal.
See "Code port (Phase 2)" sections at the bottom for the seams AE2's Phase 3 needs.

## What was done (build restructure, no code port yet)

### Before
- Root project = the entire NeoForge mod: ModDevGradle 2.0.134, shadow 9.3.0, ProGuard,
  publishing (`org.appliedenergistics:guideme`), single `main` source set + `test` +
  `testmod` (resources only), plus loader-neutral `:markdown` subproject.
- Gradle wrapper 9.2.1; settings used `FAIL_ON_PROJECT_REPOS` with only `mavenCentral()`.

### After
```
build.gradle                 # version computation + comments only (no plugins)
loader/neoforge/build.gradle # everything ModDevGradle/shadow/proguard/publishing, moved from root
loader/fabric/build.gradle   # fabric-loom 1.17.8, empty overlay; shared sources gated
loader/fabric/src/main/resources/fabric.mod.json       # id "guideme", env "*", no entrypoints yet
loader/fabric/src/main/resources/guideme.accesswidener # stub: header `accessWidener v2 official` only
```

Changed files:
- `settings.gradle` — includes `:neoforge`/`:fabric` with projectDir under `loader/`;
  pluginManagement gained `maven.fabricmc.net` + `net.fabricmc.fabric-loom` 1.17.8;
  ModDevGradle bumped 2.0.134 → 2.0.141 (the combination proven with Gradle 9.5.1 in AE2);
  `repositoriesMode` switched `FAIL_ON_PROJECT_REPOS` → `PREFER_PROJECT` (loom injects
  project-level repos and hard-fails otherwise); settings-level repos added for
  `net.fabricmc*` (maven.fabricmc.net) and `com.mojang` (libraries.minecraft.net).
- `gradle/wrapper/gradle-wrapper.properties` — 9.2.1 → 9.5.1 (fabric-loom 1.17.8 requires 9.5+).
- `gradle.properties` — added `fabric_loader_version=0.19.3`,
  `fabric_api_version=0.151.0+26.1.2`, `guideme.fabric.shared=false`.
- `build.gradle` — reduced to version-number logic (TAG env / `version` property /
  `ProjectVersionSource`) + `printProjectVersion`. Subprojects use `version = rootProject.version`.
- `loader/neoforge/build.gradle` — the old root build, with all paths re-anchored:
  `sourceSets` use `srcDir rootProject.file('src/main/java')` etc. (loader-local default
  srcDirs are kept for future NeoForge-only overlay code); `generateModMetadata` reads
  `rootProject.file('src/main/neoforge.mods.toml')`; shadowJar `from(rootProject.file('web/dist'))`;
  proguard template/sourceDir point at root; data run writes to root `src/generated/resources`;
  run `gameDirectory = rootProject.file('run')` (unchanged location). `group` set explicitly to
  `org.appliedenergistics` (ProjectDefaultsPlugin defaults it to `guideme`), publishing
  artifactId stays `guideme` — coordinates unchanged.
- `loader/fabric/build.gradle` — loom 1.17.8. MC 26.1 is unobfuscated: **no mappings line**,
  and loom 1.17 has **no `modImplementation`** — plain `implementation` for fabric-loader
  0.19.3 and fabric-api 0.151.0+26.1.2. Publishing artifactId `guideme-fabric`.
- `buildSrc/src/main/java/FlatBuffersPlugin.java` — was using project-relative paths
  (`src/main/flatbuffers/...`, `web/scene-ts`); now anchored at `project.getRootDir()` so it
  works when applied to `:neoforge`. Note it also adds `<root>/src/main/flatbuffers/generated`
  as a `main` java srcDir.
- `.gitignore` — added `/loader/*/run` (blocklist style; `build`/`.gradle` already match anywhere).

### Shading approach (preserved as-is)
GuideME shades via a custom `shaded` configuration (transitive=false) + the
`com.gradleup.shadow` plugin: `shadowJar` relocates methvin/snakeyaml/flatbuffers/lucene/
snowball into `guideme.internal.shaded.*`, then `proguardJar` (proguard-gradle 7.8.2, via
`buildscript` classpath) shrinks the shadow jar, `postProcessJar` produces the final
unclassified jar, and `apiElements`/`runtimeElements` outgoing artifacts are cleared and
replaced with `postProcessJar` so only the processed jar is published. This entire chain
moved verbatim into `loader/neoforge/build.gradle` (only paths re-anchored). The
`localImplementation` configuration mirrors the shaded deps on the compile/runtime classpath
without exposing them transitively.

### Shared-source gate
`:fabric` compiles only its (currently empty) overlay in `loader/fabric/src`. Setting
`guideme.fabric.shared=true` adds root `src/main/java` + `src/main/flatbuffers/generated`
+ resources (minus `META-INF/accesstransformer.cfg`) and the shaded deps as plain
`implementation`. Fabric-side shading (loom `include` nested jars vs. shadow) is decided
when the gate flips.

## What will bite during the actual code port

- **17 files in `src/main/java` import `net.neoforged.*`** (61 imports total). They are all
  compiled into `:neoforge:main` and must be split into loader abstractions before the
  `guideme.fabric.shared` gate can flip:
  - `guideme/internal/GuideME.java`, `GuideMEClient.java`, `GuideMEServerProxy.java` —
    mod entrypoints / bus wiring → become NeoForge overlay + Fabric `ModInitializer`/
    `ClientModInitializer` entrypoints in `loader/*/src`.
  - `guideme/internal/GuideOnStartup.java`, `GuideSourceWatcher.java`
  - `guideme/internal/siteexport/SiteExporter.java`, `SiteExportWriter.java`, `SiteExportOnStartup.java`
  - `guideme/internal/util/Platform.java` (the natural seam — grow the platform abstraction here),
    `guideme/internal/util/FluidBlitter.java` (fluid rendering → Fabric FluidRenderHandler),
  - `guideme/internal/hotkey/OpenGuideHotkey.java` (key mappings),
  - `guideme/internal/screen/GuideScreen.java`,
  - `guideme/internal/scene/FakeRenderEnvironment.java`,
  - `guideme/internal/data/GuideMELanguageProvider.java` (datagen),
  - `guideme/render/RenderContext.java` (public API — NeoForge type in API surface!),
  - `guideme/scene/element/FakeForwardingServerLevel.java`, `guideme/scene/level/GuidebookLevel.java`.
- **`META-INF/accesstransformer.cfg`** (root `src/main/resources`) must be translated to
  `guideme.accesswidener` entries (namespace `official` on unobfuscated MC). The AT file ships
  in the neoforge jar only; the fabric resources gate excludes it.
- **Mod metadata**: `src/main/neoforge.mods.toml` is injected via the `generateModMetadata`
  task in `:neoforge` only; `fabric.mod.json` lives in the fabric overlay and is expanded by
  `processResources` (`version`, `minecraft_version`, `fabric_loader_version`).
- **`testmod`/`test`/unitTest** are ModDevGradle features and live in `:neoforge` only.
  Fabric gametests need their own wiring later (fabric-loader JUnit / fabric gametest API).
- **ProGuard runs on the Gradle JVM** (`System.getProperty('java.home')` → the JDK 21 used to
  launch Gradle), while code is compiled with the toolchain JDK 25. Watch for class-version
  complaints from proguard if shared code starts using JDK 22+ APIs.
- Gradle configuration cache: don't reference `project` inside task-execution closures;
  capture into local vars first (pattern already followed in the moved build files).

## Build gates

Always set the Gradle JVM first (no system JDK on this machine; compilation itself uses
the toolchain JDK resolved from `java_version`):

```
export JAVA_HOME="$HOME/.gradle/jdks/eclipse_adoptium-21-aarch64-os_x.2/jdk-21.0.11+10/Contents/Home"
```

- `./gradlew :fabric:build` — **GREEN.** Produces
  `loader/fabric/build/libs/guideme-fabric-<version>.jar` containing the expanded
  `fabric.mod.json` (id `guideme`, env `*`, empty entrypoints) and the
  `guideme.accesswidener` stub. `compileJava` is NO-SOURCE (empty overlay,
  `guideme.fabric.shared=false`); loom's `validateAccessWidener` passes on the stub.
- `./gradlew :neoforge:build` — **FAILS only at `:neoforge:javaImmaculateCheck`**
  (code-formatting check, dev.lukebemish.immaculate 0.1.15 / eclipse formatter 3.37.0).
  This failure is **pre-existing**, not caused by the restructure: a pristine worktree of
  the base commit (`14de80c`, tag `v26.1.10-alpha`, original single-project layout,
  original Gradle 9.2.1 wrapper) fails `javaImmaculateCheck` identically — formatting
  drift in unmodified upstream sources (line-wrapping differences in e.g.
  `GuideScreen.java`, `SceneExporter.java`, `DocumentScreen.java`; >5 files, immaculate
  truncates the report). Likely the upstream alpha tag was cut without running `check`,
  or the formatter wraps differently on this JDK/platform.
- `./gradlew :neoforge:build -x :neoforge:javaImmaculateCheck` — **GREEN**, i.e.
  everything else passes: `compileJava` (shared sources + flatbuffers), `test`
  (JUnit via ModDevGradle unitTest, NeoForge runtime), `jar` → `shadowJar` →
  `proguardJar` → `postProcessJar`, `javadoc`, `sourcesJar`, `assemble`, `check`.
- `./gradlew :neoforge:compileJava :neoforge:test` — **GREEN** (the fallback gate).

Do NOT "fix" the formatting failure by running `javaImmaculateFormat` on this branch —
it would touch dozens of shared source files and bloat the port diff. Leave it to
upstream (or a dedicated formatting-only commit).

## Code port (Phase 2) — gate results

1. **Decoupling:** `grep -rl "net.neoforged" src/main/java | wc -l` → **0**.
   `:neoforge:compileJava` + `:neoforge:test` green; full `:neoforge:build
   -x :neoforge:javaImmaculateCheck` green (jar→shadowJar→proguardJar→postProcessJar,
   javadoc, sources, unit tests incl. ApiConsistencyTest).
2. **Fabric build:** `guideme.fabric.shared=true` is now the default;
   `:fabric:build -x :fabric:javaImmaculateCheck` green (the immaculate failure is the same
   PRE-EXISTING formatting drift in unmodified upstream shared sources that already failed on
   `:neoforge` at the base commit — :fabric now also runs the check over the shared tree).
3. **Publish:** `version=26.1.10-alpha` pinned in `gradle.properties` (without it the version
   is derived from the branch name → `26.1.11-alpha.0+fabric-26.1`).
   `./gradlew :fabric:publishToMavenLocal` →
   `~/.m2/repository/org/appliedenergistics/guideme-fabric/26.1.10-alpha/` (jar+pom+module).
4. **Static smoke:** published jar contains expanded `fabric.mod.json` (id `guideme`,
   version `26.1.10-alpha`, entrypoints below, depends fabricloader>=0.19.3 / fabric-api /
   minecraft ~26.1.2), `guideme.accesswidener` (loom-validated), `guideme.mixins.json` +
   `guideme.optional.mixins.json`, both entrypoint classes, all shaded deps. NO runClient
   was attempted (no display) — first runtime smoke test still outstanding.
5. **Shading:** `:fabric` uses com.gradleup.shadow with the SAME relocations as `:neoforge`
   (methvin/snakeyaml/flatbuffers/lucene/snowball → `guideme.internal.shaded.*`), service
   files merged (relocated lucene SPI files verified), `web/dist` embedded at
   `guideme/internal/web/default-assets`, `:markdown` bundled (`guideme.libs.*`, not
   relocated — same as NeoForge). The PUBLISHED artifact is the shaded jar
   (apiElements/runtimeElements outgoing artifacts replaced, plain jar classifier `slim`).
   **ProGuard is skipped on Fabric** (published unshrunk, ~10 MB vs ~? MB shrunk NeoForge).

## Code port (Phase 2) — architecture

### Entrypoints
- NeoForge (in `loader/neoforge/src/main/java`, package `guideme.internal.neoforge` —
  must be an `internal` package or ApiConsistencyTest fails):
  `GuideMENeoForge` (@Mod common: DeferredRegisters, payload handler, /guide command,
  OnDatapackSyncEvent recipe sync) and `GuideMENeoForgeClient` (@Mod dist=CLIENT: all 17
  client events, datagen, ConfigurationScreen extension point). `src/test`'s TestMod now
  constructs `GuideMENeoForgeClient`.
- Fabric (in `loader/fabric/src/main/java`):
  `guideme.internal.fabric.GuideMEFabric` (main) and
  `guideme.internal.fabric.client.GuideMEFabricClient` (client).

### Platform seams (shared, `guideme.internal.platform`)
- `GuideMEPlatform` (common; AE2 NetworkAdapter-style static holder): `isModLoaded`,
  `getModDisplayName`, `getModVersion`, `populateDatapackRepository` (NeoForge:
  ResourcePackLoader; Fabric: NO-OP — fabric-resource-loader's PackRepository mixin injects
  mod data packs into vanilla-source repositories automatically), `sendOpenGuideRequest`.
- `GuideMEClientPlatform` (client-only): `createListenerCookie` (NeoForge appends
  ConnectionType.NEOFORGE to the vanilla 14-arg CommonListenerCookie), `getFluidIcon`
  (sprite+tint; NeoForge `fluidTintSource()`, Fabric vanilla `tintSource()` on the legacy
  block state), `getFluidName` (NeoForge FluidStack.getHoverName, Fabric
  FluidVariantAttributes), `getFluidsInDisplay` (NeoForge FluidStackContentsFactory; Fabric
  empty — fluid slot displays don't exist there), `getBoundKey(KeyMapping)` (NeoForge
  `getKey()` patch, Fabric KeyMappingHelper.getBoundKeyOf), `renderCustomFluid` (NeoForge
  FluidModel.customRenderer() hook; Fabric always false), `runOnNextClientTick`,
  `interceptScreenOpening` (ScreenEvent.Opening / ScreenEvents.AFTER_INIT+setScreen),
  `whenResourcesLoaded` (ClientResourceLoadFinishedEvent isInitial / CLIENT_STARTED +
  LoadingOverlay.reload future — the old `GuideOnStartup.afterClientStart` was DELETED, it
  had no callers).
- `ConfigBackend` (AE2 step-9 style, shaped to actual usage: 5 boolean options in 3
  sections + save): `NeoForgeConfigBackend` wraps ModConfigSpec → identical
  `config/guideme.toml`; `FabricConfigBackend` hand-rolls a TOML subset (same sections/
  comments/keys, parses bare `key = true/false` lines; can read a NeoForge-written file).
  No config SCREEN on Fabric (ModMenu integration = future work).
- `FluidIcon` record (sprite + tintColor).

### Client registration surface (documented on GuideMEClient's javadoc, wired per loader)
sound event GUIDE_CLICK_EVENT; keybind category+hotkey (`OpenGuideHotkey.init(KeyMapping)` —
NeoForge passes KeyConflictContext.GUI, Fabric has no equivalent); item model dispatch codec
(`GuideItemDispatchUnbaked`; Fabric writes into AW'd `ItemModels.ID_MAPPER` directly); PiP
renderer (`PictureInPictureRendererRegistry` on Fabric); GuideReloadListener (now public;
Fabric wraps it in `FabricReloadListenerWrapper implements IdentifiableResourceReloadListener`
— note 26.1 reload signature `reload(SharedState, Executor, PreparationBarrier, Executor)`);
GUI-atlas sprite reset (NeoForge TextureAtlasStitchedEvent; Fabric: extra reload listener →
GuiAssets.resetSprites()); tick start/end hooks; tooltip hook
(`OpenGuideHotkey.onItemTooltip`; Fabric's ItemTooltipCallback has NO player context — guarded
with `player != null && screen != null`, so creative-search-tree tooltip pollution is only
approximately filtered); client commands (`GuideClientCommand.register(dispatcher, Feedback<S>)`
is now GENERIC over the source type — NeoForge CommandSourceStack, Fabric
FabricClientCommandSource); StructureCommands via server-command registration from the client
entrypoint; packet receivers; `onRecipesReceived`/`onClientDisconnected`.
**Render pipelines are NOT pre-registered on Fabric** (no API; the backend compiles them
lazily on first use — NeoForge's RegisterRenderPipelinesEvent is an optimization only).

### Networking
- `OpenGuideRequest` (vanilla CustomPacketPayload) gained a shared `handle(Player)`; registered
  per loader (NeoForge registrar "1.0" playToClient; Fabric PayloadTypeRegistry.clientboundPlay()
  — NOTE: loom-era name, `playS2C()` does not exist in fabric-api 0.151).
- Recipe sync (NeoForge OnDatapackSyncEvent.sendRecipes/RecipesReceivedEvent) is replicated on
  Fabric with a custom chunked payload `guideme:sync_recipes`
  (`guideme.internal.fabric.network.SyncRecipesPayload`: clear flag → 250-recipe chunks via
  RecipeHolder.STREAM_CODEC → final payload with recipe-type ids; client rebuilds
  RecipeMap.create(...)). Sent on ServerPlayConnectionEvents.JOIN and END_DATA_PACK_RELOAD,
  guarded by `ServerPlayNetworking.canSend` (vanilla/older clients are skipped).
- `Platform.fallbackClientRecipeManager` (RecipeManager) became
  `Platform.fallbackClientRecipeMap` (RecipeMap) — vanilla RecipeManager has NO `recipeMap()`
  accessor (NeoForge patch); GuideOnStartup builds the map via
  `RecipeMap.create(getRecipeManager().getRecipes())`.

### PUBLIC API CHANGES (for AE2 Phase 3)
- **`guideme.render.RenderContext`: `renderFluid(FluidStack, int, int, int, int)` was REMOVED**
  (NeoForge FluidStack in the public API). `renderFluid(Fluid, ...)` is kept; callers with a
  stack use `stack.getFluid()` (components were already ignored — the icon comes from the
  default fluid state model). `FluidBlitter.create(FluidStack)` → `create(Fluid)` accordingly.
- `Platform.getFluidDisplayName(Fluid)` unchanged (now routed through the platform seam).
- `SiteExportWriter.addFluid(String, FluidStack, String)` → `(String, Fluid, String)`;
  `SiteExporter.referenceFluid(FluidStack)` overload removed (`referenceFluid(Fluid)` stays).
- `guideme.scene.element.FakeForwardingServerLevel` is now abstract with a protected
  `delegate`; instantiate `PlatformFakeServerLevel` instead (loader-duplicated subclass, the
  NeoForge twin carries the isAreaLoaded/getAuxLightManager/getModelData forwards).
- `GuidebookLevel` extends the loader-duplicated `guideme.scene.level.GuidebookLevelPlatform`
  (NeoForge twin: ModelDataManager + PartEntity dragonParts + onAddedToLevel hook; Fabric twin:
  vanilla `Collection<EnderDragonPart> dragonParts()` — the signature differs between loaders!).
- `GuideMEClient` ctor is now `GuideMEClient(ConfigBackend)`; all event wiring left the class.
- `GuideME` is final with static holders; `GuideME.GUIDE_ITEM` is still a
  `Supplier<GuideItem>` (loader sets the instance via `GuideME.setGuideItem`).
- `SpriteFinder` (package-private) ctor takes `Collection<TextureAtlasSprite>` (was Map) —
  fed from AW'd `TextureAtlas.sprites` (NeoForge's `getTextures()` returns a Map and is a patch).

### NeoForge patches encountered in shared code WITHOUT imports (the silent ones)
`RenderPipeline.toBuilder()` (→ shared `guideme.internal.util.PipelineBuilders.toBuilder`,
rebuilds a vanilla Snippet from the public getters);
`GuiGraphicsExtractor.submitGuiElementRenderState/submitPictureInPictureRenderState/
peekScissorStack` (→ vanilla `guiRenderState.addGuiElement` /
`guiRenderState.addPicturesInPictureState` / `scissorStack.peek()` — needed TWO NEW AT entries
(`scissorStack` field + ScissorStack inner class) mirrored in the AW);
`TextureTarget(String,int,int,boolean,boolean)` (5th stencil arg — vanilla 4-arg used);
`ModelProvider(PackOutput,String)` + `LanguageProvider` (datagen → both providers moved to the
NeoForge overlay, datagen stays NeoForge-only); `ItemStackTemplate(Holder<Item>)` convenience
ctor (→ vanilla `(Holder, int, DataComponentPatch)`); `Entity.onAddedToLevel()` (→
GuidebookLevelPlatform hook); `Level.dragonParts()` signature widening;
`RecipeManager.recipeMap()`; `FluidModel.fluidTintSource()/customRenderer()`;
`KeyMapping.getKey()`; `CommonListenerCookie` extra ConnectionType ctor arg.

### AT → AW translation (guideme.accesswidener, namespace `official`)
ALL 38 original AT entries exist with identical signatures in the vanilla 26.1.2 jar
(verified via javap on `~/.gradle/caches/fabric-loom/26.1.2/minecraft-merged.jar`) —
**no NeoForge-patched members, hence NO mixin accessors were needed** (the anticipated
RenderType.state / SpriteContents$AnimatedTexture / GameRenderer / Minecraft.mainRenderTarget /
BufferSource.startedBuilders entries are all plain vanilla members in 26.1). Mapping notes:
- all AT `public` → `accessible`; `public-f Minecraft.mainRenderTarget` → accessible+mutable;
- AT `protected GuiGraphicsExtractor.<init>` (private ctor, super-called by a GuideME
  subclass) → `accessible method <init>` (AW has no protected target; public works);
- package-private inner classes (`RenderSetup$TextureBinding`, `SpriteContents$AnimatedTexture`,
  `SpriteContents$FrameInfo`) → `accessible class` + ctor entries where the AT had them;
- `RenderType.create(String,RenderSetup)` needed NOTHING: fabric-api's transitive access
  wideners already widen it (this is also why fabric-loader/fabric-api MUST stay on
  `implementation`, not a custom configuration — loom only applies mod metadata/interface
  injection/transitive AWs for dependencies on the standard configurations; moving them to
  `localImplementation` silently dropped sponge-mixin AND the transitive AWs);
- fabric-only extra entries: `ItemModels.ID_MAPPER` (item model dispatch registration),
  `GuiGraphicsExtractor.scissorStack` + `ScissorStack` class (mirrored as new AT entries so
  shared code uses the vanilla member on both loaders).
- Rebase checklist: any AT change must be mirrored into the AW (and vice versa for the two
  shared-code entries added on this branch).

### Mixins
`guideme.mixins.json` (empty) + `guideme.optional.mixins.json` (required:false,
LuceneVectorizationMixin targeting the SHADED lucene class) are both registered in
fabric.mod.json. There is NO mixin plugin upstream, so no LoaderPlatform seam was needed.
Shadow relocates the mixin class's own references, so the prod-jar mixin correctly references
`guideme.internal.shaded.lucene...`; in dev the optional config simply doesn't apply.

### Known gaps / future work (Fabric)
- No runClient/runServer smoke test yet (no display on this machine).
- No config screen (NeoForge keeps ConfigurationScreen; add ModMenu integration later).
- `runOnNextClientTick`/`whenResourcesLoaded` leave inert listeners registered (fabric events
  can't unregister); dev-tooling only.
- Tooltip hotkey progress bar may appear in creative search tree text in rare cases (no
  player context on Fabric's ItemTooltipCallback).
- Fabric gametests not wired (`test`/`testmod` remain :neoforge-only).
- Datagen (language/model providers) is NeoForge-only; regenerate via :neoforge runData.
- ProGuard not applied to the fabric jar (published unshrunk).
