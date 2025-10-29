package io.quarkiverse.langchain4j.agentic.deployment;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.jboss.jandex.DotName;

import io.quarkus.builder.item.SimpleBuildItem;

public final class OutputKeyBuildItem extends SimpleBuildItem {

    private final Map<String, DotName> keyToTypeMap;
    private final Set<String> userProvidedKeys;

    private OutputKeyBuildItem(Map<String, DotName> keyToTypeMap, Set<String> userProvidedKeys) {
        this.keyToTypeMap = keyToTypeMap;
        this.userProvidedKeys = userProvidedKeys;
    }

    public Map<String, DotName> getKeyToTypeMap() {
        return keyToTypeMap;
    }

    public Set<String> getUserProvidedKeys() {
        return userProvidedKeys;
    }

    public static Builder of() {
        return new Builder();
    }

    public static class Builder {
        private final Map<String, DotName> keyToTypeMap = new HashMap<>();
        private final Set<String> userProvidedKeys = new HashSet<>();

        private Builder() {
        }

        public Builder addKeyType(String key, DotName type) {
            Objects.requireNonNull(key, "key must not be null");
            Objects.requireNonNull(type, "type must not be null");

            DotName previous = keyToTypeMap.put(key, type);
            if (previous != null) {
                if (!previous.equals(type)) {
                    throw new IllegalStateException(
                            "Output key '%s' is used both with type '%s' and type '%s' ".formatted(key, previous, type));
                }
            }
            return this;
        }

        public Builder addUserProvidedKey(String name) {
            Objects.requireNonNull(name, "name must not be null");
            userProvidedKeys.add(name);
            return this;
        }

        public OutputKeyBuildItem build() {
            return new OutputKeyBuildItem(Map.copyOf(keyToTypeMap), Set.copyOf(userProvidedKeys));
        }
    }
}
