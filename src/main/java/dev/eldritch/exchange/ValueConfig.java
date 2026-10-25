package dev.eldritch.exchange;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

/**
 * Editable item values stored in {@code config/eldritch/values.json}.
 *
 * <p>The file has two sections. {@code values} is the table the player may edit.
 * {@code auto} is the table the algorithm produced the last time the file was
 * written; it is only used to tell an edited value apart from an automatically
 * generated one. Entries where {@code values} differs from {@code auto} are
 * treated as manual overrides. Untouched entries keep following recipe changes.
 */
public final class ValueConfig {

    public record Data(Map<String, Long> values, Map<String, Long> auto) {
        public static final Data EMPTY = new Data(Map.of(), Map.of());
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String COMMENT = String.join(" ",
            "Edit the numbers under 'values' to set custom essence costs.",
            "'auto' is the cost the mod calculated and is rewritten automatically, so do not edit it.",
            "An entry whose value differs from 'auto' is treated as a manual override.",
            "Delete an entry from 'values' to restore its automatic cost. Set 0 to make an item unburnable.");

    private ValueConfig() {
    }

    public static Path path() {
        return FMLPaths.CONFIGDIR.get().resolve("eldritch_exchange").resolve("values.json");
    }

    /**
     * Modification stamp used to notice a file edit while the server is running.
     * Zero means the file does not exist yet.
     */
    public static long stamp() {
        try {
            Path path = path();
            return Files.exists(path) ? Files.getLastModifiedTime(path).toMillis() : 0L;
        } catch (IOException e) {
            return 0L;
        }
    }

    public static Data load(Logger logger) {
        Path path = path();
        if (!Files.exists(path)) {
            return Data.EMPTY;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) {
                logger.warn("Ignoring {}: expected a JSON object", path);
                return Data.EMPTY;
            }
            JsonObject object = root.getAsJsonObject();
            Map<String, Long> values = readSection(object, "values", logger);
            Map<String, Long> auto = readSection(object, "auto", logger);
            if (values.isEmpty() && auto.isEmpty()) {
                // Accept a hand-written flat map such as {"minecraft:dirt": 4}.
                values = readSection(object, null, logger);
            }
            return new Data(values, auto);
        } catch (IOException | RuntimeException e) {
            logger.warn("Could not read {}, using calculated values", path, e);
            return Data.EMPTY;
        }
    }

    private static Map<String, Long> readSection(JsonObject root, String section, Logger logger) {
        JsonElement element = section == null ? root : root.get(section);
        if (element == null || !element.isJsonObject()) {
            return Map.of();
        }
        Map<String, Long> result = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("_") || !key.contains(":")) {
                continue;
            }
            try {
                long value = entry.getValue().getAsLong();
                result.put(key, Math.max(0L, value));
            } catch (RuntimeException e) {
                logger.warn("Ignoring value for '{}': not a number", key);
            }
        }
        return result;
    }

    public static Map<String, Long> overrides(Data data) {
        Map<String, Long> overrides = new HashMap<>();
        for (Map.Entry<String, Long> entry : data.values().entrySet()) {
            Long generated = data.auto().get(entry.getKey());
            if (generated == null || !generated.equals(entry.getValue())) {
                overrides.put(entry.getKey(), entry.getValue());
            }
        }
        return overrides;
    }

    public static void save(Map<String, Long> values, Map<String, Long> auto, Logger logger) {
        Path path = path();
        JsonObject root = new JsonObject();
        root.addProperty("_comment", COMMENT);
        root.add("values", toJson(values));
        root.add("auto", toJson(auto));
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(root) + System.lineSeparator(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.warn("Could not write {}", path, e);
        }
    }

    private static JsonObject toJson(Map<String, Long> values) {
        JsonObject object = new JsonObject();
        for (Map.Entry<String, Long> entry : new TreeMap<>(values).entrySet()) {
            object.addProperty(entry.getKey(), entry.getValue());
        }
        return object;
    }
}
