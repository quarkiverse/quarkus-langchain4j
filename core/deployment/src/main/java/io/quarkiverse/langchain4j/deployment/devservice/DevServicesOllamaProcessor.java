package io.quarkiverse.langchain4j.deployment.devservice;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;

import io.quarkiverse.langchain4j.deployment.config.LangChain4jBuildConfig;
import io.quarkiverse.langchain4j.deployment.items.DevServicesChatModelRequiredBuildItem;
import io.quarkiverse.langchain4j.deployment.items.DevServicesEmbeddingModelRequiredBuildItem;
import io.quarkiverse.langchain4j.deployment.items.DevServicesModelRequired;
import io.quarkus.deployment.IsNormal;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.BuildSteps;
import io.quarkus.deployment.builditem.DevServicesResultBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.console.ConsoleInstalledBuildItem;
import io.quarkus.deployment.console.StartupLogCompressor;
import io.quarkus.deployment.logging.LoggingSetupBuildItem;

@BuildSteps(onlyIfNot = IsNormal.class)
public class DevServicesOllamaProcessor {

    private final static Logger LOGGER = Logger.getLogger(DevServicesOllamaProcessor.class);

    private static final String OLLAMA_PROVIDER = "ollama";

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    @BuildStep
    private void handleModels(List<DevServicesChatModelRequiredBuildItem> devServicesChatModels,
            List<DevServicesEmbeddingModelRequiredBuildItem> devServicesEmbeddingModels,
            LoggingSetupBuildItem loggingSetupBuildItem,
            Optional<ConsoleInstalledBuildItem> consoleInstalledBuildItem,
            LaunchModeBuildItem launchMode,
            LangChain4jBuildConfig config,
            BuildProducer<DevServicesResultBuildItem> producer) {
        if (devServicesChatModels.isEmpty() && devServicesEmbeddingModels.isEmpty()) {
            return;
        }

        var ollamaChatModels = devServicesChatModels.stream().filter(bi -> OLLAMA_PROVIDER.equals(bi.getProvider())).toList();
        var ollamaEmbeddingModels = devServicesEmbeddingModels.stream().filter(bi -> OLLAMA_PROVIDER.equals(bi.getProvider()))
                .toList();

        List<DevServicesModelRequired> allOllamaModels = new ArrayList<>();
        allOllamaModels.addAll(ollamaChatModels);
        allOllamaModels.addAll(ollamaEmbeddingModels);
        if (allOllamaModels.isEmpty()) {
            return;
        }

        OllamaClient client = OllamaClient.create(new OllamaClient.Options("localhost", config.devservices().port()));
        try {
            Set<ModelName> localModels = client.localModels().stream().map(mi -> ModelName.of(mi.name()))
                    .collect(Collectors.toSet());
            List<String> modelsToPull = new ArrayList<>(ollamaChatModels.size() + ollamaEmbeddingModels.size());
            for (var requiredModel : allOllamaModels) {
                if (localModels.contains(ModelName.of(requiredModel.getModelName()))) {
                    LOGGER.debug("Ollama already has model " + requiredModel.getModelName() + " pulled locally");
                } else {
                    modelsToPull.add(requiredModel.getModelName());
                }
            }
            LOGGER.debug("Need to pull the following models into Ollama server: " + String.join(", ", modelsToPull));

            AtomicReference<String> clientThreadName = new AtomicReference<>();
            StartupLogCompressor compressor = new StartupLogCompressor(
                    (launchMode.isTest() ? "(test) " : "") + "Ollama model pull:", consoleInstalledBuildItem,
                    loggingSetupBuildItem,
                    // ensure that the progress logging done on the async thread is also caught by the compressor
                    thread -> {
                        String t = clientThreadName.get();
                        if (t == null) {
                            return false;
                        }
                        return thread.getName().equals(t);
                    });
            for (String model : modelsToPull) {
                // we pull one model at a time and provide progress updates to the user via logging
                LOGGER.info("Pulling model " + model);
                AtomicReference<Long> LAST_UPDATE_REF = new AtomicReference<>();

                CompletableFuture<Void> cf = new CompletableFuture<>();
                client.pullAsync(model).subscribe(new Flow.Subscriber<>() {

                    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

                    @Override
                    public void onSubscribe(Flow.Subscription subscription) {
                        subscription.request(Long.MAX_VALUE);
                    }

                    @Override
                    public void onNext(OllamaClient.PullAsyncLine line) {
                        clientThreadName.compareAndSet(null, Thread.currentThread().getName());
                        if ((line.total() != null) && (line.completed() != null) && (line.status() != null)
                                && line.status().contains("pulling")) {
                            if (!logUpdate(LAST_UPDATE_REF.get())) {
                                return;
                            }

                            LAST_UPDATE_REF.set(System.nanoTime());
                            BigDecimal percentage = new BigDecimal(line.completed()).divide(new BigDecimal(line.total()), 4,
                                    RoundingMode.HALF_DOWN).multiply(ONE_HUNDRED);
                            BigDecimal progress = percentage.setScale(2, RoundingMode.HALF_DOWN);
                            if (progress.compareTo(ONE_HUNDRED) >= 0) {
                                // avoid showing 100% for too long
                                LOGGER.infof("Verifying and cleaning up\n", progress);
                            } else {
                                LOGGER.infof("Progress: %s%%\n", progress);
                            }
                        }
                    }

                    /**
                     * @param lastUpdate The last update time in nanoseconds
                     *        Determines whether we should log an update.
                     *        This is done in order to not overwhelm the console with updates which might make
                     *        canceling the download difficult. See
                     *        <a href="https://github.com/quarkiverse/quarkus-langchain4j/issues/1044">this</a>
                     */
                    private boolean logUpdate(Long lastUpdate) {
                        if (lastUpdate == null) {
                            return true;
                        } else {
                            return TimeUnit.NANOSECONDS.toMillis(System.nanoTime())
                                    - TimeUnit.NANOSECONDS.toMillis(lastUpdate) > 1_000;
                        }
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        cf.completeExceptionally(throwable);
                    }

                    @Override
                    public void onComplete() {
                        cf.complete(null);
                    }
                });

                try {
                    cf.get(5, TimeUnit.MINUTES);
                } catch (InterruptedException | TimeoutException | ExecutionException e) {
                    compressor.closeAndDumpCaptured();
                    throw new RuntimeException(e.getCause());
                }
            }

            // preload model - it only makes sense to load a single model
            if ((ollamaChatModels.size() == 1) && (config.devservices().preload())) {
                String modelName = ollamaChatModels.get(0).getModelName();
                LOGGER.infof("Preloading model %s", modelName);
                client.preloadChatModel(modelName);
            }

            compressor.close();

            String ollamaBaseUrl = String.format("http://localhost:%d", config.devservices().port());

            Map<String, String> modelBaseUrls = new HashMap<>();
            for (var bi : allOllamaModels) {
                modelBaseUrls.put(bi.getBaseUrlProperty(), ollamaBaseUrl);
            }
            producer.produce(new DevServicesResultBuildItem("ollama", null, modelBaseUrls));

        } catch (OllamaClient.ServerUnavailableException e) {
            LOGGER.warn(e.getMessage()
                    + " therefore no dev service will be started. Ollama can be installed via https://ollama.com/download");
            return;
        }
    }

    private record ModelName(String model, String tag) {

        public static ModelName of(String modelName) {
            Objects.requireNonNull(modelName, "modelName cannot be null");
            String[] parts = modelName.split(":");
            if (parts.length == 1) {
                return new ModelName(modelName, "latest");
            } else if (parts.length == 2) {
                return new ModelName(parts[0], parts[1]);
            } else {
                throw new IllegalArgumentException("Invalid model name: " + modelName);
            }
        }

    }
}
