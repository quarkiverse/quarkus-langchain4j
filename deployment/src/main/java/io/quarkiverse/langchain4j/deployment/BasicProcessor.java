package io.quarkiverse.langchain4j.deployment;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.jandex.DotName;

import com.knuddels.jtokkit.Encodings;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.language.LanguageModel;
import dev.langchain4j.model.language.StreamingLanguageModel;
import dev.langchain4j.model.moderation.ModerationModel;
import io.quarkiverse.langchain4j.runtime.BasicRecorder;
import io.quarkiverse.langchain4j.runtime.config.LangChain4jBuildConfig;
import io.quarkiverse.langchain4j.runtime.config.LangChain4jRuntimeConfig;
import io.quarkiverse.langchain4j.runtime.config.ModelProvider;
import io.quarkus.arc.deployment.BeanDiscoveryFinishedBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.processor.InjectionPointInfo;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.IndexDependencyBuildItem;
import io.quarkus.deployment.builditem.ShutdownContextBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import io.quarkus.runtime.configuration.ConfigurationException;

class BasicProcessor {

    private static final String FEATURE = "langchain4j";
    private static final DotName CHAT_MODEL = DotName.createSimple(ChatLanguageModel.class);
    private static final DotName STREAMING_CHAT_MODEL = DotName.createSimple(StreamingChatLanguageModel.class);
    private static final DotName LANGUAGE_MODEL = DotName.createSimple(LanguageModel.class);
    private static final DotName STREAMING_LANGUAGE_MODEL = DotName.createSimple(StreamingLanguageModel.class);
    private static final DotName EMBEDDING_MODEL = DotName.createSimple(EmbeddingModel.class);
    private static final DotName MODERATION_MODEL = DotName.createSimple(ModerationModel.class);

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void indexDependencies(BuildProducer<IndexDependencyBuildItem> producer) {
        producer.produce(new IndexDependencyBuildItem("dev.ai4j", "openai4j"));
        producer.produce(new IndexDependencyBuildItem("dev.langchain4j", "langchain4j-core"));
    }

    @BuildStep
    void nativeImageSupport(BuildProducer<NativeImageResourceBuildItem> resourcesProducer) {
        registerJtokkitResources(resourcesProducer);
    }

