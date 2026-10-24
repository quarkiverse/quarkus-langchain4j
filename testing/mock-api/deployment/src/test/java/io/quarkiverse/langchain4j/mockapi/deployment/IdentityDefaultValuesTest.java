package io.quarkiverse.langchain4j.mockapi.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.microprofile.config.Config;
import org.junit.jupiter.api.Test;

import io.quarkiverse.langchain4j.deployment.items.SelectedChatModelProviderBuildItem;
import io.quarkiverse.langchain4j.deployment.items.SelectedEmbeddingModelCandidateBuildItem;
import io.quarkiverse.langchain4j.mockapi.internal.ProviderSpec;
import io.quarkiverse.langchain4j.mockapi.internal.ProviderSpecs;
import io.smallrye.config.SmallRyeConfigBuilder;

class IdentityDefaultValuesTest {

    private static final String DEFAULT = "<default>";

    @Test
    void fillsEveryIdentityKeyOfEveryModelInUse() {
        var providers = providers(
                Map.of(ProviderSpecs.OPENAI, List.of(DEFAULT, "m1"), ProviderSpecs.AZURE_OPENAI, List.of("a1")));
        Map<String, String> values = MockModelApiProcessor.identityDefaultValues(providers, config(Map.of()));
        for (ProviderSpec spec : providers.getProviders()) {
            for (String name : providers.getConfigNames(spec)) {
                spec.identityDefaults()
                        .forEach((key, value) -> assertThat(values).containsEntry(spec.modelPrefix(name) + key, value));
            }
        }
        assertThat(values).containsEntry("quarkus.langchain4j.openai.api-key", "mock-api-key")
                .containsEntry("quarkus.langchain4j.openai.m1.api-key", "mock-api-key")
                .containsEntry("quarkus.langchain4j.azure-openai.a1.api-key", "mock-api-key")
                .doesNotContainKey("quarkus.langchain4j.azure-openai.api-key");
    }

    @Test
    void skipsKeysTheUserConfiguredForThatModelOnly() {
        var providers = providers(Map.of(ProviderSpecs.OPENAI, List.of(DEFAULT, "m1")));
        assertThat(MockModelApiProcessor.identityDefaultValues(providers,
                config(Map.of("quarkus.langchain4j.openai.m1.api-key", "real"))))
                .containsEntry("quarkus.langchain4j.openai.api-key", "mock-api-key")
                .doesNotContainKey("quarkus.langchain4j.openai.m1.api-key");
    }

    @Test
    void anExplicitlyEmptyKeyCountsAsConfigured() {
        // a provider that only installs its ModelAuthProvider filter without an api key needs a way to keep the key empty
        assertThat(MockModelApiProcessor.identityDefaultValues(providers(Map.of(ProviderSpecs.OPENAI, List.of(DEFAULT))),
                config(Map.of("quarkus.langchain4j.openai.api-key", ""))))
                .doesNotContainKey("quarkus.langchain4j.openai.api-key");
    }

    @Test
    void skipsAKeyWhenAnAlternativeIsConfigured() {
        assertThat(MockModelApiProcessor.identityDefaultValues(providers(Map.of(ProviderSpecs.AZURE_OPENAI, List.of(DEFAULT))),
                config(Map.of("quarkus.langchain4j.azure-openai.ad-token", "t"))))
                .doesNotContainKey("quarkus.langchain4j.azure-openai.api-key");
        assertThat(MockModelApiProcessor.identityDefaultValues(providers(Map.of(ProviderSpecs.WATSONX, List.of(DEFAULT))),
                config(Map.of("quarkus.langchain4j.watsonx.space-id", "s"))))
                .containsEntry("quarkus.langchain4j.watsonx.api-key", "mock-api-key")
                .doesNotContainKey("quarkus.langchain4j.watsonx.project-id");
    }

