package io.quarkiverse.langchain4j.openai.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

public class OpenAiChatLanguageModelSmokeTest extends OpenAiBaseTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    @Inject
    ChatModel chatLanguageModel;

    @BeforeEach
    void reset() {
        resetRequests();
    }

    @Test
    void test() {
        setChatCompletionMessageContent("whatever");
        String response = chatLanguageModel.chat("hello");
        assertThat(response).isEqualTo("whatever");

        LoggedRequest loggedRequest = singleLoggedRequest();
        assertThat(loggedRequest.getHeader("User-Agent")).isEqualTo("langchain4j-openai");
    }

    @Test
    void customHeaders() {
        OpenAiChatModel chatModel = OpenAiChatModel.builder().baseUrl(resolvedWiremockUrl("/v1"))
                .apiKey("whatever")
                .modelName("gpt-4o-mini")
                .customHeaders(Map.of("Foo", "Bar"))
                .build();

        setChatCompletionMessageContent("whatever");
        String response = chatModel.chat("hello");
        assertThat(response).isEqualTo("whatever");

        LoggedRequest loggedRequest = singleLoggedRequest();
        assertThat(loggedRequest.getHeader("User-Agent")).isEqualTo("langchain4j-openai");
        assertThat(loggedRequest.getHeader("Authorization")).isEqualTo("Bearer whatever");
        assertThat(loggedRequest.getHeader("Foo")).isEqualTo("Bar");
    }
}
