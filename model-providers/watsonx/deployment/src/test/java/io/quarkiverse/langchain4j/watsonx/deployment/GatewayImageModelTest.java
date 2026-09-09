package io.quarkiverse.langchain4j.watsonx.deployment;

import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.API_KEY;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.DEFAULT_GATEWAY_IMAGE_MODEL;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.RESPONSE_WATSONX_GATEWAY_IMAGE_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.RESPONSE_WATSONX_GATEWAY_IMAGE_API_URL;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_IAM_SERVER;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_GATEWAY_IMAGE_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_SERVER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.net.URI;
import java.util.Date;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.ibm.watsonx.ai.gateway.image.ModelGatewayImageGenerationRequest;
import com.ibm.watsonx.ai.gateway.image.ModelGatewayImageParameters.Background;
import com.ibm.watsonx.ai.gateway.image.ModelGatewayImageParameters.Moderation;
import com.ibm.watsonx.ai.gateway.image.ModelGatewayImageParameters.OutputFormat;
import com.ibm.watsonx.ai.gateway.image.ModelGatewayImageParameters.Quality;
import com.ibm.watsonx.ai.gateway.image.ModelGatewayImageParameters.ResponseFormat;
import com.ibm.watsonx.ai.gateway.image.ModelGatewayImageParameters.Style;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.watsonx.WatsonxGatewayImageModel;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusUnitTest;

