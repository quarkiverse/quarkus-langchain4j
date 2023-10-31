package io.quarkiverse.langchain4j.chroma.deployment;

import java.io.Closeable;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Supplier;

import org.jboss.logging.Logger;
import org.testcontainers.containers.GenericContainer;
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
import io.quarkus.deployment.dev.devservices.GlobalDevServicesConfig;
import io.quarkus.deployment.logging.LoggingSetupBuildItem;
import io.quarkus.devservices.common.ConfigureUtil;
import io.quarkus.devservices.common.ContainerLocator;
import io.quarkus.runtime.LaunchMode;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
@BuildSteps(onlyIfNot = IsNormal.class, onlyIf = GlobalDevServicesConfig.Enabled.class)
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
    static volatile DevServicesResultBuildItem.RunningDevService devService;
    static volatile ChromaDevServiceCfg cfg;
    static volatile boolean first = true;

    @BuildStep
    public DevServicesResultBuildItem startChromaDevService(
            DockerStatusBuildItem dockerStatusBuildItem,
            LaunchModeBuildItem launchMode,
            ChromaBuildConfig chromaBuildConfig,
            Optional<ConsoleInstalledBuildItem> consoleInstalledBuildItem,
            List<DevServicesSharedNetworkBuildItem> devServicesSharedNetworkBuildItem,
            LoggingSetupBuildItem loggingSetupBuildItem,
            GlobalDevServicesConfig devServicesConfig) {

        ChromaDevServiceCfg configuration = getConfiguration(chromaBuildConfig);

        if (devService != null) {
            boolean shouldShutdownTheBroker = !configuration.equals(cfg);
            if (!shouldShutdownTheBroker) {
                return devService.toBuildItem();
            }
            shutdownContainer();
            cfg = null;
        }

        StartupLogCompressor compressor = new StartupLogCompressor(
                (launchMode.isTest() ? "(test) " : "") + "Chroma Dev Services Starting:", consoleInstalledBuildItem,
                loggingSetupBuildItem);
        try {
            DevServicesResultBuildItem.RunningDevService newDevService = startContainer(dockerStatusBuildItem, configuration,
                    launchMode,
                    !devServicesSharedNetworkBuildItem.isEmpty(), devServicesConfig.timeout);
            if (newDevService != null) {
                devService = newDevService;

                Map<String, String> config = devService.getConfig();
                if (devService.isOwner()) {
                    log.info("Dev Services for Chroma started.");
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

                    log.info("Dev Services for Chroma shut down.");
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
                log.error("Failed to stop the Chroma server", e);
            } finally {
                devService = null;
            }
        }
    }

    private DevServicesResultBuildItem.RunningDevService startContainer(DockerStatusBuildItem dockerStatusBuildItem,
            ChromaDevServiceCfg config, LaunchModeBuildItem launchMode,
            boolean useSharedNetwork, Optional<Duration> timeout) {
        if (!config.devServicesEnabled) {
            // explicitly disabled
            log.debug("Not starting Dev Services for Chroma, as it has been disabled in the config.");
            return null;
        }

        if (!dockerStatusBuildItem.isDockerAvailable()) {
            log.warn("Docker isn't working, please configure the Chroma server location.");
            return null;
        }

        ConfiguredChromaContainer container = new ConfiguredChromaContainer(
                DockerImageName.parse(config.imageName).asCompatibleSubstituteFor(IMAGE_NAME),
                config.fixedExposedPort,
                launchMode.getLaunchMode() == LaunchMode.DEVELOPMENT ? config.serviceName : null,
                useSharedNetwork);

        final Supplier<DevServicesResultBuildItem.RunningDevService> defaultChromaSupplier = () -> {

            // Starting the broker
            timeout.ifPresent(container::withStartupTimeout);
            container.withEnv(config.containerEnv);
            container.start();
            return getRunningDevService(
                    container.getContainerId(),
                    container::close,
                    container.getHost(),
                    container.getPort());
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
                .orElseGet(defaultChromaSupplier);
    }

    private DevServicesResultBuildItem.RunningDevService getRunningDevService(
            String containerId,
            Closeable closeable,
            String host,
            int port) {
        Map<String, String> configMap = Map.of("quarkus.langchain4j.chroma.url", "http://" + host + ":" + port);
        return new DevServicesResultBuildItem.RunningDevService(ChromaProcessor.FEATURE,
                containerId, closeable, configMap);
    }

    private ChromaDevServiceCfg getConfiguration(ChromaBuildConfig cfg) {
        return new ChromaDevServiceCfg(cfg.devservices());
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static final class ChromaDevServiceCfg {

        private final boolean devServicesEnabled;
        private final String imageName;
        private final OptionalInt fixedExposedPort;
        private final boolean shared;
        private final String serviceName;
        private final Map<String, String> containerEnv;

        public ChromaDevServiceCfg(ChromaBuildConfig.ChromaDevServicesBuildTimeConfig devServicesConfig) {
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
            ChromaDevServiceCfg that = (ChromaDevServiceCfg) o;
            return devServicesEnabled == that.devServicesEnabled && Objects.equals(imageName, that.imageName)
                    && Objects.equals(fixedExposedPort, that.fixedExposedPort)
                    && Objects.equals(containerEnv, that.containerEnv);
        }

        @Override
        public int hashCode() {
            return Objects.hash(devServicesEnabled, imageName, fixedExposedPort, containerEnv);
        }
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static class ConfiguredChromaContainer extends GenericContainer<ConfiguredChromaContainer> {
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
