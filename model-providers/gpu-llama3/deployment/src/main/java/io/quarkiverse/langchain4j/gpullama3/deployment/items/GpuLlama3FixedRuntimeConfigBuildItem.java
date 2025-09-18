package io.quarkiverse.langchain4j.gpullama3.deployment.items;

import io.quarkiverse.langchain4j.gpullama3.runtime.config.GpuLlama3FixedRuntimeConfig;
import io.quarkus.builder.item.SimpleBuildItem;

public final class GpuLlama3FixedRuntimeConfigBuildItem extends SimpleBuildItem {
    private final GpuLlama3FixedRuntimeConfig config;

    public GpuLlama3FixedRuntimeConfigBuildItem(GpuLlama3FixedRuntimeConfig config) {
        this.config = config;
    }

    public GpuLlama3FixedRuntimeConfig getConfig() {
        return config;
    }
}
