package io.quarkiverse.langchain4j.deployment;

import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.CHAT_MODEL;
import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.EMBEDDING_MODEL;
import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.IMAGE_MODEL;
import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.MODEL_NAME;
import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.MODERATION_MODEL;
import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.SCORING_MODEL;
import static io.quarkiverse.langchain4j.deployment.LangChain4jDotNames.STREAMING_CHAT_MODEL;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.DotName;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.model.scoring.ScoringModel;
import io.quarkiverse.langchain4j.auth.ModelAuthProvider;
import io.quarkiverse.langchain4j.deployment.config.LangChain4jBuildConfig;
import io.quarkiverse.langchain4j.deployment.items.AutoCreateEmbeddingModelBuildItem;
import io.quarkiverse.langchain4j.deployment.items.ChatModelProviderCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.EmbeddingModelProviderCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.ImageModelProviderCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.ImplicitlyUserConfiguredChatProviderBuildItem;
import io.quarkiverse.langchain4j.deployment.items.InProcessEmbeddingBuildItem;
import io.quarkiverse.langchain4j.deployment.items.ModerationModelProviderCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.ProviderHolder;
import io.quarkiverse.langchain4j.deployment.items.ScoringModelProviderCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedChatModelProviderBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedEmbeddingModelCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedImageModelProviderBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedModerationModelProviderBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedScoringModelProviderBuildItem;
import io.quarkiverse.langchain4j.runtime.LangChain4jRecorder;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.arc.deployment.BeanDiscoveryFinishedBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.arc.processor.BeanStream;
import io.quarkus.arc.processor.InjectionPointInfo;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.IndexDependencyBuildItem;
import io.quarkus.deployment.builditem.ShutdownContextBuildItem;
import io.quarkus.deployment.builditem.nativeimage.RuntimeInitializedClassBuildItem;
import io.quarkus.deployment.logging.LogCleanupFilterBuildItem;
import io.quarkus.runtime.configuration.ConfigurationException;

public class BeansProcessor {

    private static final String FEATURE = "langchain4j";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void indexDependencies(BuildProducer<IndexDependencyBuildItem> producer) {
        producer.produce(new IndexDependencyBuildItem("dev.langchain4j", "langchain4j-core"));
    }

