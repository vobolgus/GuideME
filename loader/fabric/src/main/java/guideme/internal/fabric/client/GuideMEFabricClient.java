package guideme.internal.fabric.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import guideme.internal.GuideMEClient;
import guideme.internal.GuideReloadListener;
import guideme.internal.command.GuideClientCommand;
import guideme.internal.command.StructureCommands;
import guideme.internal.fabric.network.SyncRecipesPayload;
import guideme.internal.hotkey.OpenGuideHotkey;
import guideme.internal.item.GuideItemDispatchUnbaked;
import guideme.internal.network.OpenGuideRequest;
import guideme.internal.platform.GuideMEClientPlatform;
import guideme.internal.scene.ScenePictureInPictureRenderer;
import guideme.render.GuiAssets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemModels;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * Fabric entrypoint for the client-side part of GuideME. Wires the registration surface documented on
 * {@link GuideMEClient} to the Fabric API callbacks.
 */
public class GuideMEFabricClient implements ClientModInitializer {
    // Recipes received from the server in chunks, waiting for the final payload
    private final List<RecipeHolder<?>> pendingRecipes = new ArrayList<>();

    @Override
    public void onInitializeClient() {
        GuideMEClientPlatform.init(new FabricClientPlatform());

        var client = new GuideMEClient(new FabricConfigBackend());

        Registry.register(BuiltInRegistries.SOUND_EVENT, GuideMEClient.GUIDE_CLICK_ID,
                GuideMEClient.GUIDE_CLICK_EVENT);

        // Register the category in the vanilla sort order; it is equal (by record semantics)
        // to the shared KEYBIND_CATEGORY constant attached to the key mapping.
        KeyMapping.Category.register(GuideMEClient.KEYBIND_CATEGORY.id());
        // Note: NeoForge additionally restricts this mapping to the GUI key conflict context,
        // which has no Fabric equivalent.
        OpenGuideHotkey.init(KeyMappingHelper.registerKeyMapping(new KeyMapping(
                OpenGuideHotkey.HOTKEY_TRANSLATION_KEY, InputConstants.Type.KEYSYM, OpenGuideHotkey.DEFAULT_KEY,
                GuideMEClient.KEYBIND_CATEGORY)));

        ItemTooltipCallback.EVENT.register((stack, tooltipContext, tooltipFlag, lines) -> {
            if (!isLocalPlayerTooltip()) {
                return;
            }
            OpenGuideHotkey.onItemTooltip(stack, tooltipFlag, lines);
        });

        ClientTickEvents.START_CLIENT_TICK.register(minecraft -> client.clientTickStart());
        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> OpenGuideHotkey.onClientTickEnd());

        // Item model dispatch codec (vanilla mapper made accessible via the access widener)
        ItemModels.ID_MAPPER.put(GuideItemDispatchUnbaked.ID, GuideItemDispatchUnbaked.CODEC);

        PictureInPictureRendererRegistry.register(context -> new ScenePictureInPictureRenderer(
                context.bufferSource()));

        // Note: The custom render pipelines (Blitter.GUI_TEXTURED_OPAQUE, TextureDownloader.COPY_BLIT,
        // InWorldAnnotationRenderer.OCCLUDED_PIPELINE) are not pre-registered on Fabric; the render
        // backend compiles them lazily on first use.

        var clientResources = ResourceManagerHelper.get(PackType.CLIENT_RESOURCES);
        clientResources.registerReloadListener(
                new FabricReloadListenerWrapper(GuideReloadListener.ID, new GuideReloadListener()));
        // The GUI sprite atlas is rebuilt during every resource reload; reset the cached sprites then.
        clientResources.registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            private final Identifier id = guideme.internal.GuideME.makeId("reset_gui_sprites");

            @Override
            public Identifier getFabricId() {
                return id;
            }

