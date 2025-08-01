package io.quarkiverse.langchain4j.ollama.deployment;

import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.CHAT_MODEL;
import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.EMBEDDING_MODEL;
import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.STREAMING_CHAT_MODEL;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.ParameterizedType;
import org.jboss.jandex.Type;

import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.deployment.DotNames;
import io.quarkiverse.langchain4j.deployment.devservice.Langchain4jDevServicesEnabled;
import io.quarkiverse.langchain4j.deployment.items.ChatModelProviderCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.DevServicesChatModelRequiredBuildItem;
import io.quarkiverse.langchain4j.deployment.items.DevServicesEmbeddingModelRequiredBuildItem;
import io.quarkiverse.langchain4j.deployment.items.EmbeddingModelProviderCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.ImplicitlyUserConfiguredChatProviderBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedChatModelProviderBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedEmbeddingModelCandidateBuildItem;
import io.quarkiverse.langchain4j.ollama.runtime.OllamaRecorder;
import io.quarkiverse.langchain4j.ollama.runtime.config.LangChain4jOllamaFixedRuntimeConfig;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;
import io.quarkus.deployment.IsNormal;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.IndexDependencyBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveHierarchyBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ServiceProviderBuildItem;
import io.quarkus.resteasy.reactive.spi.MessageBodyReaderOverrideBuildItem;
import io.quarkus.resteasy.reactive.spi.MessageBodyWriterOverrideBuildItem;
import io.smallrye.config.ConfigSourceInterceptor;
import io.smallrye.config.ConfigValue;
import io.smallrye.config.Priorities;
import io.smallrye.config.SmallRyeConfig;

public class OllamaProcessor {

    private static final String FEATURE = "langchain4j-ollama";
    private static final String PROVIDER = "ollama";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    IndexDependencyBuildItem indexUpstreamOllamaModule() {
        return new IndexDependencyBuildItem("dev.langchain4j", "langchain4j-ollama");
    }

    @BuildStep
    void nativeSupport(BuildProducer<ServiceProviderBuildItem> serviceProviderProducer,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClassProducer,
            BuildProducer<ReflectiveHierarchyBuildItem> reflectiveHierarchyProducer) {
        serviceProviderProducer
                .produce(ServiceProviderBuildItem.allProvidersFromClassPath(ConfigSourceInterceptor.class.getName()));
        reflectiveHierarchyProducer
                .produce(ReflectiveHierarchyBuildItem.builder("dev.langchain4j.model.ollama.OllamaChatRequest")
                        .source(getClass().getSimpleName()).build());
        reflectiveHierarchyProducer
                .produce(ReflectiveHierarchyBuildItem.builder("dev.langchain4j.model.ollama.OllamaChatResponse")
                        .source(getClass().getSimpleName()).ignoreNested(false).build());
        reflectiveClassProducer.produce(ReflectiveClassBuildItem
                .builder("dev.langchain4j.model.ollama.FormatSerializer", "dev.langchain4j.model.ollama.OllamaDateDeserializer")
                .constructors().methods(false).fields(false).build());
    }

    @BuildStep
    public void providerCandidates(BuildProducer<ChatModelProviderCandidateBuildItem> chatProducer,
            BuildProducer<EmbeddingModelProviderCandidateBuildItem> embeddingProducer,
            LangChain4jOllamaOpenAiBuildConfig config) {
        if (config.chatModel().enabled().isEmpty() || config.chatModel().enabled().get()) {
            chatProducer.produce(new ChatModelProviderCandidateBuildItem(PROVIDER));
        }
        if (config.embeddingModel().enabled().isEmpty() || config.embeddingModel().enabled().get()) {
            embeddingProducer.produce(new EmbeddingModelProviderCandidateBuildItem(PROVIDER));
        }
    }

