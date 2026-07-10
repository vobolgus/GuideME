package guideme.internal.hotkey;

import com.google.common.base.Strings;
import com.mojang.blaze3d.platform.InputConstants;
import guideme.Guide;
import guideme.PageAnchor;
import guideme.indices.ItemIndex;
import guideme.internal.GuideMEClient;
import guideme.internal.GuideRegistry;
import guideme.internal.GuidebookText;
import guideme.internal.platform.GuideMEClientPlatform;
import guideme.internal.screen.GuideScreen;
import guideme.ui.GuideUiHost;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Adds a "Hold X to show guide" tooltip.
 * <p>
 * The loader-specific client entrypoint creates the key mapping (potentially with loader-specific conflict-context
 * settings), registers it, passes it to {@link #init} and forwards item tooltip events to {@link #onItemTooltip} as
 * well as the end of every client tick to {@link #onClientTickEnd()}.
 */
public final class OpenGuideHotkey {
    public static final String HOTKEY_TRANSLATION_KEY = "key.guideme.guide";
    public static final int DEFAULT_KEY = InputConstants.KEY_G;

    private static KeyMapping openGuideMapping;

    private static final int TICKS_TO_OPEN = 10;

    private static boolean newTick = true;

    // The previous item the tooltip was being shown for
    private static Identifier previousItemId;
    private static final List<FoundPage> guidebookPages = new ArrayList<>();
    // Full ticks since the button was held (reduces slowly when not held)
    private static int ticksKeyHeld;
    // Is the key to open currently held
    private static boolean holding;

    private OpenGuideHotkey() {
    }

    private record FoundPage(Guide guide, PageAnchor page) {
    }

    public static void init(KeyMapping mapping) {
        openGuideMapping = mapping;
    }

    /**
     * Called by the loader for item tooltips shown to the local player. Loaders should not forward tooltip events fired
     * for other purposes (i.e. while building the search tree for the creative menu) where they can detect this.
     */
    public static void onItemTooltip(ItemStack itemStack, TooltipFlag tooltipFlag, List<Component> lines) {
        handleTooltip(itemStack, tooltipFlag, lines);
    }

    /**
     * Called by the loader at the end of every client tick.
     */
    public static void onClientTickEnd() {
        newTick = true;
    }

    private static void handleTooltip(ItemStack itemStack, TooltipFlag tooltipFlag, List<Component> lines) {
        // Player didn't bind the key
        if (!isKeyBound()) {
            holding = false;
            ticksKeyHeld = 0;
            return;
        }

        // This should only update once per client-tick
        if (newTick) {
            newTick = false;
            update(itemStack);
        }

        if (guidebookPages.isEmpty()) {
            return;
        }

        var guide = guidebookPages.getFirst().guide();
        var pageAnchor = guidebookPages.getFirst().page();

        // Don't do anything if we're already on the target page
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof GuideScreen guideScreen
                && guideScreen.getGuide() == guide
                && guideScreen.getCurrentPageId().equals(pageAnchor.pageId())) {
            return;
        }

        // Compute the progress value between [0,1]
        float progress = ticksKeyHeld;
        if (holding) {
            progress += minecraft.getDeltaTracker().getRealtimeDeltaTicks();
        } else {
            progress -= minecraft.getDeltaTracker().getRealtimeDeltaTicks();
        }
        progress /= (float) TICKS_TO_OPEN;
        var component = makeProgressBar(Mth.clamp(progress, 0, 1));
        // It may happen that we're the only line
        if (lines.isEmpty()) {
            lines.add(component);
        } else {
            lines.add(1, component);
        }
    }

    private static Component makeProgressBar(float progress) {
        var minecraft = Minecraft.getInstance();

        var holdW = GuidebookText.HoldToShow
                .text(getHotkey().getTranslatedKeyMessage().copy().withStyle(ChatFormatting.GRAY))
                .withStyle(ChatFormatting.DARK_GRAY);

        var fontRenderer = minecraft.font;
        var charWidth = fontRenderer.width("|");
        var tipWidth = fontRenderer.width(holdW);

        var total = tipWidth / charWidth;
        var current = (int) (progress * total);

        if (progress > 0) {
            var result = Component.literal(Strings.repeat("|", current)).withStyle(ChatFormatting.GRAY);
            if (progress < 1)
                result = result.append(
                        Component.literal(Strings.repeat("|", total - current)).withStyle(ChatFormatting.DARK_GRAY));
            return result;
        }

        return holdW;
    }

    private static void update(ItemStack itemStack) {
        var itemId = itemStack.typeHolder()
                .unwrapKey()
                .map(ResourceKey::identifier)
                .orElse(null);

        if (!Objects.equals(itemId, previousItemId)) {
            previousItemId = itemId;
            guidebookPages.clear();
            ticksKeyHeld = 0;

            if (itemId == null) {
                return;
            }

            for (var guide : GuideRegistry.getAll()) {
                if (!guide.isAvailableToOpenHotkey()) {
                    continue;
                }

                var itemIndex = guide.getIndex(ItemIndex.class);
                var page = itemIndex.get(itemId);
                if (page != null) {
                    guidebookPages.add(new FoundPage(guide, page));
                }
            }
        }

        // Bump the ticks the key was held
        holding = isKeyHeld();
        if (holding) {
            if (ticksKeyHeld < TICKS_TO_OPEN && ++ticksKeyHeld == TICKS_TO_OPEN) {
                if (!guidebookPages.isEmpty()) {
                    var foundPage = guidebookPages.getFirst();
                    var guide = foundPage.guide();

                    if (Minecraft.getInstance().screen instanceof GuideUiHost uiHost && uiHost.getGuide() == guide) {
                        uiHost.navigateTo(foundPage.page());
                    } else {
                        GuideMEClient.openGuideAtAnchor(guide, foundPage.page());
                    }
                    // Reset the ticks held immediately to avoid reopening another page if
                    // our cursors lands on an item
                    ticksKeyHeld = 0;
                    holding = false;
                }
            } else if (ticksKeyHeld > TICKS_TO_OPEN) {
                ticksKeyHeld = TICKS_TO_OPEN;
            }
        } else {
            ticksKeyHeld = Math.max(0, ticksKeyHeld - 2);
        }
    }

    /**
     * This circumvents any current UI key handling.
     */
    private static boolean isKeyHeld() {
        int keyCode = GuideMEClientPlatform.get().getBoundKey(getHotkey()).getValue();
        var window = Minecraft.getInstance().getWindow();

        return InputConstants.isKeyDown(window, keyCode);
    }

    private static boolean isKeyBound() {
        return !getHotkey().isUnbound();
    }

    public static KeyMapping getHotkey() {
        return Objects.requireNonNull(openGuideMapping, "Open guide hotkey is not initialized");
    }
}
