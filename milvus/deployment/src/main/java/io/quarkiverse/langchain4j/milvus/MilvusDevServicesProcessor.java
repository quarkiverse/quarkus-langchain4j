package io.quarkiverse.langchain4j.milvus;

import java.io.Closeable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Supplier;

import org.jboss.logging.Logger;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.utility.DockerImageName;

import io.quarkus.bootstrap.classloading.QuarkusClassLoader;
import io.quarkus.deployment.IsNormal;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.BuildSteps;
import io.quarkus.deployment.builditem.DevServicesResultBuildItem;
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
     * Label to add to shared Dev Service for Chroma running in containers.
     * This allows other applications to discover the running service and use it instead of starting a new instance.
     */
    private static final String DEV_SERVICE_LABEL = "quarkus-dev-service-milvus";

    private static final String ETCD_IMAGE_NAME = "docker.io/coreos/etcd";
    private static final String MINIO_IMAGE_NAME = "docker.io/minio/minio";
    private static final String MILVUS_IMAGE_NAME = "docker.io/milvusdb/milvus";

    private static final int MILVUS_PORT = 19530;
    private static final int MINIO_PORT = 9000;
    private static final int ETCD_PORT = 2379;

    private static final ContainerLocator containerLocator = new ContainerLocator(DEV_SERVICE_LABEL, MILVUS_PORT);
    static volatile DevServicesResultBuildItem.RunningDevService milvusDevService;
    static volatile DevServicesResultBuildItem.RunningDevService minioDevService;
    static volatile DevServicesResultBuildItem.RunningDevService etcdDevService;
    static volatile MilvusDevServiceCfg cfg;
    static volatile boolean first = true;

    @BuildStep
    public List<DevServicesResultBuildItem> startMilvusDevServices(
            DockerStatusBuildItem dockerStatusBuildItem,
            LaunchModeBuildItem launchMode,
            MilvusBuildConfig milvusBuildConfig,
            Optional<ConsoleInstalledBuildItem> consoleInstalledBuildItem,
            LoggingSetupBuildItem loggingSetupBuildItem,
            GlobalDevServicesConfig devServicesConfig) {

        List<DevServicesResultBuildItem> result = new ArrayList<>();
        MilvusDevServiceCfg configuration = getConfiguration(milvusBuildConfig);

        if (milvusDevService != null || etcdDevService != null || minioDevService != null) {
            boolean shouldShutdown = !configuration.equals(cfg);
            if (!shouldShutdown) {
                result.add(milvusDevService.toBuildItem());
                result.add(etcdDevService.toBuildItem());
                result.add(minioDevService.toBuildItem());
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
            DevServicesResultBuildItem.RunningDevService newEtcdDevService = startEtcdContainer(
                    dockerStatusBuildItem, configuration, launchMode,
                    devServicesConfig.timeout);
            if (newEtcdDevService != null) {
                etcdDevService = newEtcdDevService;
                if (etcdDevService.isOwner()) {
                    log.info("Dev Services instance of Etcd started.");
                }
            }
            if (etcdDevService == null) {
                compressor.closeAndDumpCaptured();
            } else {
                compressor.close();
            }
            DevServicesResultBuildItem.RunningDevService newMinioDevService = startMinioContainer(
                    dockerStatusBuildItem, configuration, launchMode,
                    devServicesConfig.timeout);
            if (newMinioDevService != null) {
                minioDevService = newMinioDevService;
                if (minioDevService.isOwner()) {
                    log.info("Dev Services instance of Minio started.");
                }
            }
            if (minioDevService == null) {
                compressor.closeAndDumpCaptured();
            } else {
                compressor.close();
            }
            DevServicesResultBuildItem.RunningDevService newMilvusDevService = startMilvusContainer(
                    dockerStatusBuildItem, configuration, launchMode,
                    devServicesConfig.timeout,
                    newMinioDevService.getConfig().get("minio-host"), newMinioDevService.getConfig().get("minio-port"),
                    newEtcdDevService.getConfig().get("etcd-host"), newEtcdDevService.getConfig().get("etcd-port"));
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

        if (milvusDevService == null || etcdDevService == null || minioDevService == null) {
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
        result.add(etcdDevService.toBuildItem());
        result.add(minioDevService.toBuildItem());
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
        if (etcdDevService != null) {
            try {
                etcdDevService.close();
            } catch (Throwable e) {
                log.error("Failed to stop the Etcd server", e);
            } finally {
                etcdDevService = null;
            }
        }
        if (minioDevService != null) {
            try {
                minioDevService.close();
            } catch (Throwable e) {
                log.error("Failed to stop the Minio server", e);
            } finally {
                minioDevService = null;
            }
        }
    }

    private DevServicesResultBuildItem.RunningDevService startMilvusContainer(DockerStatusBuildItem dockerStatusBuildItem,
            MilvusDevServiceCfg config, LaunchModeBuildItem launchMode,
            Optional<Duration> timeout, String minioHost, String minioPort, String etcdHost, String etcdPort) {
        if (!dockerStatusBuildItem.isDockerAvailable()) {
            log.warn("Docker isn't working, please configure the Milvus server location.");
            return null;
        }

        ConfiguredMilvusContainer container = new ConfiguredMilvusContainer(
                DockerImageName.parse(config.milvusImageName).asCompatibleSubstituteFor(MILVUS_IMAGE_NAME),
                config.fixedMilvusPort,
                launchMode.getLaunchMode() == LaunchMode.DEVELOPMENT ? config.serviceName : null);

        final Supplier<DevServicesResultBuildItem.RunningDevService> defaultMilvusSupplier = () -> {

            // Starting the broker
            timeout.ifPresent(container::withStartupTimeout);
            container.addEnv("ETCD_ENDPOINTS", etcdHost + ":" + etcdPort);
            container.addEnv("MINIO_ADDRESS", minioHost + ":" + minioPort);
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

    private DevServicesResultBuildItem.RunningDevService startMinioContainer(DockerStatusBuildItem dockerStatusBuildItem,
            MilvusDevServiceCfg config, LaunchModeBuildItem launchMode, Optional<Duration> timeout) {

        ConfiguredMinioContainer container = new ConfiguredMinioContainer(
                DockerImageName.parse(config.minioImageName).asCompatibleSubstituteFor(MINIO_IMAGE_NAME),
                launchMode.getLaunchMode() == LaunchMode.DEVELOPMENT ? config.serviceName : null);

        final Supplier<DevServicesResultBuildItem.RunningDevService> defaultMinioSupplier = () -> {

            // Starting the broker
            timeout.ifPresent(container::withStartupTimeout);
            container.start();
            return getRunningMinioDevService(
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
                .map(containerAddress -> getRunningMinioDevService(
                        containerAddress.getId(),
                        null,
                        containerAddress.getHost(),
                        containerAddress.getPort()))
                .orElseGet(defaultMinioSupplier);
    }

    private DevServicesResultBuildItem.RunningDevService startEtcdContainer(DockerStatusBuildItem dockerStatusBuildItem,
            MilvusDevServiceCfg config, LaunchModeBuildItem launchMode,
            Optional<Duration> timeout) {
        ConfiguredEtcdContainer container = new ConfiguredEtcdContainer(
                DockerImageName.parse(config.etcdImageName).asCompatibleSubstituteFor(ETCD_IMAGE_NAME),
                launchMode.getLaunchMode() == LaunchMode.DEVELOPMENT ? config.serviceName : null);

        final Supplier<DevServicesResultBuildItem.RunningDevService> defaultEtcdSupplier = () -> {

            timeout.ifPresent(container::withStartupTimeout);
            container.addEnv("ETCD_AUTO_COMPACTION_MODE", "revision");
            container.addEnv("ETCD_AUTO_COMPACTION_RETENTION", "1000");
            container.addEnv("ETCD_QUOTA_BACKEND_BYTES", "4294967296");
            container.addEnv("ETCD_SNAPSHOT_COUNT", "50000");
            container.setCommand("etcd", "-advertise-client-urls=http://127.0.0.1:2379",
                    "-listen-client-urls=http://0.0.0.0:2379",
                    "--data-dir=/etcd");
            container.start();
            return getRunningEtcdDevService(
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
                .map(containerAddress -> getRunningEtcdDevService(
                        containerAddress.getId(),
                        null,
                        containerAddress.getHost(),
                        containerAddress.getPort()))
                .orElseGet(defaultEtcdSupplier);
    }

    private DevServicesResultBuildItem.RunningDevService getRunningMilvusDevService(
            String containerId,
            Closeable closeable,
            String host,
            int port) {
        Map<String, String> configMap = Map.of("quarkus.langchain4j.milvus.host", "localhost",
                "quarkus.langchain4j.milvus.port", String.valueOf(port));
        return new DevServicesResultBuildItem.RunningDevService(MilvusProcessor.FEATURE,
                containerId, closeable, configMap);
    }

    private DevServicesResultBuildItem.RunningDevService getRunningMinioDevService(
            String containerId,
            Closeable closeable,
            String host,
            int port) {
        Map<String, String> configMap = new HashMap<>();
        configMap.put("minio-host", host);
        configMap.put("minio-port", String.valueOf(port));
        return new DevServicesResultBuildItem.RunningDevService(MilvusProcessor.FEATURE,
                containerId, closeable, configMap);
    }

    private DevServicesResultBuildItem.RunningDevService getRunningEtcdDevService(
            String containerId,
            Closeable closeable,
            String host,
            int port) {
        Map<String, String> configMap = new HashMap<>();
        configMap.put("etcd-host", host);
        configMap.put("etcd-port", String.valueOf(port));
        return new DevServicesResultBuildItem.RunningDevService(MilvusProcessor.FEATURE,
                containerId, closeable, configMap);
    }

    private MilvusDevServiceCfg getConfiguration(MilvusBuildConfig cfg) {
        return new MilvusDevServiceCfg(cfg.devservices());
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static final class MilvusDevServiceCfg {

        public OptionalInt fixedEtcdPort;
        private boolean devServicesEnabled;
        private OptionalInt fixedMilvusPort;
        private String milvusImageName;
        private String etcdImageName;
        private String minioImageName;
        private String serviceName;
        private boolean shared;

        public MilvusDevServiceCfg(MilvusBuildConfig.MilvusDevServicesBuildTimeConfig devservices) {
            this.devServicesEnabled = devservices.enabled();
            this.fixedMilvusPort = devservices.port();
            this.milvusImageName = devservices.milvusImageName();
            this.etcdImageName = devservices.etcdImageName();
            this.minioImageName = devservices.minioImageName();
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
                    Objects.equals(etcdImageName, that.etcdImageName) &&
                    Objects.equals(minioImageName, that.minioImageName) &&
                    Objects.equals(serviceName, that.serviceName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(devServicesEnabled, fixedMilvusPort, milvusImageName,
                    etcdImageName, minioImageName, serviceName, shared);
        }
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static class ConfiguredMilvusContainer extends GenericContainer<ConfiguredMilvusContainer> {
        private final OptionalInt fixedExposedPort;
        private String hostName = null;

        public ConfiguredMilvusContainer(DockerImageName dockerImageName,
                OptionalInt fixedExposedPort,
                String serviceName) {
            super(dockerImageName);
            this.fixedExposedPort = fixedExposedPort;
            if (serviceName != null) {
                withLabel(DEV_SERVICE_LABEL, serviceName);
            }
        }

        @Override
        protected void configure() {
            super.configure();
            this.setCommand("milvus", "run", "standalone");
            setWaitStrategy(new LogMessageWaitStrategy().withRegEx(".*QueryNode successfully started.*\\s"));
            hostName = ConfigureUtil.configureSharedNetwork(this, "milvus");
            if (fixedExposedPort.isPresent()) {
                addFixedExposedPort(fixedExposedPort.getAsInt(), MILVUS_PORT);
            } else {
                addExposedPort(MILVUS_PORT);
            }
        }

        public int getPort() {
            if (fixedExposedPort.isPresent()) {
                return fixedExposedPort.getAsInt();
            }
            return super.getMappedPort(MILVUS_PORT);
        }

        @Override
        public String getHost() {
            return hostName;
        }

    }

    private static class ConfiguredMinioContainer extends GenericContainer<ConfiguredMinioContainer> {

        private String hostName = null;

        public ConfiguredMinioContainer(DockerImageName dockerImageName,
                String serviceName) {
            super(dockerImageName);
            if (serviceName != null) {
                withLabel(DEV_SERVICE_LABEL, serviceName);
            }
        }

        @Override
        protected void configure() {
            super.configure();
            this.setCommand("server", "--console-address", ":9001", "/data");
            hostName = ConfigureUtil.configureSharedNetwork(this, "minio");
        }

        public int getPort() {
            return MINIO_PORT;
        }

        @Override
        public String getHost() {
            return hostName;
        }
    }

    private static class ConfiguredEtcdContainer extends GenericContainer<ConfiguredEtcdContainer> {

        private String hostName = null;

        public ConfiguredEtcdContainer(DockerImageName dockerImageName,
                String serviceName) {
            super(dockerImageName);
            if (serviceName != null) {
                withLabel(DEV_SERVICE_LABEL, serviceName);
            }
        }

        @Override
        protected void configure() {
            super.configure();
            hostName = ConfigureUtil.configureSharedNetwork(this, "etcd");
        }

        public int getPort() {
            return ETCD_PORT;
        }

        @Override
        public String getHost() {
            return hostName;
        }
    }
}
