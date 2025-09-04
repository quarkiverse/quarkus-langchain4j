package io.quarkiverse.langchain4j.jlama.deployment;

import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.CHAT_MODEL;
import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.EMBEDDING_MODEL;
import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.STREAMING_CHAT_MODEL;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.enterprise.context.ApplicationScoped;

import org.apache.commons.io.file.PathUtils;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.logging.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.github.tjake.jlama.safetensors.SafeTensorSupport;
import com.github.tjake.jlama.util.ProgressReporter;

import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.deployment.items.ChatModelProviderCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.EmbeddingModelProviderCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedChatModelProviderBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedEmbeddingModelCandidateBuildItem;
import io.quarkiverse.langchain4j.jlama.JlamaModelRegistry;
import io.quarkiverse.langchain4j.jlama.runtime.JlamaAiRecorder;
import io.quarkiverse.langchain4j.jlama.runtime.config.LangChain4jJlamaFixedRuntimeConfig;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.builder.item.MultiBuildItem;
import io.quarkus.deployment.IsNormal;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Produce;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.builditem.ServiceStartBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.console.ConsoleInstalledBuildItem;
import io.quarkus.deployment.console.StartupLogCompressor;
import io.quarkus.deployment.logging.LoggingSetupBuildItem;
import io.quarkus.deployment.pkg.PackageConfig.JarConfig.JarType;
import io.quarkus.deployment.pkg.builditem.ArtifactResultBuildItem;
import io.quarkus.deployment.pkg.builditem.JarBuildItem;

public class JlamaProcessor {

    private final static Logger LOGGER = Logger.getLogger(JlamaProcessor.class);

    private static final String FEATURE = "langchain4j-jlama";
    private static final String PROVIDER = "jlama";
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(JlamaProcessor.class);

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void nativeSupport(BuildProducer<ReflectiveClassBuildItem> reflectiveClassProducer) {
        reflectiveClassProducer
                .produce(ReflectiveClassBuildItem.builder(PropertyNamingStrategies.SnakeCaseStrategy.class).build());
    }

