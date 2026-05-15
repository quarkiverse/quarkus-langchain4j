package io.quarkiverse.langchain4j.runtime.aiservice;

import java.util.List;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolProviderRequest;

/**
 * Quarkus-specific {@link ToolProviderRequest} that carries the per-method {@code mcpClientNames}
 * scoping list. Tool providers (notably the MCP integration) can downcast to this type to honour
 * the per-AiService-method MCP client filter.
 *
 * <p>
 * Instances are built by the {@code toolProviderRequestFactory} hook installed on the
 * {@code AiServices} builder; see {@code QuarkusAiServicesFactory.applyDelegationHooks}.
 */
public class QuarkusToolProviderRequest extends ToolProviderRequest {

    private final List<String> mcpClientNames;

    public QuarkusToolProviderRequest(InvocationContext invocationContext, UserMessage userMessage,
            List<String> mcpClientNames) {
        super(ToolProviderRequest.builder()
                .invocationContext(invocationContext)
                .userMessage(userMessage));
        this.mcpClientNames = mcpClientNames;
    }

    /**
     * Convenience constructor for the {@code toolProviderRequestFactory} factory: copies all fields
     * from a fully populated upstream {@link ToolProviderRequest} (invocationContext + userMessage +
     * messages) and adds the per-method {@code mcpClientNames}.
     */
    public QuarkusToolProviderRequest(ToolProviderRequest source, List<String> mcpClientNames) {
        super(ToolProviderRequest.builder()
                .invocationContext(source.invocationContext())
                .userMessage(source.userMessage())
                .messages(source.messages()));
        this.mcpClientNames = mcpClientNames;
    }

    /**
     * @deprecated use {@link #QuarkusToolProviderRequest(ToolProviderRequest, List)} which preserves
     *             the messages list. Prefer building the upstream request from the builder first
     *             via {@code builder.build()}.
     */
    @Deprecated
    public QuarkusToolProviderRequest(ToolProviderRequest.Builder builder, List<String> mcpClientNames) {
        this(builder.build(), mcpClientNames);
    }

    public List<String> getMcpClientNames() {
        return mcpClientNames;
    }
}
