package io.quarkiverse.langchain4j.ollama.deployment.devservices;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import org.jboss.logging.Logger;

import io.quarkiverse.langchain4j.deployment.devservice.Langchain4jDevServicesEnabled;
import io.quarkiverse.langchain4j.deployment.devservice.OllamaClient;
import io.quarkiverse.langchain4j.deployment.items.DevServicesChatModelRequiredBuildItem;
import io.quarkiverse.langchain4j.deployment.items.DevServicesEmbeddingModelRequiredBuildItem;
import io.quarkiverse.langchain4j.deployment.items.DevServicesOllamaConfigBuildItem;
import io.quarkiverse.langchain4j.ollama.deployment.LangChain4jOllamaOpenAiBuildConfig;
import io.quarkus.bootstrap.classloading.QuarkusClassLoader;
import io.quarkus.deployment.IsNormal;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.BuildSteps;
import io.quarkus.deployment.builditem.DevServicesResultBuildItem;
import io.quarkus.deployment.builditem.DevServicesSharedNetworkBuildItem;
import io.quarkus.deployment.builditem.DockerStatusBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.console.ConsoleInstalledBuildItem;
import io.quarkus.deployment.console.StartupLogCompressor;
import io.quarkus.deployment.dev.devservices.DevServicesConfig;
import io.quarkus.deployment.logging.LoggingSetupBuildItem;

/**
 * Starts a Ollama server as dev service if needed.
 */
@BuildSteps(onlyIfNot = IsNormal.class, onlyIf = DevServicesConfig.Enabled.class)
public class OllamaDevServicesProcessor {
    private static final Logger log = Logger.getLogger(OllamaDevServicesProcessor.class);

    public static final String FEATURE = "langchain4j-ollama-dev-service";
    public static final String PROVIDER = "ollama";

    /**
     * Label to add to shared Dev Service for Ollama running in containers.
     * This allows other applications to discover the running service and use it instead of starting a new instance.
     */
    static final String DEV_SERVICE_LABEL = "quarkus-dev-service-ollama";

    static volatile DevServicesResultBuildItem.RunningDevService devService;
    static volatile OllamaDevServicesBuildConfig cfg;
    static volatile boolean first = true;

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep(onlyIfNot = IsNormal.class, onlyIf = Langchain4jDevServicesEnabled.class)
    public void startOllamaDevService(
            DockerStatusBuildItem dockerStatusBuildItem,
            LaunchModeBuildItem launchMode,
            LangChain4jOllamaOpenAiBuildConfig ollamaBuildConfig,
            Optional<ConsoleInstalledBuildItem> consoleInstalledBuildItem,
            LoggingSetupBuildItem loggingSetupBuildItem,
            List<DevServicesSharedNetworkBuildItem> devServicesSharedNetworkBuildItem,
            List<DevServicesChatModelRequiredBuildItem> devServicesChatModels,
            List<DevServicesEmbeddingModelRequiredBuildItem> devServicesEmbeddingModels,
            BuildProducer<DevServicesOllamaConfigBuildItem> ollamaDevServicesBuildItemBuildProducer,
            BuildProducer<DevServicesResultBuildItem> devServicesResultProducer) {

        var ollamaDevServicesBuildConfig = ollamaBuildConfig.devservices();

        if (devService != null) {
            boolean shouldShutdownTheBroker = !ollamaDevServicesBuildConfig.equals(cfg);
            if (!shouldShutdownTheBroker) {
                devServicesResultProducer.produce(devService.toBuildItem());
            }
            shutdown();
            cfg = null;
        }

        if (isOllamaClientRunning()) {
            log.infof("Not starting Ollama dev services container, as there is already an Ollama instance running on port %d",
                    OllamaContainer.DEFAULT_OLLAMA_PORT);
            return;
        }

        var modelBaseUrlKeys = new LinkedHashSet<String>();
        devServicesChatModels.stream()
                .map(DevServicesChatModelRequiredBuildItem::getBaseUrlProperty)
                .forEach(modelBaseUrlKeys::add);

        devServicesEmbeddingModels.stream()
                .map(DevServicesEmbeddingModelRequiredBuildItem::getBaseUrlProperty)
                .forEach(modelBaseUrlKeys::add);

        if (!modelBaseUrlKeys.isEmpty()) {
            var compressor = new StartupLogCompressor((launchMode.isTest() ? "(test) "
                    : "") + "Ollama Dev Services Starting:", consoleInstalledBuildItem, loggingSetupBuildItem);
            try {
                devService = startOllama(dockerStatusBuildItem, ollamaDevServicesBuildConfig,
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

            if (devService != null) {
                // Update the config for all of the configured models to the container endpoint
                var devServiceConfig = new LinkedHashMap<>(devService.getConfig());
                modelBaseUrlKeys.forEach(baseUrlKey -> devServiceConfig.put(baseUrlKey,
                        devServiceConfig.get(OllamaContainer.CONFIG_OLLAMA_ENDPOINT)));

                if (devService.isOwner()) {
                    log.info("Dev Services for Ollama started.");
                    ollamaDevServicesBuildItemBuildProducer.produce(new DevServicesOllamaConfigBuildItem(devServiceConfig));
                }

                // Configure the watch dog
                if (first) {
                    first = false;
                    Runnable closeTask = () -> {
                        if (devService != null) {
                            shutdown();

                            log.info("Dev Services for Ollama shut down.");
                        }
                        first = true;
                        devService = null;
                        cfg = null;
                    };
                    QuarkusClassLoader cl = (QuarkusClassLoader) Thread.currentThread()
                            .getContextClassLoader();
                    ((QuarkusClassLoader) cl.parent()).addCloseTask(closeTask);
                }
                cfg = ollamaDevServicesBuildConfig;
                devServicesResultProducer.produce(new DevServicesResultBuildItem(devService.getName(),
                        devService.getDescription(), devService.getContainerId(), devServiceConfig));
            }
        }
    }

    private boolean isOllamaClientRunning() {
        return OllamaClient.create(new OllamaClient.Options("localhost", OllamaContainer.DEFAULT_OLLAMA_PORT))
                .isRunning();
    }

    private DevServicesResultBuildItem.RunningDevService startOllama(DockerStatusBuildItem dockerStatusBuildItem,
            OllamaDevServicesBuildConfig ollamaDevServicesBuildConfig,
            boolean useSharedNetwork) {

        if (!ollamaDevServicesBuildConfig.enabled()) {
            // explicitly disabled
            log.warn("Not starting dev services for Ollama, as it has been disabled in the config.");
            return null;
        }

        if (!dockerStatusBuildItem.isContainerRuntimeAvailable()) {
            log.warn("Container runtime isn't working, not starting dev services for Ollama.");
            return null;
        }

        OllamaContainer ollama = new OllamaContainer(ollamaDevServicesBuildConfig, useSharedNetwork);
        ollama.start();

        return new DevServicesResultBuildItem.RunningDevService(PROVIDER,
                ollama.getContainerId(),
                ollama::close,
                ollama.getExposedConfig());
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
}