    @BuildStep
    public void handleProviders(BeanDiscoveryFinishedBuildItem beanDiscoveryFinished,
            List<ChatModelProviderCandidateBuildItem> chatCandidateItems,
            List<ScoringModelProviderCandidateBuildItem> scoringCandidateItems,
            List<EmbeddingModelProviderCandidateBuildItem> embeddingCandidateItems,
            List<ModerationModelProviderCandidateBuildItem> moderationCandidateItems,
            List<ImageModelProviderCandidateBuildItem> imageCandidateItems,
            List<RequestChatModelBeanBuildItem> requestChatModelBeanItems,
            List<RequestModerationModelBeanBuildItem> requestModerationModelBeanBuildItems,
            List<RequestImageModelBeanBuildItem> requestImageModelBeanBuildItems,
            List<ImplicitlyUserConfiguredChatProviderBuildItem> userConfiguredProviderBuildItems,
            LangChain4jBuildConfig buildConfig,
            Optional<AutoCreateEmbeddingModelBuildItem> autoCreateEmbeddingModelBuildItem,
            BuildProducer<SelectedChatModelProviderBuildItem> selectedChatProducer,
            BuildProducer<SelectedScoringModelProviderBuildItem> selectedScoringProducer,
            BuildProducer<SelectedEmbeddingModelCandidateBuildItem> selectedEmbeddingProducer,
            BuildProducer<SelectedModerationModelProviderBuildItem> selectedModerationProducer,
            BuildProducer<SelectedImageModelProviderBuildItem> selectedImageProducer,
            List<InProcessEmbeddingBuildItem> inProcessEmbeddingBuildItems) {

        Set<String> requestedChatModels = new HashSet<>();
        Set<String> requestedStreamingChatModels = new HashSet<>();
        Set<String> requestScoringModels = new HashSet<>();
        Set<String> requestEmbeddingModels = new HashSet<>();
        Set<String> requestedModerationModels = new HashSet<>();
        Set<String> requestedImageModels = new HashSet<>();
        Set<String> tokenCountEstimators = new HashSet<>();

        // detection of injection points for default models
        boolean defaultChatModelRequested = false;
        boolean defaultScoringModelRequested = false;
        boolean defaultEmbeddingModelRequested = false;
        boolean defaultModerationModelRequested = false;
        boolean defaultImageModelRequested = false;

        // default model names
        final String chatModelConfigNamespace = "chat-model";
        final String embeddingModelConfigNamespace = "embedding-model";
        final String scoringModelConfigNamespace = "scoring-model";
        final String moderationModelConfigNamespace = "moderation-model";
        final String imageModelConfigNamespace = "image-model";

        // separator symbol for named configs
        final String dot = ".";

        // bean types for models
        final String chatModelBeanType = "ChatLanguageModel or StreamingChatLanguageModel";
        final String embeddingModelBeanType = "EmbeddingModel";
        final String scoringModelBeanType = "ScoringModel";
        final String moderationModelBeanType = "ModerationModel";
        final String imageModelBeanType = "ImageModel";

        for (InjectionPointInfo ip : beanDiscoveryFinished.getInjectionPoints()) {
            DotName requiredName = ip.getRequiredType().name();
            String modelName = determineModelName(ip);
            if (modelName == null) {
                continue;
            }
            if (CHAT_MODEL.equals(requiredName)) {
                requestedChatModels.add(modelName);
            } else if (STREAMING_CHAT_MODEL.equals(requiredName)) {
                requestedStreamingChatModels.add(modelName);
            } else if (SCORING_MODEL.equals(requiredName)) {
                requestScoringModels.add(modelName);
            } else if (EMBEDDING_MODEL.equals(requiredName)) {
                requestEmbeddingModels.add(modelName);
            } else if (MODERATION_MODEL.equals(requiredName)) {
                requestedModerationModels.add(modelName);
            } else if (IMAGE_MODEL.equals(requiredName)) {
                requestedImageModels.add(modelName);
            }
        }
        for (

        var bi : requestChatModelBeanItems) {
            requestedChatModels.add(bi.getConfigName());
        }
        for (var bi : requestModerationModelBeanBuildItems) {
            requestedModerationModels.add(bi.getConfigName());
        }
        for (var bi : requestImageModelBeanBuildItems) {
            requestedImageModels.add(bi.getConfigName());
        }

        if (!requestedChatModels.isEmpty() || !requestedStreamingChatModels.isEmpty() || !tokenCountEstimators.isEmpty()) {
            Set<String> allChatModelNames = new HashSet<>(requestedChatModels);
            allChatModelNames.addAll(requestedStreamingChatModels);
            allChatModelNames.addAll(tokenCountEstimators);
            for (String modelName : allChatModelNames) {
                Optional<String> userSelectedProvider;
                String configNamespace;
                if (NamedConfigUtil.isDefault(modelName)) {
                    userSelectedProvider = buildConfig.defaultConfig().chatModel().provider();
                    configNamespace = chatModelConfigNamespace;
                    defaultChatModelRequested = true;
                } else {
                    if (buildConfig.namedConfig().containsKey(modelName)) {
                        userSelectedProvider = buildConfig.namedConfig().get(modelName).chatModel().provider();
                    } else {
                        userSelectedProvider = Optional.empty();
                    }
                    configNamespace = modelName + dot + chatModelConfigNamespace;
                }
                if (userSelectedProvider.isEmpty() && !NamedConfigUtil.isDefault(modelName)) {
                    // let's see if the user has configured a model name for one of the named providers
                    List<ImplicitlyUserConfiguredChatProviderBuildItem> matchingImplicitlyUserConfiguredChatProviders = userConfiguredProviderBuildItems
                            .stream().filter(bi -> bi.getConfigName().equals(modelName))
                            .toList();
                    if (matchingImplicitlyUserConfiguredChatProviders.size() == 1) {
                        userSelectedProvider = Optional.of(matchingImplicitlyUserConfiguredChatProviders.get(0).getProvider());
                    }
                }

                String provider = selectProvider(
                        chatCandidateItems,
                        beanDiscoveryFinished.beanStream().withBeanType(ChatLanguageModel.class),
                        userSelectedProvider,
                        chatModelBeanType,
                        configNamespace);
                if (provider != null) {
                    selectedChatProducer.produce(new SelectedChatModelProviderBuildItem(provider, modelName));
                }
            }

        }

        for (String modelName : requestScoringModels) {
            Optional<String> userSelectedProvider;
            String configNamespace;
            if (NamedConfigUtil.isDefault(modelName)) {
                userSelectedProvider = buildConfig.defaultConfig().scoringModel().provider();
                configNamespace = scoringModelConfigNamespace;
                defaultScoringModelRequested = true;
            } else {
                if (buildConfig.namedConfig().containsKey(modelName)) {
                    userSelectedProvider = buildConfig.namedConfig().get(modelName).scoringModel().provider();
                } else {
                    userSelectedProvider = Optional.empty();
                }
                configNamespace = modelName + dot + scoringModelConfigNamespace;
            }

            String provider = selectProvider(
                    scoringCandidateItems,
                    beanDiscoveryFinished.beanStream().withBeanType(ScoringModel.class),
                    userSelectedProvider,
                    scoringModelBeanType,
                    configNamespace);
            if (provider != null) {
                selectedScoringProducer.produce(new SelectedScoringModelProviderBuildItem(provider, modelName));
            }
        }

        for (String modelName : requestEmbeddingModels) {
            Optional<String> userSelectedProvider;
            String configNamespace;
            if (NamedConfigUtil.isDefault(modelName)) {
                userSelectedProvider = buildConfig.defaultConfig().embeddingModel().provider();
                configNamespace = embeddingModelConfigNamespace;
                defaultEmbeddingModelRequested = true;
            } else {
                if (buildConfig.namedConfig().containsKey(modelName)) {
                    userSelectedProvider = buildConfig.namedConfig().get(modelName).embeddingModel().provider();
                } else {
                    userSelectedProvider = Optional.empty();
                }
                configNamespace = modelName + dot + embeddingModelConfigNamespace;
            }

            String provider = selectEmbeddingModelProvider(
                    inProcessEmbeddingBuildItems,
                    embeddingCandidateItems,
                    beanDiscoveryFinished.beanStream().withBeanType(EmbeddingModel.class),
                    userSelectedProvider,
                    embeddingModelBeanType,
                    configNamespace);
            if (provider != null) {
                selectedEmbeddingProducer.produce(new SelectedEmbeddingModelCandidateBuildItem(provider, modelName));
            }
        }
        // If the Easy RAG extension requested to automatically generate an embedding model...
        if (requestEmbeddingModels.isEmpty() && autoCreateEmbeddingModelBuildItem.isPresent()) {
            // in case multiple embedding model providers are available,
            // the user has to specify `quarkus.langchain4j.embedding-model.provider` to choose one
            Optional<String> userSelectedProvider = buildConfig.defaultConfig().embeddingModel().provider();
            if (userSelectedProvider.isEmpty()) {
                String provider = selectEmbeddingModelProvider(inProcessEmbeddingBuildItems, embeddingCandidateItems,
                        beanDiscoveryFinished.beanStream().withBeanType(EmbeddingModel.class),
                        userSelectedProvider, embeddingModelBeanType, embeddingModelConfigNamespace);
                selectedEmbeddingProducer
                        .produce(new SelectedEmbeddingModelCandidateBuildItem(provider, NamedConfigUtil.DEFAULT_NAME));
            }
            // else: if the user actually selected a provider, the model will be registered automatically below anyway
        }

        for (String modelName : requestedModerationModels) {
            Optional<String> userSelectedProvider;
            String configNamespace;
            if (NamedConfigUtil.isDefault(modelName)) {
                userSelectedProvider = buildConfig.defaultConfig().moderationModel().provider();
                configNamespace = moderationModelConfigNamespace;
                defaultModerationModelRequested = true;
            } else {
                if (buildConfig.namedConfig().containsKey(modelName)) {
                    userSelectedProvider = buildConfig.namedConfig().get(modelName).moderationModel().provider();
                } else {
                    userSelectedProvider = Optional.empty();
                }
                configNamespace = modelName + dot + moderationModelConfigNamespace;
            }

            String provider = selectProvider(
                    moderationCandidateItems,
                    beanDiscoveryFinished.beanStream().withBeanType(ModerationModel.class),
                    userSelectedProvider,
                    moderationModelBeanType,
                    configNamespace);
            if (provider != null) {
                selectedModerationProducer.produce(new SelectedModerationModelProviderBuildItem(provider, modelName));
            }
        }

        for (String modelName : requestedImageModels) {
            Optional<String> userSelectedProvider;
            String configNamespace;
            if (NamedConfigUtil.isDefault(modelName)) {
                userSelectedProvider = buildConfig.defaultConfig().imageModel().provider();
                configNamespace = imageModelConfigNamespace;
                defaultImageModelRequested = true;
            } else {
                if (buildConfig.namedConfig().containsKey(modelName)) {
                    userSelectedProvider = buildConfig.namedConfig().get(modelName).imageModel().provider();
                } else {
                    userSelectedProvider = Optional.empty();
                }
                configNamespace = modelName + dot + imageModelConfigNamespace;
            }

            String provider = selectProvider(
                    imageCandidateItems,
                    beanDiscoveryFinished.beanStream().withBeanType(ImageModel.class),
                    userSelectedProvider,
                    imageModelBeanType,
                    configNamespace);
            if (provider != null) {
                selectedImageProducer.produce(new SelectedImageModelProviderBuildItem(provider, modelName));
            }
        }

        // There can be configured models for which we found no injection points.
        // While we cannot perform full validation of those, we can still add them as beans.
        // This enabled injection such as @Inject @Any Instance<ChatLanguageModel>

        // process default configuration
        LangChain4jBuildConfig.BaseConfig defaultConfig = buildConfig.defaultConfig();
        if (!defaultChatModelRequested && !defaultConfig.chatModel().provider().isEmpty()) {
            Optional<String> userSelectedProvider = defaultConfig.chatModel().provider();
            String provider = selectProvider(
                    chatCandidateItems,
                    beanDiscoveryFinished.beanStream().withBeanType(ChatLanguageModel.class),
                    userSelectedProvider,
                    chatModelBeanType,
                    chatModelConfigNamespace);
            if (provider != null) {
                selectedChatProducer.produce(new SelectedChatModelProviderBuildItem(provider, NamedConfigUtil.DEFAULT_NAME));
            }
        }
        if (!defaultEmbeddingModelRequested && !defaultConfig.embeddingModel().provider().isEmpty()) {
            Optional<String> userSelectedProvider = defaultConfig.embeddingModel().provider();
            String provider = selectEmbeddingModelProvider(
                    inProcessEmbeddingBuildItems,
                    embeddingCandidateItems,
                    beanDiscoveryFinished.beanStream().withBeanType(EmbeddingModel.class),
                    userSelectedProvider,
                    embeddingModelBeanType,
                    embeddingModelConfigNamespace);
            if (provider != null) {
                selectedEmbeddingProducer
                        .produce(new SelectedEmbeddingModelCandidateBuildItem(provider, NamedConfigUtil.DEFAULT_NAME));
            }
        }

        if (!defaultScoringModelRequested && !defaultConfig.scoringModel().provider().isEmpty()) {
            Optional<String> userSelectedProvider = defaultConfig.scoringModel().provider();
            String provider = selectProvider(
                    scoringCandidateItems,
                    beanDiscoveryFinished.beanStream().withBeanType(ScoringModel.class),
                    userSelectedProvider,
                    scoringModelBeanType,
                    scoringModelConfigNamespace);
            if (provider != null) {
                selectedScoringProducer
                        .produce(new SelectedScoringModelProviderBuildItem(provider, NamedConfigUtil.DEFAULT_NAME));
            }
        }
        if (!defaultModerationModelRequested && !defaultConfig.moderationModel().provider().isEmpty()) {
            Optional<String> userSelectedProvider = defaultConfig.moderationModel().provider();
            String provider = selectProvider(
                    moderationCandidateItems,
                    beanDiscoveryFinished.beanStream().withBeanType(ModerationModel.class),
                    userSelectedProvider,
                    moderationModelBeanType,
                    moderationModelConfigNamespace);
            if (provider != null) {
                selectedModerationProducer
                        .produce(new SelectedModerationModelProviderBuildItem(provider, NamedConfigUtil.DEFAULT_NAME));
            }
        }
        if (!defaultImageModelRequested && !defaultConfig.imageModel().provider().isEmpty()) {
            Optional<String> userSelectedProvider = defaultConfig.imageModel().provider();
            String provider = selectProvider(
                    imageCandidateItems,
                    beanDiscoveryFinished.beanStream().withBeanType(ImageModel.class),
                    userSelectedProvider,
                    imageModelBeanType,
                    imageModelConfigNamespace);
            if (provider != null) {
                selectedImageProducer.produce(new SelectedImageModelProviderBuildItem(provider, NamedConfigUtil.DEFAULT_NAME));
            }
        }

        // process named configuration
        for (Map.Entry<String, LangChain4jBuildConfig.BaseConfig> entry : buildConfig.namedConfig().entrySet()) {
            LangChain4jBuildConfig.BaseConfig value = entry.getValue();
            if (!requestedStreamingChatModels.contains(entry.getKey()) &&
                    !requestedChatModels.contains(entry.getKey()) &&
                    !value.chatModel().provider().isEmpty()) {
                Optional<String> userSelectedProvider = value.chatModel().provider();
                String configNamespace = entry.getKey() + dot + chatModelConfigNamespace;
                String provider = selectProvider(
                        chatCandidateItems,
                        beanDiscoveryFinished.beanStream().withBeanType(ChatLanguageModel.class),
                        userSelectedProvider,
                        chatModelBeanType,
                        configNamespace);
                if (provider != null) {
                    selectedChatProducer.produce(new SelectedChatModelProviderBuildItem(provider, entry.getKey()));
                }
            }
            if (!requestEmbeddingModels.contains(entry.getKey()) && !value.embeddingModel().provider().isEmpty()) {
                Optional<String> userSelectedProvider = value.embeddingModel().provider();
                String configNamespace = entry.getKey() + dot + embeddingModelConfigNamespace;
                String provider = selectEmbeddingModelProvider(
                        inProcessEmbeddingBuildItems,
                        embeddingCandidateItems,
                        beanDiscoveryFinished.beanStream().withBeanType(EmbeddingModel.class),
                        userSelectedProvider,
                        embeddingModelBeanType,
                        configNamespace);
                if (provider != null) {
                    selectedEmbeddingProducer.produce(new SelectedEmbeddingModelCandidateBuildItem(provider, entry.getKey()));
                }
            }
            if (!requestScoringModels.contains(entry.getKey()) && !value.scoringModel().provider().isEmpty()) {
                Optional<String> userSelectedProvider = value.scoringModel().provider();
                String configNamespace = entry.getKey() + dot + scoringModelConfigNamespace;
                String provider = selectProvider(
                        scoringCandidateItems,
                        beanDiscoveryFinished.beanStream().withBeanType(ScoringModel.class),
                        userSelectedProvider,
                        scoringModelBeanType,
                        configNamespace);
                if (provider != null) {
                    selectedScoringProducer.produce(new SelectedScoringModelProviderBuildItem(provider, entry.getKey()));
                }
            }
            if (!requestedModerationModels.contains(entry.getKey()) && !value.moderationModel().provider().isEmpty()) {
                Optional<String> userSelectedProvider = value.moderationModel().provider();
                String configNamespace = entry.getKey() + dot + moderationModelConfigNamespace;
                String provider = selectProvider(
                        moderationCandidateItems,
                        beanDiscoveryFinished.beanStream().withBeanType(ModerationModel.class),
                        userSelectedProvider,
                        moderationModelBeanType,
                        configNamespace);
                if (provider != null) {
                    selectedModerationProducer.produce(new SelectedModerationModelProviderBuildItem(provider, entry.getKey()));
                }
            }
            if (!requestedImageModels.contains(entry.getKey()) && !value.imageModel().provider().isEmpty()) {
                Optional<String> userSelectedProvider = value.imageModel().provider();
                String configNamespace = entry.getKey() + dot + imageModelConfigNamespace;
                String provider = selectProvider(
                        imageCandidateItems,
                        beanDiscoveryFinished.beanStream().withBeanType(ImageModel.class),
                        userSelectedProvider,
                        imageModelBeanType,
                        configNamespace);
                if (provider != null) {
                    selectedImageProducer.produce(new SelectedImageModelProviderBuildItem(provider, entry.getKey()));
                }
            }
        }

    }

