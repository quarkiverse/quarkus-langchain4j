package io.quarkiverse.langchain4j.ollama.deployment.devservices;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.jboss.logging.Logger;

import io.quarkiverse.langchain4j.deployment.devservice.DevServicesOllamaProcessor;
import io.quarkiverse.langchain4j.deployment.devservice.Langchain4jDevServicesEnabled;
import io.quarkiverse.langchain4j.deployment.devservice.OllamaClient;
import io.quarkiverse.langchain4j.deployment.items.DevServicesChatModelRequiredBuildItem;
import io.quarkiverse.langchain4j.deployment.items.DevServicesEmbeddingModelRequiredBuildItem;
import io.quarkiverse.langchain4j.deployment.items.DevServicesModelRequired;
import io.quarkiverse.langchain4j.deployment.items.DevServicesOllamaConfigBuildItem;
import io.quarkiverse.langchain4j.ollama.deployment.LangChain4jOllamaOpenAiBuildConfig;
import io.quarkus.deployment.IsNormal;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.BuildSteps;
import io.quarkus.deployment.builditem.DevServicesResultBuildItem;
import io.quarkus.deployment.builditem.DevServicesSharedNetworkBuildItem;
import io.quarkus.deployment.builditem.DockerStatusBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.builditem.Startable;
import io.quarkus.deployment.console.ConsoleInstalledBuildItem;
import io.quarkus.deployment.dev.devservices.DevServicesConfig;
import io.quarkus.deployment.logging.LoggingSetupBuildItem;
import io.quarkus.devservices.common.StartableContainer;

/**
 * Starts an Ollama server as dev service if needed. The container is started lazily by Quarkus; the required
 * models are pulled in the post-start hook.
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
        OllamaDevServicesBuildConfig ollamaDevServicesBuildConfig = ollamaBuildConfig.devservices();
        Map<String, String> modelOptions = ollamaDevServicesBuildConfig.modelOptions();

        if (isOllamaClientRunning()) {
            log.infof("Not starting Ollama dev services container, as there is already an Ollama instance running on port %d",
                    OllamaContainer.DEFAULT_OLLAMA_PORT);
            ollamaDevServicesBuildItemBuildProducer.produce(new DevServicesOllamaConfigBuildItem(Map.of(), modelOptions));
            return;
        }

        var ollamaModels = new LinkedHashSet<DevServicesModelRequired>();
        devServicesChatModels.stream().filter(bi -> PROVIDER.equals(bi.getProvider())).forEach(ollamaModels::add);
        devServicesEmbeddingModels.stream().filter(bi -> PROVIDER.equals(bi.getProvider())).forEach(ollamaModels::add);
        if (ollamaModels.isEmpty()) {
            return;
        }
        if (!ollamaDevServicesBuildConfig.enabled()) {
            log.warn("Not starting dev services for Ollama, as it has been disabled in the config.");
            return;
        }
        if (!dockerStatusBuildItem.isContainerRuntimeAvailable()) {
            log.warn("Container runtime isn't working, not starting dev services for Ollama.");
            return;
        }
        boolean useSharedNetwork = !devServicesSharedNetworkBuildItem.isEmpty();

        // the endpoint for every model that asked for it, plus the container coordinates for the Dev UI
        Map<String, Function<StartableContainer<OllamaContainer>, String>> configProvider = new LinkedHashMap<>();
        for (DevServicesModelRequired model : ollamaModels) {
            configProvider.put(model.getBaseUrlProperty(), StartableContainer::getConnectionInfo);
        }
        for (String key : List.of(OllamaContainer.CONFIG_OLLAMA_PORT, OllamaContainer.CONFIG_OLLAMA_HTTP_SERVER,
                OllamaContainer.CONFIG_OLLAMA_ENDPOINT)) {
            configProvider.put(key, s -> s.getContainer().getExposedConfig().get(key));
        }
        for (Map.Entry<String, String> entry : modelOptions.entrySet()) {
            configProvider.put("quarkus.langchain4j.ollama.chat-model.model-options." + entry.getKey(),
                    s -> entry.getValue());
        }

        // tell the core not to pull models itself: the container does it once it is up
        ollamaDevServicesBuildItemBuildProducer.produce(new DevServicesOllamaConfigBuildItem(Map.of(), modelOptions, true));

        devServicesResultProducer.produce(DevServicesResultBuildItem.owned()
                .feature(FEATURE)
                .serviceName(PROVIDER)
                .serviceConfig(ollamaDevServicesBuildConfig)
                .startable(() -> new StartableContainer<>(new OllamaContainer(ollamaDevServicesBuildConfig, useSharedNetwork),
                        OllamaContainer::getEndpoint))
                .postStartHook(s -> {
                    OllamaContainer ollama = container(s);
                    log.info("Dev Services for Ollama started.");
                    if (!modelOptions.isEmpty()) {
                        log.infof("Applying model options from Dev Services: %s", modelOptions);
                    }
                    OllamaClient client = OllamaClient.create(new OllamaClient.Options(ollama.getHost(), ollama.getPort()));
                    DevServicesOllamaProcessor.pullMissingModels(client, ollamaModels, launchMode, consoleInstalledBuildItem,
                            loggingSetupBuildItem);
                })
                .configProvider(configProvider)
                .build());
    }

    @SuppressWarnings("unchecked")
    private static OllamaContainer container(Startable startable) {
        return ((StartableContainer<OllamaContainer>) startable).getContainer();
    }

    private boolean isOllamaClientRunning() {
        return OllamaClient.create(new OllamaClient.Options("localhost", OllamaContainer.DEFAULT_OLLAMA_PORT))
                .isRunning();
    }
}
