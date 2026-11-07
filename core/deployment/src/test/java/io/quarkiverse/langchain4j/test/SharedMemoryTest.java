package io.quarkiverse.langchain4j.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.SharedMemory;
import io.quarkus.test.QuarkusExtensionTest;

public class SharedMemoryTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(AgentOne.class, Agent99.class, TypeLevelAgent.class, AppScopedAgent.class,
                            MyChatModelSupplier.class, RecordingChatMemoryProvider.class));

    @Inject
    AgentOne agentOne;

    @Inject
    Agent99 agent99;

    @Inject
    TypeLevelAgent typeLevelAgent;

    @Inject
    AppScopedAgent appScopedAgent;

    @Inject
    RecordingChatMemoryProvider chatMemoryProvider;

    @BeforeEach
    void reset() {
        chatMemoryProvider.memories.clear();
    }

    @Test
    @ActivateRequestContext
    void methodsWithSameSharedMemoryShareTheChatMemory() {
        assertThat(agentOne.chat("hello")).isEqualTo("1");
        // user + ai messages from the previous call + the new user message
        assertThat(agent99.chat("hello")).isEqualTo("3");

        assertThat(chatMemoryProvider.memories.keySet()).singleElement()
                .asString().endsWith("#get-smart");
    }

    @Test
    @ActivateRequestContext
    void methodWithoutSharedMemoryUsesPerMethodMemoryId() {
        assertThat(agentOne.chat("hello")).isEqualTo("1");
        assertThat(agentOne.unshared("hello")).isEqualTo("1");

        assertThat(chatMemoryProvider.memories.keySet()).hasSize(2)
                .anySatisfy(id -> assertThat(id).asString().endsWith("#get-smart"))
                .anySatisfy(id -> assertThat(id).asString().endsWith("#" + AgentOne.class.getName() + ".unshared"));
    }

    @Test
    @ActivateRequestContext
    void typeLevelAnnotationAppliesToAllMethodsUnlessOverridden() {
        assertThat(typeLevelAgent.first("hello")).isEqualTo("1");
        assertThat(typeLevelAgent.second("hello")).isEqualTo("3");
        assertThat(typeLevelAgent.overridden("hello")).isEqualTo("1");

        assertThat(chatMemoryProvider.memories.keySet()).hasSize(2)
                .anySatisfy(id -> assertThat(id).asString().endsWith("#" + SharedMemory.DEFAULT))
                .anySatisfy(id -> assertThat(id).asString().endsWith("#get-smart"));
    }

    @Test
    @ActivateRequestContext
    void explicitMemoryIdTakesPrecedence() {
        assertThat(agentOne.withMemoryId("my-id", "hello")).isEqualTo("1");

        assertThat(chatMemoryProvider.memories.keySet()).containsExactly("my-id");
    }

    /**
     * An application scoped AI service can be invoked outside a request context, from a scheduled task or during
     * application startup for instance. No provider reports an ambient id in that case, so the two kinds of method have
     * to be told apart: a method without {@code @SharedMemory} keeps the plain "default" id that vanilla LC4J uses,
     * while a {@code @SharedMemory} one still gets the suffix that groups it with its siblings.
     *
     * <p>
     * Both halves live in one test because the memory ids here carry no ambient prefix, so every method invoked
     * without a request context would otherwise compete for the same state in the provider.
     */
    @Test
    void memoryIdsOutsideRequestScope() {
        // Plain methods fall back to "default", so the second call sees the messages of the first...
        assertThat(appScopedAgent.unshared("hello")).isEqualTo("1");
        assertThat(appScopedAgent.anotherUnshared("hello")).isEqualTo("3");

        // ...while the @SharedMemory ones are grouped under their own suffix and start with an empty memory.
        assertThat(appScopedAgent.chat("hello")).isEqualTo("1");
        assertThat(appScopedAgent.chatAgain("hello")).isEqualTo("3");

        assertThat(chatMemoryProvider.memories.keySet()).hasSize(2)
                .anySatisfy(id -> assertThat(id).asString().isEqualTo("default"))
                .anySatisfy(id -> assertThat(id).asString().endsWith("#outside-request-scope"));
    }

    @RegisterAiService(chatLanguageModelSupplier = MyChatModelSupplier.class)
    public interface AgentOne {

        @SharedMemory("get-smart")
        String chat(@UserMessage String msg);

        String unshared(@UserMessage String msg);

        @SharedMemory("get-smart")
        String withMemoryId(@MemoryId String id, @UserMessage String msg);
    }

    @RegisterAiService(chatLanguageModelSupplier = MyChatModelSupplier.class)
    public interface Agent99 {

        @SharedMemory("get-smart")
        String chat(@UserMessage String msg);
    }

    @SharedMemory
    @RegisterAiService(chatLanguageModelSupplier = MyChatModelSupplier.class)
    public interface TypeLevelAgent {

        String first(@UserMessage String msg);

        String second(@UserMessage String msg);

        @SharedMemory("get-smart")
        String overridden(@UserMessage String msg);
    }

    /**
     * Application scoped since #3077, so it is usable without an active request context - which is what the two tests
     * above this interface exercise.
     */
    @ApplicationScoped
    @RegisterAiService(chatLanguageModelSupplier = MyChatModelSupplier.class)
    public interface AppScopedAgent {

        @SharedMemory("outside-request-scope")
        String chat(@UserMessage String msg);

        @SharedMemory("outside-request-scope")
        String chatAgain(@UserMessage String msg);

        String unshared(@UserMessage String msg);

        String anotherUnshared(@UserMessage String msg);
    }

    public static class MyChatModelSupplier implements Supplier<ChatModel> {

        @Override
        public ChatModel get() {
            return new ChatModel() {
                @Override
                public ChatResponse doChat(ChatRequest chatRequest) {
                    return ChatResponse.builder()
                            .aiMessage(new AiMessage(String.valueOf(chatRequest.messages().size())))
                            .build();
                }
            };
        }
    }

    @Singleton
    public static class RecordingChatMemoryProvider implements ChatMemoryProvider {

        final Map<Object, ChatMemory> memories = new ConcurrentHashMap<>();

        @Override
        public ChatMemory get(Object memoryId) {
            return memories.computeIfAbsent(memoryId,
                    id -> MessageWindowChatMemory.builder().id(id).maxMessages(10).build());
        }
    }
}
