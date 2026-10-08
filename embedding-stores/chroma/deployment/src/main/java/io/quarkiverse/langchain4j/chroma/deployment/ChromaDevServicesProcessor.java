package io.quarkiverse.langchain4j.chroma.deployment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Function;

import org.jboss.logging.Logger;
import org.testcontainers.chromadb.ChromaDBContainer;
import org.testcontainers.utility.DockerImageName;

import io.quarkus.deployment.IsNormal;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.BuildSteps;
import io.quarkus.deployment.builditem.DevServicesResultBuildItem;
import io.quarkus.deployment.builditem.DevServicesSharedNetworkBuildItem;
import io.quarkus.deployment.builditem.DockerStatusBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.dev.devservices.DevServicesConfig;
import io.quarkus.devservices.common.ConfigureUtil;
import io.quarkus.devservices.common.ContainerAddress;
import io.quarkus.devservices.common.ContainerLocator;
import io.quarkus.devservices.common.StartableContainer;
import io.quarkus.runtime.LaunchMode;

@BuildSteps(onlyIfNot = IsNormal.class, onlyIf = DevServicesConfig.Enabled.class)
public class ChromaDevServicesProcessor {

    private static final Logger log = Logger.getLogger(ChromaDevServicesProcessor.class);

    /**
     * Label to add to shared Dev Service for Chroma running in containers.
     * This allows other applications to discover the running service and use it instead of starting a new instance.
     */
    private static final String DEV_SERVICE_LABEL = "quarkus-dev-service-chroma";
    private static final String IMAGE_NAME = "ghcr.io/chroma-core/chroma";
    private static final int CHROMA_PORT = 8000;

    private static final ContainerLocator containerLocator = new ContainerLocator(DEV_SERVICE_LABEL, CHROMA_PORT);

    @BuildStep
    public DevServicesResultBuildItem startChromaDevService(
            DockerStatusBuildItem dockerStatusBuildItem,
            LaunchModeBuildItem launchMode,
            ChromaEmbeddingStoreBuildTimeConfig chromaBuildConfig,
            List<DevServicesSharedNetworkBuildItem> devServicesSharedNetworkBuildItem,
            DevServicesConfig devServicesConfig) {
        ChromaEmbeddingStoreBuildTimeConfig.ChromaDevServicesBuildTimeConfig config = chromaBuildConfig.devservices();
        Set<String> namedStoreNames = chromaBuildConfig.namedConfig().keySet();
        if (!config.enabled()) {
            log.debug("Not starting Dev Services for Chroma, as it has been disabled in the config.");
            return null;
        }
        if (!dockerStatusBuildItem.isContainerRuntimeAvailable()) {
            log.warn("Docker isn't working, please configure the Chroma server location.");
            return null;
        }
        boolean useSharedNetwork = !devServicesSharedNetworkBuildItem.isEmpty();

        Optional<ContainerAddress> located = containerLocator.locateContainer(config.serviceName(), config.shared(),
                launchMode.getLaunchMode());
        if (located.isPresent()) {
            ContainerAddress address = located.get();
            return DevServicesResultBuildItem.discovered()
                    .feature(ChromaProcessor.FEATURE)
                    .containerId(address.getId())
                    .config(configMap("http://" + address.getHost() + ":" + address.getPort(), namedStoreNames))
                    .build();
        }

        Map<String, Function<StartableContainer<ConfiguredChromaContainer>, String>> configProvider = new HashMap<>();
        for (String key : configMap("", namedStoreNames).keySet()) {
            configProvider.put(key, StartableContainer::getConnectionInfo);
        }
        return DevServicesResultBuildItem.owned()
                .feature(ChromaProcessor.FEATURE)
                .serviceName(config.serviceName())
                .serviceConfig(config)
                .startable(() -> {
                    ConfiguredChromaContainer container = new ConfiguredChromaContainer(
                            DockerImageName.parse(config.imageName()).asCompatibleSubstituteFor(IMAGE_NAME),
                            config.port(),
                            launchMode.getLaunchMode() == LaunchMode.DEVELOPMENT ? config.serviceName() : null,
                            useSharedNetwork);
                    devServicesConfig.timeout().ifPresent(container::withStartupTimeout);
                    container.withEnv(config.containerEnv());
                    return new StartableContainer<>(container, c -> "http://" + c.getHost() + ":" + c.getPort());
                })
                .configProvider(configProvider)
                .build();
    }

    private static Map<String, String> configMap(String chromaUrl, Set<String> namedStoreNames) {
        Map<String, String> configMap = new HashMap<>();
        configMap.put("quarkus.langchain4j.chroma.url", chromaUrl);
        for (String namedStore : namedStoreNames) {
            configMap.put("quarkus.langchain4j.chroma." + namedStore + ".url", chromaUrl);
        }
        return configMap;
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static class ConfiguredChromaContainer extends ChromaDBContainer {
        private final OptionalInt fixedExposedPort;
        private final boolean useSharedNetwork;

        private String hostName = null;

        public ConfiguredChromaContainer(DockerImageName dockerImageName,
                OptionalInt fixedExposedPort,
                String serviceName,
                boolean useSharedNetwork) {
            super(dockerImageName);
            this.fixedExposedPort = fixedExposedPort;
            this.useSharedNetwork = useSharedNetwork;

            if (serviceName != null) {
                withLabel(DEV_SERVICE_LABEL, serviceName);
            }
        }

        @Override
        protected void configure() {
            super.configure();

            if (useSharedNetwork) {
                hostName = ConfigureUtil.configureSharedNetwork(this, "chroma");
                return;
            }

            if (fixedExposedPort.isPresent()) {
                addFixedExposedPort(fixedExposedPort.getAsInt(), CHROMA_PORT);
            } else {
                addExposedPort(CHROMA_PORT);
            }
        }

        public int getPort() {
            if (useSharedNetwork) {
                return CHROMA_PORT;
            }

            if (fixedExposedPort.isPresent()) {
                return fixedExposedPort.getAsInt();
            }
            return super.getFirstMappedPort();
        }

        @Override
        public String getHost() {
            return useSharedNetwork ? hostName : super.getHost();
        }
    }
}
