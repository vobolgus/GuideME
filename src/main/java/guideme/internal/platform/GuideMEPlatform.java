package guideme.internal.platform;

import guideme.internal.network.OpenGuideRequest;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.repository.PackRepository;
import org.jetbrains.annotations.Nullable;

/**
 * Loader-specific functionality needed on both the logical client and server. Must not reference client-only classes.
 * <p>
 * Implemented once per loader and installed via {@link #init} from the loader entrypoint before any other GuideME code
 * runs.
 */
public interface GuideMEPlatform {
    /**
     * @return true if a mod with the given id is loaded.
     */
    boolean isModLoaded(String modId);

    /**
     * @return The display name of the given mod, if it is loaded.
     */
    @Nullable
    String getModDisplayName(String modId);

    /**
     * @return The version of the given mod, if it is loaded.
     */
    Optional<String> getModVersion(String modId);

    /**
     * Adds loader-provided data packs (i.e. packs bundled with mods) to the given pack repository, which was created
     * from only the vanilla server pack sources. Used for the fake data pack reload when guides are used outside of a
     * world.
     */
    void populateDatapackRepository(PackRepository packRepository);

    /**
     * Sends a request to open a guide to the given player.
     */
    void sendOpenGuideRequest(ServerPlayer player, OpenGuideRequest request);

    static GuideMEPlatform get() {
        return Objects.requireNonNull(Holder.instance, "GuideME platform is not initialized");
    }

    static void init(GuideMEPlatform platform) {
        Holder.instance = platform;
    }

    final class Holder {
        private static GuideMEPlatform instance;

        private Holder() {
        }
    }
}
