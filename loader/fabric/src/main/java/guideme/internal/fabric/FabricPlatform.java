package guideme.internal.fabric;

import guideme.internal.network.OpenGuideRequest;
import guideme.internal.platform.GuideMEPlatform;
import java.util.Optional;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.repository.PackRepository;
import org.jetbrains.annotations.Nullable;

public class FabricPlatform implements GuideMEPlatform {
    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public @Nullable String getModDisplayName(String modId) {
        return FabricLoader.getInstance().getModContainer(modId)
                .map(container -> container.getMetadata().getName())
                .orElse(null);
    }

    @Override
    public Optional<String> getModVersion(String modId) {
        return FabricLoader.getInstance().getModContainer(modId)
                .map(ModContainer::getMetadata)
                .map(metadata -> metadata.getVersion().getFriendlyString());
    }

    @Override
    public void populateDatapackRepository(PackRepository packRepository) {
        // Nothing to do: fabric-resource-loader injects mod data packs into every PackRepository
        // built from the vanilla server pack sources via its PackRepository mixin.
    }

    @Override
    public void sendOpenGuideRequest(ServerPlayer player, OpenGuideRequest request) {
        ServerPlayNetworking.send(player, request);
    }
}