    @BuildStep
    public void implicitlyConfiguredProviders(LangChain4jOllamaFixedRuntimeConfig fixedRuntimeConfig,
            BuildProducer<ImplicitlyUserConfiguredChatProviderBuildItem> producer) {
        fixedRuntimeConfig.namedConfig().keySet().forEach(configName -> {
            producer.produce(new ImplicitlyUserConfiguredChatProviderBuildItem(configName, PROVIDER));
        });
    }

    @BuildStep(onlyIfNot = IsNormal.class, onlyIf = Langchain4jDevServicesEnabled.class)
    public void devServicesSupport(List<SelectedChatModelProviderBuildItem> selectedChatModels,
            List<SelectedEmbeddingModelCandidateBuildItem> selectedEmbeddingModels,
            LangChain4jOllamaFixedRuntimeConfig fixedRuntimeConfig,
            BuildProducer<DevServicesChatModelRequiredBuildItem> chatProducer,
            BuildProducer<DevServicesEmbeddingModelRequiredBuildItem> embeddingProducer) {
        for (var selected : selectedChatModels) {
            if (PROVIDER.equals(selected.getProvider())) {
                String configName = selected.getConfigName();
                String baseUrlProperty = String.format("quarkus.langchain4j.ollama%s%s",
                        NamedConfigUtil.isDefault(configName) ? "." : ("." + configName + "."), "base-url");

                // same as with other dev services, we only need it if the base URL has not been set by the user
                if (canUseDevServices(baseUrlProperty)) {
                    String modelId = NamedConfigUtil.isDefault(configName)
                            ? fixedRuntimeConfig.defaultConfig().chatModel().modelId()
                            : fixedRuntimeConfig.namedConfig().get(configName).chatModel().modelId();
                    chatProducer.produce(new DevServicesChatModelRequiredBuildItem(PROVIDER, modelId, baseUrlProperty));
                }
            }
        }

        for (var selected : selectedEmbeddingModels) {
            if (PROVIDER.equals(selected.getProvider())) {
                String configName = selected.getConfigName();
                String baseUrlProperty = String.format("quarkus.langchain4j.ollama%s%s",
                        NamedConfigUtil.isDefault(configName) ? "." : ("." + configName + "."), "base-url");

                // same as with other dev services, we only need it if the base URL has not been set by the user
                if (canUseDevServices(baseUrlProperty)) {
                    String modelId = NamedConfigUtil.isDefault(configName)
                            ? fixedRuntimeConfig.defaultConfig().embeddingModel().modelId()
                            : fixedRuntimeConfig.namedConfig().get(configName).embeddingModel().modelId();
                    embeddingProducer
                            .produce(new DevServicesEmbeddingModelRequiredBuildItem(PROVIDER, modelId, baseUrlProperty));
                }
            }
        }
    }

    /**
     * DevServices can be used either when the user has not configured the base-url, or that URL points to a local instance
     */
    private boolean canUseDevServices(String baseUrlProperty) {
        SmallRyeConfig smallRyeConfig = ConfigProvider.getConfig().unwrap(SmallRyeConfig.class);
        ConfigValue configValue = smallRyeConfig.getConfigValue(baseUrlProperty);
        if (configValue.getValue() == null) {
            return true;
        }
        return configValue.getValue().startsWith("http://localhost");
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void generateBeans(OllamaRecorder recorder,
            List<SelectedChatModelProviderBuildItem> selectedChatItem,
            List<SelectedEmbeddingModelCandidateBuildItem> selectedEmbedding,
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
                                new Type[] { ClassType.create(DotNames.CHAT_MODEL_LISTENER) }, null))
                        .createWith(recorder.chatModel(configName));
                addQualifierIfNecessary(builder, configName);
                beanProducer.produce(builder.done());

                var streamingBuilder = SyntheticBeanBuildItem
                        .configure(STREAMING_CHAT_MODEL)
                        .setRuntimeInit()
                        .defaultBean()
                        .scope(ApplicationScoped.class)
                        .addInjectionPoint(ParameterizedType.create(DotNames.CDI_INSTANCE,
                                new Type[] { ClassType.create(DotNames.CHAT_MODEL_LISTENER) }, null))
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
