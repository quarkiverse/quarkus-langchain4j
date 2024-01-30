package io.quarkiverse.langchain4j.runtime;

public class NamedModelUtil {

    public static final String DEFAULT_NAME = "<default>";

    public static boolean isDefault(String modelName) {
        return DEFAULT_NAME.equals(modelName);
    }
}
