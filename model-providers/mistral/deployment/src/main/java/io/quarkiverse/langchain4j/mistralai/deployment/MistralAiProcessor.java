package io.quarkiverse.langchain4j.mistralai.deployment;

import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.CHAT_MODEL;
import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.EMBEDDING_MODEL;
import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.MODERATION_MODEL;
import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.STREAMING_CHAT_MODEL;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.ParameterizedType;
import org.jboss.jandex.Type;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;

import dev.langchain4j.model.mistralai.MistralAiChatModel;
import dev.langchain4j.model.mistralai.MistralAiEmbeddingModel;
import dev.langchain4j.model.mistralai.MistralAiModerationModel;
import dev.langchain4j.model.mistralai.MistralAiStreamingChatModel;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.deployment.DotNames;
import io.quarkiverse.langchain4j.deployment.items.ChatModelProviderCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.EmbeddingModelProviderCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.ModerationModelProviderCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedChatModelProviderBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedEmbeddingModelCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedModerationModelProviderBuildItem;
import io.quarkiverse.langchain4j.mistralai.runtime.MistralAiRecorder;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.IndexDependencyBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.resteasy.reactive.spi.MessageBodyReaderOverrideBuildItem;
import io.quarkus.resteasy.reactive.spi.MessageBodyWriterOverrideBuildItem;
import io.smallrye.config.Priorities;

public class MistralAiProcessor {

    private static final String FEATURE = "langchain4j-mistralai";
    private static final String PROVIDER = "mistralai";

    private static final DotName MISTRAL_CHAT_MODEL_BUILDER = DotName
            .createSimple(MistralAiChatModel.MistralAiChatModelBuilder.class);
    private static final DotName MISTRAL_STREAMING_CHAT_MODEL_BUILDER = DotName
            .createSimple(MistralAiStreamingChatModel.MistralAiStreamingChatModelBuilder.class);
    private static final DotName MISTRAL_EMBEDDING_MODEL_BUILDER = DotName
            .createSimple(MistralAiEmbeddingModel.MistralAiEmbeddingModelBuilder.class);
    private static final DotName MISTRAL_MODERATION_MODEL_BUILDER = DotName
            .createSimple(MistralAiModerationModel.Builder.class);

    private static final AnnotationInstance ANY = AnnotationInstance.builder(DotName.createSimple(
            Any.class)).build();

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    public IndexDependencyBuildItem indexMistralaiDependency() {
        // needed for Jackson support in native
        return new IndexDependencyBuildItem("dev.langchain4j", "langchain4j-mistral-ai");
    }

    @BuildStep
    void nativeSupport(BuildProducer<ReflectiveClassBuildItem> reflectiveClassProducer) {
        reflectiveClassProducer
                .produce(ReflectiveClassBuildItem.builder(PropertyNamingStrategies.SnakeCaseStrategy.class).build());
    }

