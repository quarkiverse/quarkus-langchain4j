package io.quarkiverse.langchain4j.runtime;

public class NamedConfigUtil {

    public static final String DEFAULT_NAME = "<default>";

    public static boolean isDefault(String configName) {
        return DEFAULT_NAME.equals(configName);
    }
}