            @Override
            public void onResourceManagerReload(ResourceManager resourceManager) {
                GuiAssets.resetSprites();
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(OpenGuideRequest.TYPE,
                (payload, context) -> payload.handle(context.player()));
        ClientPlayNetworking.registerGlobalReceiver(SyncRecipesPayload.TYPE,
                (payload, context) -> handleRecipeSync(client, payload));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, minecraft) -> client.onClientDisconnected());

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> GuideClientCommand
                .register(dispatcher, new GuideClientCommand.Feedback<FabricClientCommandSource>() {
                    @Override
                    public void sendFailure(FabricClientCommandSource source, Component message) {
                        source.sendError(message);
                    }

                    @Override
                    public void sendSystemMessage(FabricClientCommandSource source, Component message) {
                        source.sendFeedback(message);
                    }
                }));

        // These are meant for command blocks only usable in single player; registered from the client
        // entrypoint so they only exist with an integrated server, mirroring NeoForge's client-dist mod.
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> StructureCommands.register(dispatcher));
    }

    /**
     * Decides whether an {@link ItemTooltipCallback} invocation is a tooltip actually being shown to the local player,
     * which is the only case {@link OpenGuideHotkey#onItemTooltip} is contracted to receive.
     * <p>
     * NeoForge's {@code ItemTooltipEvent} carries the player the tooltip is built for and GuideME filters on it
     * ({@code evt.getEntity() != Minecraft.getInstance().player}). Fabric's callback has no player parameter, so the
     * same cases have to be recognised from client state instead.
     * <p>
     * The thread check is the load-bearing one and must stay FIRST. On 26.1
     * {@code net.minecraft.client.multiplayer.SessionSearchTrees} builds the creative-menu and recipe-book search trees
     * on {@code Util.backgroundExecutor()} ({@code CompletableFuture.supplyAsync}) and calls
     * {@code ItemStack.getTooltipLines(ctx, null, flag)} from there — with a live player and screen, so the checks
     * below do not catch it. {@link OpenGuideHotkey} then measures its "hold to show" hint with {@code Font.width},
     * which lazily bakes glyphs into the font texture, which is a GL call: "RenderSystem called from wrong thread". The
     * exception surfaces later on the render thread when the screen joins the search-tree future (observed as a crash
     * while typing in the creative search box, 2026-07-30). Skipping off-thread invocations only costs the progress-bar
     * line, which is a purely visual affordance — the search index does not need it — and it additionally keeps
     * {@link OpenGuideHotkey}'s static hotkey state confined to the client thread.
     * <p>
     * Upstream shipped the same guard on its 1.20.1 branch (commit {@code 59b53582ca}, "Try to fix EMI interfering with
     * GuideME", GuideME issue #70 — EMI indexes tooltips off-thread) as
     * {@code if (!Minecraft.getInstance().isSameThread()) return;}, but it was never forward-ported past 1.20.1, so
     * upstream 26.1 is still exposed to the EMI variant of this on NeoForge. Here the static, null-safe
     * {@link RenderSystem#isOnRenderThread()} is used instead — same thread in practice (Minecraft's constructor
     * assigns {@code gameThread} and calls {@code RenderSystem.initRenderThread()} on one thread), but it is the exact
     * invariant the failing glyph bake asserts and it stays callable without a client instance.
     */
    static boolean isLocalPlayerTooltip() {
        // Short-circuit: the thread check gates the client-state check, it is not merely first by luck.
        return RenderSystem.isOnRenderThread() && isLocalPlayerVisible();
    }

    /**
     * Skips the remaining cases that clearly aren't a tooltip shown to the local player, i.e. the search tree built on
     * the client thread during startup, before a screen exists.
     */
    private static boolean isLocalPlayerVisible() {
        var minecraft = Minecraft.getInstance();
        return minecraft.player != null && minecraft.screen != null;
    }

    private void handleRecipeSync(GuideMEClient client, SyncRecipesPayload payload) {
        if (payload.clear()) {
            pendingRecipes.clear();
        }
        pendingRecipes.addAll(payload.recipes());

        if (payload.availableRecipeTypes().isPresent()) {
            Set<RecipeType<?>> recipeTypes = new HashSet<>();
            for (var typeId : payload.availableRecipeTypes().get()) {
                var recipeType = BuiltInRegistries.RECIPE_TYPE.getValue(typeId);
                if (recipeType != null) {
                    recipeTypes.add(recipeType);
                }
            }
            client.onRecipesReceived(RecipeMap.create(List.copyOf(pendingRecipes)), recipeTypes);
            pendingRecipes.clear();
        }
    }
}
