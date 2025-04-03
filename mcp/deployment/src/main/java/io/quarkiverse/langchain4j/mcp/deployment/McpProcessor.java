package io.quarkiverse.langchain4j.mcp.deployment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;

import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.tool.ToolProvider;
import io.quarkiverse.langchain4j.mcp.runtime.McpClientName;
import io.quarkiverse.langchain4j.mcp.runtime.McpRecorder;
import io.quarkiverse.langchain4j.mcp.runtime.config.McpBuildTimeConfiguration;
import io.quarkiverse.langchain4j.mcp.runtime.config.McpClientBuildTimeConfig;
import io.quarkiverse.langchain4j.mcp.runtime.config.McpRuntimeConfiguration;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.IndexDependencyBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;

public class McpProcessor {

    private static final DotName MCP_CLIENT = DotName.createSimple(McpClient.class);
    private static final DotName MCP_CLIENT_NAME = DotName.createSimple(McpClientName.class);
    private static final DotName TOOL_PROVIDER = DotName.createSimple(ToolProvider.class);

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public void registerMcpClients(McpBuildTimeConfiguration mcpBuildTimeConfiguration,
            McpRuntimeConfiguration mcpRuntimeConfiguration,
            BuildProducer<SyntheticBeanBuildItem> beanProducer,
            McpRecorder recorder) {
        if (mcpBuildTimeConfiguration.clients() != null && !mcpBuildTimeConfiguration.clients().isEmpty()) {
            // generate MCP clients
            List<AnnotationInstance> qualifiers = new ArrayList<>();
            for (Map.Entry<String, McpClientBuildTimeConfig> client : mcpBuildTimeConfiguration.clients()
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
                        .supplier(
                                recorder.mcpClientSupplier(client.getKey(), mcpBuildTimeConfiguration, mcpRuntimeConfiguration))
                        .done());
            }
            // generate a tool provider if configured to do so
            if (mcpBuildTimeConfiguration.generateToolProvider().orElse(true)) {
                Set<String> mcpClientNames = mcpBuildTimeConfiguration.clients().keySet();
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

    @BuildStep
    public void indexMcpClientDependency(BuildProducer<IndexDependencyBuildItem> index) {
        // this is needed for the 'reflectionRegistrations' build step to work
        index.produce(new IndexDependencyBuildItem("dev.langchain4j", "langchain4j-mcp"));
    }

    @BuildStep
    public void reflectionRegistrations(BuildProducer<ReflectiveClassBuildItem> reflectiveClass,
            CombinedIndexBuildItem indexBuildItem) {
        // register everything in the dev.langchain4j.mcp.client.protocol package
        String PROTOCOL_PACKAGE_PATTERN = "dev\\.langchain4j\\.mcp\\.client\\.protocol\\..+";
        IndexView index = indexBuildItem.getIndex();
        for (ClassInfo clazz : index.getKnownClasses()) {
            if (clazz.name().toString().matches(PROTOCOL_PACKAGE_PATTERN)) {
                reflectiveClass
                        .produce(ReflectiveClassBuildItem.builder(clazz.name().toString()).fields(true).methods(true).build());
            }
        }
    }

}
