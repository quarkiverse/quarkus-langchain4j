package io.quarkiverse.langchain4j.watsonx.bean;

import java.util.List;

public record TextRerankResponse(List<TextRerankOutput> results, Integer inputTokenCount) {
    public record TextRerankOutput(Integer index, Double score) {
    }
}
