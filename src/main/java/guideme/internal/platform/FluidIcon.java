package guideme.internal.platform;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * The still sprite and tint color used to display a fluid in the UI.
 */
public record FluidIcon(TextureAtlasSprite sprite, int tintColor) {
}
