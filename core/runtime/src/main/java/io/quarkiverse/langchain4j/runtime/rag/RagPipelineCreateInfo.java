package io.quarkiverse.langchain4j.runtime.rag;

import java.util.List;

import io.quarkiverse.langchain4j.runtime.aiservice.DeclarativeAiServiceCreateInfo.ComponentEntry;

public record RagPipelineCreateInfo(
        ComponentEntry augmentor,
        List<String> retrieverClassNames,
        ComponentEntry router,
        ComponentEntry transformer,
        ComponentEntry aggregator,
        ComponentEntry injector) {
}
