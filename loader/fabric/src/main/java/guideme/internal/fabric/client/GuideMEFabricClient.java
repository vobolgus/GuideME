package guideme.internal.fabric.client;

import com.mojang.blaze3d.platform.InputConstants;
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
            // The callback has no player context; skip cases that clearly aren't a tooltip shown
            // to the local player (i.e. building the creative menu search tree during startup).
            var minecraft = Minecraft.getInstance();
            if (minecraft.player == null || minecraft.screen == null) {
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
