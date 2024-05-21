package io.quarkiverse.langchain4j.bam;

import java.util.List;

public record EmbeddingRequest(String modelId, List<String> input) {

}
