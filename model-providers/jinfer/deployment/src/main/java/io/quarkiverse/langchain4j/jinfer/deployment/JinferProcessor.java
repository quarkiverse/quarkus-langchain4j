package io.quarkiverse.langchain4j.jinfer.deployment;

import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.AUDIO_TRANSCRIPTION_MODEL;
import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.CHAT_MODEL;
import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.EMBEDDING_MODEL;
import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.SCORING_MODEL;
import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.STREAMING_CHAT_MODEL;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;

import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.deployment.items.AudioTranscriptionModelProviderCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.ChatModelProviderCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.EmbeddingModelProviderCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.ScoringModelProviderCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedAudioTranscriptionModelProviderBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedChatModelProviderBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedEmbeddingModelCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedScoringModelProviderBuildItem;
import io.quarkiverse.langchain4j.jinfer.runtime.JinferRecorder;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.arc.deployment.BeanDiscoveryFinishedBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.processor.InjectionPointInfo;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.ModuleEnableNativeAccessBuildItem;
import io.quarkus.deployment.builditem.ShutdownContextBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageEnableModule;

public class JinferProcessor {

    private static final String FEATURE = "langchain4j-jinfer";
    private static final String PROVIDER = "jinfer";

    // Automatic-Module-Name of the jinfer integration jar; used to request native access.
    private static final String JINFER_MODULE = "com.qxotic.jinfer.langchain4j";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    /**
     * jinfer uses the Foreign Function & Memory API. Requesting native access writes
     * {@code Enable-Native-Access: ALL-UNNAMED} into the production jar manifest so
     * {@code java -jar quarkus-run.jar} needs no extra flag for native access. (The incubator
     * Vector API still requires {@code --add-modules jdk.incubator.vector} at launch; that cannot
     * be expressed in the jar manifest and is documented instead.)
     */
    @BuildStep
    ModuleEnableNativeAccessBuildItem enableNativeAccess() {
        return new ModuleEnableNativeAccessBuildItem(JINFER_MODULE);
    }

    /**
     * For native image builds, add the incubator Vector API module so the native compiler can see it.
     */
    @BuildStep
    NativeImageEnableModule enableVectorApiForNative() {
        return new NativeImageEnableModule("jdk.incubator.vector");
    }

