package io.quarkiverse.langchain4j.deployment.items;

import java.util.Collections;
import java.util.Map;

import io.quarkus.builder.item.SimpleBuildItem;

/**
 * Build item used to carry running values to Dev UI.
 */
public final class DevServicesOllamaConfigBuildItem extends SimpleBuildItem {

    private final Map<String, String> config;
    private final Map<String, String> modelOptions;
    private final boolean containerManaged;

    public DevServicesOllamaConfigBuildItem(Map<String, String> config) {
        this(config, Collections.emptyMap());
    }

    public DevServicesOllamaConfigBuildItem(Map<String, String> config, Map<String, String> modelOptions) {
        this(config, modelOptions, false);
    }

    /**
     * @param containerManaged {@code true} when the Ollama Dev Service container (started lazily by Quarkus) takes care
     *        of pulling the models itself, so the core must not try to reach a server at build time
     */
    public DevServicesOllamaConfigBuildItem(Map<String, String> config, Map<String, String> modelOptions,
            boolean containerManaged) {
        this.config = config;
        this.modelOptions = modelOptions != null ? modelOptions : Collections.emptyMap();
        this.containerManaged = containerManaged;
    }

    public boolean isContainerManaged() {
        return containerManaged;
    }

    public Map<String, String> getConfig() {
        return config;
    }

    public Map<String, String> getModelOptions() {
        return modelOptions;
    }

}
