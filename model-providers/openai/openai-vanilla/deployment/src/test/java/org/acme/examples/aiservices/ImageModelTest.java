package org.acme.examples.aiservices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
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

public class ImageModelTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true");

    @Inject
    ImageModel imageModel;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void reset() {
        mock.reset();
    }

    @Test
    public void test() {
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
        assertNotNull(response);
        assertNotNull(response.content().base64Data());

        assertThat(mock.requests()).hasSize(1);
        CapturedRequest request = mock.requests().get(0);
        assertThat(request.header("Accept")).isEqualToIgnoringCase("application/json");
        assertThat(request.header("Content-Type")).isEqualToIgnoringCase("application/json");
        assertThat(request.header("Authorization")).matches("Bearer .+");
        JsonNode body = request.bodyAsJson();
        assertThat(body.get("model").textValue()).isNotEmpty();
        assertThat(body.get("prompt").textValue()).isNotEmpty();
        assertThat(body.get("n").intValue()).isEqualTo(1);
        assertThat(body.get("size").textValue()).isEqualTo("1024x1024");
        assertThat(body.get("quality").textValue()).isEqualTo("auto");
    }
}
