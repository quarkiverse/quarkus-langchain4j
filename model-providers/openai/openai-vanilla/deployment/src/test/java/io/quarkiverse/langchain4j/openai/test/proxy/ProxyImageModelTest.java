package io.quarkiverse.langchain4j.openai.test.proxy;

import java.net.URI;
import java.util.Map;

import jakarta.inject.Inject;

import org.assertj.core.api.SoftAssertions;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.JsonNode;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.model.output.Response;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkiverse.langchain4j.mockapi.RawResponse;
import io.quarkus.test.QuarkusExtensionTest;

public class ProxyImageModelTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class).addClass(ForwardProxy.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.proxy-configuration-name", "local")
            .overrideRuntimeConfigKey("quarkus.proxy.local.host", "localhost")
            .overrideRuntimeConfigKey("quarkus.proxy.local.port", String.valueOf(ForwardProxy.port()));

    @Inject
    ImageModel imageModel;

    @Inject
    MockModelServer mock;

    @ConfigProperty(name = "quarkus.langchain4j.mock-api.url")
    String mockUrl;

    @BeforeEach
    void reset() {
        mock.reset();
    }

    @AfterAll
    static void stopProxy() {
        ForwardProxy.stop();
    }

    @Test
    public void shouldLoadImageModel() {
        // the mock does not render image generation replies: the recorded OpenAI response is served as is
        mock.raw().on("POST", "/v1/images/generations").replies(RawResponse.of(200,
                Map.of("Content-Type", "application/json",
                        "openai-organization", "my-org-1234",
                        "openai-version", "2020-10-01",
                        "openai-processing-ms", "15000"),
                """
                        {
                          "created": 1708633271,
                          "data": [
                            {
                              "b64_json": "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="
                            }
                           ]
                          }
                        """));

        Response<Image> response = imageModel.generate("whatever");
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(response).isNotNull();
        softly.assertThat(response.content().base64Data()).isNotNull();

        // the mock is always the endpoint, so only the proxy's record shows the request went through the named proxy
        softly.assertThat(ForwardProxy.forwardedUris()).containsExactly(mockUrl + "/v1/images/generations");
        softly.assertThat(mock.requests()).hasSize(1);
        CapturedRequest request = mock.requests().get(0);
        softly.assertThat(request.header("Host")).isEqualTo(URI.create(mockUrl).getAuthority());
        softly.assertThat(request.header("User-Agent")).isEqualTo("Quarkus REST Client");
        softly.assertThat(request.header("Accept")).isEqualToIgnoringCase("application/json");
        softly.assertThat(request.header("Content-Type")).isEqualToIgnoringCase("application/json");
        softly.assertThat(request.header("Authorization")).matches("Bearer .+");
        JsonNode body = request.bodyAsJson();
        softly.assertThat(body.get("model").textValue()).isNotEmpty();
        softly.assertThat(body.get("prompt").textValue()).isNotEmpty();
        softly.assertThat(body.get("n").intValue()).isEqualTo(1);
        softly.assertThat(body.get("size").textValue()).isEqualTo("1024x1024");
        softly.assertThat(body.get("quality").textValue()).isEqualTo("auto");

        softly.assertAll();
    }
}
