package guideme.scene.level;

import java.util.Collection;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.WritableLevelData;

/**
 * Loader-specific intermediate base class for {@link GuidebookLevel}. The NeoForge version implements the NeoForge
 * extensions to {@link Level} (model data, dragon parts with NeoForge's generalized signature); this Fabric twin only
 * implements the vanilla abstract methods.
 */
public abstract class GuidebookLevelPlatform extends Level {
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
    public Collection<EnderDragonPart> dragonParts() {
        return List.of();
    }

    /**
     * Called when an entity was added to the guidebook level; fires the loader-specific hook (none on Fabric).
     */
    protected void onEntityAddedToLevel(Entity entity) {
    }
}
