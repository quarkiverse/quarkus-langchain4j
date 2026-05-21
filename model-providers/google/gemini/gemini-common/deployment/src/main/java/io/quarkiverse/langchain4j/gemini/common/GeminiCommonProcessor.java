package io.quarkiverse.langchain4j.gemini.common;

import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveHierarchyBuildItem;

public class GeminiCommonProcessor {

    @BuildStep
    public void nativeSupport(BuildProducer<ReflectiveHierarchyBuildItem> reflectiveHierarchyProducer) {
        reflectiveHierarchyProducer.produce(ReflectiveHierarchyBuildItem.builder(GenerateContentRequest.class).build());
        reflectiveHierarchyProducer.produce(ReflectiveHierarchyBuildItem.builder(GenerateContentResponse.class).build());
    }
}
