package guideme.internal;

import guideme.internal.item.GuideItem;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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
     * The recipe types for which GuideME itself has default handlers, plus everything add-ons contributed through
     * {@link guideme.GuidesCommon#addSyncedRecipeTypes}. Loaders synchronize the recipes of these types from the server
     * to the client; the client recipe map built from them is what the guide's recipe tags resolve against.
     * <p>
     * ⚠ Add-ons that render their own recipe types in a guide MUST register them here (see
     * {@link guideme.GuidesCommon#addSyncedRecipeTypes}) — on NeoForge every mod's
     * {@code OnDatapackSyncEvent#sendRecipes} call feeds one shared set and one shared {@code RecipesReceivedEvent}, so
     * an add-on syncing its own types incidentally fills GuideME's client map as well. Fabric has no such aggregation:
     * every mod runs its own payload into its own client-side map, so recipe types an add-on syncs on its own channel
     * are invisible to GuideME.
     */
    private static final Set<RecipeType<?>> SYNCED_RECIPE_TYPES = new LinkedHashSet<>(List.of(
            RecipeType.CRAFTING,
            RecipeType.BLASTING,
            RecipeType.SMELTING,
            RecipeType.SMITHING));

    private GuideME() {
    }

    /**
     * Adds recipe types to the set synchronized from server to client. Must be called during mod initialization (before
     * any player can join), on both the client and the server. Duplicates are ignored.
     */
    public static void addSyncedRecipeTypes(RecipeType<?>... recipeTypes) {
        synchronized (SYNCED_RECIPE_TYPES) {
            Collections.addAll(SYNCED_RECIPE_TYPES, recipeTypes);
        }
    }

    /**
     * @return An immutable snapshot of the recipe types to synchronize from server to client, in registration order.
     */
    public static List<RecipeType<?>> getSyncedRecipeTypes() {
        synchronized (SYNCED_RECIPE_TYPES) {
            return List.copyOf(SYNCED_RECIPE_TYPES);
        }
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
