package io.quarkiverse.langchain4j.test.rag;

import static io.quarkiverse.langchain4j.test.rag.UserSpecificRagSupport.ALICE_DOCUMENT;
import static io.quarkiverse.langchain4j.test.rag.UserSpecificRagSupport.BOB_DOCUMENT;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.test.rag.UserSpecificRagSupport.EchoModelSupplier;
import io.quarkiverse.langchain4j.test.rag.UserSpecificRagSupport.EchoStreamingModelSupplier;
import io.quarkiverse.langchain4j.test.rag.UserSpecificRagSupport.UserAugmentorSupplier;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

/**
 * Verifies that a retrieval augmentor can serve documents that are private to the authenticated
 * user: the augmentor resolves the caller from the request-scoped SecurityIdentity, so alice
 * retrieves only her documents and bob only his, for blocking and streaming invocations.
 */
public class UserSpecificRagAugmentationTest {

    private static final String QUESTION = "What is my launch passcode?";
    private static final String SUMMARY_QUESTION = "Summarize my documents.";

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(UserSpecificRagAugmentationTest::createArchive)
            .overrideConfigKey("quarkus.http.auth.basic", "true")
            .overrideConfigKey("quarkus.security.users.embedded.enabled", "true")
            .overrideConfigKey("quarkus.security.users.embedded.plain-text", "true")
            .overrideConfigKey("quarkus.security.users.embedded.users.alice", "alice")
            .overrideConfigKey("quarkus.security.users.embedded.roles.alice", "user")
            .overrideConfigKey("quarkus.security.users.embedded.users.bob", "bob")
            .overrideConfigKey("quarkus.security.users.embedded.roles.bob", "user");

    @BeforeEach
    void resetObservations() {
        UserAugmentorSupplier.LAST_USER.set(null);
        UserAugmentorSupplier.LAST_METHOD.set(null);
        UserAugmentorSupplier.LAST_INTERFACE.set(null);
        UserAugmentorSupplier.LAST_MEMORY_ID.set(null);
        UserAugmentorSupplier.LAST_METHOD_ARGUMENTS.set(null);
    }

    @Test
    void aliceBlockingRetrievesOnlyHerDocuments() {
        String answer = chatAs("alice", "/chat/blocking");

        assertThat(answer).contains(ALICE_DOCUMENT);
        assertThat(answer).doesNotContain(BOB_DOCUMENT);
        assertAugmentedFor("alice", "chat", QUESTION);
    }

    @Test
    void bobBlockingRetrievesOnlyHisDocuments() {
        String answer = chatAs("bob", "/chat/blocking");

        assertThat(answer).contains(BOB_DOCUMENT);
        assertThat(answer).doesNotContain(ALICE_DOCUMENT);
        assertAugmentedFor("bob", "chat", QUESTION);
    }

    @Test
    void aliceStreamingRetrievesOnlyHerDocuments() {
        String answer = chatAs("alice", "/chat/streaming");

        assertThat(answer).contains(ALICE_DOCUMENT);
        assertThat(answer).doesNotContain(BOB_DOCUMENT);
        assertAugmentedFor("alice", "stream", QUESTION);
    }

    @Test
    void bobStreamingRetrievesOnlyHisDocuments() {
        String answer = chatAs("bob", "/chat/streaming");

        assertThat(answer).contains(BOB_DOCUMENT);
        assertThat(answer).doesNotContain(ALICE_DOCUMENT);
        assertAugmentedFor("bob", "stream", QUESTION);
    }

    @Test
    void summarizeUsesShortInstructionForTheSameUser() {
        String answer = chatAs("alice", "/chat/summarize");

        assertThat(answer).contains(ALICE_DOCUMENT).contains("Answer briefly");
        assertThat(answer).doesNotContain(BOB_DOCUMENT);
        assertAugmentedFor("alice", "summarize", SUMMARY_QUESTION);
    }

    @Test
    void bobDoesNotSeeAliceConversationHistory() {
        chatAs("alice", "/chat/blocking");
        String answer = chatAs("bob", "/chat/blocking");

        assertThat(answer).contains(BOB_DOCUMENT);
        assertThat(answer).doesNotContain(ALICE_DOCUMENT);
    }

    @Test
    void anonymousCallerIsRejected() {
        given().when().get("/chat/blocking").then().statusCode(401);
    }

    private static JavaArchive createArchive() {
        return ShrinkWrap.create(JavaArchive.class)
                .addClasses(UserSpecificRagAugmentationTest.class, UserSpecificRagSupport.class);
    }

    private static String chatAs(String user, String path) {
        return given().auth().preemptive().basic(user, user)
                .when().get(path)
                .then().statusCode(200)
                .extract().asString();
    }

    private static void assertAugmentedFor(String user, String methodName, String question) {
        assertThat(UserAugmentorSupplier.AUGMENTOR_CREATIONS.get()).isEqualTo(1);
        assertThat(UserAugmentorSupplier.LAST_USER.get()).isEqualTo(user);
        assertThat(UserAugmentorSupplier.LAST_METHOD.get()).isEqualTo(methodName);
        assertThat(UserAugmentorSupplier.LAST_INTERFACE.get()).isEqualTo(Assistant.class.getName());
        assertThat(UserAugmentorSupplier.LAST_MEMORY_ID.get()).isEqualTo(user);
        assertThat(UserAugmentorSupplier.LAST_METHOD_ARGUMENTS.get()).containsExactly(user, question);
    }

    @Path("/chat")
    public static class ChatResource {

        @Inject
        Assistant assistant;

        @Inject
        SecurityIdentity identity;

        @GET
        @Path("/blocking")
        @Produces(MediaType.TEXT_PLAIN)
        @Authenticated
        public String blocking() {
            return assistant.chat(username(), QUESTION);
        }

        @GET
        @Path("/summarize")
        @Produces(MediaType.TEXT_PLAIN)
        @Authenticated
        public String summarize() {
            return assistant.summarize(username(), SUMMARY_QUESTION);
        }

        @GET
        @Path("/streaming")
        @Produces(MediaType.TEXT_PLAIN)
        @Authenticated
        public Uni<String> streaming() {
            return assistant.stream(username(), QUESTION)
                    .collect().with(Collectors.joining());
        }

        private String username() {
            return identity.getPrincipal().getName();
        }
    }

    @RegisterAiService(chatLanguageModelSupplier = EchoModelSupplier.class, streamingChatLanguageModelSupplier = EchoStreamingModelSupplier.class, retrievalAugmentor = UserAugmentorSupplier.class)
    @ApplicationScoped
    public interface Assistant {

        String chat(@MemoryId String memoryId, @UserMessage String question);

        String summarize(@MemoryId String memoryId, @UserMessage String text);

        Multi<String> stream(@MemoryId String memoryId, @UserMessage String question);
    }
}
