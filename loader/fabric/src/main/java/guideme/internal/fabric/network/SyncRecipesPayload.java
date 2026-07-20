package guideme.internal.fabric.network;

import guideme.internal.GuideME;
import io.netty.handler.codec.DecoderException;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * Fabric replacement for NeoForge's built-in recipe synchronization ({@code OnDatapackSyncEvent#sendRecipes} /
 * {@code RecipesReceivedEvent}). The server sends the recipes of {@link GuideME#SYNCED_RECIPE_TYPES} in chunks to stay
 * below the payload size limit: first a {@code clear} payload, then recipe chunks, and finally a payload carrying the
 * synced recipe type ids, which signals the client to build its recipe map.
 */
public record SyncRecipesPayload(boolean clear,
        List<RecipeHolder<?>> recipes,
        Optional<List<Identifier>> availableRecipeTypes) implements CustomPacketPayload {

    public static final Type<SyncRecipesPayload> TYPE = new Type<>(GuideME.makeId("sync_recipes"));

    /**
     * ⚠ Recipes are serialized via the DFU {@link Recipe#CODEC} (string ids), NOT {@link RecipeHolder#STREAM_CODEC}.
     * The vanilla stream codec dispatches the recipe serializer by RAW registry id, but since 1.21.2 vanilla no longer
     * sends recipe serializers over the network, so Fabric does not raw-id-sync {@code minecraft:recipe_serializer}
     * (or {@code data_component_type}) between client and server. With client-only mods installed the raw ids diverge
     * and the client dispatches to the WRONG serializer, desyncing the whole connection. The DFU codec is
     * identifier-based end to end (serializer, items, components) and immune to raw-id drift.
     */
    private static final StreamCodec<RegistryFriendlyByteBuf, Recipe<?>> RECIPE_BY_NAME_CODEC = ByteBufCodecs
            .fromCodecWithRegistries(Recipe.CODEC);

    private static final StreamCodec<io.netty.buffer.ByteBuf, ResourceKey<Recipe<?>>> RECIPE_KEY_CODEC = ResourceKey
            .streamCodec(Registries.RECIPE);

    private static final StreamCodec<RegistryFriendlyByteBuf, List<RecipeHolder<?>>> RECIPE_LIST_CODEC = StreamCodec
            .of((buf, recipes) -> {
                buf.writeVarInt(recipes.size());
                for (var holder : recipes) {
                    RECIPE_KEY_CODEC.encode(buf, holder.id());
                    RECIPE_BY_NAME_CODEC.encode(buf, holder.value());
                }
            }, buf -> {
                int count = buf.readVarInt();
                var recipes = new java.util.ArrayList<RecipeHolder<?>>(Math.min(count, 4096));
                for (int i = 0; i < count; i++) {
                    ResourceKey<Recipe<?>> key = null;
                    try {
                        key = RECIPE_KEY_CODEC.decode(buf);
                        recipes.add(new RecipeHolder<>(key, RECIPE_BY_NAME_CODEC.decode(buf)));
                    } catch (Exception e) {
                        // Name the recipe: an opaque mid-stream DecoderException three packets later is undebuggable.
                        throw new DecoderException("GuideME recipe sync: failed to decode recipe #" + i + " of " + count
                                + " id=" + (key != null ? key.identifier() : "<key unreadable>"), e);
                    }
                }
                return recipes;
            });

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncRecipesPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, SyncRecipesPayload::clear,
            RECIPE_LIST_CODEC, SyncRecipesPayload::recipes,
            ByteBufCodecs.optional(Identifier.STREAM_CODEC.apply(ByteBufCodecs.list())),
            SyncRecipesPayload::availableRecipeTypes,
            SyncRecipesPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
