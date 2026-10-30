package io.quarkiverse.langchain4j.runtime.aiservice;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.langchain4j.agent.tool.ReturnBehavior;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.AiServiceTool;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderResult;
import dev.langchain4j.service.tool.ToolServiceContext;

/**
 * The {@linkplain ToolProvider#isDynamic() dynamic} tool providers of an AI service invocation, which are re-evaluated
 * before each LLM call instead of only once at the beginning of the invocation.
 * <p>
 * As upstream, tools are only ever added: once a dynamic provider has returned a tool, it stays available for the
 * remainder of the invocation, and a tool whose name is already known is ignored.
 */
final class DynamicToolProviders {

    private final List<ToolProvider> providers;
    private final InvocationContext invocationContext;
    private final UserMessage userMessage;
    private final List<String> mcpClientNames;

    DynamicToolProviders(List<ToolProvider> providers, InvocationContext invocationContext, UserMessage userMessage,
            List<String> mcpClientNames) {
        this.providers = providers;
        this.invocationContext = invocationContext;
        this.userMessage = userMessage;
        this.mcpClientNames = mcpClientNames;
    }

    List<ToolProvider> providers() {
        return providers;
    }

    /**
     * Calls the dynamic providers with the messages about to be sent to the LLM and adds the tools that are not known
     * yet to {@code toolSpecifications}, {@code toolExecutors} and {@code returnBehaviors}, which are modified in place.
     *
     * @param toolSearchContext the context of the tool search, if any, to which the new tools are added as well
     * @return {@code toolSearchContext} with the new tools added, or {@code null} if {@code toolSearchContext} is
     *         {@code null}
     */
    ToolServiceContext refresh(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications,
            Map<String, ToolExecutor> toolExecutors, Map<String, ReturnBehavior> returnBehaviors,
            ToolServiceContext toolSearchContext) {
        QuarkusToolProviderRequest request = new QuarkusToolProviderRequest(invocationContext, userMessage, messages,
                mcpClientNames);
        List<AiServiceTool> newTools = new ArrayList<>();
        for (ToolProvider provider : providers) {
            ToolProviderResult result = provider.provideTools(request);
            if (result == null) {
                continue;
            }
            for (AiServiceTool tool : result.aiServiceTools()) {
                if (toolExecutors.putIfAbsent(tool.name(), tool.toolExecutor()) == null) {
                    toolSpecifications.add(tool.toolSpecification());
                    returnBehaviors.put(tool.name(), tool.returnBehavior());
                    newTools.add(tool);
                }
            }
        }
        if (toolSearchContext == null || newTools.isEmpty()) {
            return toolSearchContext;
        }

        List<ToolSpecification> effectiveTools = new ArrayList<>(toolSearchContext.effectiveTools());
        List<ToolSpecification> availableTools = new ArrayList<>(toolSearchContext.availableTools());
        Map<String, ToolExecutor> searchToolExecutors = new HashMap<>(toolSearchContext.toolExecutors());
        Map<String, ReturnBehavior> searchReturnBehaviors = new HashMap<>(toolSearchContext.returnBehaviors());
        for (AiServiceTool tool : newTools) {
            if (searchToolExecutors.putIfAbsent(tool.name(), tool.toolExecutor()) == null) {
                effectiveTools.add(tool.toolSpecification());
                availableTools.add(tool.toolSpecification());
                searchReturnBehaviors.put(tool.name(), tool.returnBehavior());
            }
        }
        return toolSearchContext.toBuilder()
                .effectiveTools(effectiveTools)
                .availableTools(availableTools)
                .toolExecutors(searchToolExecutors)
                .returnBehaviors(searchReturnBehaviors)
                .build();
    }
}
