package guideme.scene.element;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.neoforged.neoforge.common.world.AuxiliaryLightManager;
import net.neoforged.neoforge.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

/**
 * Loader-specific subclass of {@link FakeForwardingServerLevel}. The NeoForge version forwards the NeoForge extension
 * methods to the delegate; the Fabric twin adds nothing.
 */
public class PlatformFakeServerLevel extends FakeForwardingServerLevel {
    public PlatformFakeServerLevel(LevelAccessor delegate) {
        super(delegate);
    }

    @Override
    public boolean isAreaLoaded(BlockPos center, int range) {
        return delegate.isAreaLoaded(center, range);
    }

    @Override
    public @Nullable AuxiliaryLightManager getAuxLightManager(BlockPos pos) {
        return delegate.getAuxLightManager(pos);
    }

    @Override
    public @Nullable AuxiliaryLightManager getAuxLightManager(ChunkPos pos) {
        return delegate.getAuxLightManager(pos);
    }

    @Override
    public ModelData getModelData(BlockPos pos) {
        return delegate.getModelData(pos);
    }
}
