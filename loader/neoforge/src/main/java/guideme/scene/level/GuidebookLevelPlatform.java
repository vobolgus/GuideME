package guideme.scene.level;

import java.util.Collection;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.WritableLevelData;
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.neoforge.model.data.ModelData;
import net.neoforged.neoforge.model.data.ModelDataManager;
import org.jetbrains.annotations.Nullable;

/**
 * Loader-specific intermediate base class for {@link GuidebookLevel}. The NeoForge version implements the NeoForge
 * extensions to {@link Level} (model data, dragon parts); the Fabric twin is empty.
 */
public abstract class GuidebookLevelPlatform extends Level {
    private final ModelDataManager modelDataManager = new ModelDataManager(this);

    protected GuidebookLevelPlatform(WritableLevelData levelData,
            ResourceKey<Level> dimension,
            RegistryAccess registryAccess,
            Holder<DimensionType> dimensionType,
            boolean isClientSide,
            boolean isDebug,
            long biomeZoomSeed,
            int maxChainedNeighborUpdates) {
        super(levelData, dimension, registryAccess, dimensionType, isClientSide, isDebug, biomeZoomSeed,
                maxChainedNeighborUpdates);
    }

    @Override
    public ModelData getModelData(BlockPos pos) {
        return modelDataManager.getAt(pos);
    }

    @Override
    public @Nullable ModelDataManager getModelDataManager() {
        return modelDataManager;
    }

    @Override
    public Collection<PartEntity<?>> dragonParts() {
        return List.of();
    }

    /**
     * Called when an entity was added to the guidebook level; fires the loader-specific hook.
     */
    protected void onEntityAddedToLevel(Entity entity) {
        entity.onAddedToLevel();
    }
}
