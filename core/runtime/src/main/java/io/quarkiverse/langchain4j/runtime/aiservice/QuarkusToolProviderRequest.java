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
     * Convenience constructor for the {@code toolProviderRequestFactory} factory: takes a fully
     * populated upstream {@link ToolProviderRequest.Builder} (which we cannot read field-by-field
     * because the builder accessors are package-private) and an {@code mcpClientNames} list, builds
     * the upstream request via {@link ToolProviderRequest.Builder#build()}, and copies the fields
     * we care about across.
     */
    public QuarkusToolProviderRequest(ToolProviderRequest.Builder builder, List<String> mcpClientNames) {
        this(buildFrom(builder), mcpClientNames);
    }

    private QuarkusToolProviderRequest(ToolProviderRequest source, List<String> mcpClientNames) {
        super(ToolProviderRequest.builder()
                .invocationContext(source.invocationContext())
                .userMessage(source.userMessage())
                .messages(source.messages()));
        this.mcpClientNames = mcpClientNames;
    }

    private static ToolProviderRequest buildFrom(ToolProviderRequest.Builder builder) {
        return builder.build();
    }

    public List<String> getMcpClientNames() {
        return mcpClientNames;
    }
}