    @Test
    void theUnnamedModelIsAlwaysInUseAndNamedModelsComeFromTheSelection() {
        var models = new MockModelApiProcessor().modelsInUse(
                new MockApiProvidersBuildItem(List.of(ProviderSpecs.OPENAI, ProviderSpecs.ANTHROPIC)),
                List.of(new SelectedChatModelProviderBuildItem("openai", "m1"),
                        new SelectedChatModelProviderBuildItem("openai", DEFAULT)),
                List.of(new SelectedEmbeddingModelCandidateBuildItem("openai", "e1")), List.of(), List.of(), List.of(),
                List.of(),
                List.of());
        assertThat(models.getConfigNames(ProviderSpecs.OPENAI)).containsExactly(DEFAULT, "e1", "m1");
        assertThat(models.getConfigNames(ProviderSpecs.ANTHROPIC)).containsExactly(DEFAULT);
        assertThat(MockModelApiProcessor.endpointSuffixes(models))
                .containsKey("quarkus.langchain4j.anthropic.base-url")
                .containsKey("quarkus.langchain4j.openai.e1.base-url")
                .doesNotContainKey("quarkus.langchain4j.anthropic.m1.base-url");
    }

    @Test
    void ollamaAndTypeSafeHaveNoIdentityDefaults() {
        assertThat(MockModelApiProcessor.identityDefaultValues(
                providers(Map.of(ProviderSpecs.OLLAMA, List.of(DEFAULT), ProviderSpecs.TYPESAFE, List.of(DEFAULT))),
                config(Map.of())))
                .isEmpty();
    }

    @Test
    void endpointSuffixesCoverEveryKeyOfEveryModelInUse() {
        var providers = providers(
                Map.of(ProviderSpecs.HUGGING_FACE, List.of(DEFAULT, "h1"), ProviderSpecs.BEDROCK, List.of("b1"),
                        ProviderSpecs.WATSONX, List.of(DEFAULT), ProviderSpecs.AZURE_OPENAI, List.of("a1")));
        Map<String, String> suffixes = MockModelApiProcessor.endpointSuffixes(providers);
        assertThat(suffixes)
                .containsEntry("quarkus.langchain4j.huggingface.chat-model.inference-endpoint-url", "/huggingface/chat")
                .containsEntry("quarkus.langchain4j.huggingface.h1.embedding-model.inference-endpoint-url",
                        "/huggingface/embeddings")
                .containsEntry("quarkus.langchain4j.bedrock.b1.aws.endpoint-override", "/bedrock")
                .containsEntry("quarkus.langchain4j.bedrock.b1.chat-model.aws.endpoint-override", "/bedrock")
                .containsEntry("quarkus.langchain4j.bedrock.b1.embedding-model.aws.endpoint-override", "/bedrock")
                .doesNotContainKey("quarkus.langchain4j.bedrock.aws.endpoint-override")
                .containsEntry("quarkus.langchain4j.watsonx.base-url", "/watsonx")
                .containsEntry("quarkus.langchain4j.watsonx.iam.base-url", "/watsonx")
                .doesNotContainKey("quarkus.langchain4j.watsonx.text-extraction.cos-url")
                .containsEntry("quarkus.langchain4j.azure-openai.a1.chat-model.endpoint",
                        "/openai/deployments/mock-deployment");
        int expected = 0;
        for (ProviderSpec spec : providers.getProviders()) {
            expected += providers.getConfigNames(spec).size() * spec.endpointKeys().size();
        }
        assertThat(suffixes).hasSize(expected);
    }

    @Test
    void pathPrefixGoesBetweenTheMockUrlAndTheSuffix() {
        ProviderSpec spec = new ProviderSpec("prefixed", "quarkus.langchain4j.prefixed", "/prefixed",
                List.of(new ProviderSpec.EndpointKey("base-url", "/v1")), Map.of(), Map.of());
        assertThat(MockModelApiProcessor.endpointSuffixes(providers(Map.of(spec, List.of(DEFAULT, "m1")))))
                .containsEntry("quarkus.langchain4j.prefixed.base-url", "/prefixed/v1")
                .containsEntry("quarkus.langchain4j.prefixed.m1.base-url", "/prefixed/v1");
    }

    private static MockApiModelsBuildItem providers(Map<ProviderSpec, List<String>> configNames) {
        Map<String, List<String>> byId = new HashMap<>();
        configNames.forEach((spec, names) -> byId.put(spec.id(), names));
        return new MockApiModelsBuildItem(List.copyOf(configNames.keySet()), byId);
    }

    static Config config(Map<String, String> properties) {
        return new SmallRyeConfigBuilder().withDefaultValues(properties).build();
    }
}
