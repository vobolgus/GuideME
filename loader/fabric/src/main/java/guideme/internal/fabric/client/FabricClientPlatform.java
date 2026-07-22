package guideme.internal.fabric.client;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.QuadInstance;
import guideme.internal.platform.FluidIcon;
import guideme.internal.platform.GuideMEClientPlatform;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.fabricmc.fabric.api.client.renderer.v1.sprite.FabricTextureAtlas;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.CommonListenerCookie;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.telemetry.WorldSessionTelemetryManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.server.ServerLinks;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

public class FabricClientPlatform implements GuideMEClientPlatform {
    @Override
    public CommonListenerCookie createListenerCookie(LevelLoadTracker loadTracker,
            GameProfile profile,
            WorldSessionTelemetryManager telemetryManager,
            RegistryAccess.Frozen registryAccess,
            FeatureFlagSet enabledFeatures) {
        return new CommonListenerCookie(
                loadTracker,
                profile,
                telemetryManager,
                registryAccess,
                enabledFeatures,
                null,
                null,
                null,
                Map.of(),
                null,
                Map.of(),
                new ServerLinks(List.of()),
                Map.of(),
                false);
    }

    @Override
    public FluidIcon getFluidIcon(Fluid fluid) {
        var modelSet = Minecraft.getInstance().getModelManager().getFluidStateModelSet();
        var fluidState = fluid.defaultFluidState();
        var model = modelSet.get(fluidState);
        int tintColor = model.tintSource() != null
                ? model.tintSource().color(fluidState.createLegacyBlock())
                : -1;
        return new FluidIcon(model.stillMaterial().sprite(), tintColor);
    }

    @Override
    public Component getFluidName(Fluid fluid) {
        return FluidVariantAttributes.getName(FluidVariant.of(fluid));
    }

    @Override
    public Stream<Fluid> getFluidsInDisplay(SlotDisplay display, ContextMap context) {
        // Fluid slot displays are a NeoForge-specific extension and do not exist on Fabric
        return Stream.empty();
    }

    @Override
    public InputConstants.Key getBoundKey(KeyMapping keyMapping) {
        return KeyMappingHelper.getBoundKeyOf(keyMapping);
    }

    @Override
    public boolean renderCustomFluid(FluidRenderer fluidRenderer, FluidState fluidState, BlockAndTintGetter level,
            BlockPos pos, FluidRenderer.Output output, BlockState blockState) {
        // Custom fluid renderers are a NeoForge extension; always use the vanilla renderer.
        return false;
    }

    @Override
    public boolean renderCustomBlock(BlockQuadOutput output, boolean ambientOcclusion, BlockAndTintGetter level,
            BlockPos pos, BlockState blockState, BlockStateModel model, long seed) {
        var renderer = Renderer.get();
        var emitter = renderer.quadEmitter(quad -> {
            var atlas = (TextureAtlas) Minecraft.getInstance().getTextureManager().getTexture(
                    quad.atlas().getTextureLocation());
            var sprite = ((FabricTextureAtlas) atlas).spriteFinder().find(quad);
            var instance = new QuadInstance();
            for (int vertex = 0; vertex < 4; vertex++) {
                instance.setColor(vertex, quad.color(vertex));
                instance.setLightCoords(vertex, quad.lightmap(vertex));
            }
            output.put(0, 0, 0, quad.toBakedQuad(sprite), instance);
        });
        renderer.altModelBlockRenderer(ambientOcclusion, false, Minecraft.getInstance().getBlockColors())
                .tesselateBlock(emitter, 0, 0, 0, level, pos, blockState, model, seed);
        return true;
    }

    @Override
    public void runOnNextClientTick(Runnable runnable) {
        // Fabric events cannot be unregistered; the listener becomes a no-op after it ran once.
        var done = new AtomicBoolean(false);
        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
            if (done.compareAndSet(false, true)) {
                runnable.run();
            }
        });
    }

    @Override
    public void interceptScreenOpening(UnaryOperator<@Nullable Screen> interceptor) {
        ScreenEvents.AFTER_INIT.register((minecraft, screen, scaledWidth, scaledHeight) -> {
            var replacement = interceptor.apply(screen);
            if (replacement != null) {
                minecraft.setScreen(replacement);
            }
        });
    }

    @Override
    public void whenResourcesLoaded(Runnable action) {
        ClientLifecycleEvents.CLIENT_STARTED.register(minecraft -> {
            if (minecraft.getOverlay() instanceof LoadingOverlay loadingOverlay) {
                loadingOverlay.reload.done().whenCompleteAsync((result, error) -> {
                    if (error == null) {
                        action.run();
                    }
                }, minecraft);
            } else {
                action.run();
            }
        });
    }
}
