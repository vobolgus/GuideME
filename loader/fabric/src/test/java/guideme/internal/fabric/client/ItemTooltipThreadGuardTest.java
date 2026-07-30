package guideme.internal.fabric.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.util.Util;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Regression test for the "RenderSystem called from wrong thread" crash reported 2026-07-30 (typing in the creative
 * search box).
 * <p>
 * On 26.1 {@code net.minecraft.client.multiplayer.SessionSearchTrees} builds the creative-menu and recipe-book search
 * trees on {@link Util#backgroundExecutor()} and calls {@code ItemStack.getTooltipLines(ctx, null, flag)} from there.
 * On Fabric that fires {@code ItemTooltipCallback}, which used to forward straight into
 * {@code OpenGuideHotkey.onItemTooltip} -> {@code Font.width} -> lazy glyph bake -> GL call -> throw. The exception
 * then surfaces on the render thread when the screen joins the search-tree future.
 * <p>
 * NeoForge is unaffected: its {@code ItemTooltipEvent} carries the player the tooltip is built for (null here), and
 * GuideME filters on it. This test therefore lives in the Fabric overlay only.
 * <p>
 * The environment is headless, so {@code Minecraft.getInstance()} is null. That is what makes the assertions
 * discriminating: reaching the client-state half of the gate throws, so "returns false without throwing" proves the
 * thread check ran and short-circuited first.
 */
class ItemTooltipThreadGuardTest {
    @BeforeAll
    static void claimRenderThread() {
        // The JUnit test thread becomes THE render thread for the rest of the JVM's life.
        // Nothing else in the suite initializes it; guard anyway so a re-run inside one JVM is harmless.
        if (!RenderSystem.isOnRenderThread()) {
            RenderSystem.initRenderThread();
        }
        assertThat(RenderSystem.isOnRenderThread())
                .as("test thread must own the render thread for this test to mean anything")
                .isTrue();
    }

    @Test
    void offRenderThreadTooltipsAreSkippedInsteadOfCrashing() throws Exception {
        var offThread = CompletableFuture.supplyAsync(() -> {
            assertThat(RenderSystem.isOnRenderThread())
                    .as("vanilla builds the search tree on this executor, off the render thread")
                    .isFalse();
            // Must not throw: if the thread check is removed or reordered behind the client-state
            // check, this dereferences the (headless: null) Minecraft instance and fails.
            return GuideMEFabricClient.isLocalPlayerTooltip();
        }, Util.backgroundExecutor());

        assertThat(offThread.get(30, TimeUnit.SECONDS))
                .as("off-thread tooltip requests must not be forwarded to the hotkey handler")
                .isFalse();
    }

    @Test
    void onRenderThreadTheGateFallsThroughToClientState() {
        // Structural probe pinning the gate's ordering: on the render thread the check does NOT
        // short-circuit, it goes on to inspect Minecraft.getInstance() -- which is absent headless.
        // (If client-state null-safety is ever added, this assertion becomes `isFalse()`.)
        assertThatThrownBy(GuideMEFabricClient::isLocalPlayerTooltip)
                .isInstanceOf(NullPointerException.class);
    }
}
