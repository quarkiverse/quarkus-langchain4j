package io.quarkiverse.langchain4j.huggingface.deployment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.huggingface.HuggingFaceModelName;
import dev.langchain4j.model.huggingface.client.EmbeddingRequest;
import dev.langchain4j.model.huggingface.client.Options;
import dev.langchain4j.model.huggingface.client.Parameters;
import dev.langchain4j.model.huggingface.client.TextGenerationRequest;
import dev.langchain4j.model.huggingface.client.TextGenerationResponse;
import io.quarkiverse.langchain4j.huggingface.HuggingFaceRestApi;
import io.quarkiverse.langchain4j.huggingface.QuarkusHuggingFaceClientFactory;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import io.quarkus.test.QuarkusUnitTest;

public class QuarkusHuggingFaceClientTest extends WiremockAware {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class));
    private static final String CHAT_MODEL_ID = HuggingFaceModelName.TII_UAE_FALCON_7B_INSTRUCT;
    private static final String EMBED_MODEL_ID = HuggingFaceModelName.SENTENCE_TRANSFORMERS_ALL_MINI_LM_L6_V2;
    private static final String API_KEY = "key";

    @Test
    void chat() {
        wiremock().register(
                post(urlEqualTo("/models/" + sanitizeModelForUrl(CHAT_MODEL_ID)))
                        .withHeader("Authorization", equalTo("Bearer " + API_KEY))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody("""
                                        [
                                          {
                                            "generated_text": "\\nI'm hanging with my bro...\\nAnd so on and so forth...\\nSo,"
                                          }
                                        ]
                                        """)));

        TextGenerationRequest request = TextGenerationRequest.builder()
                .inputs("What are you doing?")
                .parameters(Parameters.builder()
                        .temperature(1.0)
                        .returnFullText(false)
                        .build())
                .options(Options.builder()
                        .waitForModel(true)
                        .build())
                .build();
        TextGenerationResponse response = createClientForChat().chat(request);
        assertThat(response).isNotNull().satisfies(r -> {
            assertThat(r.getGeneratedText()).contains("hanging with");
        });
    }

    @Test
    void embed() {
        wiremock().register(
                post(urlEqualTo("/models/" + sanitizeModelForUrl(EMBED_MODEL_ID)))
                        .withHeader("Authorization", equalTo("Bearer " + API_KEY))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody(
                                        "[[-0.06277172267436981,0.054958775639534,0.052164800465106964,0.08578997850418091]]")));

        List<float[]> response = createClient().embed(new EmbeddingRequest(List.of("whatever"), true));
        assertThat(response).singleElement().satisfies(fa -> {
            assertThat(fa).hasSize(4);
        });
    }

    private String sanitizeModelForUrl(String modelId) {
        return modelId.replace("/", "%2F");
    }

    private QuarkusHuggingFaceClientFactory.QuarkusHuggingFaceClient createClient() {
        try {
            HuggingFaceRestApi restApi = QuarkusRestClientBuilder.newBuilder()
                    .baseUrl(new URL(resolvedWiremockUrl("/models/" + sanitizeModelForUrl(EMBED_MODEL_ID))))
                    .build(HuggingFaceRestApi.class);
            return new QuarkusHuggingFaceClientFactory.QuarkusHuggingFaceClient(restApi, API_KEY);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    private QuarkusHuggingFaceClientFactory.QuarkusHuggingFaceClient createClientForChat() {
        try {
            HuggingFaceRestApi restApi = RestClientBuilder.newBuilder()
                    .baseUrl(new URL(resolvedWiremockUrl("/models/" + sanitizeModelForUrl(CHAT_MODEL_ID))))
                    .build(HuggingFaceRestApi.class);
            return new QuarkusHuggingFaceClientFactory.QuarkusHuggingFaceClient(restApi, API_KEY);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }
}
