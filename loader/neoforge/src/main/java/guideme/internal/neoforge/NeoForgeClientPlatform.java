package guideme.internal.neoforge;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.InputConstants;
import guideme.internal.platform.FluidIcon;
import guideme.internal.platform.GuideMEClientPlatform;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.CommonListenerCookie;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.multiplayer.LevelLoadTracker;
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
import net.neoforged.neoforge.client.event.ClientResourceLoadFinishedEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.display.FluidStackContentsFactory;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.jetbrains.annotations.Nullable;

public class NeoForgeClientPlatform implements GuideMEClientPlatform {
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
                false,
                ConnectionType.NEOFORGE);
    }

    @Override
    public FluidIcon getFluidIcon(Fluid fluid) {
        var modelSet = Minecraft.getInstance().getModelManager().getFluidStateModelSet();
        // TODO: stack-aware fluid models, should they be added back
        var model = modelSet.get(fluid.defaultFluidState());
        int tintColor = model.fluidTintSource() != null
                ? model.fluidTintSource().color(fluid.defaultFluidState())
                : -1;
        return new FluidIcon(model.stillMaterial().sprite(), tintColor);
    }

    @Override
    public Component getFluidName(Fluid fluid) {
        return new FluidStack(fluid, 1).getHoverName();
    }

    @Override
    public Stream<Fluid> getFluidsInDisplay(SlotDisplay display, ContextMap context) {
        return display.resolve(context, FluidStackContentsFactory.INSTANCE)
                .map(FluidStack::getFluid);
    }

    @Override
    public InputConstants.Key getBoundKey(KeyMapping keyMapping) {
        return keyMapping.getKey();
    }

    @Override
    public boolean renderCustomFluid(FluidRenderer fluidRenderer, FluidState fluidState, BlockAndTintGetter level,
            BlockPos pos, FluidRenderer.Output output, BlockState blockState) {
        var fluidModelSet = Minecraft.getInstance().getModelManager().getFluidStateModelSet();
        var customRenderer = fluidModelSet.get(fluidState).customRenderer();
        return customRenderer != null
                && customRenderer.renderFluid(fluidRenderer, fluidState, level, pos, output, blockState);
    }

    @Override
    public void runOnNextClientTick(Runnable runnable) {
        Consumer<ClientTickEvent.Post> listener = new Consumer<>() {
            @Override
            public void accept(ClientTickEvent.Post post) {
                NeoForge.EVENT_BUS.unregister(this);
                runnable.run();
            }
        };
        NeoForge.EVENT_BUS.addListener(listener);
    }

    @Override
    public void interceptScreenOpening(UnaryOperator<@Nullable Screen> interceptor) {
        NeoForge.EVENT_BUS.addListener((ScreenEvent.Opening e) -> {
            var replacement = interceptor.apply(e.getNewScreen());
            if (replacement != null) {
                e.setNewScreen(replacement);
            }
        });
    }

    @Override
    public void whenResourcesLoaded(Runnable action) {
        NeoForge.EVENT_BUS.addListener((ClientResourceLoadFinishedEvent e) -> {
            if (!e.isInitial()) {
                return;
            }
            action.run();
        });
    }
}
