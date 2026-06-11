package guideme.internal.fabric.client;

import guideme.internal.platform.ConfigBackend;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Minimal TOML-subset storage for the GuideME client config on Fabric. Writes {@code config/guideme.toml} with the
 * same sections, keys and comments as the NeoForge version, and reads back any {@code key = true/false} pairs (key
 * names are unique across sections).
 */
public class FabricConfigBackend implements ConfigBackend {
    private static final Logger LOG = LoggerFactory.getLogger(FabricConfigBackend.class);

    private final Path file = FabricLoader.getInstance().getConfigDir().resolve("guideme.toml");

    private final List<OptionImpl> options = new ArrayList<>();
    private boolean loaded;

    @Override
    public BooleanOption defineBoolean(String section, String name, boolean defaultValue, String comment) {
        if (loaded) {
            throw new IllegalStateException("Config options must be defined before finishLoading()");
        }
        var option = new OptionImpl(section, name, defaultValue, comment);
        options.add(option);
        return option;
    }

    @Override
    public void finishLoading() {
        loaded = true;
        load();
        save(); // Persist defaults / newly added options
    }

    private void load() {
        if (!Files.exists(file)) {
            return;
        }

        Map<String, OptionImpl> byName = new HashMap<>();
        for (var option : options) {
            byName.put(option.name, option);
        }

        try {
            for (var line : Files.readAllLines(file)) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("[")) {
                    continue;
                }
                var idx = line.indexOf('=');
                if (idx == -1) {
                    continue;
                }
                var key = line.substring(0, idx).trim();
                var value = line.substring(idx + 1).trim();
                var option = byName.get(key);
                if (option != null && ("true".equals(value) || "false".equals(value))) {
                    option.value = Boolean.parseBoolean(value);
                }
            }
        } catch (IOException e) {
            LOG.error("Failed to read GuideME config {}", file, e);
        }
    }

    @Override
    public void save() {
        var lines = new ArrayList<String>();
        String currentSection = null;
        for (var option : options) {
            if (!option.section.equals(currentSection)) {
                if (currentSection != null) {
                    lines.add("");
                }
                lines.add("[" + option.section + "]");
                currentSection = option.section;
            }
            lines.add("\t#" + option.comment);
            lines.add("\t" + option.name + " = " + option.value);
        }

        try {
            Files.createDirectories(file.getParent());
            Files.write(file, lines);
        } catch (IOException e) {
            LOG.error("Failed to write GuideME config {}", file, e);
        }
    }

    private static class OptionImpl implements BooleanOption {
        final String section;
        final String name;
        final String comment;
        boolean value;

        OptionImpl(String section, String name, boolean defaultValue, String comment) {
            this.section = section;
            this.name = name;
            this.comment = comment;
            this.value = defaultValue;
        }

        @Override
        public boolean get() {
            return value;
        }

        @Override
        public void set(boolean value) {
            this.value = value;
        }
    }
}
