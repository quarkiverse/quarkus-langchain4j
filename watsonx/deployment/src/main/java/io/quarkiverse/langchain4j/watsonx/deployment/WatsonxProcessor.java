package io.quarkiverse.langchain4j.watsonx.deployment;

import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.CHAT_MODEL;
import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.EMBEDDING_MODEL;
import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.STREAMING_CHAT_MODEL;

import java.util.List;
import java.util.Objects;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;

import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.deployment.LangChain4jDotNames;
import io.quarkiverse.langchain4j.deployment.items.ChatModelProviderCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.EmbeddingModelProviderCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedChatModelProviderBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedEmbeddingModelCandidateBuildItem;
import io.quarkiverse.langchain4j.runtime.NamedModelUtil;
import io.quarkiverse.langchain4j.watsonx.annotation.Deployment;
import io.quarkiverse.langchain4j.watsonx.deployment.items.SelectedWatsonxChatModelProviderBuildItem;
import io.quarkiverse.langchain4j.watsonx.runtime.WatsonxRecorder;
import io.quarkiverse.langchain4j.watsonx.runtime.config.LangChain4jWatsonxConfig;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;

public class WatsonxProcessor {

    private static final String FEATURE = "langchain4j-watsonx";
    private static final String PROVIDER = "watsonx";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    public void providerCandidates(BuildProducer<ChatModelProviderCandidateBuildItem> chatProducer,
            BuildProducer<EmbeddingModelProviderCandidateBuildItem> embeddingProducer,
            LangChain4jWatsonBuildConfig config) {

        if (config.chatModel().enabled().isEmpty() || config.chatModel().enabled().get()) {
            chatProducer.produce(new ChatModelProviderCandidateBuildItem(PROVIDER));
        }

        if (config.embeddingModel().enabled().isEmpty() || config.embeddingModel().enabled().get()) {
            embeddingProducer.produce(new EmbeddingModelProviderCandidateBuildItem(PROVIDER));
        }
    }

    @BuildStep
    void selectDeploymentBeansCandidates(CombinedIndexBuildItem indexBuildItem,
            List<SelectedChatModelProviderBuildItem> selectedChatItem,
            BuildProducer<SelectedWatsonxChatModelProviderBuildItem> builderBeans) {

        // The goal of this method is to find all classes of the watsonx module annotated with @Deployment, filtered by the modelName
        // attribute of the @RegisterAiService annotation.

        IndexView index = indexBuildItem.getIndex();
        var instances = index.getAnnotations(Deployment.class);

        for (var selected : selectedChatItem) {
            if (!PROVIDER.equals(selected.getProvider()))
                continue;

            boolean created = false;
            for (AnnotationInstance instance : instances) {
                ClassInfo declarativeAiServiceClassInfo = instance.target().asClass();
                var registerAiService = declarativeAiServiceClassInfo.annotation(LangChain4jDotNames.REGISTER_AI_SERVICES);
                if (registerAiService == null)
                    continue;

                var modelName = registerAiService.value("modelName");

                if (NamedModelUtil.isDefault(selected.getModelName()) && Objects.nonNull(modelName)
                        && !modelName.asString().equals(NamedModelUtil.DEFAULT_NAME))
                    continue;

                if (!NamedModelUtil.isDefault(selected.getModelName())
                        && (Objects.isNull(modelName) || !modelName.asString().equals(selected.getModelName())))
                    continue;

                if (checkIfLangchain4jAnnotationsExist(declarativeAiServiceClassInfo))
                    throw new RuntimeException(
                            "The class %s cannot be annotated with @SystemMessage/@UserMessage if the annotation @Deployment is present"
                                    .formatted(declarativeAiServiceClassInfo.name()));

                for (MethodInfo method : declarativeAiServiceClassInfo.methods()) {
                    if (checkIfLangchain4jAnnotationsExist(method))
                        throw new RuntimeException(
                                "The method %s cannot be annotated with @SystemMessage/@UserMessage if the annotation @Deployment is present"
                                        .formatted(method.name()));
                }

                builderBeans.produce(new SelectedWatsonxChatModelProviderBuildItem(
                        selected.getProvider(), selected.getModelName(), instance.value().asString()));

                created = true;
            }

            if (!created)
                builderBeans.produce(new SelectedWatsonxChatModelProviderBuildItem(
                        selected.getProvider(), selected.getModelName(), null));
        }
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void generateBeans(WatsonxRecorder recorder, LangChain4jWatsonxConfig config,
            List<SelectedWatsonxChatModelProviderBuildItem> selectedChatItem,
            List<SelectedEmbeddingModelCandidateBuildItem> selectedEmbedding,
            BuildProducer<SyntheticBeanBuildItem> beanProducer) {

        for (var selected : selectedChatItem) {
            String modelName = selected.getModelName();

            var chatBuilder = SyntheticBeanBuildItem
                    .configure(CHAT_MODEL)
                    .setRuntimeInit()
                    .defaultBean()
                    .scope(ApplicationScoped.class)
                    .supplier(recorder.chatModel(config, modelName, selected.getDeployment()));
            addQualifierIfNecessary(chatBuilder, modelName);
            beanProducer.produce(chatBuilder.done());

            var streamingBuilder = SyntheticBeanBuildItem
                    .configure(STREAMING_CHAT_MODEL)
                    .setRuntimeInit()
                    .defaultBean()
                    .scope(ApplicationScoped.class)
                    .supplier(recorder.streamingChatModel(config, modelName, selected.getDeployment()));
            addQualifierIfNecessary(streamingBuilder, modelName);
            beanProducer.produce(streamingBuilder.done());
        }

        for (var selected : selectedEmbedding) {
            if (PROVIDER.equals(selected.getProvider())) {
                String modelName = selected.getModelName();
                var builder = SyntheticBeanBuildItem
                        .configure(EMBEDDING_MODEL)
                        .setRuntimeInit()
                        .defaultBean()
                        .unremovable()
                        .scope(ApplicationScoped.class)
                        .supplier(recorder.embeddingModel(config, modelName));
                addQualifierIfNecessary(builder, modelName);
                beanProducer.produce(builder.done());
            }
        }

    }

    private boolean checkIfLangchain4jAnnotationsExist(Object obj) {

        if (obj instanceof ClassInfo classInfo) {

            if (classInfo.hasAnnotation(LangChain4jDotNames.SYSTEM_MESSAGE)
                    || classInfo.hasAnnotation(LangChain4jDotNames.USER_MESSAGE))
                return true;
        }

        if (obj instanceof MethodInfo method) {

            if (method.hasAnnotation(LangChain4jDotNames.SYSTEM_MESSAGE)
                    || method.hasAnnotation(LangChain4jDotNames.USER_MESSAGE))
                return true;
        }

        return false;
    }

    private void addQualifierIfNecessary(SyntheticBeanBuildItem.ExtendedBeanConfigurator builder, String modelName) {
        if (!NamedModelUtil.isDefault(modelName)) {
            builder.addQualifier(AnnotationInstance.builder(ModelName.class).add("value", modelName).build());
        }
    }
}
