package guideme.internal.fabric.client;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;

/**
 * Adapts a vanilla {@link PreparableReloadListener} to Fabric's identified reload listener interface.
 */
class FabricReloadListenerWrapper implements IdentifiableResourceReloadListener {
    private final Identifier id;
    private final PreparableReloadListener listener;

    FabricReloadListenerWrapper(Identifier id, PreparableReloadListener listener) {
        this.id = id;
        this.listener = listener;
    }

    @Override
    public Identifier getFabricId() {
        return id;
    }

    @Override
    public CompletableFuture<Void> reload(SharedState sharedState, Executor backgroundExecutor,
            PreparationBarrier barrier, Executor gameExecutor) {
        return listener.reload(sharedState, backgroundExecutor, barrier, gameExecutor);
    }

    @Override
    public void prepareSharedState(SharedState sharedState) {
        listener.prepareSharedState(sharedState);
    }

    @Override
    public String getName() {
        return listener.getName();
    }
}
