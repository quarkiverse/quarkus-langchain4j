package io.quarkiverse.langchain4j.milvus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Function;

import org.jboss.logging.Logger;
import org.testcontainers.milvus.MilvusContainer;
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
public class MilvusDevServicesProcessor {

    private static final Logger log = Logger.getLogger(MilvusDevServicesProcessor.class);

    /**
     * Label to add to shared Dev Service for Milvus running in containers.
     * This allows other applications to discover the running service and use it instead of starting a new instance.
     */
    private static final String DEV_SERVICE_LABEL = "quarkus-dev-service-milvus";
    private static final String MILVUS_IMAGE_NAME = "docker.io/milvusdb/milvus";
    private static final int MILVUS_PORT = 19530;

    private static final ContainerLocator containerLocator = new ContainerLocator(DEV_SERVICE_LABEL, MILVUS_PORT);

    @BuildStep
    public DevServicesResultBuildItem startMilvusDevService(
            DockerStatusBuildItem dockerStatusBuildItem,
            LaunchModeBuildItem launchMode,
            MilvusBuildConfig milvusBuildConfig,
            List<DevServicesSharedNetworkBuildItem> devServicesSharedNetworkBuildItem,
            DevServicesConfig devServicesConfig) {
        var config = milvusBuildConfig.devservices();
        if (!config.enabled()) {
            log.debug("Not starting Dev Services for Milvus, as it has been disabled in the config.");
            return null;
        }
        if (!dockerStatusBuildItem.isContainerRuntimeAvailable()) {
            log.warn("Docker isn't working, please configure the Milvus server location.");
            return null;
        }
        boolean useSharedNetwork = !devServicesSharedNetworkBuildItem.isEmpty();

        Optional<ContainerAddress> located = containerLocator.locateContainer(config.serviceName(), config.shared(),
                launchMode.getLaunchMode());
        if (located.isPresent()) {
            ContainerAddress address = located.get();
            return DevServicesResultBuildItem.discovered()
                    .feature(MilvusProcessor.FEATURE)
                    .containerId(address.getId())
                    .config(Map.of("quarkus.langchain4j.milvus.host", address.getHost(),
                            "quarkus.langchain4j.milvus.port", String.valueOf(address.getPort())))
                    .build();
        }

        Map<String, Function<StartableContainer<QuarkusMilvusContainer>, String>> configProvider = new HashMap<>();
        configProvider.put("quarkus.langchain4j.milvus.host",
                s -> s.getContainer().getHost());
        configProvider.put("quarkus.langchain4j.milvus.port",
                s -> String.valueOf(s.getContainer().getPort()));
        return DevServicesResultBuildItem.owned()
                .feature(MilvusProcessor.FEATURE)
                .serviceName(config.serviceName())
                .serviceConfig(config)
                .startable(() -> {
                    QuarkusMilvusContainer container = new QuarkusMilvusContainer(
                            config.milvusImageName(),
                            config.port(),
                            launchMode.getLaunchMode() == LaunchMode.DEVELOPMENT ? config.serviceName() : null,
                            useSharedNetwork);
                    devServicesConfig.timeout().ifPresent(container::withStartupTimeout);
                    return new StartableContainer<>(container, c -> c.getHost() + ":" + c.getPort());
                })
                .configProvider(configProvider)
                .build();
    }

    static class QuarkusMilvusContainer extends MilvusContainer {

        private final OptionalInt fixedExposedPort;
        private final boolean useSharedNetwork;
        private String hostName = null;

        public QuarkusMilvusContainer(String image,
                OptionalInt fixedExposedPort,
                String serviceName,
                boolean useSharedNetwork) {
            super(DockerImageName.parse(image).asCompatibleSubstituteFor("milvusdb/milvus"));
            if (serviceName != null) {
                withLabel(DEV_SERVICE_LABEL, serviceName);
            }
            this.fixedExposedPort = fixedExposedPort;
            this.useSharedNetwork = useSharedNetwork;
        }

        @Override
        protected void configure() {
            super.configure();

            if (useSharedNetwork) {
                hostName = ConfigureUtil.configureSharedNetwork(this, "milvus");
                return;
            }

            if (fixedExposedPort.isPresent()) {
                addFixedExposedPort(fixedExposedPort.getAsInt(), MILVUS_PORT);
            } else {
                addExposedPort(MILVUS_PORT);
            }
        }

        public int getPort() {
            if (useSharedNetwork) {
                return MILVUS_PORT;
            }

            if (fixedExposedPort.isPresent()) {
                return fixedExposedPort.getAsInt();
            }
            return super.getMappedPort(MILVUS_PORT);
        }

        @Override
        public String getHost() {
            return useSharedNetwork ? hostName : super.getHost();
        }

    }

}
