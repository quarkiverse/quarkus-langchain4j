package io.quarkiverse.langchain4j.bam;

import java.util.List;

public record EmbeddingResponse(List<Result> results) {
    public record Result(List<Float> embedding) {
    }
}
