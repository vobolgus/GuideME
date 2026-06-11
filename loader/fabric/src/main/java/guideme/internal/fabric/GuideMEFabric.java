package guideme.internal.fabric;

import guideme.internal.GuideME;
import guideme.internal.command.GuideCommand;
import guideme.internal.command.GuideIdArgument;
import guideme.internal.command.PageAnchorArgument;
import guideme.internal.fabric.network.SyncRecipesPayload;
import guideme.internal.item.GuideItem;
import guideme.internal.network.OpenGuideRequest;
import guideme.internal.platform.GuideMEPlatform;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.ArgumentTypeRegistry;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

/**
 * Fabric entrypoint for the common (client+server) part of GuideME. Registers the content defined in the shared
 * {@link GuideME} holder.
 */
public class GuideMEFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        GuideMEPlatform.init(new FabricPlatform());

        var itemKey = ResourceKey.create(Registries.ITEM, GuideME.makeId("guide"));
        var guideItem = new GuideItem(new Item.Properties().setId(itemKey));
        Registry.register(BuiltInRegistries.ITEM, itemKey, guideItem);
        GuideME.setGuideItem(guideItem);

        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, GuideME.makeId("guide_id"),
                GuideME.GUIDE_ID_COMPONENT);

        ArgumentTypeRegistry.registerArgumentType(GuideME.makeId("guide_id"), GuideIdArgument.class,
                SingletonArgumentInfo.contextFree(GuideIdArgument::argument));
        ArgumentTypeRegistry.registerArgumentType(GuideME.makeId("page_anchor"), PageAnchorArgument.class,
                SingletonArgumentInfo.contextFree(PageAnchorArgument::argument));

        PayloadTypeRegistry.clientboundPlay().register(OpenGuideRequest.TYPE, OpenGuideRequest.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SyncRecipesPayload.TYPE, SyncRecipesPayload.STREAM_CODEC);

        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> GuideCommand.register(dispatcher));

        RecipeSync.init();
    }
}
