package io.quarkiverse.langchain4j.gpullama3.deployment;

import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.CHAT_MODEL;
import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.STREAMING_CHAT_MODEL;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.logging.Logger;

import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.deployment.items.ChatModelProviderCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedChatModelProviderBuildItem;
import io.quarkiverse.langchain4j.gpullama3.GPULlama3ModelRegistry;
import io.quarkiverse.langchain4j.gpullama3.runtime.GPULlama3Recorder;
import io.quarkiverse.langchain4j.gpullama3.runtime.NameAndQuantization;
import io.quarkiverse.langchain4j.gpullama3.runtime.config.ChatModelFixedRuntimeConfig;
import io.quarkiverse.langchain4j.gpullama3.runtime.config.LangChain4jGPULlama3FixedRuntimeConfig;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.builder.item.MultiBuildItem;
import io.quarkus.deployment.annotations.*;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.builditem.ServiceStartBuildItem;
import io.quarkus.deployment.console.ConsoleInstalledBuildItem;
import io.quarkus.deployment.console.StartupLogCompressor;
import io.quarkus.deployment.logging.LoggingSetupBuildItem;

public class GPULlama3Processor {

    private final static Logger LOG = Logger.getLogger(GPULlama3Processor.class);

    private static final String PROVIDER = "gpu-llama3";
    private static final String FEATURE = "langchain4j-gpu-llama3";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    public void providerCandidates(BuildProducer<ChatModelProviderCandidateBuildItem> chatProducer,
            LangChain4jGPULlama3BuildTimeConfig config) {
        if (config.chatModel().enabled().isEmpty() || config.chatModel().enabled().get()) {
            chatProducer.produce(new ChatModelProviderCandidateBuildItem(PROVIDER));
        }
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void generateBeans(GPULlama3Recorder recorder,
            List<SelectedChatModelProviderBuildItem> selectedChatModels,
            BuildProducer<SyntheticBeanBuildItem> beanProducer) {

        for (var selected : selectedChatModels) {
            if (PROVIDER.equals(selected.getProvider())) {
                String configName = selected.getConfigName();

                var builder = SyntheticBeanBuildItem
                        .configure(CHAT_MODEL)
                        .setRuntimeInit()
                        .defaultBean()
                        .scope(ApplicationScoped.class)
                        .supplier(recorder.chatModel(configName));
                addQualifierIfNecessary(builder, configName);
                beanProducer.produce(builder.done());

                var streamingBuilder = SyntheticBeanBuildItem
                        .configure(STREAMING_CHAT_MODEL)
                        .setRuntimeInit()
                        .defaultBean()
                        .scope(ApplicationScoped.class)
                        .supplier(recorder.streamingChatModel(configName));
                addQualifierIfNecessary(streamingBuilder, configName);
                beanProducer.produce(streamingBuilder.done());
            }
        }
    }

    private void addQualifierIfNecessary(SyntheticBeanBuildItem.ExtendedBeanConfigurator builder, String configName) {
        if (!NamedConfigUtil.isDefault(configName)) {
            builder.addQualifier(AnnotationInstance.builder(ModelName.class).add("value", configName).build());
        }
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    @Produce(ServiceStartBuildItem.class)
    @BuildStep
    void downloadModels(List<SelectedChatModelProviderBuildItem> selectedChatModels,
            LoggingSetupBuildItem loggingSetupBuildItem,
            Optional<ConsoleInstalledBuildItem> consoleInstalledBuildItem,
            LaunchModeBuildItem launchMode,
            LangChain4jGPULlama3BuildTimeConfig buildTimeConfig,
            LangChain4jGPULlama3FixedRuntimeConfig fixedRuntimeConfig,
            BuildProducer<ModelDownloadedBuildItem> modelDownloadedProducer) {
        if (!buildTimeConfig.includeModelsInArtifact()) {
            return;
        }
        GPULlama3ModelRegistry registry = GPULlama3ModelRegistry.getOrCreate(fixedRuntimeConfig.modelsPath());

        BigDecimal ONE_HUNDRED = new BigDecimal("100");

        if (buildTimeConfig.chatModel().enabled().orElse(true)) {
            List<NameAndQuantization> modelsNeeded = new ArrayList<>();
            for (var selected : selectedChatModels) {
                if (PROVIDER.equals(selected.getProvider())) {
                    String configName = selected.getConfigName();

                    ChatModelFixedRuntimeConfig matchingConfig = NamedConfigUtil.isDefault(configName)
                            ? fixedRuntimeConfig.defaultConfig().chatModel()
                            : fixedRuntimeConfig.namedConfig().get(configName).chatModel();
                    modelsNeeded.add(new NameAndQuantization(matchingConfig.modelName(), matchingConfig.quantization()));
                }
            }

            if (!modelsNeeded.isEmpty()) {
                StartupLogCompressor compressor = new StartupLogCompressor(
                        (launchMode.isTest() ? "(test) " : "") + "GPULlama3.java model pull:",
                        consoleInstalledBuildItem,
                        loggingSetupBuildItem);

                for (var model : modelsNeeded) {
                    GPULlama3ModelRegistry.ModelInfo modelInfo = GPULlama3ModelRegistry.ModelInfo.from(model.name());
                    Path pathOfModelDirOnDisk = registry.constructModelDirectoryPath(modelInfo);
                    // Check if the model is already downloaded
                    // this is done automatically by download model, but we want to provide a good progress experience, so we do it again here
                    if (Files.exists(pathOfModelDirOnDisk.resolve(GPULlama3ModelRegistry.FINISHED_MARKER))) {
                        LOG.debug("Model " + model.name() + "already exists in " + pathOfModelDirOnDisk);
                    } else {
                        // we pull one model at a time and provide progress updates to the user via logging
                        LOG.info("Pulling model " + model.name());

                        AtomicReference<Long> LAST_UPDATE_REF = new AtomicReference<>();

                        try {
                            registry.downloadModel(model.name(), model.quantization(), Optional.empty(),
                                    Optional.of(new GPULlama3ModelRegistry.ProgressReporter() {
                                        @Override
                                        public void update(String filename, long sizeDownloaded, long totalSize) {
                                            // Jlama downloads a bunch of files for each mode of which only the
                                            // weights file is large
                                            // and makes sense to report progress on
                                            if (totalSize < 100_000) {
                                                return;
                                            }

                                            if (!logUpdate(LAST_UPDATE_REF.get())) {
                                                return;
                                            }

                                            LAST_UPDATE_REF.set(System.nanoTime());

                                            BigDecimal percentage = new BigDecimal(sizeDownloaded)
                                                    .divide(new BigDecimal(totalSize),
                                                            4,
                                                            RoundingMode.HALF_DOWN)
                                                    .multiply(ONE_HUNDRED);
                                            BigDecimal progress = percentage.setScale(2, RoundingMode.HALF_DOWN);
                                            if (progress.compareTo(ONE_HUNDRED) >= 0) {
                                                // avoid showing 100% for too long
                                                LOG.infof("Verifying and cleaning up\n", progress);
                                            } else {
                                                LOG.infof("%s - Progress: %s%%\n", model.name(), progress);
                                            }
                                        }

                                        /**
                                         * @param lastUpdate The last update time in nanoseconds
                                         *        Determines whether we should log an update.
                                         *        This is done in order to not overwhelm the console with updates which might
                                         *        make
                                         *        canceling the download difficult. See
                                         *        <a href=
                                         *        "https://github.com/quarkiverse/quarkus-langchain4j/issues/1044">this</a>
                                         */
                                        private boolean logUpdate(Long lastUpdate) {
                                            if (lastUpdate == null) {
                                                return true;
                                            } else {
                                                return TimeUnit.NANOSECONDS.toMillis(System.nanoTime())
                                                        - TimeUnit.NANOSECONDS.toMillis(lastUpdate) > 1_000;
                                            }
                                        }
                                    }));
                        } catch (IOException e) {
                            compressor.closeAndDumpCaptured();
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }

                    modelDownloadedProducer.produce(new ModelDownloadedBuildItem(model, pathOfModelDirOnDisk));
                }

                compressor.close();
            }
        }

    }

    public static final class ModelDownloadedBuildItem extends MultiBuildItem {

        private final NameAndQuantization model;
        private final Path directory;

        public ModelDownloadedBuildItem(NameAndQuantization model, Path directory) {
            this.model = model;
            this.directory = directory;
        }

        public NameAndQuantization getModel() {
            return model;
        }

        public Path getDirectory() {
            return directory;
        }
    }
}
