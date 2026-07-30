# GuideME Fabric Port — Notes

Branch: `fabric-26.1` (based on `v26.1.12-beta`; previously `v26.1.10-alpha` — see "Rebase log").
Goal: dual-loader Gradle structure
mirroring the recipe proven in the sibling AE2 repo (`Applied-Energistics-2`, branch with
`loader/neoforge` + `loader/fabric`).

**STATUS: code port COMPLETE and SHIPPING.** `src/main/java` has zero `net.neoforged` imports;
`org.appliedenergistics:guideme-fabric:26.1.12-beta` (shaded jar) publishes to mavenLocal.
See "Code port (Phase 2)" sections at the bottom for the seams AE2's Phase 3 needs.

**State 2026-07-29:** rebased onto upstream **`v26.1.12-beta`** (8 fork commits replayed, **zero
conflicts** — see the last entry of the Rebase log), **pushed** to `fork/fabric-26.1` (in sync, no
local-only commits), and the AE2 fork bumped to it. Both loaders build green **with no `-x`
exclusions** — the old "minus the pre-existing `javaImmaculateCheck` failure" caveat no longer
applies, since `javaImmaculateApply` was run across the shared tree; shared JUnit **379/379 on both
loaders**. The shipped `guideme-fabric-26.1.12-beta.jar` runs in the live pack alongside
`appliedenergistics2-fabric-26.1.10-beta.jar`, so the "first runtime smoke test still outstanding"
note further down (a Phase-2 record) is closed. Open items across the forks are indexed in
`~/IdeaProjects/create26-ports/BACKLOG.md`.

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
- **`testmod`/unitTest** are ModDevGradle features and live in `:neoforge` only.
  Fabric gametests need their own wiring later (fabric gametest API).
- **Shared JUnit suite runs on BOTH loaders** (2026-07-10): `:fabric` wires root `src/test/java`
  into its `test` source set (excluding the NeoForge-only `guideme/guidebook/TestMod.java` @Mod
  entrypoint) and uses `net.fabricmc:fabric-loader-junit` as the loom twin of MDG's unitTest.
  Two fabric-only traps solved in `loader/fabric/src/test`: (a) vanilla registries are NOT
  bootstrapped by fabric-loader-junit → `BootstrapMinecraftExtension` (auto-registered via
  META-INF/services + `junit-platform.properties` autodetection) calls
  `SharedConstants.tryDetectVersion()` + `Bootstrap.bootStrap()`; (b) the extension instance is
  created on the app classloader while test classes load in Knot → the bootstrap is invoked
  REFLECTIVELY through `context.getRequiredTestClass().getClassLoader()`, otherwise you
  bootstrap the wrong classloader's registry copies and still crash "Not bootstrapped".
  Both loaders execute the identical 379-test suite (`:fabric:test` / `:neoforge:test`).
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
- `./gradlew :neoforge:build` — **GREEN** (since the 2026-07-10 formatting fix below):
  `compileJava` (shared sources + flatbuffers), `test` (JUnit via ModDevGradle unitTest,
  NeoForge runtime), `jar` → `shadowJar` → `proguardJar` → `postProcessJar`, `javadoc`,
  `sourcesJar`, `assemble`, `check`. ⚠ `proguardJar` requires the **Gradle JVM** to be a
  JDK whose `jmods` proguard 7.8.2 can read — with a Temurin **26** launcher it fails
  "incomplete class hierarchy" (class-file major 70); use the JDK **21** launcher above.
- `./gradlew :neoforge:compileJava :neoforge:test` — **GREEN** (the fallback gate).

### Formatting drift — FIXED 2026-07-10 (`javaImmaculateApply`, formatting-only)

`javaImmaculateCheck` used to fail on ~26 files (blocking plain `:fabric:build` /
`:neoforge:build`, which run `check`). Root cause was **(a) sources genuinely out of
format**, NOT formatter/toolchain nondeterminism:

- The failure reproduces on a pristine worktree of the upstream base commit `14de80c`
  (tag `v26.1.10-alpha`) with the same files — most of the drift is **inherited from
  upstream** (over-length lines in `StructureCommands`, `SiteExporter`,
  `GuideItemDispatch*`, javadoc wraps, etc.).
- Upstream CI never catches it: upstream's `build.yml`/`release.yml` run
  `./gradlew build publish -x check` — the formatter check is explicitly excluded.
- The Temurin-26 suspicion is disproven: the check fails/passes **identically** under
  JDK 21 and JDK 26 Gradle daemons, and `javaImmaculateApply` is idempotent (a second
  apply changes nothing). Formatter versions are pinned (immaculate 0.1.15, eclipse JDT
  3.37.0, `docs/codeformat.xml`) — output is deterministic.
