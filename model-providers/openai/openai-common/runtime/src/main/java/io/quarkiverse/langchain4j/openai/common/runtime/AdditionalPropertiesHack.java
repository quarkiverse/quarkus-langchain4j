package io.quarkiverse.langchain4j.openai.common.runtime;

import java.net.Proxy;
import java.util.HashMap;
import java.util.Map;

/**
 * This is done because we have no way of passing Quarkus specific properties from a model to a client.
 * This only works because:
 * <ul>
 * <li>The creation of beans does not happen in parallel</li>
 * <li>Setting up a model builder always precedes setting up a client builder on the same thread</li>
 * </ul>
 */
public final class AdditionalPropertiesHack {

    private AdditionalPropertiesHack() {
    }

    static final ThreadLocal<Map<String, String>> PROPS = ThreadLocal.withInitial(HashMap::new);
    static final ThreadLocal<Proxy> PROXY = new ThreadLocal<>();

    public static void reset() {
        PROPS.get().clear();
        PROXY.remove();
    }

    public static void setConfigName(String configName) {
        PROPS.get().put("configName", configName);
    }

    public static void setTlsConfigurationName(String tlsConfigurationName) {
        PROPS.get().put("tlsConfigurationName", tlsConfigurationName);
    }

    public static String getAndClearConfigName() {
        return PROPS.get().remove("configName");
    }

    public static String getAndClearTlsConfigurationName() {
        return PROPS.get().remove("tlsConfigurationName");
    }

    public static void setLogCurl(boolean logCurl) {
        PROPS.get().put("logCurl", Boolean.toString(logCurl));
    }

    public static boolean getAndClearLogCurl() {
        return Boolean.parseBoolean(PROPS.get().remove("logCurl"));
    }

    public static void setProxy(Proxy proxy) {
        PROXY.set(proxy);
    }

    public static Proxy getAndClearProxy() {
        Proxy proxy = PROXY.get();
        PROXY.remove();
        return proxy;
    }
}