    @BuildStep
    public void providerCandidates(BuildProducer<ChatModelProviderCandidateBuildItem> chatProducer,
            BuildProducer<EmbeddingModelProviderCandidateBuildItem> embeddingProducer,
            BuildProducer<ModerationModelProviderCandidateBuildItem> moderationProducer,
            LangChain4jMistralAiBuildConfig config) {
        if (config.chatModel().enabled().isEmpty() || config.chatModel().enabled().get()) {
            chatProducer.produce(new ChatModelProviderCandidateBuildItem(PROVIDER));
        }
        if (config.embeddingModel().enabled().isEmpty() || config.embeddingModel().enabled().get()) {
            embeddingProducer.produce(new EmbeddingModelProviderCandidateBuildItem(PROVIDER));
        }
        if (config.moderationModel().enabled().isEmpty() || config.moderationModel().enabled().get()) {
            moderationProducer.produce(new ModerationModelProviderCandidateBuildItem(PROVIDER));
        }
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void generateBeans(MistralAiRecorder recorder,
            List<SelectedChatModelProviderBuildItem> selectedChatItem,
            List<SelectedEmbeddingModelCandidateBuildItem> selectedEmbedding,
            List<SelectedModerationModelProviderBuildItem> selectedModeration,
            BuildProducer<SyntheticBeanBuildItem> beanProducer) {

        for (var selected : selectedChatItem) {
            if (PROVIDER.equals(selected.getProvider())) {
                String configName = selected.getConfigName();
                var builder = SyntheticBeanBuildItem
                        .configure(CHAT_MODEL)
                        .setRuntimeInit()
                        .defaultBean()
                        .scope(ApplicationScoped.class)
                        .addInjectionPoint(ParameterizedType.create(DotNames.CDI_INSTANCE,
                                new Type[] { ParameterizedType.create(DotNames.MODEL_BUILDER_CUSTOMIZER,
                                        new Type[] { ClassType.create(MISTRAL_CHAT_MODEL_BUILDER) }, null) },
                                null), ANY)
                        .createWith(recorder.chatModel(configName));
                addQualifierIfNecessary(builder, configName);
                beanProducer.produce(builder.done());

                var streamingBuilder = SyntheticBeanBuildItem
                        .configure(STREAMING_CHAT_MODEL)
                        .setRuntimeInit()
                        .defaultBean()
                        .scope(ApplicationScoped.class)
                        .addInjectionPoint(ParameterizedType.create(DotNames.CDI_INSTANCE,
                                new Type[] { ParameterizedType.create(DotNames.MODEL_BUILDER_CUSTOMIZER,
                                        new Type[] { ClassType.create(MISTRAL_STREAMING_CHAT_MODEL_BUILDER) }, null) },
                                null), ANY)
                        .createWith(recorder.streamingChatModel(configName));
                addQualifierIfNecessary(streamingBuilder, configName);
                beanProducer.produce(streamingBuilder.done());
            }
        }

        for (var selected : selectedEmbedding) {
            if (PROVIDER.equals(selected.getProvider())) {
                String configName = selected.getConfigName();
                var builder = SyntheticBeanBuildItem
                        .configure(EMBEDDING_MODEL)
                        .setRuntimeInit()
                        .defaultBean()
                        .unremovable()
                        .scope(ApplicationScoped.class)
                        .addInjectionPoint(ParameterizedType.create(DotNames.CDI_INSTANCE,
                                new Type[] { ParameterizedType.create(DotNames.MODEL_BUILDER_CUSTOMIZER,
                                        new Type[] { ClassType.create(MISTRAL_EMBEDDING_MODEL_BUILDER) }, null) },
                                null), ANY)
                        .createWith(recorder.embeddingModel(configName));
                addQualifierIfNecessary(builder, configName);
                beanProducer.produce(builder.done());
            }
        }

        for (var selected : selectedModeration) {
            if (PROVIDER.equals(selected.getProvider())) {
                String configName = selected.getConfigName();
                var builder = SyntheticBeanBuildItem
                        .configure(MODERATION_MODEL)
                        .setRuntimeInit()
                        .defaultBean()
                        .scope(ApplicationScoped.class)
                        .addInjectionPoint(ParameterizedType.create(DotNames.CDI_INSTANCE,
                                new Type[] { ParameterizedType.create(DotNames.MODEL_BUILDER_CUSTOMIZER,
                                        new Type[] { ClassType.create(MISTRAL_MODERATION_MODEL_BUILDER) }, null) },
                                null), ANY)
                        .createWith(recorder.moderationModel(configName));
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

    /**
     * When both {@code rest-client-jackson} and {@code rest-client-jsonb} are present on the classpath we need to make sure
     * that Jackson is used.
     * This is not a proper solution as it affects all clients, but it's better than the having the reader/writers be selected
     * at random.
     */
    @BuildStep
    public void deprioritizeJsonb(Capabilities capabilities,
            BuildProducer<MessageBodyReaderOverrideBuildItem> readerOverrideProducer,
            BuildProducer<MessageBodyWriterOverrideBuildItem> writerOverrideProducer) {
        if (capabilities.isPresent(Capability.REST_CLIENT_REACTIVE_JSONB)) {
            readerOverrideProducer.produce(
                    new MessageBodyReaderOverrideBuildItem("org.jboss.resteasy.reactive.server.jsonb.JsonbMessageBodyReader",
                            Priorities.APPLICATION + 1, true));
            writerOverrideProducer.produce(new MessageBodyWriterOverrideBuildItem(
                    "org.jboss.resteasy.reactive.server.jsonb.JsonbMessageBodyWriter", Priorities.APPLICATION + 1, true));
        }
    }
}
