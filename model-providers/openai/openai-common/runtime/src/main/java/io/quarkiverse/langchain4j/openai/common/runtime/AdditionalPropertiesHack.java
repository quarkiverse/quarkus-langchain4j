package io.quarkiverse.langchain4j.openai.common.runtime;

import java.util.HashMap;
import java.util.Map;

/**
 * This is done because we have no way of passing Quarkus specific properties from a model to a client.
 * This only works because:
 * <ul>
 * <li>The creation of beans does not happen in parallel</li>
 * <li>The creation of beans happens on the same thread</li>
 * <li>Setting up a model builder always precedes setting up a client builder</li>
 * </ul>
 */
public final class AdditionalPropertiesHack {

    private AdditionalPropertiesHack() {
    }

    static final ThreadLocal<Map<String, String>> PROPS = new ThreadLocal<>();
    static {
        reset();
    }

    public static void reset() {
        PROPS.set(new HashMap<>());
    }

    public static void setTlsConfigurationName(String tlsConfigurationName) {
        Map<String, String> map = PROPS.get();
        if (map == null) {
            // this should never happen
            return;
        }
        map.put("tlsConfigurationName", tlsConfigurationName);
    }

    public static String getAndClearTlsConfigurationName() {
        Map<String, String> map = PROPS.get();
        if (map == null) {
            // this should never happen
            return null;
        }
        return map.remove("tlsConfigurationName");
    }
}
