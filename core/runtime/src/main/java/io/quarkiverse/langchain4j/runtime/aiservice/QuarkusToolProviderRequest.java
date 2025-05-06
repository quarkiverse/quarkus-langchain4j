package io.quarkiverse.langchain4j.runtime.aiservice;

import java.util.List;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.service.tool.ToolProviderRequest;

public class QuarkusToolProviderRequest extends ToolProviderRequest {

    private final List<String> mcpClientNames;

    public QuarkusToolProviderRequest(Object chatMemoryId, UserMessage userMessage, List<String> mcpClientNames) {
        super(chatMemoryId, userMessage);
        this.mcpClientNames = mcpClientNames;
    }

    public List<String> getMcpClientNames() {
        return mcpClientNames;
    }
}
