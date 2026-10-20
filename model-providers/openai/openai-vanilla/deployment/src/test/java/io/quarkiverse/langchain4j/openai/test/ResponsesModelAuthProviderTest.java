package io.quarkiverse.langchain4j.openai.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.quarkiverse.langchain4j.auth.ModelAuthProvider;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * A {@link ModelAuthProvider} bean supplies the Authorization header on the Responses path, as it does on the
 * chat-completions path. No API key is configured.
 */
public class ResponsesModelAuthProviderTest {

    @ApplicationScoped
    public static class DummyModelAuthProvider implements ModelAuthProvider {

        @Override
        public String getAuthorization(Input input) {
            return "Bearer token-from-provider";
        }
    }

    // "dummy" is the api-key default that means no key is configured; setting it keeps the mock from filling one in
    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClasses(DummyModelAuthProvider.class))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.chat-model.mode", "responses")
            .overrideConfigKey("quarkus.langchain4j.openai.api-key", "dummy");

    @Inject
    ChatModel chatModel;

    @Inject
    StreamingChatModel streamingChatModel;

    @Inject
    MockModelServer mock;

    @BeforeEach
    void setUp() {
        mock.reset();
        mock.chat().reply("hello");
    }

    @Test
    void blockingRequestUsesTheAuthProvider() {
        assertThat(chatModel.chat("hi")).isEqualTo("hello");
        assertAuthorizationFromProvider();
    }

    @Test
    void streamingRequestUsesTheAuthProvider() throws InterruptedException {
        var error = new AtomicReference<Throwable>();
        var latch = new CountDownLatch(1);
        streamingChatModel.chat("hi", new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String token) {
            }

            @Override
            public void onError(Throwable t) {
                error.set(t);
                latch.countDown();
            }

            @Override
            public void onCompleteResponse(ChatResponse response) {
                latch.countDown();
            }
        });
        if (!latch.await(1, TimeUnit.MINUTES)) {
            fail("Streaming did not complete in time");
        }

        assertThat(error.get()).isNull();
        assertAuthorizationFromProvider();
    }

    private void assertAuthorizationFromProvider() {
        CapturedRequest request = mock.chat().singleRequest();
        assertThat(request.path()).isEqualTo("/v1/responses");
        assertThat(request.header("Authorization")).isEqualTo("Bearer token-from-provider");
    }
}
