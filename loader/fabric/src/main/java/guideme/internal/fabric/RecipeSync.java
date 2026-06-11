package guideme.internal.fabric;

import guideme.internal.GuideME;
import guideme.internal.fabric.network.SyncRecipesPayload;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * Server-side part of the Fabric recipe synchronization. See {@link SyncRecipesPayload}.
 */
public final class RecipeSync {
    /**
     * Number of recipes per chunk payload, to stay well below the custom payload size limit.
     */
    private static final int CHUNK_SIZE = 250;

    private RecipeSync() {
    }

    public static void init() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sendRecipes(server, handler.player));
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
            if (success) {
                for (var player : server.getPlayerList().getPlayers()) {
                    sendRecipes(server, player);
                }
            }
        });
    }

    private static void sendRecipes(MinecraftServer server, ServerPlayer player) {
        if (!ServerPlayNetworking.canSend(player, SyncRecipesPayload.TYPE)) {
            return; // Client doesn't have GuideME installed
        }

        // Vanilla offers no accessor for the server's RecipeMap; rebuild one from the recipe collection
        var recipeMap = RecipeMap.create(server.getRecipeManager().getRecipes());

        List<RecipeHolder<?>> recipes = new ArrayList<>();
        List<Identifier> typeIds = new ArrayList<>();
        for (var recipeType : GuideME.SYNCED_RECIPE_TYPES) {
            recipes.addAll(recipesOfType(recipeMap, recipeType));
            typeIds.add(BuiltInRegistries.RECIPE_TYPE.getKey(recipeType));
        }

        ServerPlayNetworking.send(player, new SyncRecipesPayload(true, List.of(), Optional.empty()));
        for (int i = 0; i < recipes.size(); i += CHUNK_SIZE) {
            var chunk = recipes.subList(i, Math.min(recipes.size(), i + CHUNK_SIZE));
            ServerPlayNetworking.send(player, new SyncRecipesPayload(false, chunk, Optional.empty()));
        }
        ServerPlayNetworking.send(player, new SyncRecipesPayload(false, List.of(), Optional.of(typeIds)));
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static Collection<RecipeHolder<?>> recipesOfType(RecipeMap recipeMap, RecipeType<?> recipeType) {
        return (Collection) recipeMap.byType((RecipeType) recipeType);
    }
}
