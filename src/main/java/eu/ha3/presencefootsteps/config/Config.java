package eu.ha3.presencefootsteps.config;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import eu.ha3.presencefootsteps.PresenceFootsteps;
import net.minecraft.resources.Identifier;

/**
 * A hierarchical JSON-backed settings file.
 *
 * Values are grouped by category, and each value may carry a comment
 * which is written alongside it under a {@code _comment_<key>} entry.
 *
 * @author Sollace
 */
public abstract class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file;
    private final Map<String, List<Setting<?>>> categories = new LinkedHashMap<>();
    private final List<Setting<?>> settings = new ArrayList<>();
    private final List<Consumer<Config>> changeListeners = new ArrayList<>();

    protected Config(Path file) {
        this.file = file;
    }

    protected <T> Setting<T> value(String category, String key, T defaultValue) {
        Setting<T> setting = new Setting<>(this, category, key, () -> defaultValue, null);
        register(setting);
        return setting;
    }

    public <K, T extends Collection<K>> Setting<T> value(String category, String key, Supplier<T> defaultValue, Class<K> elementType) {
        Setting<T> setting = new Setting<>(this, category, key, defaultValue, elementType);
        register(setting);
        return setting;
    }

    private void register(Setting<?> setting) {
        settings.add(setting);
        categories.computeIfAbsent(setting.getCategory(), category -> new ArrayList<>()).add(setting);
    }

    public Path getConfigFile() {
        return file;
    }

    public void onChangedExternally(Consumer<Config> callback) {
        changeListeners.add(callback);
    }

    void onChange(Setting<?> setting) {
        // Callback used by subclasses to react to settings changing.
    }

    public void load() {
        if (Files.isReadable(file)) {
            try (Reader reader = Files.newBufferedReader(file)) {
                JsonElement root = JsonParser.parseReader(reader);

                if (root.isJsonObject()) {
                    read(root.getAsJsonObject());
                }

                changeListeners.forEach(listener -> listener.accept(this));
            } catch (Exception e) {
                PresenceFootsteps.logger.error("Invalid config: " + file, e);
            }
        }

        save();
    }

    private void read(JsonObject root) {
        for (Setting<?> setting : settings) {
            JsonElement category = root.get(setting.getCategory());

            if (category == null || !category.isJsonObject()) {
                continue;
            }

            JsonElement value = category.getAsJsonObject().get(setting.getKey());

            if (value != null) {
                Object parsed = readValue(setting, value);

                if (parsed != null) {
                    setting.setUnchecked(parsed);
                }
            }
        }
    }

    private @Nullable Object readValue(Setting<?> setting, JsonElement element) {
        Object current = setting.get();

        if (current instanceof Integer) {
            return element.isJsonPrimitive() ? element.getAsInt() : null;
        }

        if (current instanceof Boolean) {
            return element.isJsonPrimitive() ? element.getAsBoolean() : null;
        }

        if (current instanceof String) {
            return element.isJsonPrimitive() ? element.getAsString() : null;
        }

        if (current instanceof Enum<?> enumValue) {
            return element.isJsonPrimitive()
                    ? Enum.valueOf(enumValue.getDeclaringClass(), element.getAsString())
                    : null;
        }

        if (current instanceof Collection<?> && setting.getElementType() == Identifier.class) {
            if (!element.isJsonArray()) {
                return null;
            }

            Collection<Identifier> parsed = new LinkedHashSet<>();

            for (JsonElement entry : element.getAsJsonArray()) {
                if (entry.isJsonPrimitive()) {
                    parsed.add(Identifier.parse(entry.getAsString()));
                }
            }

            return parsed;
        }

        return null;
    }

    public synchronized void save() {
        try {
            Files.createDirectories(file.getParent());

            try (BufferedWriter writer = Files.newBufferedWriter(file)) {
                GSON.toJson(write(), writer);
            }
        } catch (IOException e) {
            PresenceFootsteps.logger.error("Error whilst saving config: " + file, e);
        }
    }

    private JsonObject write() {
        JsonObject root = new JsonObject();

        categories.forEach((category, entries) -> {
            JsonObject contents = new JsonObject();

            for (Setting<?> setting : entries) {
                if (!setting.getComment().isEmpty()) {
                    contents.addProperty("_comment_" + setting.getKey(), setting.getComment());
                }

                Object value = setting.get();

                if (value instanceof Enum<?> enumValue) {
                    contents.addProperty(setting.getKey(), enumValue.name());
                } else if (value instanceof Collection<?> collection) {
                    JsonArray array = new JsonArray();
                    collection.forEach(entry -> array.add(String.valueOf(entry)));
                    contents.add(setting.getKey(), array);
                } else if (value instanceof Number number) {
                    contents.addProperty(setting.getKey(), number);
                } else if (value instanceof Boolean bool) {
                    contents.addProperty(setting.getKey(), bool);
                } else if (value instanceof String string) {
                    contents.addProperty(setting.getKey(), string);
                }
            }

            root.add(category, contents);
        });

        return root;
    }
}
