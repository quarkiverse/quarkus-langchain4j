package io.quarkiverse.langchain4j.deployment;

import org.jboss.jandex.DotName;

import io.quarkiverse.langchain4j.runtime.rag.RagPipelineCreateInfo;
import io.quarkus.builder.item.MultiBuildItem;

/**
 * Carries info for a standalone {@code @RagPipeline} interface — one that is NOT
 * an AI service — from the scan/validate build step to the RUNTIME_INIT synthetic
 * bean creation step.
 */
public final class StandaloneRagPipelineBuildItem extends MultiBuildItem {

    private final DotName interfaceDotName;
    private final RagPipelineCreateInfo createInfo;

    public StandaloneRagPipelineBuildItem(DotName interfaceDotName, RagPipelineCreateInfo createInfo) {
        this.interfaceDotName = interfaceDotName;
        this.createInfo = createInfo;
    }

    public DotName getInterfaceDotName() {
        return interfaceDotName;
    }

    public RagPipelineCreateInfo getCreateInfo() {
        return createInfo;
    }
}
