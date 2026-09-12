package io.quarkiverse.langchain4j.weaviate.deployment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import org.jboss.logging.Logger;
import org.testcontainers.utility.DockerImageName;

import io.quarkus.deployment.IsNormal;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.BuildSteps;
import io.quarkus.deployment.builditem.DevServicesResultBuildItem;
import io.quarkus.deployment.builditem.DevServicesSharedNetworkBuildItem;
import io.quarkus.deployment.builditem.DockerStatusBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.dev.devservices.DevServicesConfig;
import io.quarkus.devservices.common.ContainerAddress;
import io.quarkus.devservices.common.ContainerLocator;
import io.quarkus.devservices.common.StartableContainer;
import io.quarkus.runtime.LaunchMode;

@BuildSteps(onlyIfNot = IsNormal.class, onlyIf = DevServicesConfig.Enabled.class)
public class WeaviateDevServicesProcessor {

    private static final Logger log = Logger.getLogger(WeaviateDevServicesProcessor.class);

    /**
     * Label to add to shared Dev Service for Weaviate running in containers.
     * This allows other applications to discover the running service and use it instead of starting a new instance.
     */
    private static final String DEV_SERVICE_LABEL = "quarkus-dev-service-weaviate";
    private static final String IMAGE_NAME = "cr.weaviate.io/semitechnologies/weaviate";
    private static final int WEAVIATE_PORT = 8080;

    private static final ContainerLocator containerLocator = new ContainerLocator(DEV_SERVICE_LABEL, WEAVIATE_PORT);

    @BuildStep
    public DevServicesResultBuildItem startWeaviateDevService(
            DockerStatusBuildItem dockerStatusBuildItem,
            LaunchModeBuildItem launchMode,
            WeaviateEmbeddingStoreBuildTimeConfig weaviateBuildConfig,
            List<DevServicesSharedNetworkBuildItem> devServicesSharedNetworkBuildItem,
            DevServicesConfig devServicesConfig) {
        var config = weaviateBuildConfig.devservices();
        Set<String> namedStoreNames = weaviateBuildConfig.namedConfig().keySet();
        if (!config.enabled()) {
            log.debug("Not starting Dev Services for Weaviate, as it has been disabled in the config.");
            return null;
        }
        if (!dockerStatusBuildItem.isContainerRuntimeAvailable()) {
            log.warn("Docker isn't working, please configure the Weaviate server location.");
            return null;
        }
        boolean useSharedNetwork = !devServicesSharedNetworkBuildItem.isEmpty();

        Optional<ContainerAddress> located = containerLocator.locateContainer(config.serviceName(), config.shared(),
                launchMode.getLaunchMode());
        if (located.isPresent()) {
            ContainerAddress address = located.get();
            return DevServicesResultBuildItem.discovered()
                    .feature(WeaviateProcessor.FEATURE)
                    .containerId(address.getId())
                    .config(configMap(address.getHost(), String.valueOf(address.getPort()), namedStoreNames))
                    .build();
        }

        Map<String, Function<StartableContainer<WeaviateContainer>, String>> configProvider = new HashMap<>();
        Function<StartableContainer<WeaviateContainer>, String> host = s -> s.getContainer().getHost();
        Function<StartableContainer<WeaviateContainer>, String> port = s -> String
                .valueOf(s.getContainer().getPort());
        configProvider.put("quarkus.langchain4j.weaviate.scheme", s -> "http");
        configProvider.put("quarkus.langchain4j.weaviate.host", host);
        configProvider.put("quarkus.langchain4j.weaviate.port", port);
        for (String namedStore : namedStoreNames) {
            configProvider.put("quarkus.langchain4j.weaviate." + namedStore + ".scheme", s -> "http");
            configProvider.put("quarkus.langchain4j.weaviate." + namedStore + ".host", host);
            configProvider.put("quarkus.langchain4j.weaviate." + namedStore + ".port", port);
        }
        return DevServicesResultBuildItem.owned()
                .feature(WeaviateProcessor.FEATURE)
                .serviceName(config.serviceName())
                .serviceConfig(config)
                .startable(() -> {
                    WeaviateContainer container = new WeaviateContainer(
                            DockerImageName.parse(config.imageName()).asCompatibleSubstituteFor(IMAGE_NAME),
                            config.port(),
                            launchMode.getLaunchMode() == LaunchMode.DEVELOPMENT ? config.serviceName() : null,
                            useSharedNetwork);
                    devServicesConfig.timeout().ifPresent(container::withStartupTimeout);
                    container.withEnv(config.containerEnv());
                    return new StartableContainer<>(container, c -> c.getHost() + ":" + c.getPort());
                })
                .configProvider(configProvider)
                .build();
    }

    private static Map<String, String> configMap(String host, String port, Set<String> namedStoreNames) {
        Map<String, String> configMap = new HashMap<>();
        configMap.put("quarkus.langchain4j.weaviate.scheme", "http");
        configMap.put("quarkus.langchain4j.weaviate.host", host);
        configMap.put("quarkus.langchain4j.weaviate.port", port);
        for (String namedStore : namedStoreNames) {
            configMap.put("quarkus.langchain4j.weaviate." + namedStore + ".scheme", "http");
            configMap.put("quarkus.langchain4j.weaviate." + namedStore + ".host", host);
            configMap.put("quarkus.langchain4j.weaviate." + namedStore + ".port", port);
        }
        return configMap;
    }
}
