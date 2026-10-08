package io.quarkiverse.langchain4j.opensearch.deployment;

import static io.quarkus.devservices.common.ConfigureUtil.configureNetwork;
import static io.quarkus.devservices.common.ConfigureUtil.configureSharedServiceLabel;
import static io.quarkus.devservices.common.ContainerLocator.locateContainerWithLabels;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

import org.jboss.logging.Logger;
import org.opensearch.testcontainers.OpensearchContainer;
import org.testcontainers.utility.DockerImageName;

import io.quarkus.deployment.IsDevServicesSupportedByLaunchMode;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.BuildSteps;
import io.quarkus.deployment.builditem.DevServicesComposeProjectBuildItem;
import io.quarkus.deployment.builditem.DevServicesResultBuildItem;
import io.quarkus.deployment.builditem.DevServicesSharedNetworkBuildItem;
import io.quarkus.deployment.builditem.DockerStatusBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.builditem.Startable;
import io.quarkus.deployment.dev.devservices.DevServicesConfig;
import io.quarkus.devservices.common.ComposeLocator;
import io.quarkus.devservices.common.ContainerLocator;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.configuration.ConfigUtils;

@BuildSteps(onlyIf = { IsDevServicesSupportedByLaunchMode.class, DevServicesConfig.Enabled.class })
public class OpenSearchDevServicesProcessor {
    private static final Logger log = Logger.getLogger(OpenSearchDevServicesProcessor.class);

    private static final int OPENSEARCH_EXPOSED_PORT = 9200;
    private static final String OPENSEARCH_SCHEME = "http://";
    private static final String DEFAULT_SERVER_URL_PROPERTY = "quarkus.langchain4j.opensearch.server-url";

    /**
     * Label to add to shared Dev Service for OpenSearch running in containers.
     * This allows other applications to discover the running service and use it instead of starting a new instance.
     */
    private static final String DEV_SERVICE_LABEL = "quarkus-dev-service-opensearch";

    private static final ContainerLocator opensearchContainerLocator = locateContainerWithLabels(OPENSEARCH_EXPOSED_PORT,
            DEV_SERVICE_LABEL);

    @BuildStep
    public void startOpenSearchDevService(
            LaunchModeBuildItem launchMode,
            DockerStatusBuildItem dockerStatusBuildItem,
            DevServicesComposeProjectBuildItem composeProjectBuildItem,
            List<DevServicesSharedNetworkBuildItem> devServicesSharedNetworkBuildItem,
            OpenSearchEmbeddingStoreBuildTimeConfig config,
            BuildProducer<DevServicesResultBuildItem> devServicesResult,
            DevServicesConfig devServicesConfig) {

        boolean useSharedNetwork = DevServicesSharedNetworkBuildItem.isSharedNetworkRequired(devServicesConfig,
                devServicesSharedNetworkBuildItem);

        OpenSearchEmbeddingStoreBuildTimeConfig.OpenSearchDevServicesBuildTimeConfig devServicesCfg = config.devservices();
        Set<String> namedStoreNames = config.namedConfig().keySet();
        if (openSearchDevServicesDisabled(dockerStatusBuildItem, devServicesCfg, namedStoreNames)) {
            return;
        }

        DevServicesResultBuildItem discovered = discoverRunningService(composeProjectBuildItem, devServicesCfg,
                namedStoreNames, launchMode.getLaunchMode(), useSharedNetwork);
        if (discovered != null) {
            devServicesResult.produce(discovered);
        } else {
            Map<String, java.util.function.Function<DevServicesOpensearchContainer, String>> configProvider = new HashMap<>();
            for (String key : devServiceConfigKeys(namedStoreNames)) {
                configProvider.put(key, s -> OPENSEARCH_SCHEME + s.getConnectionInfo());
            }
            devServicesResult
                    .produce(DevServicesResultBuildItem.owned()
                            .feature(OpenSearchProcessor.FEATURE)
                            .serviceName(devServicesCfg.serviceName())
                            .serviceConfig(devServicesCfg)
                            .startable(() -> new DevServicesOpensearchContainer(
                                    DockerImageName.parse(devServicesCfg.imageName())
                                            .asCompatibleSubstituteFor("opensearchproject/opensearch"),
                                    devServicesCfg.port(),
                                    composeProjectBuildItem.getDefaultNetworkId(),
                                    useSharedNetwork)
                                    .withEnv(devServicesCfg.containerEnv())
                                    // Dev Service discovery works using a global dev service label applied in
                                    // DevServicesCustomizerBuildItem; for backwards compatibility we still add
                                    // the custom label
                                    .withSharedServiceLabel(launchMode.getLaunchMode(), devServicesCfg.serviceName()))
                            .configProvider(configProvider)
                            .build());
        }
    }

