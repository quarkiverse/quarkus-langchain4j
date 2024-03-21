package io.quarkiverse.langchain4j.milvus;

import java.io.Closeable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Supplier;

import org.jboss.logging.Logger;
import org.testcontainers.milvus.MilvusContainer;
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
import io.quarkus.runtime.configuration.ConfigUtils;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
@BuildSteps(onlyIfNot = IsNormal.class, onlyIf = GlobalDevServicesConfig.Enabled.class)
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
    static volatile DevServicesResultBuildItem.RunningDevService milvusDevService;
    static volatile MilvusDevServiceCfg cfg;
    static volatile boolean first = true;

    @BuildStep
    public List<DevServicesResultBuildItem> startMilvusDevServices(
            DockerStatusBuildItem dockerStatusBuildItem,
            LaunchModeBuildItem launchMode,
            MilvusBuildConfig milvusBuildConfig,
            Optional<ConsoleInstalledBuildItem> consoleInstalledBuildItem,
            List<DevServicesSharedNetworkBuildItem> devServicesSharedNetworkBuildItem,
            LoggingSetupBuildItem loggingSetupBuildItem,
            GlobalDevServicesConfig devServicesConfig) {

        List<DevServicesResultBuildItem> result = new ArrayList<>();
        MilvusDevServiceCfg configuration = getConfiguration(milvusBuildConfig);

        if (milvusDevService != null) {
            boolean shouldShutdown = !configuration.equals(cfg);
            if (!shouldShutdown) {
                result.add(milvusDevService.toBuildItem());
                return result;
            }
            shutdownContainers();
            cfg = null;
        }

        if (!milvusBuildConfig.devservices().enabled()) {
            // explicitly disabled
            log.debug("Not starting Dev Services for Milvus, as it has been disabled in the config.");
            return Collections.emptyList();
        }
        // if connection to Milvus was explicitly specified, don't start Dev Services
        if (ConfigUtils.isPropertyPresent("quarkus.langchain4j.milvus.host")) {
            return Collections.emptyList();
        }
        StartupLogCompressor compressor = new StartupLogCompressor(
                (launchMode.isTest() ? "(test) " : "") + "Milvus Dev Services Starting:", consoleInstalledBuildItem,
                loggingSetupBuildItem);
        try {
            DevServicesResultBuildItem.RunningDevService newMilvusDevService = startMilvusContainer(
                    dockerStatusBuildItem, configuration, launchMode,
                    devServicesConfig.timeout, !devServicesSharedNetworkBuildItem.isEmpty());
            if (newMilvusDevService != null) {
                milvusDevService = newMilvusDevService;
                if (milvusDevService.isOwner()) {
                    log.info("Dev Services instance of Milvus started.");
                }
            }
            if (milvusDevService == null) {
                compressor.closeAndDumpCaptured();
            } else {
                compressor.close();
            }
        } catch (Throwable t) {
            compressor.closeAndDumpCaptured();
            throw new RuntimeException(t);
        }

        if (milvusDevService == null) {
            return Collections.emptyList();
        }

        // Configure the watch dog
        if (first) {
            first = false;
            Runnable closeTask = () -> {
                shutdownContainers();
                first = true;
                cfg = null;
            };
            QuarkusClassLoader cl = (QuarkusClassLoader) Thread.currentThread().getContextClassLoader();
            ((QuarkusClassLoader) cl.parent()).addCloseTask(closeTask);
        }
        cfg = configuration;
        result.add(milvusDevService.toBuildItem());
        return result;
    }

    private void shutdownContainers() {
        if (milvusDevService != null) {
            try {
                milvusDevService.close();
            } catch (Throwable e) {
                log.error("Failed to stop the Milvus server", e);
            } finally {
                milvusDevService = null;
            }
        }
    }

    private DevServicesResultBuildItem.RunningDevService startMilvusContainer(DockerStatusBuildItem dockerStatusBuildItem,
            MilvusDevServiceCfg config, LaunchModeBuildItem launchMode,
            Optional<Duration> timeout,
            boolean useSharedNetwork) {
        if (!dockerStatusBuildItem.isDockerAvailable()) {
            log.warn("Docker isn't working, please configure the Milvus server location.");
            return null;
        }

        QuarkusMilvusContainer container = new QuarkusMilvusContainer(
                config.milvusImageName,
                config.fixedMilvusPort,
                launchMode.getLaunchMode() == LaunchMode.DEVELOPMENT ? config.serviceName : null,
                useSharedNetwork);

        final Supplier<DevServicesResultBuildItem.RunningDevService> defaultMilvusSupplier = () -> {

            // Starting Milvus
            timeout.ifPresent(container::withStartupTimeout);
            container.start();
            return getRunningMilvusDevService(
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
                .map(containerAddress -> getRunningMilvusDevService(
                        containerAddress.getId(),
                        null,
                        containerAddress.getHost(),
                        containerAddress.getPort()))
                .orElseGet(defaultMilvusSupplier);
    }

    private DevServicesResultBuildItem.RunningDevService getRunningMilvusDevService(
            String containerId,
            Closeable closeable,
            String host,
            int port) {
        Map<String, String> configMap = Map.of("quarkus.langchain4j.milvus.host", host,
                "quarkus.langchain4j.milvus.port", String.valueOf(port));
        return new DevServicesResultBuildItem.RunningDevService(MilvusProcessor.FEATURE,
                containerId, closeable, configMap);
    }

    private MilvusDevServiceCfg getConfiguration(MilvusBuildConfig cfg) {
        return new MilvusDevServiceCfg(cfg.devservices());
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static final class MilvusDevServiceCfg {

        private boolean devServicesEnabled;
        private OptionalInt fixedMilvusPort;
        private String milvusImageName;
        private String serviceName;
        private boolean shared;

        public MilvusDevServiceCfg(MilvusBuildConfig.MilvusDevServicesBuildTimeConfig devservices) {
            this.devServicesEnabled = devservices.enabled();
            this.fixedMilvusPort = devservices.port();
            this.milvusImageName = devservices.milvusImageName();
            this.serviceName = devservices.serviceName();
            this.shared = devservices.shared();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            MilvusDevServiceCfg that = (MilvusDevServiceCfg) o;
            return devServicesEnabled == that.devServicesEnabled &&
                    shared == that.shared &&
                    Objects.equals(fixedMilvusPort, that.fixedMilvusPort) &&
                    Objects.equals(milvusImageName, that.milvusImageName) &&
                    Objects.equals(serviceName, that.serviceName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(devServicesEnabled, fixedMilvusPort, milvusImageName,
                    serviceName, shared);
        }
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
