package com.ibm.langchain4j.watsonx.deployment;

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
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.TokenCountEstimator;
import dev.langchain4j.model.input.Prompt;
import io.quarkiverse.langchain4j.watsonx.bean.TokenizationRequest;
import io.quarkiverse.langchain4j.watsonx.runtime.config.LangChain4jWatsonxConfig;
import io.quarkus.test.QuarkusUnitTest;

public class TokenCountEstimatorTest extends WireMockAbstract {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.base-url", WireMockUtil.URL_WATSONX_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.iam.base-url", WireMockUtil.URL_IAM_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", WireMockUtil.API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-id", WireMockUtil.PROJECT_ID)
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(WireMockUtil.class));

    @Override
    void handlerBeforeEach() {
        mockServers.mockIAMBuilder(200)
                .response(WireMockUtil.BEARER_TOKEN, new Date())
                .build();
    }

    @Inject
    ChatLanguageModel model;

    @Inject
    TokenCountEstimator tokenization;

    @Inject
    LangChain4jWatsonxConfig langchain4jWatsonxConfig;

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

        var modelId = langchain4jWatsonFixedRuntimeConfig.defaultConfig().chatModel().modelId();
        var input = "Write a tagline for an alumni\nassociation: Together we";
        var projectId = langchain4jWatsonxConfig.defaultConfig().projectId();
        var body = new TokenizationRequest(modelId, input, projectId);

        mockServers.mockWatsonxBuilder(WireMockUtil.URL_WATSONX_TOKENIZER_API, 200)
                .body(mapper.writeValueAsString(body))
                .response(WireMockUtil.RESPONSE_WATSONX_TOKENIZER_API.formatted(modelId))
                .build();

        assertEquals(11, tokenization.estimateTokenCount(
                List.of(SystemMessage.from("Write a tagline for an alumni"), UserMessage.from("association: Together we"))));
    }

    private String mockServer() throws Exception {

        var modelId = langchain4jWatsonFixedRuntimeConfig.defaultConfig().chatModel().modelId();
        var input = "Write a tagline for an alumni association: Together we";
        var projectId = langchain4jWatsonxConfig.defaultConfig().projectId();
        var body = new TokenizationRequest(modelId, input, projectId);

        mockServers.mockWatsonxBuilder(WireMockUtil.URL_WATSONX_TOKENIZER_API, 200)
                .body(mapper.writeValueAsString(body))
                .response(WireMockUtil.RESPONSE_WATSONX_TOKENIZER_API.formatted(modelId))
                .build();

        return input;
    }
}
