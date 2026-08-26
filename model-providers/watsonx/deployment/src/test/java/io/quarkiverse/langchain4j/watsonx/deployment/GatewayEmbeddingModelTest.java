package io.quarkiverse.langchain4j.watsonx.deployment;

import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.API_KEY;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.DEFAULT_GATEWAY_EMBEDDING_MODEL;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.RESPONSE_WATSONX_GATEWAY_EMBEDDING_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_IAM_SERVER;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_GATEWAY_EMBEDDING_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_SERVER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Date;
import java.util.List;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.ibm.watsonx.ai.gateway.embedding.ModelGatewayEmbeddingParameters.EncodingFormat;
import com.ibm.watsonx.ai.gateway.embedding.ModelGatewayEmbeddingPayload;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.watsonx.WatsonxGatewayEmbeddingModel;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusUnitTest;

public class GatewayEmbeddingModelTest extends WireMockAbstract {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.base-url", URL_WATSONX_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.iam.base-url", URL_IAM_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.gateway-embedding-model.model-name",
                    DEFAULT_GATEWAY_EMBEDDING_MODEL)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.\"all-properties\".base-url", URL_WATSONX_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.\"all-properties\".iam.base-url", URL_IAM_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.\"all-properties\".api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.\"all-properties\".gateway-embedding-model.model-name",
                    DEFAULT_GATEWAY_EMBEDDING_MODEL)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.\"all-properties\".gateway-embedding-model.dimensions",
                    "512")
            .overrideRuntimeConfigKey(
                    "quarkus.langchain4j.watsonx.\"all-properties\".gateway-embedding-model.encoding-format", "base64")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.\"all-properties\".gateway-embedding-model.user",
                    "my-user")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(WireMockUtil.class));

    @Override
    void handlerBeforeEach() {
        mockIAMBuilder(200)
                .response("my_super_token", new Date())
                .build();
    }

    @Inject
    EmbeddingModel embeddingModel;

    @Inject
    @ModelName("all-properties")
    EmbeddingModel allPropertiesEmbeddingModel;

    @Test
    void check_config() {
        var gatewayConfig = langchain4jWatsonConfig.defaultConfig().gatewayEmbeddingModel();
        assertEquals(DEFAULT_GATEWAY_EMBEDDING_MODEL, gatewayConfig.modelName().orElseThrow());
        assertThat(gatewayConfig.dimensions()).isEmpty();
        assertThat(gatewayConfig.encodingFormat()).isEmpty();
        assertThat(gatewayConfig.user()).isEmpty();

        var allProperties = langchain4jWatsonConfig.namedConfig().get("all-properties").gatewayEmbeddingModel();
        assertEquals(512, allProperties.dimensions().orElseThrow());
        assertEquals(EncodingFormat.BASE64, allProperties.encodingFormat().orElseThrow());
        assertEquals("my-user", allProperties.user().orElseThrow());
    }

    @Test
    void model_implementations() {
        assertInstanceOf(WatsonxGatewayEmbeddingModel.class, ClientProxy.unwrap(embeddingModel));
    }

    @Test
    void embed() throws Exception {

        var input = "Embedding THIS!";
        var body = new ModelGatewayEmbeddingPayload(DEFAULT_GATEWAY_EMBEDDING_MODEL, List.of(input), null, null, null);

        mockWatsonxBuilder(URL_WATSONX_GATEWAY_EMBEDDING_API, 200)
                .body(mapper.writeValueAsString(body))
                .response(RESPONSE_WATSONX_GATEWAY_EMBEDDING_API)
                .build();

        Response<Embedding> response = embeddingModel.embed(input);
        assertNotNull(response);
        assertNotNull(response.content());
        assertEquals(List.of(-0.006929283f, -0.005336422f, -0.024047505f), response.content().vectorAsList());
    }

    @Test
    void embed_with_all_properties() throws Exception {

        var input = "Embedding THIS!";
        var body = new ModelGatewayEmbeddingPayload(DEFAULT_GATEWAY_EMBEDDING_MODEL, List.of(input), 512, "base64",
                "my-user");

        mockWatsonxBuilder(URL_WATSONX_GATEWAY_EMBEDDING_API, 200)
                .body(mapper.writeValueAsString(body))
                .response(RESPONSE_WATSONX_GATEWAY_EMBEDDING_API)
                .build();

        Response<Embedding> response = allPropertiesEmbeddingModel.embed(input);
        assertNotNull(response);
        assertNotNull(response.content());
        assertEquals(List.of(-0.006929283f, -0.005336422f, -0.024047505f), response.content().vectorAsList());
    }
}
