package eu.ha3.presencefootsteps.config;

import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

/**
 * A single value in a {@link Config}.
 *
 * @author Sollace
 */
public final class Setting<T> {
    private final Config config;
    private final String category;
    private final String key;
    private final Supplier<T> defaultValue;

    @Nullable
    private final Class<?> elementType;

    private T value;
    private String comment = "";

    Setting(Config config, String category, String key, Supplier<T> defaultValue, @Nullable Class<?> elementType) {
        this.config = config;
        this.category = category;
        this.key = key;
        this.defaultValue = defaultValue;
        this.elementType = elementType;
        this.value = defaultValue.get();
    }

    public String getCategory() {
        return category;
    }

    public String getKey() {
        return key;
    }

    public String getComment() {
        return comment;
    }

    @Nullable
    Class<?> getElementType() {
        return elementType;
    }

    public Setting<T> addComment(String comment) {
        if (!this.comment.isEmpty()) {
            this.comment += "\n";
        }
        this.comment += comment;
        return this;
    }

    public T get() {
        return value;
    }

    public void set(T value) {
        this.value = value;
        config.onChange(this);
    }

    @SuppressWarnings("unchecked")
    void setUnchecked(Object value) {
        set((T)value);
    }
}
