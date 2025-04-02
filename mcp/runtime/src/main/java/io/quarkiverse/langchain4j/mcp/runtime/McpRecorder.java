package io.quarkiverse.langchain4j.mcp.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.tool.ToolProvider;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.mcp.runtime.config.McpBuildTimeConfiguration;
import io.quarkiverse.langchain4j.mcp.runtime.config.McpClientBuildTimeConfig;
import io.quarkiverse.langchain4j.mcp.runtime.config.McpClientRuntimeConfig;
import io.quarkiverse.langchain4j.mcp.runtime.config.McpRuntimeConfiguration;
import io.quarkiverse.langchain4j.mcp.runtime.http.QuarkusHttpMcpTransport;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.annotations.Recorder;
import io.quarkus.runtime.configuration.ConfigurationException;

@Recorder
public class McpRecorder {

    public Function<SyntheticCreationalContext<McpClient>, McpClient> mcpClientSupplier(String clientName,
            McpBuildTimeConfiguration buildTimeConfiguration,
            McpRuntimeConfiguration mcpRuntimeConfiguration) {
        return new Function<>() {
            @Override
            public McpClient apply(SyntheticCreationalContext<McpClient> context) {
                McpTransport transport;
                McpClientBuildTimeConfig buildTimeConfig = buildTimeConfiguration.clients().get(clientName);
                McpClientRuntimeConfig runtimeConfig = mcpRuntimeConfiguration.clients().get(clientName);
                transport = switch (buildTimeConfig.transportType()) {
                    case STDIO -> {
                        List<String> command = runtimeConfig.command().orElseThrow(() -> new ConfigurationException(
                                "MCP client configuration named " + clientName + " is missing the 'command' property"));
                        yield new StdioMcpTransport.Builder()
                                .command(command)
                                .logEvents(runtimeConfig.logResponses().orElse(false))
                                .environment(runtimeConfig.environment())
                                .build();
                    }
                    case HTTP -> new QuarkusHttpMcpTransport.Builder()
                            .sseUrl(runtimeConfig.url().orElseThrow(() -> new ConfigurationException(
                                    "MCP client configuration named " + clientName + " is missing the 'url' property")))
                            .logRequests(runtimeConfig.logRequests().orElse(false))
                            .logResponses(runtimeConfig.logResponses().orElse(false))
                            .build();
                };
                McpClient result = new DefaultMcpClient.Builder()
                        .transport(transport)
                        .toolExecutionTimeout(runtimeConfig.toolExecutionTimeout())
                        .resourcesTimeout(runtimeConfig.resourcesTimeout())
                        // TODO: it should be possible to choose a log handler class via configuration
                        .logHandler(new QuarkusDefaultMcpLogHandler(clientName))
                        .build();
                if (runtimeConfig.toolValidationModelName().isPresent()) {
                    ChatLanguageModel chatLanguageModel;
                    if ("default".equals(runtimeConfig.toolValidationModelName().get())) {
                        chatLanguageModel = context.getInjectedReference(ChatLanguageModel.class);
                    } else {
                        chatLanguageModel = context.getInjectedReference(ChatLanguageModel.class,
                                ModelName.Literal.of(runtimeConfig.toolValidationModelName().get()));
                    }
                    result = new ValidatingMcpClient(result, chatLanguageModel);
                }
                return result;
            }
        };
    }

    public Function<SyntheticCreationalContext<ToolProvider>, ToolProvider> toolProviderFunction(
            Set<String> mcpClientNames) {
        return new Function<>() {
            @Override
            public ToolProvider apply(SyntheticCreationalContext<ToolProvider> context) {
                List<McpClient> clients = new ArrayList<>();
                for (String mcpClientName : mcpClientNames) {
                    McpClientName.Literal qualifier = McpClientName.Literal.of(mcpClientName);
                    clients.add(context.getInjectedReference(McpClient.class, qualifier));
                }
                return new McpToolProvider.Builder()
                        .mcpClients(clients)
                        .build();
            }

        };
    }
}
