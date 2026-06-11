package guideme.internal.neoforge;

import guideme.internal.network.OpenGuideRequest;
import guideme.internal.platform.GuideMEPlatform;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackRepository;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.resource.ResourcePackLoader;
import net.neoforged.neoforgespi.language.IModInfo;
import org.jetbrains.annotations.Nullable;

public class NeoForgePlatform implements GuideMEPlatform {
    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public @Nullable String getModDisplayName(String modId) {
        return ModList.get().getModContainerById(modId)
                .map(ModContainer::getModInfo)
                .map(IModInfo::getDisplayName)
                .orElse(null);
    }

    @Override
    public Optional<String> getModVersion(String modId) {
        return ModList.get().getModContainerById(modId)
                .map(mc -> mc.getModInfo().getVersion().toString());
    }

    @Override
    public void populateDatapackRepository(PackRepository packRepository) {
        // This fires AddPackFindersEvent but it's probably ok.
        ResourcePackLoader.populatePackRepository(packRepository, PackType.SERVER_DATA, true);
    }

    @Override
    public void sendOpenGuideRequest(ServerPlayer player, OpenGuideRequest request) {
        PacketDistributor.sendToPlayer(player, request);
    }
}
