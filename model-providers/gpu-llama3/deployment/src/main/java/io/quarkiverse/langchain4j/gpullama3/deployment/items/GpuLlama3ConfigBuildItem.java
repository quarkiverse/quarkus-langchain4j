package io.quarkiverse.langchain4j.gpullama3.deployment.items;

import io.quarkiverse.langchain4j.gpullama3.runtime.config.GpuLlama3Config;
import io.quarkus.builder.item.SimpleBuildItem;

public final class GpuLlama3ConfigBuildItem extends SimpleBuildItem {
    private final GpuLlama3Config config;

    public GpuLlama3ConfigBuildItem(GpuLlama3Config config) {
        this.config = config;
    }

    public GpuLlama3Config getConfig() {
        return config;
    }
}
