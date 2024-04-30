package io.quarkiverse.langchain4j.watsonx.bean;

import java.util.List;

public record EmbeddingResponse(List<Result> results) {
    public record Result(List<Float> embedding) {
    }
}