- The rest of the drift was added by the fork's own port commits (unused imports in
  `Platform.java`, `GuidebookLevel.java`, import order in `GuideScreen.java`,
  `GuideSourceWatcher.java` and the fabric/neoforge overlays).

Fix: `./gradlew :fabric:javaImmaculateApply :neoforge:javaImmaculateApply
:markdown:javaImmaculateApply` — 26 files, +57/−48 lines, verified formatting-only
(import reorder / unused-import removal, 120-col rewraps, one empty-body brace style).
After the fix: all three `javaImmaculateCheck` tasks green, `:fabric:test` 379/379,
`:fabric:build` and `:neoforge:build` fully green with no `-x` exclusions.
Rebase caveat: upstream is still unformatted, so rebasing onto a new upstream alpha may
reintroduce drift in upstream-touched files — rerun the three apply tasks after every
rebase (formatting-only diffs, safe to fold into the rebase commit).

## Code port (Phase 2) — gate results

1. **Decoupling:** `grep -rl "net.neoforged" src/main/java | wc -l` → **0**.
   `:neoforge:compileJava` + `:neoforge:test` green; full `:neoforge:build
   -x :neoforge:javaImmaculateCheck` green (jar→shadowJar→proguardJar→postProcessJar,
   javadoc, sources, unit tests incl. ApiConsistencyTest).
2. **Fabric build:** `guideme.fabric.shared=true` is now the default;
   `:fabric:build -x :fabric:javaImmaculateCheck` green (the immaculate failure is the same
   PRE-EXISTING formatting drift in unmodified upstream shared sources that already failed on
   `:neoforge` at the base commit — :fabric now also runs the check over the shared tree).
3. **Publish:** `version=26.1.12-beta` pinned in `gradle.properties` (without it the version
   is derived from the branch name → `26.1.11-alpha.0+fabric-26.1`). **Bump this pin to the new
   upstream tag as part of every rebase.**
   `./gradlew :fabric:publishToMavenLocal` →
   `~/.m2/repository/org/appliedenergistics/guideme-fabric/26.1.12-beta/` (jar+pom+module).
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
- `:fabric:runServer` — **dedicated-server boot verified clean 2026-07-09** (`Done (1.565s)`, then
  graceful stop on stdin EOF). The `GuideMEFabric` main entrypoint's server-side registration path
  (guide item + data component + `guide_id`/`page_anchor` argument types + clientbound payloads +
  `/guide` command + `RecipeSync.init`) initializes with NO client-class leak and no mixin apply
  failures — the #1 dual-loader DoD gate now passes. `runClient` still unrun (needs a display on this
  machine); the only benign log noise is the first-run `server.properties` NoSuchFileException (defaults
  get generated).