    @BuildStep
    public void providerCandidates(BuildProducer<ChatModelProviderCandidateBuildItem> chatProducer,
            BuildProducer<EmbeddingModelProviderCandidateBuildItem> embeddingProducer,
            LangChain4jJlamaBuildTimeConfig config) {
        if (config.chatModel().enabled().isEmpty() || config.chatModel().enabled().get()) {
            chatProducer.produce(new ChatModelProviderCandidateBuildItem(PROVIDER));
        }
        if (config.embeddingModel().enabled().isEmpty() || config.embeddingModel().enabled().get()) {
            embeddingProducer.produce(new EmbeddingModelProviderCandidateBuildItem(PROVIDER));
        }
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void generateBeans(JlamaAiRecorder recorder, List<SelectedChatModelProviderBuildItem> selectedChatItem,
            List<SelectedEmbeddingModelCandidateBuildItem> selectedEmbedding,
            BuildProducer<SyntheticBeanBuildItem> beanProducer) {

        for (var selected : selectedChatItem) {
            if (PROVIDER.equals(selected.getProvider())) {
                String configName = selected.getConfigName();
                var builder = SyntheticBeanBuildItem.configure(CHAT_MODEL).setRuntimeInit().defaultBean()
                        .scope(ApplicationScoped.class)
                        .supplier(recorder.chatModel(configName));
                addQualifierIfNecessary(builder, configName);
                beanProducer.produce(builder.done());

                var streamingBuilder = SyntheticBeanBuildItem.configure(STREAMING_CHAT_MODEL).setRuntimeInit()
                        .defaultBean().scope(ApplicationScoped.class)
                        .supplier(recorder.streamingChatModel(configName));
                addQualifierIfNecessary(streamingBuilder, configName);
                beanProducer.produce(streamingBuilder.done());
            }
        }

        for (var selected : selectedEmbedding) {
            if (PROVIDER.equals(selected.getProvider())) {
                String configName = selected.getConfigName();
                var builder = SyntheticBeanBuildItem.configure(EMBEDDING_MODEL).setRuntimeInit().defaultBean()
                        .unremovable().scope(ApplicationScoped.class)
                        .supplier(recorder.embeddingModel(configName));
                addQualifierIfNecessary(builder, configName);
                beanProducer.produce(builder.done());
            }
        }
    }

    private void addQualifierIfNecessary(SyntheticBeanBuildItem.ExtendedBeanConfigurator builder, String configName) {
        if (!NamedConfigUtil.isDefault(configName)) {
            builder.addQualifier(AnnotationInstance.builder(ModelName.class).add("value", configName).build());
        }
    }

    @Produce(ServiceStartBuildItem.class)
    @BuildStep
    void downloadModels(List<SelectedChatModelProviderBuildItem> selectedChatModels,
            List<SelectedEmbeddingModelCandidateBuildItem> selectedEmbeddingModels,
            LoggingSetupBuildItem loggingSetupBuildItem,
            Optional<ConsoleInstalledBuildItem> consoleInstalledBuildItem,
            LaunchModeBuildItem launchMode,
            LangChain4jJlamaBuildTimeConfig buildTimeConfig,
            LangChain4jJlamaFixedRuntimeConfig fixedRuntimeConfig,
            BuildProducer<ModelDownloadedBuildItem> modelDownloadedProducer) {
        if (!buildTimeConfig.includeModelsInArtifact()) {
            return;
        }
        JlamaModelRegistry registry = JlamaModelRegistry.getOrCreate(fixedRuntimeConfig.modelsPath());

        BigDecimal ONE_HUNDRED = new BigDecimal("100");

        if (buildTimeConfig.chatModel().enabled().orElse(true) || buildTimeConfig.embeddingModel().enabled().orElse(true)) {
            List<String> modelsNeeded = new ArrayList<>();
            for (var selected : selectedChatModels) {
                if (PROVIDER.equals(selected.getProvider())) {
                    String configName = selected.getConfigName();

                    String modelName = NamedConfigUtil.isDefault(configName)
                            ? fixedRuntimeConfig.defaultConfig().chatModel().modelName()
                            : fixedRuntimeConfig.namedConfig().get(configName).chatModel().modelName();
                    modelsNeeded.add(modelName);
                }
            }

            for (var selected : selectedEmbeddingModels) {
                if (PROVIDER.equals(selected.getProvider())) {
                    String configName = selected.getConfigName();

                    String modelName = NamedConfigUtil.isDefault(configName)
                            ? fixedRuntimeConfig.defaultConfig().embeddingModel().modelName()
                            : fixedRuntimeConfig.namedConfig().get(configName).embeddingModel().modelName();
                    modelsNeeded.add(modelName);
                }
            }

            if (!modelsNeeded.isEmpty()) {
                StartupLogCompressor compressor = new StartupLogCompressor(
                        (launchMode.isTest() ? "(test) " : "") + "Jlama model pull:",
                        consoleInstalledBuildItem,
                        loggingSetupBuildItem);

                for (String modelName : modelsNeeded) {
                    JlamaModelRegistry.ModelInfo modelInfo = JlamaModelRegistry.ModelInfo.from(modelName);
                    Path pathOfModelDirOnDisk = SafeTensorSupport.constructLocalModelPath(
                            registry.getModelCachePath().toAbsolutePath().toString(), modelInfo.owner(),
                            modelInfo.name());
                    // Check if the model is already downloaded
                    // this is done automatically by download model, but we want to provide a good progress experience, so we do it again here
                    if (Files.exists(pathOfModelDirOnDisk.resolve(".finished"))) {
                        LOGGER.debug("Model " + modelName + "already exists in " + pathOfModelDirOnDisk);
                    } else {
                        // we pull one model at a time and provide progress updates to the user via logging
                        LOGGER.info("Pulling model " + modelName);

                        AtomicReference<Long> LAST_UPDATE_REF = new AtomicReference<>();

                        try {
                            registry.downloadModel(modelName, Optional.empty(), Optional.of(new ProgressReporter() {
                                @Override
                                public void update(String filename, long sizeDownloaded, long totalSize) {
                                    // Jlama downloads a bunch of files for each mode of which only the weights file is large
                                    // and makes sense to report progress on
                                    if (totalSize < 100_000) {
                                        return;
                                    }

                                    if (!logUpdate(LAST_UPDATE_REF.get())) {
                                        return;
                                    }

                                    LAST_UPDATE_REF.set(System.nanoTime());

                                    BigDecimal percentage = new BigDecimal(sizeDownloaded).divide(new BigDecimal(totalSize), 4,
                                            RoundingMode.HALF_DOWN).multiply(ONE_HUNDRED);
                                    BigDecimal progress = percentage.setScale(2, RoundingMode.HALF_DOWN);
                                    if (progress.compareTo(ONE_HUNDRED) >= 0) {
                                        // avoid showing 100% for too long
                                        LOGGER.infof("Verifying and cleaning up\n", progress);
                                    } else {
                                        LOGGER.infof("%s - Progress: %s%%\n", modelName, progress);
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
                            }));
                        } catch (IOException e) {
                            compressor.closeAndDumpCaptured();
                            throw new UncheckedIOException(e);
                        }
                    }

                    modelDownloadedProducer.produce(new ModelDownloadedBuildItem(modelName, pathOfModelDirOnDisk));
                }

                compressor.close();
            }
        }

    }

    /**
     * When building a fast jar, we can copy the model files into the directory
     *
     */
    @BuildStep(onlyIf = IsNormal.class)
    @Produce(ArtifactResultBuildItem.class)
    public void copyToFastJar(List<ModelDownloadedBuildItem> models,
            Optional<JarBuildItem> jarBuildItem) {
        if (!jarBuildItem.isPresent()) {
            return;
        }

        if (jarBuildItem.get().getType() != JarType.FAST_JAR) {
            return;
        }

        Path jarPath = jarBuildItem.get().getPath();
        Path quarkusAppDir = jarPath.getParent();
        Path jlamaInQuarkusAppDir = quarkusAppDir.resolve("jlama");

        // Using LinkedHashSet to guarantee uniqueness of models
        for (ModelDownloadedBuildItem bi : new LinkedHashSet<>(models)) {
            try {
                JlamaModelRegistry.ModelInfo modelInfo = JlamaModelRegistry.ModelInfo.from(bi.getModelName());
                Path targetDir = jlamaInQuarkusAppDir.resolve(modelInfo.toFileName());
                Files.createDirectories(targetDir);
                PathUtils.copyDirectory(bi.getDirectory(), targetDir);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

    }

    public static final class ModelDownloadedBuildItem extends MultiBuildItem {

        private final String modelName;
        private final Path directory;

        public ModelDownloadedBuildItem(String modelName, Path directory) {
            this.modelName = modelName;
            this.directory = directory;
        }

        public String getModelName() {
            return modelName;
        }

        public Path getDirectory() {
            return directory;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof ModelDownloadedBuildItem that)) {
                return false;
            }

            return Objects.equals(modelName, that.modelName) && Objects.equals(directory, that.directory);
        }

        @Override
        public int hashCode() {
            return Objects.hash(modelName, directory);
        }
    }
}
