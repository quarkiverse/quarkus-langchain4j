package io.quarkiverse.langchain4j.bam;

import java.util.List;

public record TokenizationResponse(int tokenCount, List<String> tokens) {

}
