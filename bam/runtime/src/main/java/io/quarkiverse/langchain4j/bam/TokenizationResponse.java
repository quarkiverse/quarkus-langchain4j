package io.quarkiverse.langchain4j.bam;

import java.util.List;

public record TokenizationResponse(List<Results> results) {
    public record Results(int tokenCount) {

    }
}