    @BuildStep
    public void providerCandidates(BuildProducer<ChatModelProviderCandidateBuildItem> chatProducer,
            BuildProducer<EmbeddingModelProviderCandidateBuildItem> embeddingProducer,
            BuildProducer<ScoringModelProviderCandidateBuildItem> scoringProducer,
            BuildProducer<AudioTranscriptionModelProviderCandidateBuildItem> transcriptionProducer,
            LangChain4jJinferBuildConfig config) {
        if (config.chatModel().enabled().isEmpty() || config.chatModel().enabled().get()) {
            chatProducer.produce(new ChatModelProviderCandidateBuildItem(PROVIDER));
        }
        if (config.embeddingModel().enabled().isEmpty() || config.embeddingModel().enabled().get()) {
            embeddingProducer.produce(new EmbeddingModelProviderCandidateBuildItem(PROVIDER));
        }
        if (config.scoringModel().enabled().isEmpty() || config.scoringModel().enabled().get()) {
            scoringProducer.produce(new ScoringModelProviderCandidateBuildItem(PROVIDER));
        }
        if (config.transcriptionModel().enabled().isEmpty() || config.transcriptionModel().enabled().get()) {
            transcriptionProducer.produce(new AudioTranscriptionModelProviderCandidateBuildItem(PROVIDER));
        }
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void closeModelsAtShutdown(JinferRecorder recorder, ShutdownContextBuildItem shutdown) {
        recorder.closeModelsAtShutdown(shutdown);
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void generateChatBeans(JinferRecorder recorder,
            List<SelectedChatModelProviderBuildItem> selectedChatModels,
            BuildProducer<SyntheticBeanBuildItem> beanProducer) {
        for (var selected : selectedChatModels) {
            if (!PROVIDER.equals(selected.getProvider())) {
                continue;
            }
            String configName = selected.getConfigName();

            var chat = SyntheticBeanBuildItem.configure(CHAT_MODEL)
                    .setRuntimeInit()
                    .defaultBean()
                    .scope(ApplicationScoped.class)
                    .supplier(recorder.chatModel(configName));
            addQualifierIfNecessary(chat, configName);
            beanProducer.produce(chat.done());

            var streaming = SyntheticBeanBuildItem.configure(STREAMING_CHAT_MODEL)
                    .setRuntimeInit()
                    .defaultBean()
                    .scope(ApplicationScoped.class)
                    .supplier(recorder.streamingChatModel(configName));
            addQualifierIfNecessary(streaming, configName);
            beanProducer.produce(streaming.done());
        }
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void generateEmbeddingBeans(JinferRecorder recorder,
            List<SelectedEmbeddingModelCandidateBuildItem> selectedEmbeddingModels,
            BuildProducer<SyntheticBeanBuildItem> beanProducer) {
        for (var selected : selectedEmbeddingModels) {
            if (!PROVIDER.equals(selected.getProvider())) {
                continue;
            }
            String configName = selected.getConfigName();
            var embedding = SyntheticBeanBuildItem.configure(EMBEDDING_MODEL)
                    .setRuntimeInit()
                    .defaultBean()
                    .unremovable()
                    .scope(ApplicationScoped.class)
                    .supplier(recorder.embeddingModel(configName));
            addQualifierIfNecessary(embedding, configName);
            beanProducer.produce(embedding.done());
        }
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void generateScoringBeans(JinferRecorder recorder,
            List<SelectedScoringModelProviderBuildItem> selectedScoringModels,
            BuildProducer<SyntheticBeanBuildItem> beanProducer) {
        for (var selected : selectedScoringModels) {
            if (!PROVIDER.equals(selected.getProvider())) {
                continue;
            }
            String configName = selected.getConfigName();
            var scoring = SyntheticBeanBuildItem.configure(SCORING_MODEL)
                    .setRuntimeInit()
                    .defaultBean()
                    .unremovable()
                    .scope(ApplicationScoped.class)
                    .supplier(recorder.scoringModel(configName));
            addQualifierIfNecessary(scoring, configName);
            beanProducer.produce(scoring.done());
        }
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void generateTranscriptionBeans(JinferRecorder recorder,
            List<SelectedAudioTranscriptionModelProviderBuildItem> selectedTranscriptionModels,
            BuildProducer<SyntheticBeanBuildItem> beanProducer) {
        for (var selected : selectedTranscriptionModels) {
            if (!PROVIDER.equals(selected.getProvider())) {
                continue;
            }
            String configName = selected.getConfigName();
            var transcription = SyntheticBeanBuildItem.configure(AUDIO_TRANSCRIPTION_MODEL)
                    .setRuntimeInit()
                    .defaultBean()
                    .unremovable()
                    .scope(ApplicationScoped.class)
                    .supplier(recorder.transcriptionModel(configName));
            addQualifierIfNecessary(transcription, configName);
            beanProducer.produce(transcription.done());
        }
    }

    /**
     * TextToSpeechModel has no provider SPI in core, so jinfer produces the beans itself, one per
     * config name that is actually injected (default plus any {@code @ModelName}-qualified injection
     * point), mirroring how the core framework discovers named models.
     */
    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void generateTtsBeans(JinferRecorder recorder,
            BeanDiscoveryFinishedBuildItem beanDiscovery,
            LangChain4jJinferBuildConfig config,
            BuildProducer<SyntheticBeanBuildItem> beanProducer) {
        if (config.ttsModel().enabled().isPresent() && !config.ttsModel().enabled().get()) {
            return;
        }
        Set<String> ttsNames = new LinkedHashSet<>();
        for (InjectionPointInfo ip : beanDiscovery.getInjectionPoints()) {
            if (!JinferDotNames.TEXT_TO_SPEECH_MODEL.equals(ip.getRequiredType().name())) {
                continue;
            }
            String name = determineModelName(ip);
            if (name != null) {
                ttsNames.add(name);
            }
        }
        for (String configName : ttsNames) {
            var tts = SyntheticBeanBuildItem.configure(JinferDotNames.TEXT_TO_SPEECH_MODEL)
                    .setRuntimeInit()
                    .defaultBean()
                    .unremovable()
                    .scope(ApplicationScoped.class)
                    .supplier(recorder.ttsModel(configName));
            addQualifierIfNecessary(tts, configName);
            beanProducer.produce(tts.done());
        }
    }

    private static String determineModelName(InjectionPointInfo ip) {
        AnnotationInstance modelNameInstance = ip.getRequiredQualifier(JinferDotNames.MODEL_NAME);
        if (modelNameInstance != null) {
            AnnotationValue value = modelNameInstance.value();
            if (value != null && !value.asString().isEmpty()) {
                return value.asString();
            }
        }
        if (modelNameInstance == null && ip.isProgrammaticLookup()) {
            return null;
        }
        return NamedConfigUtil.DEFAULT_NAME;
    }

    static void addQualifierIfNecessary(SyntheticBeanBuildItem.ExtendedBeanConfigurator builder, String configName) {
        if (!NamedConfigUtil.isDefault(configName)) {
            builder.addQualifier(AnnotationInstance.builder(ModelName.class).add("value", configName).build());
        }
    }
}
