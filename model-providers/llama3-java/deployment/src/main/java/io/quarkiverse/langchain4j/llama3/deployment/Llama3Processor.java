package io.quarkiverse.langchain4j.llama3.deployment;

import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.CHAT_MODEL;
import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.STREAMING_CHAT_MODEL;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.io.file.PathUtils;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.logging.Logger;

import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.deployment.items.ChatModelProviderCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedChatModelProviderBuildItem;
import io.quarkiverse.langchain4j.llama3.Llama3ModelRegistry;
import io.quarkiverse.langchain4j.llama3.ProgressReporter;
import io.quarkiverse.langchain4j.llama3.runtime.Llama3PreloadRecorder;
import io.quarkiverse.langchain4j.llama3.runtime.Llama3Recorder;
import io.quarkiverse.langchain4j.llama3.runtime.NameAndQuantization;
import io.quarkiverse.langchain4j.llama3.runtime.config.ChatModelFixedRuntimeConfig;
import io.quarkiverse.langchain4j.llama3.runtime.config.LangChain4jLlama3FixedRuntimeConfig;
import io.quarkiverse.langchain4j.llama3.runtime.config.LangChain4jLlama3RuntimeConfig;
import io.quarkiverse.langchain4j.llama3.runtime.graal.Llama3Feature;
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
import io.quarkus.deployment.builditem.NativeImageFeatureBuildItem;
import io.quarkus.deployment.builditem.ServiceStartBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageEnableModule;
import io.quarkus.deployment.builditem.nativeimage.RuntimeInitializedPackageBuildItem;
import io.quarkus.deployment.console.ConsoleInstalledBuildItem;
import io.quarkus.deployment.console.StartupLogCompressor;
import io.quarkus.deployment.logging.LoggingSetupBuildItem;
import io.quarkus.deployment.pkg.builditem.ArtifactResultBuildItem;
import io.quarkus.deployment.pkg.builditem.JarBuildItem;
import io.quarkus.deployment.pkg.steps.JarResultBuildStep;
import io.quarkus.deployment.pkg.steps.NativeBuild;

public class Llama3Processor {

    private final static Logger LOGGER = Logger.getLogger(Llama3Processor.class);

    private static final String FEATURE = "langchain4j-llama3-java";
    private static final String PROVIDER = "llama3-java";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    public void nativeSupport(BuildProducer<RuntimeInitializedPackageBuildItem> runtimeInitializedPackageProducer,
            BuildProducer<NativeImageEnableModule> moduleProducer,
            BuildProducer<NativeImageFeatureBuildItem> nativeFeatureProducer) {
        moduleProducer.produce(new NativeImageEnableModule("jdk.incubator.vector"));
        runtimeInitializedPackageProducer
                .produce(new RuntimeInitializedPackageBuildItem("io.quarkiverse.langchain4j.llama3.copy"));
        // this feature makes some types initialized at build time
        nativeFeatureProducer.produce(new NativeImageFeatureBuildItem(Llama3Feature.class));
        // see also flags added in native-image.properties file in the runtime module
    }

