package io.quarkiverse.langchain4j.openai.deployment;

import static io.quarkiverse.langchain4j.deployment.Langchain4jDotNames.CHAT_MODEL;
import static io.quarkiverse.langchain4j.deployment.Langchain4jDotNames.EMBEDDING_MODEL;
import static io.quarkiverse.langchain4j.deployment.Langchain4jDotNames.MODERATION_MODEL;
import static io.quarkiverse.langchain4j.deployment.Langchain4jDotNames.STREAMING_CHAT_MODEL;

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

import com.knuddels.jtokkit.Encodings;

import io.quarkiverse.langchain4j.deployment.items.ChatModelProviderCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.EmbeddingModelProviderCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.ModerationModelProviderCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedChatModelProviderBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedEmbeddingModelCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedModerationModelProviderBuildItem;
import io.quarkiverse.langchain4j.openai.runtime.OpenAiRecorder;
import io.quarkiverse.langchain4j.openai.runtime.config.Langchain4jOpenAiConfig;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.IndexDependencyBuildItem;
import io.quarkus.deployment.builditem.ShutdownContextBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;

public class OpenAiProcessor {

    private static final String FEATURE = "langchain4j-openai";
    private static final String PROVIDER = "openai";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void indexDependencies(BuildProducer<IndexDependencyBuildItem> producer) {
        producer.produce(new IndexDependencyBuildItem("dev.ai4j", "openai4j"));
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
    public void providerCandidates(BuildProducer<ChatModelProviderCandidateBuildItem> chatProducer,
            BuildProducer<EmbeddingModelProviderCandidateBuildItem> embeddingProducer,
            BuildProducer<ModerationModelProviderCandidateBuildItem> moderationProducer) {
        chatProducer.produce(new ChatModelProviderCandidateBuildItem(PROVIDER));
        embeddingProducer.produce(new EmbeddingModelProviderCandidateBuildItem(PROVIDER));
        moderationProducer.produce(new ModerationModelProviderCandidateBuildItem(PROVIDER));
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void generateBeans(OpenAiRecorder recorder,
            Optional<SelectedChatModelProviderBuildItem> selectedChatItem,
            Optional<SelectedEmbeddingModelCandidateBuildItem> selectedEmbedding,
            Optional<SelectedModerationModelProviderBuildItem> selectedModeration,
            Langchain4jOpenAiConfig config,
            BuildProducer<SyntheticBeanBuildItem> beanProducer) {
        if (selectedChatItem.isPresent() && PROVIDER.equals(selectedChatItem.get().getProvider())) {
            beanProducer.produce(SyntheticBeanBuildItem
                    .configure(CHAT_MODEL)
                    .setRuntimeInit()
                    .defaultBean()
                    .scope(ApplicationScoped.class)
                    .supplier(recorder.chatModel(config))
                    .done());

            beanProducer.produce(SyntheticBeanBuildItem
                    .configure(STREAMING_CHAT_MODEL)
                    .setRuntimeInit()
                    .defaultBean()
                    .scope(ApplicationScoped.class)
                    .supplier(recorder.streamingChatModel(config))
                    .done());
        }

        if (selectedEmbedding.isPresent() && PROVIDER.equals(selectedEmbedding.get().getProvider())) {
            beanProducer.produce(SyntheticBeanBuildItem
                    .configure(EMBEDDING_MODEL)
                    .setRuntimeInit()
                    .defaultBean()
                    .scope(ApplicationScoped.class)
                    .supplier(recorder.embeddingModel(config))
                    .done());
        }

        if (selectedModeration.isPresent() && PROVIDER.equals(selectedModeration.get().getProvider())) {
            beanProducer.produce(SyntheticBeanBuildItem
                    .configure(MODERATION_MODEL)
                    .setRuntimeInit()
                    .defaultBean()
                    .scope(ApplicationScoped.class)
                    .supplier(recorder.moderationModel(config))
                    .done());
        }
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public void cleanUp(OpenAiRecorder recorder, ShutdownContextBuildItem shutdown) {
        recorder.cleanUp(shutdown);
    }
}
