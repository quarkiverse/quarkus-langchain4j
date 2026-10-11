package io.quarkiverse.langchain4j.test.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailException;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.guardrail.OutputGuardrails;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderResult;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.ToolBox;
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.common.annotation.NonBlocking;
import io.smallrye.common.vertx.VertxContext;
import io.vertx.core.Context;
import io.vertx.core.Vertx;

class ToolExecutionModelWithTokenStreamTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(Assistant.class, ModelSupplier.class, RequestState.class,
                            BlockingTool.class, NonBlockingTool.class, PassGuardrail.class, FailGuardrail.class,
                            ProviderAssistant.class, ToolProviderSupplier.class));

    @Inject
    Assistant assistant;

    @Inject
    ProviderAssistant providerAssistant;

    @Inject
    Vertx vertx;

    @Inject
    RequestState requestState;

    @Test
    void blockingToolsRunOnWorkersWithRequestContextAcrossRounds() throws Exception {
        assertThat(invoke(assistant::blocking).get(10, TimeUnit.SECONDS)).isEqualTo("false/2");
    }

    @Test
    void providedToolsRunOnWorkersWithRequestContextAcrossRounds() throws Exception {
        assertThat(invoke(providerAssistant::chat).get(10, TimeUnit.SECONDS)).isEqualTo("false/2");
    }

    @Test
    void nonBlockingToolsStayOnTheEventLoop() throws Exception {
        assertThat(invoke(assistant::nonBlocking).get(10, TimeUnit.SECONDS)).isEqualTo("true/2");
    }

    @Test
    void outputGuardrailsStillValidateTheFinalResponse() throws Exception {
        PassGuardrail.invocations.set(0);
        assertThat(invoke(assistant::guarded).get(10, TimeUnit.SECONDS)).isEqualTo("false/2");
        assertThat(PassGuardrail.invocations).hasValue(1);
    }

    @Test
    void outputGuardrailFailuresReachTheErrorHandler() {
        assertThatThrownBy(() -> invoke(assistant::rejected).get(10, TimeUnit.SECONDS))
                .hasCauseInstanceOf(OutputGuardrailException.class);
    }

    private CompletableFuture<String> invoke(Supplier<TokenStream> invocation) {
        CompletableFuture<String> result = new CompletableFuture<>();
        Context context = VertxContext.getOrCreateDuplicatedContext(vertx);
        context.runOnContext(ignored -> {
            Arc.container().requestContext().activate();
            String requestId = requestState.id();
            try {
                invocation.get()
                        .onPartialResponse(ignoredToken -> {
                        })
                        .onCompleteResponse(response -> {
                            String text = response.aiMessage().text();
                            if (!text.startsWith(requestId + "/")) {
                                result.completeExceptionally(new IllegalStateException("Request context was lost: " + text));
                            } else {
                                result.complete(text.substring(requestId.length() + 1));
                            }
                        })
                        .onError(result::completeExceptionally)
                        .start();
            } catch (Exception e) {
                result.completeExceptionally(e);
            }
        });
        result.whenComplete((value, error) -> context.runOnContext(ignored -> Arc.container().requestContext().terminate()));
        return result;
    }

    @RegisterAiService(streamingChatLanguageModelSupplier = ModelSupplier.class)
    interface Assistant {
        @dev.langchain4j.service.UserMessage("Call the tool twice")
        @ToolBox(BlockingTool.class)
        TokenStream blocking();

        @dev.langchain4j.service.UserMessage("Call the tool twice")
        @ToolBox(NonBlockingTool.class)
        TokenStream nonBlocking();

        @dev.langchain4j.service.UserMessage("Call the tool twice")
        @ToolBox(BlockingTool.class)
        @OutputGuardrails(PassGuardrail.class)
        TokenStream guarded();

        @dev.langchain4j.service.UserMessage("Call the tool twice")
        @ToolBox(BlockingTool.class)
        @OutputGuardrails(FailGuardrail.class)
        TokenStream rejected();
    }

    @RegisterAiService(streamingChatLanguageModelSupplier = ModelSupplier.class, toolProviderSupplier = ToolProviderSupplier.class)
    interface ProviderAssistant {
        @dev.langchain4j.service.UserMessage("Call the tool twice")
        TokenStream chat();
    }

    @Singleton
    public static class ToolProviderSupplier implements Supplier<ToolProvider> {
        @Override
        public ToolProvider get() {
            return request -> ToolProviderResult.builder()
                    .add(ToolSpecification.builder().name("providedTool").description("Returns request state").build(),
                            (toolRequest, memoryId) -> Arc.container().instance(RequestState.class).get().result())
                    .build();
        }
    }

    @RequestScoped
    public static class RequestState {
        private final String id = UUID.randomUUID().toString();
        private int calls;

        public String id() {
            return id;
        }

        public String result() {
            return id + "/" + Context.isOnEventLoopThread() + "/" + ++calls;
        }
    }

    @RequestScoped
    public static class BlockingTool {
        @Inject
        RequestState state;

        @Tool
        public String blocking() {
            return state.result();
        }
    }

    @RequestScoped
    public static class NonBlockingTool {
        @Inject
        RequestState state;

        @Tool
        @NonBlocking
        public String nonBlocking() {
            return state.result();
        }
    }

    @Singleton
    public static class PassGuardrail implements OutputGuardrail {
        static final AtomicInteger invocations = new AtomicInteger();

        @Override
        public OutputGuardrailResult validate(AiMessage response) {
            invocations.incrementAndGet();
            return success();
        }
    }

    @Singleton
    public static class FailGuardrail implements OutputGuardrail {
        @Override
        public OutputGuardrailResult validate(AiMessage response) {
            return fatal("Rejected by the test guardrail");
        }
    }

    public static class ModelSupplier implements Supplier<StreamingChatModel> {
        @Override
        public StreamingChatModel get() {
            return new StreamingChatModel() {
                @Override
                public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
                    Vertx.currentContext().runOnContext(ignored -> {
                        try {
                            var results = request.messages().stream()
                                    .filter(ToolExecutionResultMessage.class::isInstance)
                                    .map(ToolExecutionResultMessage.class::cast).toList();
                            AiMessage message;
                            if (results.size() < 2) {
                                message = AiMessage.from(ToolExecutionRequest.builder()
                                        .id("call-" + results.size())
                                        .name(request.toolSpecifications().get(0).name())
                                        .arguments("{}").build());
                            } else {
                                String text = results.get(results.size() - 1).text();
                                handler.onPartialResponse(text);
                                message = AiMessage.from(text);
                            }
                            handler.onCompleteResponse(ChatResponse.builder().aiMessage(message).build());
                        } catch (Exception e) {
                            handler.onError(e);
                        }
                    });
                }
            };
        }
    }
}
