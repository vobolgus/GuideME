package guideme.internal.platform;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.Objects;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.CommonListenerCookie;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.telemetry.WorldSessionTelemetryManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

/**
 * Loader-specific functionality that is only needed on the client. Installed via {@link #init} from the loader client
 * entrypoint.
 */
public interface GuideMEClientPlatform {
    /**
     * Creates the listener cookie used to construct a fake client packet listener for rendering outside of a world.
     * Loaders may need to pass additional loader-specific parameters (i.e. the NeoForge connection type).
     */
    CommonListenerCookie createListenerCookie(LevelLoadTracker loadTracker,
            GameProfile profile,
            WorldSessionTelemetryManager telemetryManager,
            RegistryAccess.Frozen registryAccess,
            FeatureFlagSet enabledFeatures);

    /**
     * @return Sprite and tint color used to display the given fluid in the UI.
     */
    FluidIcon getFluidIcon(Fluid fluid);

    /**
     * @return The display name of the given fluid.
     */
    Component getFluidName(Fluid fluid);

    /**
     * Resolves fluids shown by the given slot display. Fluid slot displays are a loader-specific extension (they exist
     * on NeoForge); loaders without that concept return an empty stream.
     */
    Stream<Fluid> getFluidsInDisplay(SlotDisplay display, ContextMap context);

    /**
     * @return The key currently bound to the given key mapping. (Vanilla offers no public accessor.)
     */
    InputConstants.Key getBoundKey(KeyMapping keyMapping);

    /**
     * Gives loader-specific custom fluid renderers (a NeoForge extension) a chance to render the given fluid in a
     * guidebook scene.
     *
     * @return true if the fluid was rendered by a custom renderer, false to run the vanilla fluid renderer.
     */
    boolean renderCustomFluid(FluidRenderer fluidRenderer, FluidState fluidState, BlockAndTintGetter level,
            BlockPos pos, FluidRenderer.Output output, BlockState blockState);

    /**
     * Gives loaders a chance to tessellate a block model that requires loader-specific level context.
     *
     * @return true if the model was rendered, false to use the vanilla model renderer.
     */
    default boolean renderCustomBlock(BlockQuadOutput output, boolean ambientOcclusion, BlockAndTintGetter level,
            BlockPos pos, BlockState blockState, BlockStateModel model, long seed) {
        return false;
    }

    /**
     * Runs the given runnable once at the end of the next client tick.
     */
    void runOnNextClientTick(Runnable runnable);

    /**
     * Calls the interceptor whenever a new screen is about to be shown. If the interceptor returns a non-null screen,
     * it replaces the screen being opened.
     */
    void interceptScreenOpening(UnaryOperator<@Nullable Screen> interceptor);

    /**
     * Runs the given action once the initial client resource reload has finished.
     */
    void whenResourcesLoaded(Runnable action);

    static GuideMEClientPlatform get() {
        return Objects.requireNonNull(Holder.instance, "GuideME client platform is not initialized");
    }

    static void init(GuideMEClientPlatform platform) {
        Holder.instance = platform;
    }

    final class Holder {
        private static GuideMEClientPlatform instance;

        private Holder() {
        }
    }
}
