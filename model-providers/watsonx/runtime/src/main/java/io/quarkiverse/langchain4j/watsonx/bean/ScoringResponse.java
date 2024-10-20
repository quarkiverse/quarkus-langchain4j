package io.quarkiverse.langchain4j.watsonx.bean;

import java.util.List;

public record ScoringResponse(List<ScoringOutput> results, Integer inputTokenCount) {
    public record ScoringOutput(Integer index, Double score) {
    }
}
