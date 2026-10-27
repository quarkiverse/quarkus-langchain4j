package io.quarkiverse.langchain4j.runtime.aiservice;

import java.util.List;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolProviderRequest;

public class QuarkusToolProviderRequest extends ToolProviderRequest {

    private final List<String> mcpClientNames;

    public QuarkusToolProviderRequest(InvocationContext invocationContext, UserMessage userMessage,
            List<String> mcpClientNames) {
        this(invocationContext, userMessage, null, mcpClientNames);
    }

    public QuarkusToolProviderRequest(InvocationContext invocationContext, UserMessage userMessage,
            List<ChatMessage> messages, List<String> mcpClientNames) {
        super(ToolProviderRequest.builder()
                .invocationContext(invocationContext)
                .userMessage(userMessage)
                .messages(messages));
        this.mcpClientNames = mcpClientNames;
    }

    public List<String> getMcpClientNames() {
        return mcpClientNames;
    }
}
