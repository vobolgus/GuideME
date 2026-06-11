package guideme.internal.neoforge;

import com.mojang.blaze3d.platform.InputConstants;
import guideme.internal.GuideME;
import guideme.internal.GuideMEClient;
import guideme.internal.GuideReloadListener;
import guideme.internal.command.GuideClientCommand;
import guideme.internal.command.StructureCommands;
import guideme.internal.data.GuideMELanguageProvider;
import guideme.internal.data.GuideMEModelProvider;
import guideme.internal.hotkey.OpenGuideHotkey;
import guideme.internal.item.GuideItemDispatchUnbaked;
import guideme.internal.scene.ScenePictureInPictureRenderer;
import guideme.internal.siteexport.TextureDownloader;
import guideme.internal.util.Blitter;
import guideme.render.GuiAssets;
import guideme.scene.annotation.InWorldAnnotationRenderer;
import java.util.Set;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.AtlasIds;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterItemModelsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.TextureAtlasStitchedEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * NeoForge entrypoint for the client-side part of GuideME. Wires the registration surface documented on
 * {@link GuideMEClient} to the NeoForge event buses.
 */
@Mod(value = GuideME.MOD_ID, dist = Dist.CLIENT)
public class GuideMENeoForgeClient {
    private final GuideMEClient client;

    public GuideMENeoForgeClient(ModContainer modContainer, IEventBus modBus) {
        guideme.internal.platform.GuideMEClientPlatform.init(new NeoForgeClientPlatform());

        var configBackend = new NeoForgeConfigBackend(modContainer);
        this.client = new GuideMEClient(configBackend);

        modBus.addListener(RegisterEvent.class, e -> {
            if (e.getRegistryKey() == Registries.SOUND_EVENT) {
                Registry.register(BuiltInRegistries.SOUND_EVENT, GuideMEClient.GUIDE_CLICK_ID,
                        GuideMEClient.GUIDE_CLICK_EVENT);
            }
        });
        modBus.addListener(this::gatherData);
        modBus.addListener(this::registerHotkeys);
        modBus.addListener(this::registerItemModel);
        modBus.addListener(this::registerRenderPipelines);
        modBus.addListener(this::registerPipRenderers);

        NeoForge.EVENT_BUS.addListener(this::registerClientCommands);
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
        modBus.addListener(this::resetSprites);

        OpenGuideHotkey.init(new KeyMapping(
                OpenGuideHotkey.HOTKEY_TRANSLATION_KEY, KeyConflictContext.GUI, InputConstants.Type.KEYSYM,
                OpenGuideHotkey.DEFAULT_KEY, GuideMEClient.KEYBIND_CATEGORY));
        NeoForge.EVENT_BUS.addListener((ItemTooltipEvent evt) -> {
            // Ignore events fired for anything but the current local player,
            // for example while building the search tree for the creative menu
            if (evt.getEntity() != Minecraft.getInstance().player) {
                return;
            }
            OpenGuideHotkey.onItemTooltip(evt.getItemStack(), evt.getFlags(), evt.getToolTip());
        });
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post evt) -> OpenGuideHotkey.onClientTickEnd());

        modBus.addListener((AddClientReloadListenersEvent evt) -> {
            evt.addListener(GuideReloadListener.ID, new GuideReloadListener());
        });
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Pre evt) -> client.clientTickStart());

        modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        NeoForge.EVENT_BUS.addListener(this::onReceiveRecipes);
        NeoForge.EVENT_BUS.addListener(this::onPlayerDisconnect);
    }

    private void onReceiveRecipes(RecipesReceivedEvent event) {
        client.onRecipesReceived(event.getRecipeMap(), Set.copyOf(event.getRecipeTypes()));
    }

    private void onPlayerDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        client.onClientDisconnected();
    }

    private void registerRenderPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(Blitter.GUI_TEXTURED_OPAQUE);
        event.registerPipeline(TextureDownloader.COPY_BLIT);
        event.registerPipeline(InWorldAnnotationRenderer.OCCLUDED_PIPELINE);
    }

    private void registerPipRenderers(RegisterPictureInPictureRenderersEvent event) {
        event.register(ScenePictureInPictureRenderer.State.class, ScenePictureInPictureRenderer::new);
    }

    private void registerItemModel(RegisterItemModelsEvent event) {
        event.register(GuideItemDispatchUnbaked.ID, GuideItemDispatchUnbaked.CODEC);
    }

    private void resetSprites(TextureAtlasStitchedEvent event) {
        if (event.getAtlas().location().equals(AtlasIds.GUI)) {
            GuiAssets.resetSprites();
        }
    }

    private void registerHotkeys(RegisterKeyMappingsEvent e) {
        e.registerCategory(GuideMEClient.KEYBIND_CATEGORY);
        e.register(OpenGuideHotkey.getHotkey());
    }

    private void registerClientCommands(RegisterClientCommandsEvent evt) {
        GuideClientCommand.register(evt.getDispatcher(), new GuideClientCommand.Feedback<CommandSourceStack>() {
            @Override
            public void sendFailure(CommandSourceStack source, Component message) {
                source.sendFailure(message);
            }

            @Override
            public void sendSystemMessage(CommandSourceStack source, Component message) {
                source.sendSystemMessage(message);
            }
        });
    }

    // These are meant for command blocks only usable in single player
    private void registerCommands(RegisterCommandsEvent event) {
        StructureCommands.register(event.getDispatcher());
    }

    private void gatherData(GatherDataEvent.Client event) {
        DataGenerator gen = event.getGenerator();
        PackOutput packOutput = gen.getPackOutput();
        gen.addProvider(true, new GuideMELanguageProvider(packOutput));
        gen.addProvider(true, new GuideMEModelProvider(packOutput));
    }
}
