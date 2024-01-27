package io.quarkiverse.langchain4j.ollama.devservices;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.jboss.logging.Logger;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.LazyFuture;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Image;

import io.quarkus.bootstrap.classloading.QuarkusClassLoader;
import io.quarkus.deployment.IsNormal;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.BuildSteps;
import io.quarkus.deployment.builditem.*;
import io.quarkus.deployment.console.ConsoleInstalledBuildItem;
import io.quarkus.deployment.console.StartupLogCompressor;
import io.quarkus.deployment.dev.devservices.GlobalDevServicesConfig;
import io.quarkus.deployment.logging.LoggingSetupBuildItem;

/**
 * Starts a Ollama server as dev service if needed.
 */
@BuildSteps(onlyIfNot = IsNormal.class, onlyIf = GlobalDevServicesConfig.Enabled.class)
public class OllamaProcessor {

    private static final Logger log = Logger.getLogger(OllamaProcessor.class);

    public static final String FEATURE = "ollama";

    /**
     * Label to add to shared Dev Service for Ollama running in containers.
     * This allows other applications to discover the running service and use it instead of starting a new instance.
     */
    static final String DEV_SERVICE_LABEL = "quarkus-dev-service-ollama";

    static volatile DevServicesResultBuildItem.RunningDevService devService;
    static volatile OllamaConfig cfg;
    static volatile boolean first = true;

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    public DevServicesResultBuildItem startOllamaDevService(
            DockerStatusBuildItem dockerStatusBuildItem,
            LaunchModeBuildItem launchMode,
            OllamaConfig ollamaConfig,
            Optional<ConsoleInstalledBuildItem> consoleInstalledBuildItem,
            LoggingSetupBuildItem loggingSetupBuildItem,
            GlobalDevServicesConfig devServicesConfig,
            List<DevServicesSharedNetworkBuildItem> devServicesSharedNetworkBuildItem,
            BuildProducer<OllamaDevServicesConfigBuildItem> mailpitBuildItemBuildProducer) {

        if (devService != null) {
            boolean shouldShutdownTheBroker = !ollamaConfig.equals(cfg);
            if (!shouldShutdownTheBroker) {
                return devService.toBuildItem();
            }
            shutdown();
            cfg = null;
        }

        StartupLogCompressor compressor = new StartupLogCompressor(
                (launchMode.isTest() ? "(test) " : "") + "Ollama Dev Services Starting:",
                consoleInstalledBuildItem, loggingSetupBuildItem);
        try {
            devService = startOllama(dockerStatusBuildItem, ollamaConfig, devServicesConfig,
                    !devServicesSharedNetworkBuildItem.isEmpty());
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

        if (devService.isOwner()) {
            log.info("Dev Services for Ollama started.");
            mailpitBuildItemBuildProducer.produce(new OllamaDevServicesConfigBuildItem(devService.getConfig()));
        }

        // Configure the watch dog
        if (first) {
            first = false;
            Runnable closeTask = () -> {
                if (devService != null) {
                    shutdown();

                    log.info("Dev Services for Mailpit shut down.");
                }
                first = true;
                devService = null;
                cfg = null;
            };
            QuarkusClassLoader cl = (QuarkusClassLoader) Thread.currentThread().getContextClassLoader();
            ((QuarkusClassLoader) cl.parent()).addCloseTask(closeTask);
        }
        cfg = ollamaConfig;
        return devService.toBuildItem();
    }

    private DevServicesResultBuildItem.RunningDevService startOllama(DockerStatusBuildItem dockerStatusBuildItem,
            OllamaConfig ollamaConfig,
            GlobalDevServicesConfig devServicesConfig,
            boolean useSharedNetwork) {
        if (!ollamaConfig.enabled()) {
            // explicitly disabled
            log.warn("Not starting dev services for Ollama, as it has been disabled in the config.");
            return null;
        }

        if (!dockerStatusBuildItem.isDockerAvailable()) {
            log.warn("Docker isn't working, not starting dev services for Ollama.");
            return null;
        }

        String localOllamaImage = String.format("tc-%s-orca-mini", ollamaConfig.imageName());

        final OllamaImage ollamaImage = new OllamaImage(ollamaConfig.imageName(), localOllamaImage);
        OllamaContainer ollama = new OllamaContainer(ollamaConfig, localOllamaImage, useSharedNetwork, ollamaImage);
        ollama.start();
        createImage(ollama, localOllamaImage);

        return new DevServicesResultBuildItem.RunningDevService(FEATURE,
                ollama.getContainerId(),
                ollama::close,
                ollama.getExposedConfig());

    }

    static void createImage(GenericContainer<?> container, String localImageName) {
        DockerImageName dockerImageName = DockerImageName.parse(container.getDockerImageName());
        if (!dockerImageName.equals(DockerImageName.parse(localImageName))) {
            DockerClient dockerClient = DockerClientFactory.instance().client();
            List<Image> images = dockerClient.listImagesCmd().withReferenceFilter(localImageName).exec();
            if (images.isEmpty()) {
                DockerImageName imageModel = DockerImageName.parse(localImageName);
                dockerClient.commitCmd(container.getContainerId())
                        .withRepository(imageModel.getUnversionedPart())
                        .withLabels(Collections.singletonMap("org.testcontainers.sessionId", ""))
                        .withTag(imageModel.getVersionPart())
                        .exec();
            }
        }
    }

    private void shutdown() {
        if (devService != null) {
            try {
                log.info("Dev Services for Ollama shutting down...");
                devService.close();
            } catch (Throwable e) {
                log.error("Failed to stop the Ollama server", e);
            } finally {
                devService = null;
            }
        }
    }

    static class OllamaImage extends LazyFuture<DockerImageName> {

        private final String baseImage;

        private final String localImageName;

        OllamaImage(String baseImage, String localImageName) {
            this.baseImage = baseImage;
            this.localImageName = localImageName;
        }

        @Override
        protected DockerImageName resolve() {
            DockerImageName dockerImageName = DockerImageName.parse(this.baseImage);
            DockerClient dockerClient = DockerClientFactory.instance().client();
            List<Image> images = dockerClient.listImagesCmd().withReferenceFilter(this.localImageName).exec();
            if (images.isEmpty()) {
                return dockerImageName;
            }
            return DockerImageName.parse(this.localImageName);
        }

    }
}
