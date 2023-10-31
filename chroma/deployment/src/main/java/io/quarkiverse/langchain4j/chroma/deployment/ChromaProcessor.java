package io.quarkiverse.langchain4j.chroma.deployment;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

class ChromaProcessor {

    static final String FEATURE = "langchain4j-chroma";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }
}
