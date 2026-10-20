package io.quarkiverse.langchain4j.mockapi.deployment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Stream;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

import io.quarkiverse.langchain4j.deployment.config.LangChain4jBuildConfig;
import io.quarkiverse.langchain4j.deployment.items.AudioTranscriptionModelProviderCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.ChatModelProviderCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.DecisionModelProviderCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.EmbeddingModelProviderCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.ImageModelProviderCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.ModerationModelProviderCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.ProviderHolder;
import io.quarkiverse.langchain4j.deployment.items.ScoringModelProviderCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedAudioTranscriptionModelProviderBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedChatModelProviderBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedDecisionModelProviderBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedEmbeddingModelCandidateBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedImageModelProviderBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedModerationModelProviderBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedScoringModelProviderBuildItem;
import io.quarkiverse.langchain4j.mockapi.internal.MockModelServerProducer;
import io.quarkiverse.langchain4j.mockapi.internal.ProviderSpec;
import io.quarkiverse.langchain4j.mockapi.internal.ProviderSpecs;
import io.quarkiverse.langchain4j.mockapi.internal.config.MockApiConfigBuilder;
import io.quarkiverse.langchain4j.mockapi.internal.config.MockApiConfigSourceFactory;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.DialectRegistry;
import io.quarkiverse.langchain4j.mockapi.internal.server.MockApiStartable;
import io.quarkiverse.langchain4j.runtime.NamedConfigUtil;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.BeanDiscoveryFinishedBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Produce;
import io.quarkus.deployment.builditem.DevServicesResultBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.RunTimeConfigBuilderBuildItem;
import io.quarkus.deployment.builditem.ServiceStartBuildItem;
import io.quarkus.deployment.dev.devservices.DevServicesConfig;
import io.quarkus.runtime.configuration.ConfigurationException;

public class MockModelApiProcessor {

    private static final String OPENAI_BASE_URL = "https://api.openai.com/v1";

    static final String FEATURE = "langchain4j-mock-api";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    /**
     * Without dev services the mock never starts, so an application with the flag on would silently call the real
     * provider. Producing ServiceStartBuildItem makes the step run.
     */
    @BuildStep(onlyIf = MockApiEnabled.class)
    @Produce(ServiceStartBuildItem.class)
    void requireDevServices(DevServicesConfig devServicesConfig) {
        if (!devServicesConfig.enabled()) {
            throw new ConfigurationException(
                    "quarkus.langchain4j.mock-api.enabled is true but dev services are disabled (quarkus.devservices.enabled=false), "
                            + "so the mock model API cannot start; enable dev services or set quarkus.langchain4j.mock-api.enabled=false");
        }
    }

    /**
     * Which providers are on the classpath, from the candidates every provider's processor announces.
     */
    @BuildStep(onlyIf = MockApiEnabled.class)
    MockApiProvidersBuildItem detectProviders(
            List<ChatModelProviderCandidateBuildItem> chatCandidates,
            List<EmbeddingModelProviderCandidateBuildItem> embeddingCandidates,
            List<ScoringModelProviderCandidateBuildItem> scoringCandidates,
            List<ModerationModelProviderCandidateBuildItem> moderationCandidates,
            List<ImageModelProviderCandidateBuildItem> imageCandidates,
            List<AudioTranscriptionModelProviderCandidateBuildItem> audioCandidates,
            List<DecisionModelProviderCandidateBuildItem> decisionCandidates) {
        TreeSet<String> present = new TreeSet<>();
        Stream.of(chatCandidates, embeddingCandidates, scoringCandidates, moderationCandidates, imageCandidates,
                audioCandidates, decisionCandidates)
                .flatMap(List::stream).map(ProviderHolder::getProvider).forEach(present::add);
        List<ProviderSpec> providers = ProviderSpecs.byIds(present);
        if (providers.isEmpty()) {
            throw new ConfigurationException(
                    "quarkus.langchain4j.mock-api.enabled is true but no supported LangChain4j model provider is on the classpath");
        }
        return new MockApiProvidersBuildItem(providers);
    }