    private String determineModelName(InjectionPointInfo ip) {
        AnnotationInstance modelNameInstance = ip.getRequiredQualifier(MODEL_NAME);
        if (modelNameInstance != null) {
            String value = modelNameInstance.value().asString();
            if ((value != null) && !value.isEmpty()) {
                return value;
            }
        }
        // @Inject @Any Instance<Foo> should not be treated as default name
        if (modelNameInstance == null && ip.isProgrammaticLookup()) {
            return null;
        }
        return NamedConfigUtil.DEFAULT_NAME;
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private <T extends ProviderHolder> String selectProvider(
            List<T> candidateItems,
            BeanStream beanStream,
            Optional<String> userSelectedProvider,
            String beanType,
            String configNamespace) {
        List<String> availableProviders = candidateItems.stream().map(ProviderHolder::getProvider)
                .collect(Collectors.toList());
        if (availableProviders.isEmpty()) {
            if (beanStream.collect().isEmpty()) {
                throw new ConfigurationException(String.format(
                        "A %s bean was requested, but no langchain4j providers were configured. Consider adding an extension like 'quarkus-langchain4j-openai'",
                        beanType));
            }
            // a user provided bean exists, so there is no need to fail
            return null;
        }
        if (availableProviders.size() == 1) {
            // user has selected a provider, but it's not the one that is available
            if (userSelectedProvider.isPresent() && !availableProviders.get(0).equals(userSelectedProvider.get())) {
                throw new ConfigurationException(String.format(
                        "A %s bean with provider=%s was requested was requested via configuration, but the only provider found on the classpath is %s.",
                        beanType, userSelectedProvider.get(), availableProviders.get(0)));
            }
            return availableProviders.get(0);
        }

        if (userSelectedProvider.isEmpty()) {
            if (beanStream.collect().isEmpty()) {
                // multiple providers exist, so we now need the configuration to select the proper one
                throw new ConfigurationException(String.format(
                        "A %s bean was requested, but since there are multiple available providers, the 'quarkus.langchain4j.%s.provider' needs to be set to one of the available options (%s).",
                        beanType, configNamespace, String.join(",", availableProviders)));
            }
            // a user provided bean exists, so there is no need to fail
            return null;
        }
        boolean matches = availableProviders.stream().anyMatch(ap -> ap.equals(userSelectedProvider.get()));
        if (matches) {
            return userSelectedProvider.get();
        }
        if (beanStream.collect().isEmpty()) {
            throw new ConfigurationException(String.format(
                    "A %s bean was requested, but the value of 'quarkus.langchain4j.%s.provider' does not match any of the available options (%s).",
                    beanType, configNamespace, String.join(",", availableProviders)));
        }
        // a user provided bean exists, so there is no need to fail
        return null;
    }

    private <T extends ProviderHolder> String selectEmbeddingModelProvider(
            List<InProcessEmbeddingBuildItem> inProcessEmbeddingBuildItems,
            List<T> chatCandidateItems,
            BeanStream beanStream,
            Optional<String> userSelectedProvider,
            String requestedBeanName,
            String configNamespace) {
        List<String> availableProviders = chatCandidateItems.stream().map(ProviderHolder::getProvider)
                .collect(Collectors.toList());
        availableProviders.addAll(inProcessEmbeddingBuildItems.stream().map(InProcessEmbeddingBuildItem::getProvider)
                .toList());
        if (availableProviders.isEmpty()) {
            if (beanStream.collect().isEmpty()) {
                throw new ConfigurationException(String.format(
                        "A %s bean was requested, but no langchain4j providers were configured and no in-process embedding model were found on the classpath. "
                                +
                                "Consider adding an extension like 'quarkus-langchain4j-openai' or one of the in-process embedding models.",
                        requestedBeanName));
            }
            // a user provided bean exists, so there is no need to fail
            return null;
        }
        if (availableProviders.size() == 1) {
            // user has selected a provider, but it's not the one that is available
            if (userSelectedProvider.isPresent() && !availableProviders.get(0).equals(userSelectedProvider.get())) {
                throw new ConfigurationException(String.format(
                        "Embedding model provider %s was requested via configuration, but the only provider found on the classpath is %s.",
                        userSelectedProvider.get(), availableProviders.get(0)));
            }
            return availableProviders.get(0);
        }

        if (userSelectedProvider.isEmpty()) {
            if (beanStream.collect().isEmpty()) {
                // multiple providers exist, so we now need the configuration to select the proper one
                throw new ConfigurationException(String.format(
                        "A %s bean was requested, but since there are multiple available providers, the 'quarkus.langchain4j.%s.provider' needs to be set to one of the available options (%s).",
                        requestedBeanName, configNamespace, String.join(",", availableProviders)));
            }
            // a user provided bean exists, so there is no need to fail
            return null;
        }
        boolean matches = availableProviders.stream().anyMatch(ap -> ap.equals(userSelectedProvider.get()));
        if (matches) {
            return userSelectedProvider.get();
        }
        if (beanStream.collect().isEmpty()) {
            throw new ConfigurationException(String.format(
                    "A %s bean was requested, but the value of 'quarkus.langchain4j.%s.provider' does not match any of the available options (%s).",
                    requestedBeanName, configNamespace, String.join(",", availableProviders)));
        }
        // a user provided bean exists, so there is no need to fail
        return null;
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public void cleanUp(LangChain4jRecorder recorder, ShutdownContextBuildItem shutdown) {
        recorder.cleanUp(shutdown);
    }

    @BuildStep
    public void unremovableBeans(BuildProducer<UnremovableBeanBuildItem> unremovableProducer) {
        unremovableProducer.produce(UnremovableBeanBuildItem.beanTypes(ObjectMapper.class));
        unremovableProducer.produce(UnremovableBeanBuildItem.beanTypes(ModelAuthProvider.class));
    }

    @BuildStep
    void logCleanupFilters(BuildProducer<LogCleanupFilterBuildItem> logCleanupFilters) {
        logCleanupFilters
                .produce(new LogCleanupFilterBuildItem("ai.djl.util.Platform", Level.INFO, "Found matching platform from"));
        logCleanupFilters
                .produce(new LogCleanupFilterBuildItem("ai.djl.huggingface.tokenizers.jni.LibUtils", Level.INFO, "Extracting"));
    }

    @BuildStep
    public void nativeSupport(BuildProducer<RuntimeInitializedClassBuildItem> producer) {
        // RetryUtils initializes a java.lang.Random instance
        producer.produce(new RuntimeInitializedClassBuildItem("dev.langchain4j.internal.RetryUtils"));
    }
}
