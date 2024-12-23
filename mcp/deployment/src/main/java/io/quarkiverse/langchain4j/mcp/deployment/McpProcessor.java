package io.quarkiverse.langchain4j.mcp.deployment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;

import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.tool.ToolProvider;
import io.quarkiverse.langchain4j.mcp.runtime.McpClientName;
import io.quarkiverse.langchain4j.mcp.runtime.McpRecorder;
import io.quarkiverse.langchain4j.mcp.runtime.config.McpClientConfig;
import io.quarkiverse.langchain4j.mcp.runtime.config.McpConfiguration;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;

public class McpProcessor {

    private static final DotName MCP_CLIENT = DotName.createSimple(McpClient.class);
    private static final DotName MCP_CLIENT_NAME = DotName.createSimple(McpClientName.class);
    private static final DotName TOOL_PROVIDER = DotName.createSimple(ToolProvider.class);

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public void registerMcpClients(McpConfiguration mcpConfiguration,
            BuildProducer<SyntheticBeanBuildItem> beanProducer,
            McpRecorder recorder) {
        if (mcpConfiguration.clients() != null && !mcpConfiguration.clients().isEmpty()) {
            // generate MCP clients
            List<AnnotationInstance> qualifiers = new ArrayList<>();
            for (Map.Entry<String, McpClientConfig> client : mcpConfiguration.clients()
                    .entrySet()) {
                AnnotationInstance qualifier = AnnotationInstance.builder(MCP_CLIENT_NAME)
                        .add("value", client.getKey())
                        .build();
                qualifiers.add(qualifier);
                beanProducer.produce(SyntheticBeanBuildItem
                        .configure(MCP_CLIENT)
                        .addQualifier(qualifier)
                        .setRuntimeInit()
                        .defaultBean()
                        .unremovable()
                        // TODO: should we allow other scopes?
                        .scope(ApplicationScoped.class)
                        .supplier(recorder.mcpClientSupplier(client.getKey(), mcpConfiguration))
                        .done());
            }
            // generate a tool provider if configured to do so
            if (mcpConfiguration.generateToolProvider().orElse(true)) {
                Set<String> mcpClientNames = mcpConfiguration.clients().keySet();
                SyntheticBeanBuildItem.ExtendedBeanConfigurator configurator = SyntheticBeanBuildItem
                        .configure(TOOL_PROVIDER)
                        .addType(ClassType.create(TOOL_PROVIDER))
                        .setRuntimeInit()
                        .defaultBean()
                        .unremovable()
                        .scope(ApplicationScoped.class)
                        .createWith(recorder.toolProviderFunction(mcpClientNames));
                for (AnnotationInstance qualifier : qualifiers) {
                    configurator.addInjectionPoint(ClassType.create(MCP_CLIENT), qualifier);
                }
                beanProducer.produce(configurator.done());
            }
        }
    }
}
