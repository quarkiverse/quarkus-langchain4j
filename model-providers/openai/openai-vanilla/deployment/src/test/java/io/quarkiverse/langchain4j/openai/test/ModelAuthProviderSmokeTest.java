package io.quarkiverse.langchain4j.openai.test;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import io.quarkiverse.langchain4j.auth.ModelAuthProvider;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.QuarkusUnitTest;

public class ModelAuthProviderSmokeTest extends OpenAiBaseTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    @Inject
    ChatModel chatModel;

    @Inject
    StreamingChatModel streamingChatModel;

    @Test
    void testModelAuthProviderUsed() {
        assertThat(ClientProxy.unwrap(chatModel)).isInstanceOf(OpenAiChatModel.class);
        assertThat(ClientProxy.unwrap(streamingChatModel)).isInstanceOf(OpenAiStreamingChatModel.class);

        setChatCompletionMessageContent("whatever");
        String response = chatModel.chat("hello");
        assertThat(response).isNotBlank();

        LoggedRequest loggedRequest = singleLoggedRequest();
        assertThat(loggedRequest.getHeader("Authorization")).isEqualTo("Bearer token");
    }

    @ApplicationScoped
    public static class DummyModelAuthProvider implements ModelAuthProvider {

        @Override
        public String getAuthorization(Input input) {
            return "Bearer token";
        }
    }
}
