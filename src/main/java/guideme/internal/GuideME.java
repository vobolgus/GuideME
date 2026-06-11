package guideme.internal;

import guideme.internal.item.GuideItem;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * Loader-neutral holder for the common (client+server) parts of GuideME. The actual mod entrypoints live in the
 * per-loader projects and are responsible for registering the content defined here.
 */
public final class GuideME {

    static GuideMEProxy PROXY = new GuideMEServerProxy();

    public static final String MOD_ID = "guideme";

    private static GuideItem guideItem;

    public static final Supplier<GuideItem> GUIDE_ITEM = () -> Objects.requireNonNull(GuideME.guideItem,
            "GuideME guide item is not registered yet");

    /**
     * Attaches the guide ID to a generic guide item.
     */
    public static final DataComponentType<Identifier> GUIDE_ID_COMPONENT = DataComponentType
            .<Identifier>builder()
            .networkSynchronized(Identifier.STREAM_CODEC)
            .persistent(Identifier.CODEC)
            .build();

    /**
     * The recipe types for which GuideME has default handlers. Loaders synchronize the recipes of these types from the
     * server to the client.
     */
    public static final List<RecipeType<?>> SYNCED_RECIPE_TYPES = List.of(
            RecipeType.CRAFTING,
            RecipeType.BLASTING,
            RecipeType.SMELTING,
            RecipeType.SMITHING);

    private GuideME() {
    }

    /**
     * Called by the loader entrypoint after the guide item has been created and registered.
     */
    public static void setGuideItem(GuideItem item) {
        GuideME.guideItem = item;
    }

    public static Identifier makeId(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
