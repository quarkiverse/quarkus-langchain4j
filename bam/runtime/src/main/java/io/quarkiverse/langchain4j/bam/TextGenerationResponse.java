package io.quarkiverse.langchain4j.bam;

import java.util.List;

public record TextGenerationResponse(List<Results> results) {

    public record Results(int generatedTokenCount, int inputTokenCount, String stopReason, String generatedText) {

    }
}
