package io.quarkiverse.langchain4j.bam.deployment;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.TokenCountEstimator;
import dev.langchain4j.model.input.Prompt;
import io.quarkiverse.langchain4j.bam.BamRestApi;
import io.quarkiverse.langchain4j.bam.TokenizationRequest;
import io.quarkiverse.langchain4j.bam.runtime.config.LangChain4jBamConfig;
import io.quarkus.test.QuarkusUnitTest;

public class TokenCountEstimatorTest {

    static WireMockServer wireMockServer;
    static ObjectMapper mapper;
    static WireMockUtil mockServers;

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideRuntimeConfigKey("quarkus.langchain4j.bam.base-url", WireMockUtil.URL)
            .overrideRuntimeConfigKey("quarkus.langchain4j.bam.api-key", WireMockUtil.API_KEY)
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(WireMockUtil.class));

    @BeforeAll
    static void beforeAll() {
        wireMockServer = new WireMockServer(options().port(WireMockUtil.PORT));
        wireMockServer.start();
        mapper = BamRestApi.objectMapper(new ObjectMapper());
        mockServers = new WireMockUtil(wireMockServer);
    }

    @AfterAll
    static void afterAll() {
        wireMockServer.stop();
    }

    private static final String RESPONSE = """
              {
              "results": [
                {
                  "token_count": 13,
                  "tokens": [
                    "▁Write",
                    "▁",
                    "a",
                    "▁tag",
                    "line",
                    "▁for",
                    "▁an",
                    "▁alumni",
                    "▁association",
                    ":",
                    "▁Together",
                    "▁we",
                    "</s>"
                  ],
                  "input_text": "Write a tagline for an alumni association: Together we"
                }
              ],
              "model_id": "%s",
              "created_at": "2022-11-16T14:39:21.156Z"
            }
            """;

    @Inject
    TokenCountEstimator tokenization;

    @Inject
    LangChain4jBamConfig langchain4jBamConfig;

    @Test
    void token_count_estimator_text() throws Exception {
        var input = mockServer();
        assertEquals(13, tokenization.estimateTokenCount(input));
    }

    @Test
    void token_count_estimator_user_message() throws Exception {
        var input = mockServer();
        assertEquals(13, tokenization.estimateTokenCount(UserMessage.from(input)));
    }

    @Test
    void token_count_estimator_text_segment() throws Exception {
        var input = mockServer();
        assertEquals(13, tokenization.estimateTokenCount(TextSegment.from(input)));
    }

    @Test
    void token_count_estimator_prompt() throws Exception {
        var input = mockServer();
        assertEquals(13, tokenization.estimateTokenCount(Prompt.from(input)));
    }

    @Test
    void token_count_estimator_list() throws Exception {
        mockServer();
        assertEquals(13, tokenization.estimateTokenCount(
                List.of(SystemMessage.from("Write a tagline for an alumni"), UserMessage.from("association: Together we"))));
    }

    private String mockServer() throws Exception {
        var config = langchain4jBamConfig.defaultConfig();
        var modelId = config.chatModel().modelId();
        var input = "Write a tagline for an alumni association: Together we";
        var body = new TokenizationRequest(modelId, input);

        mockServers.mockBuilder(WireMockUtil.URL_TOKENIZATION_API, 200)
                .body(mapper.writeValueAsString(body))
                .response(RESPONSE.formatted(modelId))
                .build();

        return input;
    }
}
