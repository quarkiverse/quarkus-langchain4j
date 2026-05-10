package io.quarkiverse.langchain4j.runtime.aiservice;

import java.util.List;

public record AiServiceInvocationData(
        String methodId,
        List<String> mcpClientNames) implements QuarkusInvocationData {
}
