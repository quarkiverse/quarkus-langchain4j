package io.quarkiverse.langchain4j.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.observability.api.event.AiServiceRequestIssuedEvent;
import dev.langchain4j.service.TokenStream;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * A {@code TokenStream}-returning AI service method must be given the same {@link InvocationContext} as every
 * other return type. Upstream's {@code AiServiceTokenStream} reads the method arguments out of it to pick up a
 * user-supplied {@link ChatRequestParameters} argument, and hands it to the observability events and to tool
 * execution.
 */
public class TokenStreamInvocationContextTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(StreamingAssistant.class, CapturingStreamingModelSupplier.class,
                            RequestIssuedObserver.class));

    static volatile ChatRequest lastCapturedRequest;

    @Inject
    StreamingAssistant assistant;

    @Test
    @ActivateRequestContext
    void chatRequestParametersArgumentReachesTheModel() throws Exception {
        lastCapturedRequest = null;

        ChatRequestParameters params = ChatRequestParameters.builder()
                .temperature(0.42)
                .topP(0.9)
                .build();

        awaitCompletion(assistant.chat("hello", params));

        assertThat(lastCapturedRequest).isNotNull();
        assertThat(lastCapturedRequest.parameters()).isNotNull();
        assertThat(lastCapturedRequest.parameters().temperature()).isEqualTo(0.42);
        assertThat(lastCapturedRequest.parameters().topP()).isEqualTo(0.9);
    }

    @Test
    @ActivateRequestContext
    void invocationContextIsPopulatedInObservabilityEvents() throws Exception {
        RequestIssuedObserver.observed = null;

        awaitCompletion(assistant.chat("hello", null));

        InvocationContext invocationContext = RequestIssuedObserver.observed;
        assertThat(invocationContext).isNotNull();
        assertThat(invocationContext.interfaceName()).isEqualTo(StreamingAssistant.class.getName());
        assertThat(invocationContext.methodName()).isEqualTo("chat");
        assertThat(invocationContext.invocationId()).isNotNull();
        assertThat(invocationContext.methodArguments()).hasSize(2);
    }

    private static void awaitCompletion(TokenStream tokenStream) throws Exception {
        CompletableFuture<ChatResponse> completion = new CompletableFuture<>();
        tokenStream
                .onPartialResponse(ignored -> {
                })
                .onCompleteResponse(completion::complete)
                .onError(completion::completeExceptionally)
                .start();
        completion.get(30, TimeUnit.SECONDS);
    }

    @RegisterAiService(streamingChatLanguageModelSupplier = CapturingStreamingModelSupplier.class, chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    public interface StreamingAssistant {
        TokenStream chat(@dev.langchain4j.service.UserMessage String userMessage, ChatRequestParameters params);
    }

    public static class CapturingStreamingModelSupplier implements Supplier<StreamingChatModel> {
        @Override
        public StreamingChatModel get() {
            return new StreamingChatModel() {
                @Override
                public void chat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
                    lastCapturedRequest = chatRequest;
                    handler.onPartialResponse("response");
                    handler.onCompleteResponse(ChatResponse.builder()
                            .aiMessage(AiMessage.from("response"))
                            .build());
                }
            };
        }
    }

    @ApplicationScoped
    public static class RequestIssuedObserver {
        static volatile InvocationContext observed;

        void onRequestIssued(@Observes AiServiceRequestIssuedEvent event) {
            observed = event.invocationContext();
        }
    }
}
