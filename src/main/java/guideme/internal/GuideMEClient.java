package guideme.internal;

import guideme.Guide;
import guideme.PageAnchor;
import guideme.color.LightDarkMode;
import guideme.internal.platform.ConfigBackend;
import guideme.internal.screen.GlobalInMemoryHistory;
import guideme.internal.screen.GuideNavigation;
import guideme.internal.search.GuideSearch;
import guideme.internal.siteexport.SiteExportOnStartup;
import java.util.Objects;
import java.util.Set;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loader-neutral client-side singleton. Constructed by the loader-specific client entrypoint, which is also responsible
 * for wiring the following registrations and events to the loader (see the per-loader projects):
 * <ul>
 * <li>Registering {@link #GUIDE_CLICK_EVENT} as a sound event under {@link #GUIDE_CLICK_ID}.</li>
 * <li>Registering the {@link #KEYBIND_CATEGORY} keybind category and the open-guide hotkey (see
 * {@link guideme.internal.hotkey.OpenGuideHotkey#init}).</li>
 * <li>Registering the item model dispatch codec {@link guideme.internal.item.GuideItemDispatchUnbaked}.</li>
 * <li>Registering the custom render pipelines ({@code Blitter.GUI_TEXTURED_OPAQUE},
 * {@code TextureDownloader.COPY_BLIT}, {@code InWorldAnnotationRenderer.OCCLUDED_PIPELINE}) where the loader requires
 * pre-registration.</li>
 * <li>Registering the picture-in-picture renderer {@code ScenePictureInPictureRenderer}.</li>
 * <li>Registering {@link GuideReloadListener} as a client resource reload listener, and calling
 * {@code GuiAssets.resetSprites()} when the GUI sprite atlas is rebuilt.</li>
 * <li>Calling {@link #clientTickStart()} at the start and
 * {@link guideme.internal.hotkey.OpenGuideHotkey#onClientTickEnd()} at the end of each client tick.</li>
 * <li>Forwarding item tooltips to {@link guideme.internal.hotkey.OpenGuideHotkey#onItemTooltip}.</li>
 * <li>Registering client commands ({@code GuideClientCommand}) and the single-player structure commands
 * ({@code StructureCommands}).</li>
 * <li>Receiving the open-guide packet and recipe synchronization, then calling {@link #onRecipesReceived} and
 * {@link #onClientDisconnected}.</li>
 * </ul>
 */
public class GuideMEClient {
    private static final Logger LOG = LoggerFactory.getLogger(GuideMEClient.class);

    public static final KeyMapping.Category KEYBIND_CATEGORY = new KeyMapping.Category(GuideME.makeId("category"));

    private static GuideMEClient INSTANCE;

    public static final Identifier GUIDE_CLICK_ID = GuideME.makeId("guide.click");
    public static SoundEvent GUIDE_CLICK_EVENT = SoundEvent.createVariableRangeEvent(GUIDE_CLICK_ID);

    private final GuideSearch search = new GuideSearch();

    private RecipeMap recipeMap = RecipeMap.EMPTY;
    private Set<RecipeType<?>> availableRecipeTypes = Set.of();

    private final ClientConfig clientConfig;

    public GuideMEClient(ConfigBackend configBackend) {
        INSTANCE = this;
        GuideME.PROXY = new GuideMEClientProxy();

        this.clientConfig = new ClientConfig(configBackend);

        GuideOnStartup.init();
        SiteExportOnStartup.init();
    }

    /**
     * Called by the loader at the start of every client tick.
     */
    public void clientTickStart() {
        search.processWork();
        processDevWatchers();
    }

    /**
     * Called by the loader when the server has synchronized recipes to this client.
     */
    public void onRecipesReceived(RecipeMap recipeMap, Set<RecipeType<?>> availableRecipeTypes) {
        this.recipeMap = recipeMap;
        this.availableRecipeTypes = Set.copyOf(availableRecipeTypes);
    }

    /**
     * Called by the loader when the player disconnects from a server.
     */
    public void onClientDisconnected() {
        recipeMap = RecipeMap.EMPTY;
        availableRecipeTypes = Set.of();
    }

    private void processDevWatchers() {
        for (var guide : GuideRegistry.getAll()) {
            guide.tick();
        }
    }

    public static LightDarkMode currentLightDarkMode() {
        return LightDarkMode.LIGHT_MODE;
    }

    public static GuideMEClient instance() {
        return Objects.requireNonNull(INSTANCE, "Mod is not initialized");
    }

    public boolean isShowDebugGuiOverlays() {
        return clientConfig.showDebugGuiOverlays.get();
    }

    public boolean isAdaptiveScalingEnabled() {
        return clientConfig.adaptiveScaling.get();
    }

    public boolean isIgnoreTranslatedGuides() {
        return clientConfig.ignoreTranslatedGuides.get();
    }

    public boolean isHideMissingRecipeErrors() {
        return clientConfig.hideMissingRecipeErrors.get();
    }

    public boolean isFullWidthLayout() {
        return clientConfig.fullWidthLayout.get();
    }

    public void setFullWidthLayout(boolean fullWidth) {
        if (fullWidth != isFullWidthLayout()) {
            clientConfig.fullWidthLayout.set(fullWidth);
            clientConfig.backend.save();
            var minecraft = Minecraft.getInstance();
            var screen = minecraft.screen;
            if (screen != null) {
                var window = minecraft.getWindow();
                screen.resize(window.getGuiScaledWidth(), window.getGuiScaledHeight());
            }
        }
    }

    public static boolean openGuideAtPreviousPage(Guide guide, Identifier initialPage) {
        try {
            var history = GlobalInMemoryHistory.get(guide);
            var historyPage = history.current();
            if (historyPage.isPresent()) {
                GuideNavigation.navigateTo(guide, historyPage.get());
            } else {
                GuideNavigation.navigateTo(guide, PageAnchor.page(initialPage));
            }
            return true;
        } catch (Exception e) {
            LOG.error("Failed to open guide.", e);
            return false;
        }
    }

    public static boolean openGuideAtAnchor(Guide guide, PageAnchor anchor) {
        try {
            GuideNavigation.navigateTo(guide, anchor);
            return true;
        } catch (Exception e) {
            LOG.error("Failed to open guide at {}.", anchor, e);
            return false;
        }
    }

    public GuideSearch getSearch() {
        return search;
    }

    public RecipeMap getRecipeMap() {
        return recipeMap;
    }

    public boolean isRecipeTypeAvailable(RecipeType<?> recipeType) {
        return availableRecipeTypes.contains(recipeType);
    }

    private static class ClientConfig {
        final ConfigBackend backend;
        final ConfigBackend.BooleanOption adaptiveScaling;
        final ConfigBackend.BooleanOption showDebugGuiOverlays;
        final ConfigBackend.BooleanOption fullWidthLayout;
        final ConfigBackend.BooleanOption ignoreTranslatedGuides;
        final ConfigBackend.BooleanOption hideMissingRecipeErrors;

        public ClientConfig(ConfigBackend backend) {
            this.backend = backend;

            ignoreTranslatedGuides = backend.defineBoolean("guides", "ignoreTranslatedGuides", false,
                    "Never load translated guide pages for your current language.");
            hideMissingRecipeErrors = backend.defineBoolean("guides", "hideMissingRecipeErrors", false,
                    "Never show errors in guides when recipes can't be found (i.e. because they were hidden by a datapack).");

            adaptiveScaling = backend.defineBoolean("gui", "adaptiveScaling", true,
                    "Adapt GUI scaling for the Guide screen to fix Minecraft font issues at GUI scale 1 and 3.");
            fullWidthLayout = backend.defineBoolean("gui", "fullWidthLayout", true,
                    "Use the full width of the screen for the guide when it is opened.");

            showDebugGuiOverlays = backend.defineBoolean("debug", "showDebugGuiOverlays", false,
                    "Show debugging overlays in GUI on mouse-over.");

            backend.finishLoading();
        }
    }
}