    @BuildStep
    public void providerCandidates(BuildProducer<ChatModelProviderCandidateBuildItem> chatProducer,
            LangChain4jLlama3BuildTimeConfig config) {
        if (config.chatModel().enabled().isEmpty() || config.chatModel().enabled().get()) {
            chatProducer.produce(new ChatModelProviderCandidateBuildItem(PROVIDER));
        }
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void generateBeans(Llama3Recorder recorder,
            List<SelectedChatModelProviderBuildItem> selectedChatItem,
            LangChain4jLlama3RuntimeConfig runtimeConfig,
            LangChain4jLlama3FixedRuntimeConfig fixedRuntimeConfig,
            BuildProducer<SyntheticBeanBuildItem> beanProducer) {

        for (var selected : selectedChatItem) {
            if (PROVIDER.equals(selected.getProvider())) {
                String configName = selected.getConfigName();
                var builder = SyntheticBeanBuildItem.configure(CHAT_MODEL).setRuntimeInit().defaultBean()
                        .scope(ApplicationScoped.class)
                        .supplier(recorder.chatModel(runtimeConfig, fixedRuntimeConfig, configName));
                addQualifierIfNecessary(builder, configName);
                beanProducer.produce(builder.done());

                var streamingBuilder = SyntheticBeanBuildItem
                        .configure(STREAMING_CHAT_MODEL)
                        .setRuntimeInit()
                        .defaultBean()
                        .scope(ApplicationScoped.class)
                        .supplier(recorder.streamingChatModel(runtimeConfig, fixedRuntimeConfig, configName));
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
            LangChain4jLlama3BuildTimeConfig buildTimeConfig,
            LangChain4jLlama3FixedRuntimeConfig fixedRuntimeConfig,
            BuildProducer<ModelDownloadedBuildItem> modelDownloadedProducer) {
        if (!buildTimeConfig.includeModelsInArtifact()) {
            return;
        }
        Llama3ModelRegistry registry = Llama3ModelRegistry.getOrCreate(fixedRuntimeConfig.modelsPath());

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
                        (launchMode.isTest() ? "(test) " : "") + "Llama3.java model pull:",
                        consoleInstalledBuildItem,
                        loggingSetupBuildItem);

                for (var model : modelsNeeded) {
                    Llama3ModelRegistry.ModelInfo modelInfo = Llama3ModelRegistry.ModelInfo.from(model.name());
                    Path pathOfModelDirOnDisk = registry.constructModelDirectoryPath(modelInfo);
                    // Check if the model is already downloaded
                    // this is done automatically by download model, but we want to provide a good progress experience, so we do it again here
                    if (Files.exists(pathOfModelDirOnDisk.resolve(Llama3ModelRegistry.FINISHED_MARKER))) {
                        LOGGER.debug("Model " + model.name() + "already exists in " + pathOfModelDirOnDisk);
                    } else {
                        // we pull one model at a time and provide progress updates to the user via logging
                        LOGGER.info("Pulling model " + model.name());

                        AtomicReference<Long> LAST_UPDATE_REF = new AtomicReference<>();

                        try {
                            registry.downloadModel(model.name(), model.quantization(), Optional.empty(),
                                    Optional.of(new ProgressReporter() {
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
                                                LOGGER.infof("Verifying and cleaning up\n", progress);
                                            } else {
                                                LOGGER.infof("Progress: %s%%\n", progress);
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

        Path jarPath = jarBuildItem.get().getPath();
        if (!JarResultBuildStep.QUARKUS_RUN_JAR.equals(jarPath.getFileName().toString())) {
            return;
        }

        Path quarkusAppDir = jarPath.getParent();
        Path jlamaInQuarkusAppDir = quarkusAppDir.resolve("llama3");

        for (ModelDownloadedBuildItem bi : models) {
            try {
                Llama3ModelRegistry.ModelInfo modelInfo = Llama3ModelRegistry.ModelInfo.from(bi.getModel().name());
                Path targetDir = jlamaInQuarkusAppDir.resolve(modelInfo.toFileName());
                Files.createDirectories(targetDir);
                PathUtils.copyDirectory(bi.getDirectory(), targetDir);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

    }

    @BuildStep(onlyIf = NativeBuild.class)
    @Record(ExecutionTime.STATIC_INIT)
    public void preLoadModels(Llama3PreloadRecorder recorder,
            List<SelectedChatModelProviderBuildItem> selectedChatModels,
            List<ModelDownloadedBuildItem> modelDownloaded, // ensure that all models have been downloaded
            LangChain4jLlama3BuildTimeConfig buildTimeConfig,
            LangChain4jLlama3FixedRuntimeConfig fixedRuntimeConfig) {
        if (buildTimeConfig.chatModel().enabled().orElse(true)) {
            List<NameAndQuantization> modelsToPreLoad = new ArrayList<>();
            for (var selected : selectedChatModels) {
                if (PROVIDER.equals(selected.getProvider())) {
                    String configName = selected.getConfigName();

                    ChatModelFixedRuntimeConfig matchingConfig = NamedConfigUtil.isDefault(configName)
                            ? fixedRuntimeConfig.defaultConfig().chatModel()
                            : fixedRuntimeConfig.namedConfig().get(configName).chatModel();
                    if (matchingConfig.preLoadInNative()) {
                        modelsToPreLoad.add(new NameAndQuantization(matchingConfig.modelName(), matchingConfig.quantization()));
                    }
                }
            }

            if (!modelsToPreLoad.isEmpty()) {
                Llama3ModelRegistry modelRegistry = Llama3ModelRegistry.getOrCreate(fixedRuntimeConfig.modelsPath());
                for (NameAndQuantization model : modelsToPreLoad) {
                    recorder.preloadModel(
                            model.name(),
                            model.quantization(),
                            modelRegistry.constructGgufModelFilePath(Llama3ModelRegistry.ModelInfo.from(model.name()),
                                    model.quantization()).toAbsolutePath().toString());
                }
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
