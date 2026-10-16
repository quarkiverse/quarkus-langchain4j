package io.quarkiverse.langchain4j.security.test;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.service.tool.ToolErrorContext;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;
import io.quarkiverse.langchain4j.HandleToolExecutionError;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.ToolBox;
import io.quarkus.security.Authenticated;
import io.quarkus.security.ForbiddenException;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

/**
 * Verifies that {@link RolesAllowed} on a {@link Tool} method is enforced against the identity of the caller
 * of the AI service. The streaming model calls back from a Vert.x event loop, like HTTP based providers do,
 * so the identity has to survive the hop to the worker thread that runs the blocking tool.
 */
public class ToolRolesAllowedTest {

    private static final String DENIED = "denied";
    private static final String DELETED = "deleted";
    private static final String EVENT_LOOP = "eventloop";

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .withEmptyApplication()
            .overrideConfigKey("quarkus.http.auth.basic", "true")
            .overrideConfigKey("quarkus.security.users.embedded.enabled", "true")
            .overrideConfigKey("quarkus.security.users.embedded.plain-text", "true")
            .overrideConfigKey("quarkus.security.users.embedded.users.alice", "alice")
            .overrideConfigKey("quarkus.security.users.embedded.roles.alice", "admin")
            .overrideConfigKey("quarkus.security.users.embedded.users.bob", "bob")
            .overrideConfigKey("quarkus.security.users.embedded.roles.bob", "user");

    private static final AtomicReference<Throwable> TOOL_FAILURE = new AtomicReference<>();
    private static final AtomicReference<String> CALLBACK_THREAD = new AtomicReference<>();
    private static final AtomicReference<String> TOOL_THREAD = new AtomicReference<>();

    @BeforeEach
    void reset() {
        AdminTools.INVOCATIONS.set(0);
        TOOL_FAILURE.set(null);
        CALLBACK_THREAD.set(null);
        TOOL_THREAD.set(null);
    }

    @Test
    void blockingToolRunsForCallerWithRequiredRole() {
        callAs("alice", "/chat/blocking", DELETED);

        assertThat(AdminTools.INVOCATIONS.get()).isEqualTo(1);
        assertThat(TOOL_FAILURE.get()).isNull();
    }

    @Test
    void blockingToolIsRejectedForCallerWithoutRequiredRole() {
        callAs("bob", "/chat/blocking", DENIED);

        assertThat(AdminTools.INVOCATIONS.get()).isZero();
        assertThat(TOOL_FAILURE.get()).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void streamingToolRunsForCallerWithRequiredRole() {
        callAs("alice", "/chat/streaming", DELETED);

        assertThat(AdminTools.INVOCATIONS.get()).isEqualTo(1);
        assertThat(TOOL_FAILURE.get()).isNull();
        assertThat(CALLBACK_THREAD.get()).contains(EVENT_LOOP);
        assertThat(TOOL_THREAD.get()).doesNotContain(EVENT_LOOP);
    }

    @Test
    void streamingToolIsRejectedForCallerWithoutRequiredRole() {
        callAs("bob", "/chat/streaming", DENIED);

        assertThat(AdminTools.INVOCATIONS.get()).isZero();
        assertThat(TOOL_FAILURE.get()).isInstanceOf(ForbiddenException.class);
        assertThat(CALLBACK_THREAD.get()).contains(EVENT_LOOP);
    }

    private static void callAs(String user, String path, String expectedBody) {
        given().auth().preemptive().basic(user, user)
                .when().get(path)
                .then().statusCode(200).body(equalTo(expectedBody));
    }

    @Path("/chat")
    public static class ChatResource {

        @Inject
        BlockingAssistant blocking;

        @Inject
        StreamingAssistant streaming;

        @GET
        @Path("/blocking")
        @Produces(MediaType.TEXT_PLAIN)
        @Authenticated
        public String blocking() {
            return blocking.chat("clean up");
        }

        @GET
        @Path("/streaming")
        @Produces(MediaType.TEXT_PLAIN)
        @Authenticated
        public Uni<String> streaming() {
            return streaming.chat("clean up").collect().with(Collectors.joining());
        }
    }

    @RegisterAiService(chatLanguageModelSupplier = BlockingModelSupplier.class)
    public interface BlockingAssistant {

        @ToolBox(AdminTools.class)
        String chat(String userMessage);

        @HandleToolExecutionError
        static ToolErrorHandlerResult onToolError(ToolErrorContext context, Exception error) {
            TOOL_FAILURE.set(error);
            return ToolErrorHandlerResult.text(DENIED);
        }
    }

    @RegisterAiService(streamingChatLanguageModelSupplier = StreamingModelSupplier.class)
    public interface StreamingAssistant {

        @ToolBox(AdminTools.class)
        Multi<String> chat(String userMessage);

        @HandleToolExecutionError
        static ToolErrorHandlerResult onToolError(ToolErrorContext context, Exception error) {
            TOOL_FAILURE.set(error);
            return ToolErrorHandlerResult.text(DENIED);
        }
    }

    @ApplicationScoped
    public static class AdminTools {

        static final AtomicInteger INVOCATIONS = new AtomicInteger();

        @Tool
        @RolesAllowed("admin")
        public String deleteEverything() {
            INVOCATIONS.incrementAndGet();
            TOOL_THREAD.set(Thread.currentThread().getName());
            return DELETED;
        }
    }

    public static class BlockingModelSupplier implements Supplier<ChatModel> {
        @Override
        public ChatModel get() {
            return new ToolCallingChatModel();
        }
    }

    public static class StreamingModelSupplier implements Supplier<StreamingChatModel> {
        @Override
        public StreamingChatModel get() {
            return new ToolCallingStreamingChatModel();
        }
    }

    /**
     * Asks for the tool on the first turn, then answers with whatever the tool returned.
     */
    static ChatResponse nextTurn(List<ChatMessage> messages) {
        ChatMessage last = messages.get(messages.size() - 1);
        if (last instanceof ToolExecutionResultMessage toolResult) {
            return ChatResponse.builder().aiMessage(new AiMessage(toolResult.text())).build();
        }
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("delete-1")
                .name("deleteEverything")
                .arguments("{}")
                .build();
        return ChatResponse.builder().aiMessage(AiMessage.from(request)).build();
    }

    public static class ToolCallingChatModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            return nextTurn(chatRequest.messages());
        }
    }

    public static class ToolCallingStreamingChatModel implements StreamingChatModel {
        @Override
        public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
            Context context = Vertx.currentContext();
            context.runOnContext(new EventLoopCallback(nextTurn(chatRequest.messages()), handler));
        }
    }

    static class EventLoopCallback implements Handler<Void> {

        private final ChatResponse response;
        private final StreamingChatResponseHandler handler;

        EventLoopCallback(ChatResponse response, StreamingChatResponseHandler handler) {
            this.response = response;
            this.handler = handler;
        }

        @Override
        public void handle(Void ignored) {
            CALLBACK_THREAD.set(Thread.currentThread().getName());
            if (!response.aiMessage().hasToolExecutionRequests()) {
                handler.onPartialResponse(response.aiMessage().text());
            }
            handler.onCompleteResponse(response);
        }
    }
}
