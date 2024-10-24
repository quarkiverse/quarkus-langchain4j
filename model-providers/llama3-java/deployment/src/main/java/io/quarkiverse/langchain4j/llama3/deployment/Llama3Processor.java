package io.quarkiverse.langchain4j.llama3.deployment;

import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.CHAT_MODEL;

import java.nio.file.Path;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.logging.Logger;
import org.slf4j.LoggerFactory;

import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.deployment.items.ChatModelProviderCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedChatModelProviderBuildItem;
import io.quarkiverse.langchain4j.llama3.runtime.Llama3Recorder;
import io.quarkiverse.langchain4j.llama3.runtime.config.LangChain4jLlama3FixedRuntimeConfig;
import io.quarkiverse.langchain4j.llama3.runtime.config.LangChain4jLlama3RuntimeConfig;
import io.quarkiverse.langchain4j.llama3.runtime.graal.Llama3Feature;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.builder.item.MultiBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.NativeImageFeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageEnableModule;
import io.quarkus.deployment.builditem.nativeimage.RuntimeInitializedPackageBuildItem;

public class Llama3Processor {

    private final static Logger LOGGER = Logger.getLogger(Llama3Processor.class);

    private static final String FEATURE = "langchain4j-llama3-java";
    private static final String PROVIDER = "llama3-java";
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(Llama3Processor.class);

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    public void providerCandidates(BuildProducer<ChatModelProviderCandidateBuildItem> chatProducer,
            LangChain4jJlamaBuildTimeConfig config) {
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
            }
        }
    }

    private void addQualifierIfNecessary(SyntheticBeanBuildItem.ExtendedBeanConfigurator builder, String configName) {
        if (!NamedConfigUtil.isDefault(configName)) {
            builder.addQualifier(AnnotationInstance.builder(ModelName.class).add("value", configName).build());
        }
    }

    @BuildStep
    public void nativeSupport(BuildProducer<RuntimeInitializedPackageBuildItem> runtimeInitializedPackageProducer,
            BuildProducer<NativeImageEnableModule> moduleProducer,
            BuildProducer<NativeImageFeatureBuildItem> nativeFeatureProducer) {
        runtimeInitializedPackageProducer
                .produce(new RuntimeInitializedPackageBuildItem("io.quarkiverse.langchain4j.llama3.copy"));
        moduleProducer.produce(new NativeImageEnableModule("jdk.incubator.vector"));
        nativeFeatureProducer.produce(new NativeImageFeatureBuildItem(Llama3Feature.class));
    }

    //    @Produce(ServiceStartBuildItem.class)
    //    @BuildStep
    //    void downloadModels(List<SelectedChatModelProviderBuildItem> selectedChatModels,
    //            List<SelectedEmbeddingModelCandidateBuildItem> selectedEmbeddingModels,
    //            LoggingSetupBuildItem loggingSetupBuildItem,
    //            Optional<ConsoleInstalledBuildItem> consoleInstalledBuildItem,
    //            LaunchModeBuildItem launchMode,
    //            LangChain4jJlamaBuildTimeConfig buildTimeConfig,
    //            LangChain4jJlamaFixedRuntimeConfig fixedRuntimeConfig,
    //            BuildProducer<ModelDownloadedBuildItem> modelDownloadedProducer) {
    //        if (!buildTimeConfig.includeModelsInArtifact()) {
    //            return;
    //        }
    //        JlamaModelRegistry registry = JlamaModelRegistry.getOrCreate(fixedRuntimeConfig.modelsPath());
    //
    //        BigDecimal ONE_HUNDRED = new BigDecimal("100");
    //
    //        if (buildTimeConfig.chatModel().enabled().orElse(true) || buildTimeConfig.embeddingModel().enabled().orElse(true)) {
    //            List<String> modelsNeeded = new ArrayList<>();
    //            for (var selected : selectedChatModels) {
    //                if (PROVIDER.equals(selected.getProvider())) {
    //                    String configName = selected.getConfigName();
    //
    //                    String modelName = NamedConfigUtil.isDefault(configName)
    //                            ? fixedRuntimeConfig.defaultConfig().chatModel().modelName()
    //                            : fixedRuntimeConfig.namedConfig().get(configName).chatModel().modelName();
    //                    modelsNeeded.add(modelName);
    //                }
    //            }
    //
    //            for (var selected : selectedEmbeddingModels) {
    //                if (PROVIDER.equals(selected.getProvider())) {
    //                    String configName = selected.getConfigName();
    //
    //                    String modelName = NamedConfigUtil.isDefault(configName)
    //                            ? fixedRuntimeConfig.defaultConfig().embeddingModel().modelName()
    //                            : fixedRuntimeConfig.namedConfig().get(configName).embeddingModel().modelName();
    //                    modelsNeeded.add(modelName);
    //                }
    //            }
    //
    //            if (!modelsNeeded.isEmpty()) {
    //                StartupLogCompressor compressor = new StartupLogCompressor(
    //                        (launchMode.isTest() ? "(test) " : "") + "Jlama model pull:",
    //                        consoleInstalledBuildItem,
    //                        loggingSetupBuildItem);
    //
    //                for (String modelName : modelsNeeded) {
    //                    JlamaModelRegistry.ModelInfo modelInfo = JlamaModelRegistry.ModelInfo.from(modelName);
    //                    Path pathOfModelDirOnDisk = SafeTensorSupport.constructLocalModelPath(
    //                            registry.getModelCachePath().toAbsolutePath().toString(), modelInfo.owner(),
    //                            modelInfo.name());
    //                    // Check if the model is already downloaded
    //                    // this is done automatically by download model, but we want to provide a good progress experience, so we do it again here
    //                    if (Files.exists(pathOfModelDirOnDisk.resolve(".finished"))) {
    //                        LOGGER.debug("Model " + modelName + "already exists in " + pathOfModelDirOnDisk);
    //                    } else {
    //                        // we pull one model at a time and provide progress updates to the user via logging
    //                        LOGGER.info("Pulling model " + modelName);
    //
    //                        try {
    //                            registry.downloadModel(modelName, Optional.empty(), Optional.of(new ProgressReporter() {
    //                                @Override
    //                                public void update(String filename, long sizeDownloaded, long totalSize) {
    //                                    // Jlama downloads a bunch of files for each mode of which only the weights file is large
    //                                    // and makes sense to report progress on
    //                                    if (totalSize < 100_000) {
    //                                        return;
    //                                    }
    //
    //                                    BigDecimal percentage = new BigDecimal(sizeDownloaded).divide(new BigDecimal(totalSize), 4,
    //                                            RoundingMode.HALF_DOWN).multiply(ONE_HUNDRED);
    //                                    BigDecimal progress = percentage.setScale(2, RoundingMode.HALF_DOWN);
    //                                    if (progress.compareTo(ONE_HUNDRED) >= 0) {
    //                                        // avoid showing 100% for too long
    //                                        LOGGER.infof("Verifying and cleaning up\n", progress);
    //                                    } else {
    //                                        LOGGER.infof("Progress: %s%%\n", progress);
    //                                    }
    //                                }
    //                            }));
    //                        } catch (IOException e) {
    //                            compressor.closeAndDumpCaptured();
    //                            throw new UncheckedIOException(e);
    //                        }
    //                    }
    //
    //                    modelDownloadedProducer.produce(new ModelDownloadedBuildItem(modelName, pathOfModelDirOnDisk));
    //                }
    //
    //                compressor.close();
    //            }
    //        }
    //
    //    }

    /**
     * When building a fast jar, we can copy the model files into the directory
     *
     */
    //    @BuildStep(onlyIf = IsNormal.class)
    //    @Produce(ArtifactResultBuildItem.class)
    //    public void copyToFastJar(List<ModelDownloadedBuildItem> models,
    //            Optional<JarBuildItem> jarBuildItem) {
    //        if (!jarBuildItem.isPresent()) {
    //            return;
    //        }
    //
    //        Path jarPath = jarBuildItem.get().getPath();
    //        if (!JarResultBuildStep.QUARKUS_RUN_JAR.equals(jarPath.getFileName().toString())) {
    //            return;
    //        }
    //
    //        Path quarkusAppDir = jarPath.getParent();
    //        Path jlamaInQuarkusAppDir = quarkusAppDir.resolve("jlama");
    //
    //        for (ModelDownloadedBuildItem bi : models) {
    //            try {
    //                JlamaModelRegistry.ModelInfo modelInfo = JlamaModelRegistry.ModelInfo.from(bi.getModelName());
    //                Path targetDir = jlamaInQuarkusAppDir.resolve(modelInfo.toFileName());
    //                Files.createDirectories(targetDir);
    //                PathUtils.copyDirectory(bi.getDirectory(), targetDir);
    //            } catch (IOException e) {
    //                throw new UncheckedIOException(e);
    //            }
    //        }
    //
    //    }

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
    }
}
