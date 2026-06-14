package io.quarkiverse.langchain4j.deployment;

import io.quarkiverse.langchain4j.runtime.rag.RagPipelineCreateInfo;
import io.quarkus.builder.item.MultiBuildItem;

public final class RagPipelineBuildItem extends MultiBuildItem {

    private final String aiServiceClassName;
    private final RagPipelineCreateInfo createInfo;

    public RagPipelineBuildItem(String aiServiceClassName, RagPipelineCreateInfo createInfo) {
        this.aiServiceClassName = aiServiceClassName;
        this.createInfo = createInfo;
    }

    public String getAiServiceClassName() {
        return aiServiceClassName;
    }

    public RagPipelineCreateInfo getCreateInfo() {
        return createInfo;
    }
}
