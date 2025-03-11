package t.me.p1azmer.discord.verify.config;

import lombok.Setter;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@UtilityClass
public class Config {
    @Setter
    Map<String, Object> configMap = new HashMap<>();

    @SuppressWarnings("unchecked")
    public @Nullable String getConfigString(@NotNull String key) {
        if (configMap == null) {
            log.warn("Configuration is not loaded. Cannot get value for key: {}", key);
            return null;
        }

        String[] parts = key.split("\\.");
        Object value = configMap;
        for (String part : parts) {
            if (value instanceof Map) {
                value = ((Map<String, Object>) value).get(part);
            } else {
                log.debug("Key part '{}' is not a map in path: {}", part, key);
                return null;
            }
        }
        if (value == null) {
            log.debug("No value found for key: {}", key);
            return null;
        }
        return value.toString();
    }

    @SuppressWarnings("unchecked")
    public boolean getConfigBoolean(@NotNull String key) {
        String[] parts = key.split("\\.");
        Object value = configMap;
        for (String part : parts) {
            if (value instanceof Map) {
                value = ((Map<String, Object>) value).get(part);
            } else {
                return false;
            }
        }
        return value instanceof Boolean ? (Boolean) value : Boolean.parseBoolean(value.toString());
    }

    @SuppressWarnings("unchecked")
    public @Nullable List<String> getConfigStringList(@NotNull String key) {
        if (configMap == null) {
            log.warn("Configuration is not loaded. Cannot get string list for key: {}", key);
            return null;
        }

        String[] parts = key.split("\\.");
        Object value = configMap;
        for (String part : parts) {
            if (value instanceof Map) {
                value = ((Map<String, Object>) value).get(part);
            } else {
                log.debug("Key part '{}' is not a map in path: {}", part, key);
                return null;
            }
        }

        if (value == null) {
            log.debug("No value found for key: {}", key);
            return null;
        }

        if (value instanceof List) {
            try {
                List<?> rawList = (List<?>) value;
                List<String> result = new ArrayList<>();
                for (Object item : rawList) {
                    if (item != null) {
                        result.add(item.toString());
                    } else {
                        log.warn("Null item found in list for key: {}", key);
                        result.add(null); // Или пропустить, если null не нужен
                    }
                }
                return result;
            } catch (Exception e) {
                log.error("Failed to process list for key: {}", key, e);
                return null;
            }
        } else {
            log.warn("Value for key '{}' is not a list: {}", key, value.getClass().getSimpleName());
            return null;
        }
    }
}
