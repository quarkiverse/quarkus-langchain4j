package io.quarkiverse.langchain4j.bam;

import java.util.List;

public record EmbeddingResponse(List<List<Float>> results) {

}
