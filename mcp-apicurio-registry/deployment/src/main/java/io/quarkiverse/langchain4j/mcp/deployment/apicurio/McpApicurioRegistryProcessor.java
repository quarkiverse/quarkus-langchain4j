package io.quarkiverse.langchain4j.mcp.deployment.apicurio;

import java.util.Collections;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.ParameterizedType;
import org.jboss.jandex.Type;
import org.jboss.logging.Logger;

import io.opentelemetry.api.trace.Tracer;
import io.quarkiverse.langchain4j.deployment.DotNames;
import io.quarkiverse.langchain4j.deployment.LangChain4jDotNames;
import io.quarkiverse.langchain4j.mcp.runtime.McpRecorder;
import io.quarkiverse.langchain4j.mcp.runtime.apicurio.McpApicurioRegistryRecorder;
import io.quarkiverse.langchain4j.mcp.runtime.apicurio.config.McpApicurioRegistryBuildTimeConfig;
import io.quarkiverse.langchain4j.mcp.runtime.config.McpBuildTimeConfiguration;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.vertx.core.deployment.CoreVertxBuildItem;

public class McpApicurioRegistryProcessor {

    private static final Logger log = Logger.getLogger(McpApicurioRegistryProcessor.class);

    private static final DotName APICURIO_REGISTRY_MCP_TOOLS = DotName
            .createSimple("io.quarkiverse.langchain4j.mcp.runtime.apicurio.ApicurioRegistryMcpTools");
    private static final DotName TRACER = DotName.createSimple(Tracer.class);

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public void registerApicurioRegistryMcpTools(
            McpApicurioRegistryBuildTimeConfig buildTimeConfig,
            McpBuildTimeConfiguration mcpBuildTimeConfiguration,
            BuildProducer<SyntheticBeanBuildItem> beanProducer,
            CoreVertxBuildItem vertxBuildItem,
            McpApicurioRegistryRecorder recorder,
            McpRecorder mcpRecorder) {
        if (!buildTimeConfig.enabled()) {
            return;
        }

        // Ensure an McpToolProvider exists so that dynamically discovered MCP clients
        // can be added to it at runtime. This is a defaultBean, so if the MCP processor
        // already created one (when static MCP clients are configured), ours is ignored.
        if (mcpBuildTimeConfiguration.generateToolProvider().orElse(true)) {
            SyntheticBeanBuildItem.ExtendedBeanConfigurator configurator = SyntheticBeanBuildItem
                    .configure(LangChain4jDotNames.TOOL_PROVIDER)
                    .setRuntimeInit()
                    .defaultBean()
                    .unremovable()
                    .scope(ApplicationScoped.class)
                    .addInjectionPoint(ParameterizedType.create(DotNames.CDI_INSTANCE,
                            new Type[] { ClassType.create(TRACER) }, null))
                    .createWith(mcpRecorder.toolProviderFunction(Collections.emptySet()));
            beanProducer.produce(configurator.done());
        }

        log.info("Apicurio Registry MCP integration enabled, registering ApicurioRegistryMcpTools bean");
        beanProducer.produce(SyntheticBeanBuildItem
                .configure(APICURIO_REGISTRY_MCP_TOOLS)
                .setRuntimeInit()
                .defaultBean()
                .unremovable()
                .scope(ApplicationScoped.class)
                .addInjectionPoint(ClassType.create(LangChain4jDotNames.TOOL_PROVIDER))
                .createWith(recorder.apicurioRegistryMcpToolsFunction(vertxBuildItem.getVertx()))
                .done());
    }

    @BuildStep
    public void reflectionRegistrations(
            McpApicurioRegistryBuildTimeConfig buildTimeConfig,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClass) {
        if (!buildTimeConfig.enabled()) {
            return;
        }
        // Register Apicurio SDK model classes used for artifact metadata and search results
        reflectiveClass.produce(ReflectiveClassBuildItem.builder(
                "io.apicurio.registry.rest.client.models.ArtifactMetaData")
                .fields(true).methods(true).build());
    }
}