    private void registerJtokkitResources(BuildProducer<NativeImageResourceBuildItem> resourcesProducer) {
        List<String> resources = new ArrayList<>();
        try (JarFile jarFile = new JarFile(determineJarLocation(Encodings.class).toFile())) {
            Enumeration<JarEntry> e = jarFile.entries();
            while (e.hasMoreElements()) {
                String name = e.nextElement().getName();
                if (name.endsWith(".tiktoken")) {
                    resources.add(name);
                }

            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        resourcesProducer.produce(new NativeImageResourceBuildItem(resources));
    }

    private static Path determineJarLocation(Class<?> classFromJar) {
        URL url = classFromJar.getProtectionDomain().getCodeSource().getLocation();
        if (!url.getProtocol().equals("file")) {
            throw new IllegalStateException("Unable to find which jar class " + classFromJar + " belongs to");
        }
        try {
            return Paths.get(url.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void generateBeans(LangChain4jBuildConfig buildConfig, LangChain4jRuntimeConfig runtimeConfig,
            BeanDiscoveryFinishedBuildItem beanDiscoveryFinished,
            BuildProducer<SyntheticBeanBuildItem> beanProducer,
            BasicRecorder recorder,
            ShutdownContextBuildItem shutdown) {

        boolean chatModelBeanRequested = false;
        boolean streamingChatModelBeanRequested = false;
        boolean languageModelBeanRequested = false;
        boolean streamingLanguageModelBeanRequested = false;
        boolean embeddingModelBeanRequested = false;
        boolean moderationModelBeanRequested = false;
        for (InjectionPointInfo ip : beanDiscoveryFinished.getInjectionPoints()) {
            DotName requiredName = ip.getRequiredType().name();
            if (CHAT_MODEL.equals(requiredName)) {
                chatModelBeanRequested = true;
            } else if (STREAMING_CHAT_MODEL.equals(requiredName)) {
                streamingChatModelBeanRequested = true;
            } else if (LANGUAGE_MODEL.equals(requiredName)) {
                languageModelBeanRequested = true;
            } else if (STREAMING_LANGUAGE_MODEL.equals(requiredName)) {
                streamingLanguageModelBeanRequested = true;
            } else if (EMBEDDING_MODEL.equals(requiredName)) {
                embeddingModelBeanRequested = true;
            } else if (MODERATION_MODEL.equals(requiredName)) {
                moderationModelBeanRequested = true;
            }
        }

        if (chatModelBeanRequested) {
            Optional<ModelProvider> provider = buildConfig.chatModel().provider();
            if (provider.isEmpty()) {
                throw new ConfigurationException(configErrorMessage(CHAT_MODEL, "chat-model"));
            }

            beanProducer.produce(SyntheticBeanBuildItem
                    .configure(CHAT_MODEL)
                    .setRuntimeInit()
                    .scope(ApplicationScoped.class)
                    .supplier(recorder.chatModel(provider.get(), runtimeConfig))
                    .done());
        }
        if (streamingChatModelBeanRequested) {
            Optional<ModelProvider> provider = buildConfig.chatModel().provider();
            if (provider.isEmpty()) {
                throw new ConfigurationException(configErrorMessage(CHAT_MODEL, "chat-model"));
            }

            beanProducer.produce(SyntheticBeanBuildItem
                    .configure(STREAMING_CHAT_MODEL)
                    .setRuntimeInit()
                    .scope(ApplicationScoped.class)
                    .supplier(recorder.streamingChatModel(provider.get(), runtimeConfig))
                    .done());
        }
        if (languageModelBeanRequested) {
            Optional<ModelProvider> provider = buildConfig.languageModel().provider();
            if (provider.isEmpty()) {
                throw new ConfigurationException(configErrorMessage(LANGUAGE_MODEL, "language-model"));
            }

            beanProducer.produce(SyntheticBeanBuildItem
                    .configure(LANGUAGE_MODEL)
                    .setRuntimeInit()
                    .scope(ApplicationScoped.class)
                    .supplier(recorder.languageModel(provider.get(), runtimeConfig))
                    .done());
        }
        if (streamingLanguageModelBeanRequested) {
            Optional<ModelProvider> provider = buildConfig.languageModel().provider();
            if (provider.isEmpty()) {
                throw new ConfigurationException(configErrorMessage(LANGUAGE_MODEL, "language-model"));
            }

            beanProducer.produce(SyntheticBeanBuildItem
                    .configure(STREAMING_LANGUAGE_MODEL)
                    .setRuntimeInit()
                    .scope(ApplicationScoped.class)
                    .supplier(recorder.streamingLanguageModel(provider.get(), runtimeConfig))
                    .done());
        }
        if (embeddingModelBeanRequested) {
            Optional<ModelProvider> provider = buildConfig.embeddingModel().provider();
            if (provider.isEmpty()) {
                throw new ConfigurationException(configErrorMessage(EMBEDDING_MODEL, "embedding-model"));
            }

            beanProducer.produce(SyntheticBeanBuildItem
                    .configure(EMBEDDING_MODEL)
                    .setRuntimeInit()
                    .scope(ApplicationScoped.class)
                    .supplier(recorder.embeddingModel(provider.get(), runtimeConfig))
                    .done());
        }
        if (moderationModelBeanRequested) {
            Optional<ModelProvider> provider = buildConfig.moderationModel().provider();
            if (provider.isEmpty()) {
                throw new ConfigurationException(configErrorMessage(MODERATION_MODEL, "moderation-model"));
            }
            if (ModelProvider.OPEN_AI != provider.get()) {
                throw new ConfigurationException("Currently 'openai' is the only supported provider of the moderation model");
            }
            beanProducer.produce(SyntheticBeanBuildItem
                    .configure(MODERATION_MODEL)
                    .setRuntimeInit()
                    .scope(ApplicationScoped.class)
                    .supplier(recorder.moderationModel(provider.get(), runtimeConfig))
                    .done());
        }
        recorder.cleanUp(shutdown);
    }

    private static String configErrorMessage(DotName beanType, String configNamespace) {
        return String.format(
                "When a bean of type '%s' is being used the 'quarkus.langchain4j.%s.provider' property must be set", beanType,
                configNamespace);
    }

}
