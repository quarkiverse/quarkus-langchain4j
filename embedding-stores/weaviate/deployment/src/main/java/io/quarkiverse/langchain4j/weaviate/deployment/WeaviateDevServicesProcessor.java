package io.quarkiverse.langchain4j.weaviate.deployment;

import java.io.Closeable;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Supplier;

import org.jboss.logging.Logger;
import org.testcontainers.utility.DockerImageName;

import io.quarkus.bootstrap.classloading.QuarkusClassLoader;
import io.quarkus.deployment.IsNormal;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.BuildSteps;
import io.quarkus.deployment.builditem.DevServicesResultBuildItem;
import io.quarkus.deployment.builditem.DevServicesSharedNetworkBuildItem;
import io.quarkus.deployment.builditem.DockerStatusBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.console.ConsoleInstalledBuildItem;
import io.quarkus.deployment.console.StartupLogCompressor;
import io.quarkus.deployment.dev.devservices.DevServicesConfig;
import io.quarkus.deployment.logging.LoggingSetupBuildItem;
import io.quarkus.devservices.common.ContainerLocator;
import io.quarkus.runtime.LaunchMode;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
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
    static volatile DevServicesResultBuildItem.RunningDevService devService;
    static volatile WeaviateDevServiceCfg cfg;
    static volatile boolean first = true;

    @BuildStep
    public DevServicesResultBuildItem startWeaviateDevService(
            DockerStatusBuildItem dockerStatusBuildItem,
            LaunchModeBuildItem launchMode,
            WeaviateBuildConfig weaviateBuildConfig,
            Optional<ConsoleInstalledBuildItem> consoleInstalledBuildItem,
            List<DevServicesSharedNetworkBuildItem> devServicesSharedNetworkBuildItem,
            LoggingSetupBuildItem loggingSetupBuildItem,
            DevServicesConfig devServicesConfig) {

        WeaviateDevServiceCfg configuration = getConfiguration(weaviateBuildConfig);

        if (devService != null) {
            boolean shouldShutdownTheBroker = !configuration.equals(cfg);
            if (!shouldShutdownTheBroker) {
                return devService.toBuildItem();
            }
            shutdownContainer();
            cfg = null;
        }

        StartupLogCompressor compressor = new StartupLogCompressor(
                (launchMode.isTest() ? "(test) " : "") + "Weaviate Dev Services Starting:", consoleInstalledBuildItem,
                loggingSetupBuildItem);
        try {
            DevServicesResultBuildItem.RunningDevService newDevService = startContainer(dockerStatusBuildItem, configuration,
                    launchMode,
                    !devServicesSharedNetworkBuildItem.isEmpty(), devServicesConfig.timeout());
            if (newDevService != null) {
                devService = newDevService;

                Map<String, String> config = devService.getConfig();
                if (devService.isOwner()) {
                    log.info("Dev Services for Weaviate started.");
                }
            }
            if (devService == null) {
                compressor.closeAndDumpCaptured();
            } else {
                compressor.close();
            }
        } catch (Throwable t) {
            compressor.closeAndDumpCaptured();
            throw new RuntimeException(t);
        }

        if (devService == null) {
            return null;
        }

        // Configure the watch dog
        if (first) {
            first = false;
            Runnable closeTask = () -> {
                if (devService != null) {
                    shutdownContainer();

                    log.info("Dev Services for Weaviate shut down.");
                }
                first = true;
                devService = null;
                cfg = null;
            };
            QuarkusClassLoader cl = (QuarkusClassLoader) Thread.currentThread().getContextClassLoader();
            ((QuarkusClassLoader) cl.parent()).addCloseTask(closeTask);
        }
        cfg = configuration;
        return devService.toBuildItem();
    }

    private void shutdownContainer() {
        if (devService != null) {
            try {
                devService.close();
            } catch (Throwable e) {
                log.error("Failed to stop the Weaviate server", e);
            } finally {
                devService = null;
            }
        }
    }

    private DevServicesResultBuildItem.RunningDevService startContainer(DockerStatusBuildItem dockerStatusBuildItem,
            WeaviateDevServiceCfg config, LaunchModeBuildItem launchMode,
            boolean useSharedNetwork, Optional<Duration> timeout) {
        if (!config.devServicesEnabled) {
            // explicitly disabled
            log.debug("Not starting Dev Services for Weaviate, as it has been disabled in the config.");
            return null;
        }

        if (!dockerStatusBuildItem.isDockerAvailable()) {
            log.warn("Docker isn't working, please configure the Weaviate server location.");
            return null;
        }

        WeaviateContainer container = new WeaviateContainer(
                DockerImageName.parse(config.imageName).asCompatibleSubstituteFor(IMAGE_NAME),
                config.fixedExposedPort,
                launchMode.getLaunchMode() == LaunchMode.DEVELOPMENT ? config.serviceName : null,
                useSharedNetwork);

        final Supplier<DevServicesResultBuildItem.RunningDevService> defaultWeaviateSupplier = () -> {
            // Starting the broker
            timeout.ifPresent(container::withStartupTimeout);
            container.withEnv(config.containerEnv);
            container.start();
            return getRunningDevService(
                    container.getContainerId(),
                    container::close,
                    container.getHost(),
                    container.getMappedPort(8080));
        };

        return containerLocator
                .locateContainer(
                        config.serviceName,
                        config.shared,
                        launchMode.getLaunchMode())
                .map(containerAddress -> getRunningDevService(
                        containerAddress.getId(),
                        null,
                        containerAddress.getHost(),
                        containerAddress.getPort()))
                .orElseGet(defaultWeaviateSupplier);
    }

    private DevServicesResultBuildItem.RunningDevService getRunningDevService(
            String containerId,
            Closeable closeable,
            String host,
            int port) {

        Map<String, String> configMap = Map.of(
                "quarkus.langchain4j.weaviate.scheme", "http",
                "quarkus.langchain4j.weaviate.host", host,
                "quarkus.langchain4j.weaviate.port", String.valueOf(port));

        return new DevServicesResultBuildItem.RunningDevService(WeaviateProcessor.FEATURE,
                containerId, closeable, configMap);
    }

    private WeaviateDevServiceCfg getConfiguration(WeaviateBuildConfig cfg) {
        return new WeaviateDevServiceCfg(cfg.devservices());
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static final class WeaviateDevServiceCfg {

        private final boolean devServicesEnabled;
        private final String imageName;
        private final OptionalInt fixedExposedPort;
        private final boolean shared;
        private final String serviceName;
        private final Map<String, String> containerEnv;

        public WeaviateDevServiceCfg(WeaviateBuildConfig.WeaviateDevServicesBuildTimeConfig devServicesConfig) {
            this.devServicesEnabled = devServicesConfig.enabled();
            this.imageName = devServicesConfig.imageName();
            this.fixedExposedPort = devServicesConfig.port();
            this.shared = devServicesConfig.shared();
            this.serviceName = devServicesConfig.serviceName();
            this.containerEnv = devServicesConfig.containerEnv();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            WeaviateDevServiceCfg that = (WeaviateDevServiceCfg) o;
            return devServicesEnabled == that.devServicesEnabled && Objects.equals(imageName, that.imageName)
                    && Objects.equals(fixedExposedPort, that.fixedExposedPort)
                    && Objects.equals(containerEnv, that.containerEnv);
        }

        @Override
        public int hashCode() {
            return Objects.hash(devServicesEnabled, imageName, fixedExposedPort, containerEnv);
        }
    }
}
