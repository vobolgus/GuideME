package guideme.internal.fabric.test;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Bootstraps the vanilla built-in registries once before any test class runs.
 * <p>
 * Auto-registered for every test class via {@code META-INF/services} + JUnit extension autodetection
 * ({@code junit-platform.properties}), both living in this loader overlay's test resources — so this applies to the
 * {@code :fabric} unit tests ONLY.
 * <p>
 * Why: the shared JUnit suite (root {@code src/test/java}) runs under {@code :neoforge} inside ModDevGradle's
 * {@code unitTest} feature, which launches a fully booted FML environment. On Fabric, {@code fabric-loader-junit} only
 * starts Fabric Loader (Knot classloader, access widener, mixins) but does NOT bootstrap the game, so anything touching
 * {@code BuiltInRegistries} (e.g. {@code guideme.internal.GuideME.<clinit>} via {@code RecipeType}) throws "Not
 * bootstrapped" without this.
 * <p>
 * IMPORTANT: the extension instance itself is created on the JUnit/app classloader, while the test classes are loaded
 * by Fabric Loader's Knot classloader (verified: stack frames are {@code knot//...}). Calling
 * {@code Bootstrap.bootStrap()} directly here would bootstrap the app-classloader copy of the registries — the wrong
 * universe. So the bootstrap is invoked reflectively through the classloader that actually owns the test class.
 */
public class BootstrapMinecraftExtension implements BeforeAllCallback {
    private static final Set<ClassLoader> bootstrappedLoaders = ConcurrentHashMap.newKeySet();

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        var classLoader = context.getRequiredTestClass().getClassLoader();
        if (bootstrappedLoaders.add(classLoader)) {
            Class.forName("net.minecraft.SharedConstants", true, classLoader)
                    .getMethod("tryDetectVersion")
                    .invoke(null);
            Class.forName("net.minecraft.server.Bootstrap", true, classLoader)
                    .getMethod("bootStrap")
                    .invoke(null);
        }
    }
}