    /**
     * Which models core creates for each provider, from the selection core makes for every model an injection point or
     * AI service refers to. The unnamed model is always included for every provider on the classpath: providers read
     * the unnamed model's config for their non-model services (watsonx text extraction) and as the fallback of their
     * named models, and filling it in for an unused provider costs nothing.
     */
    @BuildStep(onlyIf = MockApiEnabled.class)
    MockApiModelsBuildItem modelsInUse(MockApiProvidersBuildItem providers,
            List<SelectedChatModelProviderBuildItem> chatModels,
            List<SelectedEmbeddingModelCandidateBuildItem> embeddingModels,
            List<SelectedScoringModelProviderBuildItem> scoringModels,
            List<SelectedModerationModelProviderBuildItem> moderationModels,
            List<SelectedImageModelProviderBuildItem> imageModels,
            List<SelectedAudioTranscriptionModelProviderBuildItem> audioModels,
            List<SelectedDecisionModelProviderBuildItem> decisionModels) {
        Map<String, TreeSet<String>> configNames = new TreeMap<>();
        providers.getProviders().forEach(spec -> use(configNames, spec.id(), NamedConfigUtil.DEFAULT_NAME));
        chatModels.forEach(m -> use(configNames, m.getProvider(), m.getConfigName()));
        embeddingModels.forEach(m -> use(configNames, m.getProvider(), m.getConfigName()));
        scoringModels.forEach(m -> use(configNames, m.getProvider(), m.getConfigName()));
        moderationModels.forEach(m -> use(configNames, m.getProvider(), m.getConfigName()));
        imageModels.forEach(m -> use(configNames, m.getProvider(), m.getConfigName()));
        audioModels.forEach(m -> use(configNames, m.getProvider(), m.getConfigName()));
        decisionModels.forEach(m -> use(configNames, m.getProvider(), m.getConfigName()));
        Map<String, List<String>> byProvider = new TreeMap<>();
        configNames.forEach((provider, names) -> byProvider.put(provider, List.copyOf(names)));
        return new MockApiModelsBuildItem(providers.getProviders(), byProvider);
    }

    private static void use(Map<String, TreeSet<String>> configNames, String provider, String configName) {
        configNames.computeIfAbsent(provider, p -> new TreeSet<>()).add(configName);
    }

    /**
     * The dev service publishes its URL, the endpoint keys of every model in use, the identity placeholders of every
     * model in use and the OpenAI chat API a model would select in production. Dev-service config reaches the application in
     * every launch mode. In the same JVM it sits below
     * application.properties, so the endpoint keys are also published as one {@code overrides} property that
     * {@link MockApiConfigBuilder} turns into a config source above the user's own URLs. The prod jar of an integration
     * test receives the values as system properties, which already beat application.properties, and may not even contain
     * this extension's runtime: that is why the endpoint keys are published plainly too, and why identity keys the user
     * configured are left out, as a system property would beat the user's value.
     */
    @BuildStep(onlyIf = MockApiEnabled.class)
    DevServicesResultBuildItem devService(LangChain4jBuildConfig buildConfig, MockApiProvidersBuildItem providers,
            MockApiModelsBuildItem models) {
        LangChain4jBuildConfig.MockApiConfig settings = buildConfig.mockApi();
        List<String> ids = providers.getProviders().stream().map(ProviderSpec::id).toList();
        DialectRegistry registry = DialectRegistry.load();
        Map<String, String> endpointSuffixes = endpointSuffixes(models);
        Map<String, Function<MockApiStartable, String>> config = new HashMap<>();
        config.put("quarkus.langchain4j.mock-api.url", MockApiStartable::getConnectionInfo);
        endpointSuffixes.forEach((key, suffix) -> config.put(key, s -> s.getConnectionInfo() + suffix));
        config.put(MockApiConfigSourceFactory.OVERRIDES,
                s -> MockApiConfigSourceFactory.format(withUrl(s.getConnectionInfo(), endpointSuffixes)));
        identityDefaultValues(models, ConfigProvider.getConfig()).forEach((key, value) -> config.put(key, s -> value));
        openAiChatModeDefaults(models, ConfigProvider.getConfig()).forEach((key, value) -> config.put(key, s -> value));
        return DevServicesResultBuildItem.<MockApiStartable> owned()
                .feature(FEATURE)
                .serviceName("Mock Model API")
                .description("LangChain4j mock model API on " + String.join(",", ids))
                .serviceConfig(List.of(settings.port(), settings.defaultChatReply(), settings.defaultEmbeddingDimension(),
                        models.getConfigNamesByProvider()).toString())
                .startable(() -> new MockApiStartable(settings.port(), settings.defaultChatReply(),
                        settings.defaultEmbeddingDimension(), registry))
                .configProvider(config)
                .build();
    }

    private static Map<String, String> withUrl(String mockUrl, Map<String, String> endpointSuffixes) {
        Map<String, String> result = new TreeMap<>();
        endpointSuffixes.forEach((key, suffix) -> result.put(key, mockUrl + suffix));
        return result;
    }

    @BuildStep(onlyIf = MockApiEnabled.class)
    RunTimeConfigBuilderBuildItem endpointOverride() {
        return new RunTimeConfigBuilderBuildItem(MockApiConfigBuilder.class);
    }

