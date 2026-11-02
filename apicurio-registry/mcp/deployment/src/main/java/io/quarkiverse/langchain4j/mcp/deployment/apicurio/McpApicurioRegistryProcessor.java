package io.quarkiverse.langchain4j.mcp.deployment.apicurio;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.logging.Logger;

import io.quarkiverse.langchain4j.deployment.LangChain4jDotNames;
import io.quarkiverse.langchain4j.mcp.deployment.AlwaysCreateMcpToolProviderBuildItem;
import io.quarkiverse.langchain4j.mcp.runtime.apicurio.McpApicurioRegistryRecorder;
import io.quarkiverse.langchain4j.mcp.runtime.apicurio.config.McpApicurioRegistryBuildTimeConfig;
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

    @BuildStep
    public void requestToolProvider(
            McpApicurioRegistryBuildTimeConfig buildTimeConfig,
            BuildProducer<AlwaysCreateMcpToolProviderBuildItem> producer) {
        if (buildTimeConfig.enabled()) {
            producer.produce(new AlwaysCreateMcpToolProviderBuildItem());
        }
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public void registerApicurioRegistryMcpTools(
            McpApicurioRegistryBuildTimeConfig buildTimeConfig,
            BuildProducer<SyntheticBeanBuildItem> beanProducer,
            CoreVertxBuildItem vertxBuildItem,
            McpApicurioRegistryRecorder recorder) {
        if (!buildTimeConfig.enabled()) {
            return;
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
        reflectiveClass.produce(ReflectiveClassBuildItem.builder(
                "io.apicurio.registry.rest.client.models.ArtifactMetaData")
                .fields(true).methods(true).build());
    }
}
