package io.quarkiverse.langchain4j.google.genai.deployment;

import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.CHAT_MODEL;
import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.EMBEDDING_MODEL;
import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.STREAMING_CHAT_MODEL;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;

import org.eclipse.microprofile.context.ManagedExecutor;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.ParameterizedType;
import org.jboss.jandex.Type;

import dev.langchain4j.model.google.genai.GoogleGenAiChatModel;
import dev.langchain4j.model.google.genai.GoogleGenAiEmbeddingModel;
import dev.langchain4j.model.google.genai.GoogleGenAiStreamingChatModel;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.deployment.DotNames;
import io.quarkiverse.langchain4j.deployment.items.ChatModelProviderCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.EmbeddingModelProviderCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedChatModelProviderBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedEmbeddingModelCandidateBuildItem;
import io.quarkiverse.langchain4j.google.genai.runtime.GoogleGenAiRecorder;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.RuntimeInitializedClassBuildItem;
import io.quarkus.proxy.ProxyConfigurationRegistry;

public class GoogleGenAiProcessor {

    private static final String FEATURE = "langchain4j-google-genai";
    private static final String PROVIDER = "google-genai";

    private static final DotName CHAT_MODEL_BUILDER = DotName
            .createSimple(GoogleGenAiChatModel.Builder.class);
    private static final DotName STREAMING_CHAT_MODEL_BUILDER = DotName
            .createSimple(GoogleGenAiStreamingChatModel.Builder.class);
    private static final DotName EMBEDDING_MODEL_BUILDER = DotName
            .createSimple(GoogleGenAiEmbeddingModel.Builder.class);
    private static final DotName MANAGED_EXECUTOR = DotName
            .createSimple(ManagedExecutor.class);

    private static final AnnotationInstance ANY = AnnotationInstance.builder(DotName.createSimple(
            Any.class)).build();

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void nativeSupport(BuildProducer<RuntimeInitializedClassBuildItem> producer) {
        producer.produce(new RuntimeInitializedClassBuildItem("com.google.genai.LocalTokenizerLoader"));
    }

    @BuildStep
    public void providerCandidates(BuildProducer<ChatModelProviderCandidateBuildItem> chatProducer,
            BuildProducer<EmbeddingModelProviderCandidateBuildItem> embeddingProducer,
            GoogleGenAiBuildConfig config) {
        if (config.chatModel().enabled().isEmpty() || config.chatModel().enabled().get()) {
            chatProducer.produce(new ChatModelProviderCandidateBuildItem(PROVIDER));
        }

        if (config.embeddingModel().enabled().isEmpty() || config.embeddingModel().enabled().get()) {
            embeddingProducer.produce(new EmbeddingModelProviderCandidateBuildItem(PROVIDER));
        }
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void generateBeans(GoogleGenAiRecorder recorder, List<SelectedChatModelProviderBuildItem> selectedChatItem,
            List<SelectedEmbeddingModelCandidateBuildItem> selectedEmbedding,
            Capabilities capabilities,
            BuildProducer<SyntheticBeanBuildItem> beanProducer) {
        boolean openTelemetryAvailable = capabilities.isPresent(Capability.OPENTELEMETRY_TRACER);

        for (var selected : selectedChatItem) {
            if (PROVIDER.equals(selected.getProvider())) {
                var configName = selected.getConfigName();
                var chatModel = recorder.chatModel(configName, openTelemetryAvailable);
                var builder = SyntheticBeanBuildItem
                        .configure(CHAT_MODEL)
                        .setRuntimeInit()
                        .defaultBean()
                        .scope(ApplicationScoped.class)
                        .addInjectionPoint(ParameterizedType.create(DotNames.CDI_INSTANCE,
                                new Type[] { ClassType.create(DotNames.CHAT_MODEL_LISTENER) }, null))
                        .addInjectionPoint(ParameterizedType.create(DotNames.CDI_INSTANCE,
                                new Type[] { ParameterizedType.create(DotNames.MODEL_BUILDER_CUSTOMIZER,
                                        new Type[] { ClassType.create(CHAT_MODEL_BUILDER) }, null) },
                                null), ANY)
                        .addInjectionPoint(ClassType.create(MANAGED_EXECUTOR))
                        .addInjectionPoint(Type.create(ProxyConfigurationRegistry.class))
                        .createWith(chatModel);

                addQualifierIfNecessary(builder, configName);
                beanProducer.produce(builder.done());

                var streamingChatModel = recorder.streamingChatModel(configName, openTelemetryAvailable);
                var streamingBuilder = SyntheticBeanBuildItem
                        .configure(STREAMING_CHAT_MODEL)
                        .setRuntimeInit()
                        .defaultBean()
                        .scope(ApplicationScoped.class)
                        .addInjectionPoint(ParameterizedType.create(DotNames.CDI_INSTANCE,
                                new Type[] { ClassType.create(DotNames.CHAT_MODEL_LISTENER) }, null))
                        .addInjectionPoint(ParameterizedType.create(DotNames.CDI_INSTANCE,
                                new Type[] { ParameterizedType.create(DotNames.MODEL_BUILDER_CUSTOMIZER,
                                        new Type[] { ClassType.create(STREAMING_CHAT_MODEL_BUILDER) }, null) },
                                null), ANY)
                        .addInjectionPoint(ClassType.create(MANAGED_EXECUTOR))
                        .addInjectionPoint(Type.create(ProxyConfigurationRegistry.class))
                        .createWith(streamingChatModel);

                addQualifierIfNecessary(streamingBuilder, configName);
                beanProducer.produce(streamingBuilder.done());
            }
        }

        for (var selected : selectedEmbedding) {
            if (PROVIDER.equals(selected.getProvider())) {
                var configName = selected.getConfigName();
                var embeddingModel = recorder.embeddingModel(configName, openTelemetryAvailable);
                var builder = SyntheticBeanBuildItem
                        .configure(EMBEDDING_MODEL)
                        .setRuntimeInit()
                        .defaultBean()
                        .unremovable()
                        .scope(ApplicationScoped.class)
                        .addInjectionPoint(ParameterizedType.create(DotNames.CDI_INSTANCE,
                                new Type[] { ParameterizedType.create(DotNames.MODEL_BUILDER_CUSTOMIZER,
                                        new Type[] { ClassType.create(EMBEDDING_MODEL_BUILDER) }, null) },
                                null), ANY)
                        .addInjectionPoint(ClassType.create(MANAGED_EXECUTOR))
                        .addInjectionPoint(Type.create(ProxyConfigurationRegistry.class))
                        .createWith(embeddingModel);

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

}
