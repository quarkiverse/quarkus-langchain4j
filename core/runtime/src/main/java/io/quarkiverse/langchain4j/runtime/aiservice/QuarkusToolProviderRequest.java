package io.quarkiverse.langchain4j.runtime.aiservice;

import java.util.List;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.service.tool.ToolProviderRequest;

public class QuarkusToolProviderRequest extends ToolProviderRequest {

    private final List<String> mcpServerNames;

    public QuarkusToolProviderRequest(Object chatMemoryId, UserMessage userMessage, List<String> mcpServerNames) {
        super(chatMemoryId, userMessage);
        this.mcpServerNames = mcpServerNames;
    }

    public List<String> getMcpServerNames() {
        return mcpServerNames;
    }
}
