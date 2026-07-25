package guideme;

import guideme.internal.GuideME;
import guideme.internal.GuideMEProxy;
import java.util.Objects;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * Functionality for guides that can be used on both the server and client.
 */
public final class GuidesCommon {
    private GuidesCommon() {
    }

    /**
     * Opens the last opened page (or start page) of the guide for the player.
     */
    public static void openGuide(Player player, Identifier guideId) {
        GuideMEProxy.instance().openGuide(player, guideId, null);
    }

    /**
     * Opens the given guide for the player and navigates to the given page position.
     */
    public static void openGuide(Player player, Identifier guideId, PageAnchor anchor) {
        GuideMEProxy.instance().openGuide(player, guideId, Objects.requireNonNull(anchor, "anchor"));
    }

    /**
     * Registers additional recipe types that the server must synchronize to clients so that guide pages can display
     * them through {@code <Recipe/>}, {@code <RecipeFor/>} and {@code <RecipesFor/>} tags. Pair this with a
     * {@link guideme.compiler.tags.RecipeTypeMappingSupplier} that knows how to lay the recipes out.
     * <p>
     * Call this during mod initialization on <em>both</em> the client and the server (the server decides what to send,
     * the client only builds a map from what it receives). GuideME's own default handlers ({@code minecraft:crafting},
     * {@code smelting}, {@code blasting}, {@code smithing}) are always synchronized; duplicates are ignored.
     * <p>
     * This is required on Fabric even if your mod already synchronizes those recipe types on its own network channel:
     * NeoForge aggregates every mod's {@code OnDatapackSyncEvent#sendRecipes} request into a single packet and a single
     * {@code RecipesReceivedEvent}, so GuideME's client recipe map incidentally sees everything; on Fabric each mod
     * owns a separate payload and a separate client-side map, so nothing is shared.
     */
    public static void addSyncedRecipeTypes(RecipeType<?>... recipeTypes) {
        GuideME.addSyncedRecipeTypes(recipeTypes);
    }
}