    @BuildStep(onlyIf = MockApiEnabled.class)
    AdditionalBeanBuildItem beans() {
        return AdditionalBeanBuildItem.unremovableOf(MockModelServerProducer.class);
    }

    /**
     * Providers that authenticate with credentials the mock cannot fill in through config get a bean of this module
     * instead (see {@link ProviderSpec#withCredentialsBean}). Registered from the providers on the classpath, because
     * additional beans must be known before bean discovery, and which models are in use is known only after it.
     */
    @BuildStep(onlyIf = MockApiEnabled.class)
    void credentialBeans(MockApiProvidersBuildItem providers, BuildProducer<AdditionalBeanBuildItem> beans) {
        for (ProviderSpec spec : providers.getProviders()) {
            if (spec.credentialsBean() != null) {
                beans.produce(AdditionalBeanBuildItem.unremovableOf(spec.credentialsBean()));
            }
        }
    }

    /**
     * Vertex AI Gemini needs one bean per model in use that the application does not already authenticate itself, see
     * {@link VertexAiGeminiCredentials}.
     */
    @BuildStep(onlyIf = MockApiEnabled.class)
    void vertexAiGeminiCredentials(MockApiModelsBuildItem models, BeanDiscoveryFinishedBuildItem beans,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeans) {
        List<String> configNames = models.getConfigNames(ProviderSpecs.VERTEX_AI_GEMINI);
        if (!configNames.isEmpty()) {
            VertexAiGeminiCredentials.register(configNames, beans, syntheticBeans);
        }
    }

    /**
     * Identity property to placeholder value, for every model in use. A property is left out when the user set it, or
     * one of its alternatives, for that model, even to an empty value, so a value the user set always wins.
     */
    static Map<String, String> identityDefaultValues(MockApiModelsBuildItem models, Config config) {
        Map<String, String> result = new TreeMap<>();
        for (ProviderSpec spec : models.getProviders()) {
            for (String configName : models.getConfigNames(spec)) {
                String prefix = spec.modelPrefix(configName);
                spec.identityDefaults().forEach((property, value) -> {
                    List<String> alternatives = spec.identityAlternatives().getOrDefault(property, List.of());
                    // a key the user set counts as configured even when its value is empty: setting a key to an empty
                    // value is how a test opts out of a placeholder, for a provider that behaves differently without the key
                    boolean userConfigured = Stream.concat(Stream.of(property), alternatives.stream())
                            .anyMatch(p -> config.getConfigValue(prefix + p).getRawValue() != null);
                    if (!userConfigured) {
                        result.put(prefix + property, value);
                    }
                });
            }
        }
        return result;
    }

    /**
     * Endpoint property to the path (provider path prefix plus suffix) appended to the mock URL, for every model in use.
     */
    /**
     * The OpenAI chat model selects its API from the base URL when {@code chat-model.mode} is not set: the Responses API
     * for OpenAI itself, Chat Completions for any other URL. Pointed at the mock, every model would select Chat
     * Completions, so the mock pins {@code responses} for a model the user would run against OpenAI (no base URL of its
     * own, or OpenAI's) and leaves a model with another base URL on Chat Completions, as it would be against the
     * compatible endpoint it was configured for. Like the identity placeholders, this looks at the build-time
     * configuration, and a mode the user set wins.
     */
    static Map<String, String> openAiChatModeDefaults(MockApiModelsBuildItem models, Config config) {
        Map<String, String> result = new TreeMap<>();
        for (String configName : models.getConfigNames(ProviderSpecs.OPENAI)) {
            String prefix = ProviderSpecs.OPENAI.modelPrefix(configName);
            String baseUrl = config.getConfigValue(prefix + "base-url").getRawValue();
            boolean modeConfigured = config.getConfigValue(prefix + "chat-model.mode").getRawValue() != null;
            if (!modeConfigured && (baseUrl == null || OPENAI_BASE_URL.equals(baseUrl.replaceAll("/+$", "")))) {
                result.put(prefix + "chat-model.mode", "responses");
            }
        }
        return result;
    }

    static Map<String, String> endpointSuffixes(MockApiModelsBuildItem models) {
        Map<String, String> result = new TreeMap<>();
        for (ProviderSpec spec : models.getProviders()) {
            for (String configName : models.getConfigNames(spec)) {
                for (ProviderSpec.EndpointKey endpointKey : spec.endpointKeys()) {
                    result.put(spec.modelPrefix(configName) + endpointKey.key(), spec.pathPrefix() + endpointKey.pathSuffix());
                }
            }
        }
        return result;
    }
}
