package guideme.internal;

import guideme.Guides;
import guideme.PageAnchor;
import guideme.internal.platform.GuideMEClientPlatform;
import guideme.internal.platform.GuideMEPlatform;
import guideme.internal.screen.GuideScreen;
import guideme.internal.util.Platform;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.commands.Commands;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.tags.TagLoader;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.level.validation.DirectoryValidator;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for facilitating the use of the Guide without entering the game.
 */
public final class GuideOnStartup {
    private static final Logger LOG = LoggerFactory.getLogger(GuideOnStartup.class);

    private GuideOnStartup() {
    }

    public static void init() {

        var guidesToValidate = getGuideIdsToValidate();
        var showOnStartup = getShowOnStartup();

        if (!guidesToValidate.isEmpty() || showOnStartup != null) {
            var guideOpenedOnce = new MutableBoolean(false);
            GuideMEClientPlatform.get().interceptScreenOpening(newScreen -> {
                if (newScreen instanceof TitleScreen && !guideOpenedOnce.booleanValue()) {
                    guideOpenedOnce.setTrue();
                    GuideOnStartup.runDatapackReload();

                    for (var guideId : guidesToValidate) {
                        var guide = GuideRegistry.getById(guideId);
                        if (guide == null) {
                            LOG.error("Cannot validate guide '{}' since it does not exist.", guideId);
                        } else {
                            guide.validateAll();
                        }
                    }

                    if (showOnStartup != null) {
                        var guide = Guides.getById(showOnStartup.guideId);
                        if (guide == null) {
                            LOG.error("Cannot show guide '{}' since it does not exist.", showOnStartup.guideId);
                        } else {
                            try {
                                var anchor = showOnStartup.anchor;
                                if (anchor == null) {
                                    anchor = PageAnchor.page(guide.getStartPage());
                                }
                                return GuideScreen.openNew(guide, anchor);
                            } catch (Exception ex) {
                                LOG.error("Failed to open {}", showOnStartup, ex);
                                System.exit(1);
                            }
                        }
                    }
                }
                return null;
            });
        }
    }

    private record ShowOnStartup(Identifier guideId, @Nullable PageAnchor anchor) {
    }

    private static ShowOnStartup getShowOnStartup() {
        var showOnStartup = System.getProperty("guideme.showOnStartup");
        if (showOnStartup == null) {
            return null;
        }

        var parts = showOnStartup.split("!", 2);
        var guideId = Identifier.parse(parts[0]);
        PageAnchor page = null;
        if (parts.length > 1) {
            page = PageAnchor.parse(parts[1]);
        }
        return new ShowOnStartup(guideId, page);
    }

    private static Set<Identifier> getGuideIdsToValidate() {
        Set<Identifier> guidesToValidate = new LinkedHashSet<>();
        var validateGuideIds = System.getProperty("guideme.validateAtStartup");
        if (validateGuideIds != null) {
            var guideIds = validateGuideIds.split(",");
            for (String guideId : guideIds) {
                guidesToValidate.add(Identifier.parse(guideId));
            }
        }
        return guidesToValidate;
    }

    // Run a fake datapack reload to properly compile the page (Recipes, Tags, etc.)
    // Only used when we try to compile pages before entering a world (validation, show on startup)
    public static void runDatapackReload() {
        try {
            var layeredAccess = RegistryLayer.createRegistryAccess();

            PackRepository packRepository = new PackRepository(
                    new ServerPacksSource(new DirectoryValidator(path -> false)));
            GuideMEPlatform.get().populateDatapackRepository(packRepository);
            packRepository.reload();
            packRepository.setSelected(packRepository.getAvailableIds());

            var resourceManager = new MultiPackResourceManager(PackType.SERVER_DATA,
                    packRepository.openAllSelected());

            var postponedTags = TagLoader.loadTagsForExistingRegistries(resourceManager,
                    layeredAccess.getLayer(RegistryLayer.STATIC));
            var worldgenLayer = RegistryDataLoader.load(
                    resourceManager,
                    TagLoader.buildUpdatedLookups(layeredAccess.getAccessForLoading(RegistryLayer.WORLDGEN),
                            postponedTags),
                    RegistryDataLoader.WORLDGEN_REGISTRIES,
                    ForkJoinPool.commonPool()).join();
            layeredAccess = layeredAccess.replaceFrom(RegistryLayer.WORLDGEN, worldgenLayer);

            var stuff = ReloadableServerResources.loadResources(
                    resourceManager,
                    layeredAccess,
                    postponedTags,
                    FeatureFlagSet.of(),
                    Commands.CommandSelection.ALL,
                    PermissionSet.ALL_PERMISSIONS,
                    command -> {
                        try {
                            command.run();
                        } catch (Exception e) {
                            e.printStackTrace();
                            throw e;
                        }
                    },
                    command -> {
                        try {
                            command.run();
                        } catch (Exception e) {
                            e.printStackTrace();
                            throw e;
                        }
                    }).get();
            stuff.updateComponentsAndStaticRegistryTags();
            Platform.fallbackClientRecipeMap = RecipeMap.create(stuff.getRecipeManager().getRecipes());
            Platform.fallbackClientRegistryAccess = layeredAccess.compositeAccess();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
