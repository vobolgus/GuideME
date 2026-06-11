package guideme.internal.neoforge;

import guideme.internal.platform.ConfigBackend;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Stores the client config in {@code guideme.toml} via NeoForge's {@link ModConfigSpec}, preserving the exact file
 * format used before the loader split.
 */
public class NeoForgeConfigBackend implements ConfigBackend {
    private final ModContainer modContainer;
    private final ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
    private String currentSection;
    private ModConfigSpec spec;

    public NeoForgeConfigBackend(ModContainer modContainer) {
        this.modContainer = modContainer;
    }

    @Override
    public BooleanOption defineBoolean(String section, String name, boolean defaultValue, String comment) {
        if (spec != null) {
            throw new IllegalStateException("Config options must be defined before finishLoading()");
        }
        if (!section.equals(currentSection)) {
            if (currentSection != null) {
                builder.pop();
            }
            builder.push(section);
            currentSection = section;
        }
        var value = builder.comment(comment).define(name, defaultValue);
        return new BooleanOption() {
            @Override
            public boolean get() {
                return value.getAsBoolean();
            }

            @Override
            public void set(boolean newValue) {
                value.set(newValue);
            }
        };
    }

    @Override
    public void finishLoading() {
        if (currentSection != null) {
            builder.pop();
            currentSection = null;
        }
        spec = builder.build();
        modContainer.registerConfig(ModConfig.Type.CLIENT, spec, "guideme.toml");
    }

    @Override
    public void save() {
        spec.save();
    }
}
