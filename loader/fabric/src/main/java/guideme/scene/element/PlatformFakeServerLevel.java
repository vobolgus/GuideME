package guideme.scene.element;

import net.minecraft.world.level.LevelAccessor;

/**
 * Loader-specific subclass of {@link FakeForwardingServerLevel}. The NeoForge version forwards the NeoForge extension
 * methods to the delegate; this Fabric twin adds nothing.
 */
public class PlatformFakeServerLevel extends FakeForwardingServerLevel {
    public PlatformFakeServerLevel(LevelAccessor delegate) {
        super(delegate);
    }
}
