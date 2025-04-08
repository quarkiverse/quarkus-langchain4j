package io.quarkiverse.langchain4j.mcp.deployment;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.tool.ToolProvider;
import io.quarkiverse.langchain4j.mcp.runtime.McpClientName;
import io.quarkiverse.langchain4j.mcp.runtime.McpRecorder;
import io.quarkiverse.langchain4j.mcp.runtime.config.LocalLaunchParams;
import io.quarkiverse.langchain4j.mcp.runtime.config.McpBuildTimeConfiguration;
import io.quarkiverse.langchain4j.mcp.runtime.config.McpRuntimeConfiguration;
import io.quarkiverse.langchain4j.mcp.runtime.config.McpTransportType;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.IndexDependencyBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class McpProcessor {

    private static final Logger log = Logger.getLogger(McpProcessor.class);

    private static final DotName MCP_CLIENT = DotName.createSimple(McpClient.class);
    private static final DotName MCP_CLIENT_NAME = DotName.createSimple(McpClientName.class);
    private static final DotName TOOL_PROVIDER = DotName.createSimple(ToolProvider.class);

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @BuildStep
    public void generateMcpConfigFileContents(McpBuildTimeConfiguration mcpBuildTimeConfiguration,
            BuildProducer<McpConfigFileContentsBuildItem> producer) {
        if (mcpBuildTimeConfiguration.configFile().isEmpty()) {
            return;
        }
        String configFileName = mcpBuildTimeConfiguration.configFile().get();
        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(
                configFileName)) {
            Map<String, LocalLaunchParams> claudeConfigContents = new HashMap<>();
            Map<String, Map<String, Object>> configFileAsMap = new ObjectMapper().readValue(is, new TypeReference<>() {
            });
            configFileAsMap.getOrDefault("mcpServers", Collections.emptyMap()).forEach((serverName, serverConfig) -> {
                if (serverConfig instanceof Map serverConfigMap) {
                    String command = (String) serverConfigMap.get("command");
                    if (command == null) {
                        log.warnf(
                                "The configured MCP Config file '%s' contains an MCP server named %s that does not contain a command and will therefore be ignored.",
                                configFileName, serverConfig);
                    } else {
                        List<String> commandAsList = new ArrayList<>();
                        commandAsList.add(command);
                        Object args = serverConfigMap.get("args");
                        if (args instanceof List argsList) {
                            commandAsList.addAll(argsList);
                        }
                        Map<String, String> environment = new HashMap<>();
                        Object env = serverConfigMap.get("env");
                        if (env instanceof Map envMap) {
                            environment.putAll(envMap);
                        }
                        claudeConfigContents.put(serverName, new LocalLaunchParams(commandAsList, environment));
                    }
                } else {
                    log.warnf(
                            "The configured MCP Config file '%s' contains the 'mcpServers' key that is not a JSON object so it will be ignored.",
                            configFileName);
                }
            });
            producer.produce(new McpConfigFileContentsBuildItem(claudeConfigContents));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    public void recordConfigFile(Optional<McpConfigFileContentsBuildItem> maybeMcpConfigFileContents, McpRecorder recorder) {
        if (maybeMcpConfigFileContents.isEmpty()) {
            return;
        }
        recorder.claudeConfigContents(maybeMcpConfigFileContents.get().getContents());
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public void registerMcpClients(McpBuildTimeConfiguration mcpBuildTimeConfiguration,
            McpRuntimeConfiguration mcpRuntimeConfiguration,
            Optional<McpConfigFileContentsBuildItem> maybeMcpConfigFileContents,
            BuildProducer<SyntheticBeanBuildItem> beanProducer,
            McpRecorder recorder) {
        Map<String, McpTransportType> clients = new HashMap<>();
        if (mcpBuildTimeConfiguration.clients() != null && !mcpBuildTimeConfiguration.clients().isEmpty()) {
            mcpBuildTimeConfiguration.clients().forEach((name, config) -> clients.put(name, config.transportType()));
        }
        if (maybeMcpConfigFileContents.isPresent()) {
            maybeMcpConfigFileContents.get().getContents().keySet().forEach(name -> {
                clients.put(name, McpTransportType.STDIO);
            });
        }
        if (!clients.isEmpty()) {
            // generate MCP clients
            List<AnnotationInstance> qualifiers = new ArrayList<>();
            clients.forEach((client, transportType) -> {
                AnnotationInstance qualifier = AnnotationInstance.builder(MCP_CLIENT_NAME)
                        .add("value", client)
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
                                recorder.mcpClientSupplier(client, transportType))
                        .done());
            });
            // generate a tool provider if configured to do so
            if (mcpBuildTimeConfiguration.generateToolProvider().orElse(true)) {
                SyntheticBeanBuildItem.ExtendedBeanConfigurator configurator = SyntheticBeanBuildItem
                        .configure(TOOL_PROVIDER)
                        .addType(ClassType.create(TOOL_PROVIDER))
                        .setRuntimeInit()
                        .defaultBean()
                        .unremovable()
                        .scope(ApplicationScoped.class)
                        .createWith(recorder.toolProviderFunction(clients.keySet()));
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
