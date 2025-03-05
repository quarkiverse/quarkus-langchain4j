package io.quarkiverse.langchain4j.watsonx.deployment;

import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.API_KEY;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.BEARER_TOKEN;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.PROJECT_ID;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.RESPONSE_WATSONX_TOKENIZER_API;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_IAM_SERVER;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_SERVER;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_TOKENIZER_API;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Date;
import java.util.List;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.TokenCountEstimator;
import dev.langchain4j.model.input.Prompt;
import io.quarkiverse.langchain4j.watsonx.bean.TokenizationRequest;
import io.quarkus.test.QuarkusUnitTest;

public class TokenCountEstimatorTest extends WireMockAbstract {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.base-url", URL_WATSONX_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.iam.base-url", URL_IAM_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-id", PROJECT_ID)
            .overrideConfigKey("quarkus.langchain4j.watsonx.mode", "generation")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(WireMockUtil.class));

    @Override
    void handlerBeforeEach() {
        mockIAMBuilder(200)
                .response(BEARER_TOKEN, new Date())
                .build();
    }

    @Inject
    TokenCountEstimator tokenization;

    @Test
    void token_count_estimator_text() throws Exception {
        var input = mockServer();
        assertEquals(11, tokenization.estimateTokenCount(input));
    }

    @Test
    void token_count_estimator_user_message() throws Exception {
        var input = mockServer();
        assertEquals(11, tokenization.estimateTokenCount(UserMessage.from(input)));
    }

    @Test
    void token_count_estimator_text_segment() throws Exception {
        var input = mockServer();
        assertEquals(11, tokenization.estimateTokenCount(TextSegment.from(input)));
    }

    @Test
    void token_count_estimator_prompt() throws Exception {
        var input = mockServer();
        assertEquals(11, tokenization.estimateTokenCount(Prompt.from(input)));
    }

    @Test
    void token_count_estimator_list() throws Exception {

        var modelId = langchain4jWatsonConfig.defaultConfig().generationModel().modelId();
        var input = "Write a tagline for an alumni\nassociation: Together we";
        var spaceId = langchain4jWatsonConfig.defaultConfig().spaceId().orElse(null);
        var projectId = langchain4jWatsonConfig.defaultConfig().projectId().orElse(null);
        var body = new TokenizationRequest(modelId, input, spaceId, projectId);

        mockWatsonxBuilder(URL_WATSONX_TOKENIZER_API, 200)
                .body(mapper.writeValueAsString(body))
                .response(RESPONSE_WATSONX_TOKENIZER_API.formatted(modelId))
                .build();

        assertEquals(11, tokenization.estimateTokenCount(
                List.of(SystemMessage.from("Write a tagline for an alumni"), UserMessage.from("association: Together we"))));
    }

    private String mockServer() throws Exception {

        var modelId = langchain4jWatsonConfig.defaultConfig().generationModel().modelId();
        var input = "Write a tagline for an alumni association: Together we";
        var spaceId = langchain4jWatsonConfig.defaultConfig().spaceId().orElse(null);
        var projectId = langchain4jWatsonConfig.defaultConfig().projectId().orElse(null);
        var body = new TokenizationRequest(modelId, input, spaceId, projectId);

        mockWatsonxBuilder(URL_WATSONX_TOKENIZER_API, 200)
                .body(mapper.writeValueAsString(body))
                .response(RESPONSE_WATSONX_TOKENIZER_API.formatted(modelId))
                .build();

        return input;
    }
}
