package guideme.internal.platform;

/**
 * Loader-specific storage backend for the GuideME client config (guideme.toml). Shaped to exactly what
 * {@link guideme.internal.GuideMEClient} uses: a flat set of boolean options grouped into sections.
 * <p>
 * Options must be defined before {@link #finishLoading()} is called; values become available afterwards.
 */
public interface ConfigBackend {
    BooleanOption defineBoolean(String section, String name, boolean defaultValue, String comment);

    /**
     * Called once after all options have been defined.
     */
    void finishLoading();

    /**
     * Persists the current values.
     */
    void save();

    interface BooleanOption {
        boolean get();

        void set(boolean value);
    }
}