public class GatewayImageModelTest extends WireMockAbstract {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.base-url", URL_WATSONX_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.iam.base-url", URL_IAM_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.gateway-image-model.model-name",
                    DEFAULT_GATEWAY_IMAGE_MODEL)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.\"all-properties\".base-url", URL_WATSONX_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.\"all-properties\".iam.base-url", URL_IAM_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.\"all-properties\".api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.\"all-properties\".gateway-image-model.model-name",
                    DEFAULT_GATEWAY_IMAGE_MODEL)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.\"all-properties\".gateway-image-model.background",
                    "opaque")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.\"all-properties\".gateway-image-model.moderation",
                    "low")
            .overrideRuntimeConfigKey(
                    "quarkus.langchain4j.watsonx.\"all-properties\".gateway-image-model.output-compression", "80")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.\"all-properties\".gateway-image-model.output-format",
                    "png")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.\"all-properties\".gateway-image-model.quality", "high")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.\"all-properties\".gateway-image-model.response-format",
                    "b64-json")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.\"all-properties\".gateway-image-model.size",
                    "1024x1024")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.\"all-properties\".gateway-image-model.style", "vivid")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.\"all-properties\".gateway-image-model.user", "my-user")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(WireMockUtil.class));

    @Override
    void handlerBeforeEach() {
        mockIAMBuilder(200)
                .response("my_super_token", new Date())
                .build();
    }

    @Inject
    ImageModel imageModel;

    @Inject
    @ModelName("all-properties")
    ImageModel allPropertiesImageModel;

    @Test
    void check_config() {
        var gatewayConfig = langchain4jWatsonConfig.defaultConfig().gatewayImageModel();
        assertEquals(DEFAULT_GATEWAY_IMAGE_MODEL, gatewayConfig.modelName().orElseThrow());
        assertThat(gatewayConfig.background()).isEmpty();
        assertThat(gatewayConfig.moderation()).isEmpty();
        assertThat(gatewayConfig.outputCompression()).isEmpty();
        assertThat(gatewayConfig.outputFormat()).isEmpty();
        assertThat(gatewayConfig.quality()).isEmpty();
        assertThat(gatewayConfig.responseFormat()).isEmpty();
        assertThat(gatewayConfig.size()).isEmpty();
        assertThat(gatewayConfig.style()).isEmpty();
        assertThat(gatewayConfig.user()).isEmpty();

        var allProperties = langchain4jWatsonConfig.namedConfig().get("all-properties").gatewayImageModel();
        assertEquals(Background.OPAQUE, allProperties.background().orElseThrow());
        assertEquals(Moderation.LOW, allProperties.moderation().orElseThrow());
        assertEquals(80, allProperties.outputCompression().orElseThrow());
        assertEquals(OutputFormat.PNG, allProperties.outputFormat().orElseThrow());
        assertEquals(Quality.HIGH, allProperties.quality().orElseThrow());
        assertEquals(ResponseFormat.B64_JSON, allProperties.responseFormat().orElseThrow());
        assertEquals("1024x1024", allProperties.size().orElseThrow());
        assertEquals(Style.VIVID, allProperties.style().orElseThrow());
        assertEquals("my-user", allProperties.user().orElseThrow());
    }

    @Test
    void model_implementations() {
        assertInstanceOf(WatsonxGatewayImageModel.class, ClientProxy.unwrap(imageModel));
    }

    @Test
    void generate() throws Exception {

        var prompt = "A futuristic city at sunset";
        var body = new ModelGatewayImageGenerationRequest(
                DEFAULT_GATEWAY_IMAGE_MODEL, prompt, null, null, null, null, null, null, null, null, null, null, null);

        mockWatsonxBuilder(URL_WATSONX_GATEWAY_IMAGE_API, 200)
                .body(mapper.writeValueAsString(body))
                .response(RESPONSE_WATSONX_GATEWAY_IMAGE_API)
                .build();

        Response<Image> response = imageModel.generate(prompt);
        assertNotNull(response);
        assertEquals("aGVsbG8gd29ybGQ=", response.content().base64Data());
        assertNull(response.content().url());
        assertEquals("image/png", response.content().mimeType());
        assertEquals("A futuristic city at sunset, revised", response.content().revisedPrompt());

        assertEquals(10, response.tokenUsage().inputTokenCount());
        assertEquals(20, response.tokenUsage().outputTokenCount());
        assertEquals(30, response.tokenUsage().totalTokenCount());
    }

    @Test
    void generate_returns_url_response() throws Exception {

        var prompt = "A futuristic city at sunset";
        var body = new ModelGatewayImageGenerationRequest(
                DEFAULT_GATEWAY_IMAGE_MODEL, prompt, null, null, null, null, null, null, null, null, null, null, null);

        mockWatsonxBuilder(URL_WATSONX_GATEWAY_IMAGE_API, 200)
                .body(mapper.writeValueAsString(body))
                .response(RESPONSE_WATSONX_GATEWAY_IMAGE_API_URL)
                .build();

        Response<Image> response = imageModel.generate(prompt);
        assertNotNull(response);
        assertNull(response.content().base64Data());
        assertEquals(URI.create("https://example.com/image.png"), response.content().url());
        assertEquals("image/png", response.content().mimeType());
    }

    @Test
    void generate_with_all_properties() throws Exception {

        var prompt = "A futuristic city at sunset";
        var body = new ModelGatewayImageGenerationRequest(
                DEFAULT_GATEWAY_IMAGE_MODEL, prompt, "opaque", "low", null, 80, "png", null, "high", "b64_json",
                "1024x1024", "vivid", "my-user");

        mockWatsonxBuilder(URL_WATSONX_GATEWAY_IMAGE_API, 200)
                .body(mapper.writeValueAsString(body))
                .response(RESPONSE_WATSONX_GATEWAY_IMAGE_API)
                .build();

        Response<Image> response = allPropertiesImageModel.generate(prompt);
        assertNotNull(response);
        assertEquals("aGVsbG8gd29ybGQ=", response.content().base64Data());
    }

    @Test
    void generate_multiple_images() throws Exception {

        var prompt = "A futuristic city at sunset";
        var body = new ModelGatewayImageGenerationRequest(
                DEFAULT_GATEWAY_IMAGE_MODEL, prompt, null, null, 2, null, null, null, null, null, null, null, null);

        var response = """
                {
                    "created": 1689958352,
                    "background": "opaque",
                    "output_format": "png",
                    "quality": "high",
                    "size": "1024x1024",
                    "data": [
                        {"b64_json": "aGVsbG8gd29ybGQ=", "revised_prompt": "Revised 1"},
                        {"url": "https://example.com/image2.png", "revised_prompt": "Revised 2"}
                    ],
                    "usage": {
                        "input_tokens": 10,
                        "output_tokens": 40,
                        "total_tokens": 50,
                        "input_tokens_details": {
                            "image_tokens": 0,
                            "text_tokens": 10
                        }
                    }
                }""";

        mockWatsonxBuilder(URL_WATSONX_GATEWAY_IMAGE_API, 200)
                .body(mapper.writeValueAsString(body))
                .response(response)
                .build();

        Response<java.util.List<Image>> images = imageModel.generate(prompt, 2);
        assertNotNull(images);
        assertEquals(2, images.content().size());

        var first = images.content().get(0);
        assertEquals("aGVsbG8gd29ybGQ=", first.base64Data());
        assertNull(first.url());
        assertEquals("Revised 1", first.revisedPrompt());

        var second = images.content().get(1);
        assertNull(second.base64Data());
        assertEquals(URI.create("https://example.com/image2.png"), second.url());
        assertEquals("Revised 2", second.revisedPrompt());

        assertEquals(50, images.tokenUsage().totalTokenCount());
    }
}
