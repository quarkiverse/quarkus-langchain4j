package io.quarkiverse.langchain4j.azure.openai.test;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.image.ImageModel;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;
import tools.jackson.databind.JsonNode;

public class AzureOpenAiImageModelGptImageOptionsTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.azure-openai.api-key", "whatever")
            .overrideRuntimeConfigKey("quarkus.langchain4j.azure-openai.image-model.output-format", "webp")
            .overrideRuntimeConfigKey("quarkus.langchain4j.azure-openai.image-model.background", "opaque")
            .overrideRuntimeConfigKey("quarkus.langchain4j.azure-openai.image-model.output-compression", "80")
            .overrideRuntimeConfigKey("quarkus.langchain4j.azure-openai.image-model.moderation", "low")
            .overrideRuntimeConfigKey("quarkus.langchain4j.azure-openai.other.api-key", "whatever");

    @Inject
    ImageModel imageModel;

    @Inject
    @ModelName("other")
    ImageModel otherImageModel;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void reset() {
        mock.reset();
        mock.raw().on("POST", "/openai/deployments/*/images/generations")
                .reply(200, "{\"created\":0,\"data\":[{\"url\":\"https://example.com/a.png\"}]}");
    }

    @Test
    void configuredOptionsAreSent() {
        imageModel.generate("cat");

        assertThat(mock.requests()).hasSize(1);
        JsonNode body = mock.requests().get(0).bodyAsJson();
        assertThat(body.get("output_format").asString()).isEqualTo("webp");
        assertThat(body.get("background").asString()).isEqualTo("opaque");
        assertThat(body.get("output_compression").asInt()).isEqualTo(80);
        assertThat(body.get("moderation").asString()).isEqualTo("low");
    }

    @Test
    void optionsAreOmittedWhenNotConfigured() {
        otherImageModel.generate("cat");

        assertThat(mock.requests()).hasSize(1);
        JsonNode body = mock.requests().get(0).bodyAsJson();
        assertThat(body.has("output_format")).isFalse();
        assertThat(body.has("background")).isFalse();
        assertThat(body.has("output_compression")).isFalse();
        assertThat(body.has("moderation")).isFalse();
    }
}
