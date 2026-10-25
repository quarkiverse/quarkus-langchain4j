package io.quarkiverse.langchain4j.test.rag;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.rag.AugmentationRequest;
import dev.langchain4j.rag.AugmentationResult;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.Content;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.security.Authenticated;
import io.quarkus.security.ForbiddenException;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

/**
 * Verifies that access to the RAG store can be controlled with roles: a request scoped
 * {@link RetrievalAugmentor} is discovered as the CDI bean backing the AI service and enforces the
 * roles of the caller by injecting {@link SecurityIdentity}.
 * <p>
 * The augmentor runs before the model is invoked. The blocking method calls it on the caller thread;
 * the streaming method is invoked on the event loop, so the augmentor runs on a worker thread when
 * the returned {@link Multi} is subscribed, with the request context propagated. The security
 * identity of the HTTP caller is available in both cases.
 * <p>
 * Denial is implemented by throwing {@link ForbiddenException} instead of returning no
 * augmentation: throwing fails closed, keeps the caller in control of how the denial is surfaced
 * (here HTTP 403) and guarantees the model is never invoked, while silently skipping the
 * augmentation would let the model answer without the restricted context and would hide the
 * authorization failure.
 */
public class RagStoreAccessControlTest {

    private static final String REQUIRED_ROLE = "rag-reader";
    private static final String RESTRICTED_CONTENT = "top secret";
    private static final String ALLOWED_USER = "alice";
    private static final String DENIED_USER = "bob";

    private static final AtomicInteger MODEL_INVOCATIONS = new AtomicInteger();
    private static final AtomicReference<String> AUGMENTED_FOR = new AtomicReference<>();

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .withEmptyApplication()
            .overrideConfigKey("quarkus.http.auth.basic", "true")
            .overrideConfigKey("quarkus.security.users.embedded.enabled", "true")
            .overrideConfigKey("quarkus.security.users.embedded.plain-text", "true")
            .overrideConfigKey("quarkus.security.users.embedded.users.alice", "alice")
            .overrideConfigKey("quarkus.security.users.embedded.roles.alice", REQUIRED_ROLE)
            .overrideConfigKey("quarkus.security.users.embedded.users.bob", "bob")
            .overrideConfigKey("quarkus.security.users.embedded.roles.bob", "user");

    @BeforeEach
    void reset() {
        MODEL_INVOCATIONS.set(0);
        AUGMENTED_FOR.set(null);
    }

    @Test
    void blockingCallIsAugmentedForUserWithRequiredRole() {
        given().auth().preemptive().basic(ALLOWED_USER, ALLOWED_USER)
                .when().get("/chat/blocking")
                .then().statusCode(200).body(containsString(RESTRICTED_CONTENT));

        assertThat(AUGMENTED_FOR.get()).isEqualTo(ALLOWED_USER);
        assertThat(MODEL_INVOCATIONS.get()).isEqualTo(1);
    }

    @Test
    void blockingCallIsDeniedForUserWithoutRequiredRole() {
        given().auth().preemptive().basic(DENIED_USER, DENIED_USER)
                .when().get("/chat/blocking")
                .then().statusCode(403);

        assertThat(AUGMENTED_FOR.get()).isEqualTo(DENIED_USER);
        assertThat(MODEL_INVOCATIONS.get()).isZero();
    }

    @Test
    void streamingCallIsAugmentedForUserWithRequiredRole() {
        given().auth().preemptive().basic(ALLOWED_USER, ALLOWED_USER)
                .when().get("/chat/streaming")
                .then().statusCode(200).body(containsString(RESTRICTED_CONTENT));

        assertThat(AUGMENTED_FOR.get()).isEqualTo(ALLOWED_USER);
        assertThat(MODEL_INVOCATIONS.get()).isEqualTo(1);
    }

    @Test
    void streamingCallIsDeniedForUserWithoutRequiredRole() {
        given().auth().preemptive().basic(DENIED_USER, DENIED_USER)
                .when().get("/chat/streaming")
                .then().statusCode(403);

        assertThat(AUGMENTED_FOR.get()).isEqualTo(DENIED_USER);
        assertThat(MODEL_INVOCATIONS.get()).isZero();
    }

    @Path("/chat")
    public static class ChatResource {

        @Inject
        Assistant assistant;

        @GET
        @Path("/blocking")
        @Produces(MediaType.TEXT_PLAIN)
        @Authenticated
        public String blocking() {
            return assistant.chat("hello");
        }

        @GET
        @Path("/streaming")
        @Produces(MediaType.TEXT_PLAIN)
        @Authenticated
        public Uni<String> streaming() {
            return assistant.chatStream("hello").collect().with(Collectors.joining());
        }
    }

    @RegisterAiService(chatLanguageModelSupplier = EchoChatModelSupplier.class, streamingChatLanguageModelSupplier = EchoStreamingChatModelSupplier.class, chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    public interface Assistant {

        String chat(String userMessage);

        Multi<String> chatStream(String userMessage);
    }

    @RequestScoped
    public static class RestrictedRetrievalAugmentor implements RetrievalAugmentor {

        @Inject
        SecurityIdentity identity;

        @Override
        public AugmentationResult augment(AugmentationRequest augmentationRequest) {
            AUGMENTED_FOR.set(identity.getPrincipal().getName());
            if (!identity.hasRole(REQUIRED_ROLE)) {
                throw new ForbiddenException("Caller does not have the required role to access the RAG store");
            }
            UserMessage userMessage = (UserMessage) augmentationRequest.chatMessage();
            return new AugmentationResult(
                    UserMessage.from(userMessage.singleText() + "\n\n" + RESTRICTED_CONTENT),
                    List.of(Content.from(RESTRICTED_CONTENT)));
        }
    }

    public static class EchoChatModelSupplier implements Supplier<ChatModel> {

        @Override
        public ChatModel get() {
            return new EchoChatModel();
        }
    }

    public static class EchoChatModel implements ChatModel {

        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            MODEL_INVOCATIONS.incrementAndGet();
            return ChatResponse.builder().aiMessage(new AiMessage(lastUserText(chatRequest))).build();
        }
    }

    public static class EchoStreamingChatModelSupplier implements Supplier<StreamingChatModel> {

        @Override
        public StreamingChatModel get() {
            return new EchoStreamingChatModel();
        }
    }

    public static class EchoStreamingChatModel implements StreamingChatModel {

        @Override
        public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
            MODEL_INVOCATIONS.incrementAndGet();
            String text = lastUserText(chatRequest);
            handler.onPartialResponse(text);
            handler.onCompleteResponse(ChatResponse.builder().aiMessage(new AiMessage(text)).build());
        }
    }

    private static String lastUserText(ChatRequest chatRequest) {
        List<ChatMessage> messages = chatRequest.messages();
        ChatMessage last = messages.get(messages.size() - 1);
        return ((UserMessage) last).singleText();
    }
}
