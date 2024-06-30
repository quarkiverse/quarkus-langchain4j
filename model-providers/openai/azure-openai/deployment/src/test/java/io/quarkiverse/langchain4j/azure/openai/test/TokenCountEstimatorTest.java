package io.quarkiverse.langchain4j.azure.openai.test;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.chat.TokenCountEstimator;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

public class TokenCountEstimatorTest {

    private static final String TOKEN = "whatever";

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.azure-openai.api-key", TOKEN)
            .overrideRuntimeConfigKey("quarkus.langchain4j.azure-openai.endpoint", WiremockAware.wiremockUrlForConfig("/v1"));

    @Inject
    TokenCountEstimator tokenization;

    @Test
    @Disabled("It is disabled because the tokenizer variable is always null. Set it in the AzureOpenAiRecorder class!")
    void token_count_estimator_text() throws Exception {
        //tokenization.estimateTokenCount(input);
    }

    @Test
    @Disabled("It is disabled because the tokenizer variable is always null. Set it in the AzureOpenAiRecorder class!")
    void token_count_estimator_user_message() throws Exception {
        //tokenization.estimateTokenCount(UserMessage.from(input));
    }

    @Test
    @Disabled("It is disabled because the tokenizer variable is always null. Set it in the AzureOpenAiRecorder class!")
    void token_count_estimator_text_segment() throws Exception {
        //tokenization.estimateTokenCount(TextSegment.from(input));
    }

    @Test
    @Disabled("It is disabled because the tokenizer variable is always null. Set it in the AzureOpenAiRecorder class!")
    void token_count_estimator_prompt() throws Exception {
        //tokenization.estimateTokenCount(Prompt.from(input));
    }

    @Test
    @Disabled("It is disabled because the tokenizer variable is always null. Set it in the AzureOpenAiRecorder class!")
    void token_count_estimator_list() throws Exception {
        //tokenization.estimateTokenCount(
        //        List.of(SystemMessage.from("Write a tagline for an alumni"), UserMessage.from("association: Together we"))));
    }
}
