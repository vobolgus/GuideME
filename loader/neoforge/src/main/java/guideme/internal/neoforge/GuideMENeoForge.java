package guideme.internal.neoforge;

import guideme.internal.GuideME;
import guideme.internal.command.GuideCommand;
import guideme.internal.command.GuideIdArgument;
import guideme.internal.command.PageAnchorArgument;
import guideme.internal.item.GuideItem;
import guideme.internal.network.OpenGuideRequest;
import guideme.internal.platform.GuideMEPlatform;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * NeoForge entrypoint for the common (client+server) part of GuideME. Registers the content defined in the shared
 * {@link GuideME} holder.
 */
@Mod(value = GuideME.MOD_ID)
public class GuideMENeoForge {

    private static final DeferredRegister.Items DR_ITEMS = DeferredRegister.createItems(GuideME.MOD_ID);
    private static final DeferredRegister<ArgumentTypeInfo<?, ?>> DR_ARGUMENT_TYPE_INFOS = DeferredRegister
            .create(Registries.COMMAND_ARGUMENT_TYPE, GuideME.MOD_ID);

    public GuideMENeoForge(IEventBus modBus) {
        GuideMEPlatform.init(new NeoForgePlatform());

        DR_ITEMS.registerItem("guide", properties -> {
            var item = new GuideItem(properties);
            GuideME.setGuideItem(item);
            return item;
        });

        DR_ARGUMENT_TYPE_INFOS.register("guide_id", () -> ArgumentTypeInfos.registerByClass(GuideIdArgument.class,
                SingletonArgumentInfo.contextFree(GuideIdArgument::argument)));
        DR_ARGUMENT_TYPE_INFOS.register("page_anchor", () -> ArgumentTypeInfos.registerByClass(PageAnchorArgument.class,
                SingletonArgumentInfo.contextFree(PageAnchorArgument::argument)));

        var drDataComponents = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, GuideME.MOD_ID);
        drDataComponents.register("guide_id", () -> GuideME.GUIDE_ID_COMPONENT);

        DR_ARGUMENT_TYPE_INFOS.register(modBus);
        DR_ITEMS.register(modBus);
        drDataComponents.register(modBus);

        modBus.addListener(this::registerNetworking);

        NeoForge.EVENT_BUS.addListener(this::registerCommands);

        NeoForge.EVENT_BUS.addListener(this::registerRecipeSync);
    }

    private void registerCommands(RegisterCommandsEvent event) {
        GuideCommand.register(event.getDispatcher());
    }

    private void registerNetworking(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1.0");
        registrar.playToClient(OpenGuideRequest.TYPE, OpenGuideRequest.STREAM_CODEC,
                (payload, context) -> payload.handle(context.player()));
    }

    // We send the recipe types for which we have default handlers, plus whatever add-ons contributed through
    // GuidesCommon#addSyncedRecipeTypes. NeoForge aggregates every mod's request into one set, so asking for a
    // type another mod already asked for costs nothing.
    private void registerRecipeSync(OnDatapackSyncEvent event) {
        event.sendRecipes(GuideME.getSyncedRecipeTypes());
    }
}
