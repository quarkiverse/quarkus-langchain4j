package io.quarkiverse.langchain4j.bam;

import java.util.List;

public record TextGenerationRequest(String modelId, List<Message> messages, Parameters parameters) {

}
