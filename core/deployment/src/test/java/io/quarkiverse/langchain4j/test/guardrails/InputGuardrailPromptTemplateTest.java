package io.quarkiverse.langchain4j.test.guardrails;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.guardrails.InputGuardrail;
import io.quarkiverse.langchain4j.guardrails.InputGuardrailParams;
import io.quarkiverse.langchain4j.guardrails.InputGuardrailResult;
import io.quarkiverse.langchain4j.guardrails.InputGuardrails;
import io.quarkiverse.langchain4j.runtime.aiservice.NoopChatMemory;
import io.quarkus.test.QuarkusUnitTest;

public class InputGuardrailPromptTemplateTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(MyAiService.class, MyAiService.class, GuardrailValidation.class,
                            MyChatModel.class, MyChatModelSupplier.class, MyMemoryProviderSupplier.class));
    @Inject
    MyAiService aiService;

    @Inject
    GuardrailValidation guardrailValidation;

    @Test
    @ActivateRequestContext
    void shouldWorkNoParameters() {
        aiService.getJoke();
        assertThat(guardrailValidation.spyUserMessageTemplate()).isEqualTo("Tell me a joke");
        assertThat(guardrailValidation.spyVariables()).isEmpty();
    }

    @Test
    @ActivateRequestContext
    void shouldWorkWithMemoryId() {
        aiService.getAnotherJoke("memory-id-001");
        assertThat(guardrailValidation.spyUserMessageTemplate()).isEqualTo("Tell me another joke");
        assertThat(guardrailValidation.spyVariables()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "memoryId", "memory-id-001",
                "it", "memory-id-001"));
    }

    @Test
    @ActivateRequestContext
    void shouldWorkWithNoMemoryIdAndOneParameter() {
        aiService.sayHiToMyFriendNoMemory("Rambo");
        assertThat(guardrailValidation.spyUserMessageTemplate()).isEqualTo("Say hi to my friend {friend}!");
        assertThat(guardrailValidation.spyVariables())
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "friend", "Rambo",
                        "it", "Rambo"));
    }

    @Test
    @ActivateRequestContext
    void shouldWorkWithMemoryIdAndOneParameter() {
        aiService.sayHiToMyFriend("1", "Chuck Norris");
        assertThat(guardrailValidation.spyUserMessageTemplate()).isEqualTo("Say hi to my friend {friend}!");
        assertThat(guardrailValidation.spyVariables())
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "friend", "Chuck Norris",
                        "mem", "1"));
    }

    @Test
    @ActivateRequestContext
    void shouldWorkWithNoMemoryIdAndThreeParameters() {
        aiService.sayHiToMyFriends("Chuck Norris", "Jean-Claude Van Damme", "Silvester Stallone");
        assertThat(guardrailValidation.spyUserMessageTemplate())
                .isEqualTo("Tell me something about {topic1}, {topic2}, {topic3}!");
        assertThat(guardrailValidation.spyVariables())
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "topic1", "Chuck Norris",
                        "topic2", "Jean-Claude Van Damme",
                        "topic3", "Silvester Stallone"));
    }

    @Test
    @ActivateRequestContext
    void shouldWorkWithNoMemoryIdAndList() {
        aiService.sayHiToMyFriends(List.of("Chuck Norris", "Jean-Claude Van Damme", "Silvester Stallone"));
        assertThat(guardrailValidation.spyUserMessageText())
                .isEqualTo("Tell me something about [Chuck Norris, Jean-Claude Van Damme, Silvester Stallone]!");
        assertThat(guardrailValidation.spyUserMessageTemplate()).isEqualTo("Tell me something about {topics}!");
        assertThat(guardrailValidation.spyVariables())
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "topics", List.of("Chuck Norris", "Jean-Claude Van Damme", "Silvester Stallone"),
                        "it", List.of("Chuck Norris", "Jean-Claude Van Damme", "Silvester Stallone")));
    }

    @Test
    @ActivateRequestContext
    void shouldWorkWithMemoryIdAndList() {
        aiService.sayHiToMyFriends("memory-id-007", List.of("Chuck Norris", "Jean-Claude Van Damme", "Silvester Stallone"));
        assertThat(guardrailValidation.spyUserMessageText()).isEqualTo(
                "Tell me something about [Chuck Norris, Jean-Claude Van Damme, Silvester Stallone]! This is my memory id: memory-id-007");
        assertThat(guardrailValidation.spyUserMessageTemplate())
                .isEqualTo("Tell me something about {topics}! This is my memory id: {memoryId}");
        assertThat(guardrailValidation.spyVariables())
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "topics", List.of("Chuck Norris", "Jean-Claude Van Damme", "Silvester Stallone"),
                        "memoryId", "memory-id-007"));
    }

    @Test
    @ActivateRequestContext
    void shouldWorkWithMemoryIdAndOneItemFromList() {
        aiService.sayHiToMyFriend("memory-id-007", List.of("Chuck Norris", "Jean-Claude Van Damme", "Silvester Stallone"));
        assertThat(guardrailValidation.spyUserMessageText())
                .isEqualTo("Tell me something about Chuck Norris! This is my memory id: memory-id-007");
        assertThat(guardrailValidation.spyUserMessageTemplate())
                .isEqualTo("Tell me something about {topics[0]}! This is my memory id: {memoryId}");
        assertThat(guardrailValidation.spyVariables())
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "topics", List.of("Chuck Norris", "Jean-Claude Van Damme", "Silvester Stallone"),
                        "memoryId", "memory-id-007"));
    }

    @Test
    @ActivateRequestContext
    void shouldWorkWithNoUserMessage() {
        // UserMessage annotation is not provided, then no user message template should be available
        aiService.saySomething("Is this a parameter or a prompt?");
        assertThat(guardrailValidation.spyUserMessageTemplate()).isEmpty();
        assertThat(guardrailValidation.spyVariables()).isEmpty();
    }

    @RegisterAiService(chatLanguageModelSupplier = MyChatModelSupplier.class, chatMemoryProviderSupplier = MyMemoryProviderSupplier.class)
    public interface MyAiService {

        @InputGuardrails(GuardrailValidation.class)
        @UserMessage("Tell me a joke")
        String getJoke();

        @UserMessage("Tell me another joke")
        @InputGuardrails(GuardrailValidation.class)
        String getAnotherJoke(@MemoryId String memoryId);

        @UserMessage("Say hi to my friend {friend}!")
        @InputGuardrails(GuardrailValidation.class)
        String sayHiToMyFriendNoMemory(String friend);

        @UserMessage("Say hi to my friend {friend}!")
        @InputGuardrails(GuardrailValidation.class)
        String sayHiToMyFriend(@MemoryId String mem, String friend);

        @UserMessage("Tell me something about {topic1}, {topic2}, {topic3}!")
        @InputGuardrails(GuardrailValidation.class)
        String sayHiToMyFriends(String topic1, String topic2, String topic3);

        @UserMessage("Tell me something about {topics}!")
        @InputGuardrails(GuardrailValidation.class)
        String sayHiToMyFriends(List<String> topics);

        @UserMessage("Tell me something about {topics}! This is my memory id: {memoryId}")
        @InputGuardrails(GuardrailValidation.class)
        String sayHiToMyFriends(@MemoryId String memoryId, List<String> topics);

        @UserMessage("Tell me something about {topics[0]}! This is my memory id: {memoryId}")
        @InputGuardrails(GuardrailValidation.class)
        String sayHiToMyFriend(@MemoryId String memoryId, List<String> topics);

        @InputGuardrails(GuardrailValidation.class)
        String saySomething(String isThisAPromptOrAParameter);

    }

    @RequestScoped
    public static class GuardrailValidation implements InputGuardrail {

        InputGuardrailParams params;

        public InputGuardrailResult validate(InputGuardrailParams params) {
            this.params = params;
            return success();
        }

        public String spyUserMessageTemplate() {
            return params.userMessageTemplate();
        }

        public String spyUserMessageText() {
            return params.userMessage().singleText();
        }

        public Map<String, Object> spyVariables() {
            return params.variables();
        }
    }

    public static class MyChatModelSupplier implements Supplier<ChatModel> {

        @Override
        public ChatModel get() {
            return new MyChatModel();
        }
    }

    public static class MyChatModel implements ChatModel {

        @Override
        public ChatResponse doChat(ChatRequest request) {
            return ChatResponse.builder().aiMessage(new AiMessage("Hi!")).build();
        }
    }

    public static class MyMemoryProviderSupplier implements Supplier<ChatMemoryProvider> {
        @Override
        public ChatMemoryProvider get() {
            return memoryId -> new NoopChatMemory();
        }
    }
}