    /**
     * The container locator finds an external service (where applicable) at augmentation time; a compose project
     * member is used as a fallback. When neither matches, the owned container started through the startable supplier
     * takes over at runtime.
     */
    private DevServicesResultBuildItem discoverRunningService(DevServicesComposeProjectBuildItem composeProjectBuildItem,
            OpenSearchEmbeddingStoreBuildTimeConfig.OpenSearchDevServicesBuildTimeConfig devServicesConfig,
            Set<String> namedStoreNames,
            LaunchMode launchMode,
            boolean useSharedNetwork) {
        return opensearchContainerLocator.locateContainer(devServicesConfig.serviceName(), devServicesConfig.shared(),
                launchMode)
                .or(() -> ComposeLocator.locateContainer(composeProjectBuildItem,
                        List.of(devServicesConfig.imageName()),
                        OPENSEARCH_EXPOSED_PORT, launchMode, useSharedNetwork))
                .map(containerAddress -> {
                    String serverUrl = OPENSEARCH_SCHEME + containerAddress.getHost() + ":" + containerAddress.getPort();
                    return DevServicesResultBuildItem.discovered()
                            .feature(OpenSearchProcessor.FEATURE)
                            .containerId(containerAddress.getId())
                            .config(devServiceConfigMap(serverUrl, namedStoreNames))
                            .build();
                }).orElse(null);
    }

    private static List<String> devServiceConfigKeys(Set<String> namedStoreNames) {
        // A single dev service instance backs the default store and every named store: they are
        // indexes on one cluster, not separate clusters.
        List<String> keys = new java.util.ArrayList<>();
        keys.add(DEFAULT_SERVER_URL_PROPERTY);
        for (String namedStore : namedStoreNames) {
            keys.add("quarkus.langchain4j.opensearch." + namedStore + ".server-url");
        }
        return keys;
    }

    private static Map<String, String> devServiceConfigMap(String serverUrl, Set<String> namedStoreNames) {
        Map<String, String> configMap = new HashMap<>();
        for (String key : devServiceConfigKeys(namedStoreNames)) {
            configMap.put(key, serverUrl);
        }
        return Map.copyOf(configMap);
    }

    private static boolean openSearchDevServicesDisabled(DockerStatusBuildItem dockerStatusBuildItem,
            OpenSearchEmbeddingStoreBuildTimeConfig.OpenSearchDevServicesBuildTimeConfig devServicesConfig,
            Set<String> namedStoreNames) {
        if (!devServicesConfig.enabled()) {
            // explicitly disabled
            log.debug("Not starting Dev Services for OpenSearch as it has been disabled in the config");
            return true;
        }

        // TODO - We shouldn't query runtime config during deployment
        if (ConfigUtils.isPropertyNonEmpty(DEFAULT_SERVER_URL_PROPERTY) && namedStoreNames.stream()
                .allMatch(name -> ConfigUtils
                        .isPropertyNonEmpty("quarkus.langchain4j.opensearch." + name + ".server-url"))) {
            // every store connection is explicitly configured
            return true;
        }

        if (!dockerStatusBuildItem.isContainerRuntimeAvailable()) {
            log.warn("Please configure quarkus.langchain4j.opensearch.server-url or get a working docker instance");
            return true;
        }
        return false;
    }

    private static final class DevServicesOpensearchContainer extends OpensearchContainer<DevServicesOpensearchContainer>
            implements Startable {
        private final OptionalInt fixedExposedPort;
        private final boolean useSharedNetwork;

        private final String hostName;

        public DevServicesOpensearchContainer(DockerImageName dockerImageName, OptionalInt fixedExposedPort,
                String defaultNetworkId, boolean useSharedNetwork) {
            super(dockerImageName);
            this.fixedExposedPort = fixedExposedPort;
            this.useSharedNetwork = useSharedNetwork;

            this.hostName = configureNetwork(this, defaultNetworkId, useSharedNetwork, "opensearch");
        }

        public DevServicesOpensearchContainer withSharedServiceLabel(LaunchMode launchMode, String serviceName) {
            return configureSharedServiceLabel(this, launchMode, DEV_SERVICE_LABEL, serviceName);
        }

        @Override
        protected void configure() {
            super.configure();
            if (useSharedNetwork) {
                return;
            }

            if (fixedExposedPort.isPresent()) {
                addFixedExposedPort(fixedExposedPort.getAsInt(), OPENSEARCH_EXPOSED_PORT);
            }
        }

        private int getPort() {
            if (useSharedNetwork) {
                return OPENSEARCH_EXPOSED_PORT;
            }

            if (fixedExposedPort.isPresent()) {
                return fixedExposedPort.getAsInt();
            }
            return super.getFirstMappedPort();
        }

        @Override
        public String getConnectionInfo() {
            return (useSharedNetwork ? hostName : super.getHost()) + ":" + getPort();
        }

        @Override
        public void close() {
            super.close();
        }
    }
}