- No config screen (NeoForge keeps ConfigurationScreen; add ModMenu integration later).
- `runOnNextClientTick`/`whenResourcesLoaded` leave inert listeners registered (fabric events
  can't unregister); dev-tooling only.
- Tooltip hotkey progress bar may appear in creative search tree text in rare cases (no
  player context on Fabric's ItemTooltipCallback).
- Fabric gametests not wired (`testmod` remains :neoforge-only). The shared JUnit `test` suite
  DOES run on :fabric since 2026-07-10 (fabric-loader-junit; see the build-layout notes above).
- Datagen (language/model providers) is NeoForge-only; regenerate via :neoforge runData.
- ProGuard not applied to the fabric jar (published unshrunk).

## Add-on recipe types must be registered — `GuidesCommon.addSyncedRecipeTypes` (2026-07-25)

Live bug on the dedicated server: AE2's guidebook rendered
`Couldn't find recipe for ae2:damaged_budding_quartz` for `<RecipeFor id="damaged_budding_quartz" />`
while the vanilla crafting recipes on the same page were fine.

**Cause.** `RecipeCompiler` resolves `<Recipe/>`, `<RecipeFor/>`, `<RecipesFor/>` against
`Platform#getRecipeMap()` → `GuideMEClient#recipeMap`, i.e. the map built from OUR sync payload alone.
On NeoForge that is effectively "everything every mod asked for": `OnDatapackSyncEvent` merges all
`sendRecipes` requests into one `ReferenceSet`, one packet and one `RecipesReceivedEvent`, so an add-on
that syncs its own recipe types incidentally fills GuideME's client map too (this is exactly what AE2
relies on — its `getServerSyncedRecipeTypes()` list carries a `// For GuideME` comment). On Fabric each
mod owns a payload and a client-side map: `ae2:sync_recipes` fed AE2's map, `guideme:sync_recipes` fed
ours with only the four built-in types, and every add-on recipe type in a guide page failed to resolve.

**API.** `GuideME.SYNCED_RECIPE_TYPES` (public static final List) → private `LinkedHashSet` +
`GuideME#getSyncedRecipeTypes()` / `GuideME#addSyncedRecipeTypes(...)`, exposed publicly as
**`GuidesCommon.addSyncedRecipeTypes(RecipeType<?>...)`** — call it during mod init on both sides,
paired with the `RecipeTypeMappingSupplier` that renders the type. Both loader sync paths consume the
union (NeoForge dedupes for free; the Fabric sender skips + warns on types with no registry key).
`RecipesReceivedEvent`-based behavior on NeoForge is unchanged: the union it sends is identical to what
the add-on already requested itself.

**Why no gate caught it:** the guide export runs without a server, so `Platform#getRecipeMap()` falls
back to `Platform.fallbackClientRecipeMap` (the complete recipe manager) and every page renders. Only a
client attached to a server (integrated OR dedicated — this is NOT an MP-only bug) exercises the synced
map. The regression guard lives in AE2's fabric gametests (`guideme_recipe_sync_types`).

## Tooltip callbacks fire OFF the render thread — `ItemTooltipCallback` guard (2026-07-30)

Live single-player crash from a full-pack player: typing in the creative-inventory search box →
`ReportedException: charTyped event handler` / `IllegalStateException: Rendersystem called from wrong thread`,
`GlStateManager._bindTexture` ← `FontTexture.add` ← `BitmapProvider$Glyph.bake` ← `Font.width`
← `OpenGuideHotkey.makeProgressBar` ← `GuideMEFabricClient.lambda$onInitializeClient$0`
← `ItemTooltipCallback` ← `ItemStack.getTooltipLines` ← `SessionSearchTrees.lambda$getTooltipLines$0`.

**Cause (verified by `javap -c` on `minecraft-merged.jar`, not by reading a decompile).**
`net.minecraft.client.multiplayer.SessionSearchTrees` schedules every search-tree build on
`net.minecraft.util.Util.backgroundExecutor()` via `CompletableFuture.supplyAsync` —
`updateCreativeTooltips` (creative name search), `updateCreativeTags`, and `updateRecipes` (recipe book).
The name/recipe builders call `getTooltipLines`, whose bytecode is
`ItemStack.getTooltipLines(ctx, aconst_null /* player */, flag)`. So on 26.1 tooltips are built for real,
off the render thread, **with a null player**. `creativeNameSearch()` then `join()`s that future on the
render thread, which is why the exception is reported inside `charTyped` rather than on the worker.

Two consequences:

- **NeoForge is safe by accident of its event shape.** `ItemTooltipEvent` carries the player, and upstream
  filters `evt.getEntity() != Minecraft.getInstance().player` — null never equals the local player.
- **Fabric's `ItemTooltipCallback` has no player parameter**, so that filter has no equivalent. The
  previous stand-in (`minecraft.player == null || minecraft.screen == null`) only catches the *startup*
  build; once you are in a world with the creative screen open, both are non-null on the worker thread too.

**Fix** (`GuideMEFabricClient.isLocalPlayerTooltip()`, Fabric overlay only — shared sources untouched, so
the NeoForge twin stays byte-identical to upstream): `RenderSystem.isOnRenderThread() && isLocalPlayerVisible()`.
The `&&` short-circuit is load-bearing — the thread check must gate the `Minecraft.getInstance()` deref, not
merely precede it. Off-thread callers get the tooltip minus the hotkey progress bar, which is a purely visual
affordance the search index does not need; it also keeps `OpenGuideHotkey`'s static state
(`ticksKeyHeld`, `guidebookPages`, `previousItemId`) confined to the client thread.

**Upstream already fixed this once and lost it.** shartte's commit `59b53582ca` (2025-08-29, GuideME issue
#70 — EMI indexing tooltips off-thread under Sinytra Connector) added
`if (!Minecraft.getInstance().isSameThread()) return;` to `OpenGuideHotkey`. It exists **only on the 1.20.1
branch**; 1.20.4 / 1.21.x / 26.1 / 26.2 / main all lack it. So upstream 26.1 NeoForge is still exposed to the
EMI variant → **PR-able**: restore the guard in `OpenGuideHotkey.init`'s listener on `26.1`/`main`.
(We did not carry it into shared code here, to keep the NeoForge side faithful and rebases clean.)

**Other sites audited, none affected.** Only `OpenGuideHotkey.makeProgressBar` reaches `Font` from a tooltip
path. `GuideItem.appendHoverText` → `GuideMEClientProxy.addGuideTooltip` is also invoked off-thread by the
search tree, but it only reads `GuideRegistry` and emits `Component`s — no GL (and NeoForge shares that
exposure verbatim). Every other `Minecraft.font` use in the fork (`DocumentScreen`, `GuideSearchScreen`,
`RenderContext`, `SiteExporter`, `SceneExporter`) is render-thread-only. `ItemTooltipCallback` is the sole
Fabric hook the fork registers that vanilla can fire off-thread.

**Regression test:** `loader/fabric/src/test/java/guideme/internal/fabric/client/ItemTooltipThreadGuardTest.java`
(`:fabric:test`, 379 → 381). It claims the render thread on the JUnit thread, then calls the gate from
`Util.backgroundExecutor()` — the exact vanilla executor — and asserts it returns `false` without throwing.
Mutation-verified: deleting the `RenderSystem.isOnRenderThread()` term makes it fail with the off-thread NPE.
The second test pins the ordering by asserting the gate does *not* short-circuit on the render thread.

## Rebase log

### 2026-07-29 — `v26.1.10-alpha` → `v26.1.12-beta` (clean, zero conflicts)

Upstream range `v26.1.10-alpha..v26.1.12-beta` = 4 commits / 5 files:

- `c21079d` **LytSlot** (#93) — new public API `isSlotVisible()` / `setSlotVisible(boolean)`;
  when hidden, the slot background is not drawn and the item renders at z=0 instead of z=1.
  Additive, default `true` → no behavior change for existing pages. Add-on-facing API.
- `40e2cb4` **keybind category translation** (#90) — lang key `key.guideme.category` →
  `key.category.guideme.category` (vanilla 26.1 `KeyMapping.Category` derives its translation key
  as `key.category.<namespace>.<path>`; our category id is `guideme:category`). Touches
  `GuideMELanguageProvider` — which lives in `loader/neoforge/src` on this branch; **the rebase
  applied it there automatically** (git tracked the port commit's move as a rename), and the
  generated `src/generated/resources/.../en_us.json` came along on the shared path.
  This fixes an untranslated keybind category on BOTH loaders.
- `fa1374e` changelog only.
- `6a5f4d4` **DocumentScreen.calculateEffectiveScale** — `(float)(effectiveScale / currentScale)`
  → `(float) effectiveScale / currentScale`: the double→int-ish integer division could yield 0 and
  make the whole guide invisible at certain GUI scales. Real end-user fix, applies to Fabric too.

Nothing touched page loading / `_<lang>/` resolution (`LangUtil`, `MutableGuide#loadAssetInternal`),
the recipe-sync paths, the AT/AW, or any platform seam — so localized-guide overlays and the AE2
integration surface are unaffected apart from the two additive LytSlot methods.

Procedure that worked (keep for the next one):

1. `git fetch origin --tags` (`origin` = upstream AppliedEnergistics, `fork` = vobolgus).
2. `git rebase v26.1.12-beta` — all 8 fork commits replayed with **no conflicts**.
3. `gradle.properties`: `version=` pin bumped to the new tag.
4. **AT↔AW check**: upstream changed neither `src/main/resources/META-INF/accesstransformer.cfg`
   (40 entries) nor anything needing new access — AW (42 entries: 40 mirrored + fabric-only
   `ItemModels.ID_MAPPER` group) untouched. `:fabric:validateAccessWidener` green.
5. **Formatter**: as predicted, upstream shipped `LytSlot` unformatted (`{return visibleSlot;}`,
   `visibleSlot? 1: 0`). `./gradlew :fabric:javaImmaculateApply :neoforge:javaImmaculateApply
   :markdown:javaImmaculateApply` → 1 file, formatting-only.
6. Gates, all green: `:neoforge:build` (incl. proguard chain + javadoc + check) and `:fabric:build`
   (incl. `javaImmaculateCheck`, `validateAccessWidener`, shadowJar) with **no `-x` exclusions**;
   shared JUnit suite **379/379 on both loaders** (unchanged count — the upstream delta adds no tests).
7. `:fabric:publishToMavenLocal` → `org.appliedenergistics:guideme-fabric:26.1.12-beta`.

⚠ Gradle JVM: use the **JDK 21** launcher (`$HOME/.gradle/jdks/eclipse_adoptium-21-aarch64-os_x.2/
jdk-21.0.11+10/Contents/Home`); the toolchain still compiles with JDK 25 (`java_version=25`).
`/usr/libexec/java_home -v 25` does NOT resolve on this machine (only 21 and 26 are system-installed)
and the JDK 26 launcher breaks `proguardJar` (see "Build gates").
