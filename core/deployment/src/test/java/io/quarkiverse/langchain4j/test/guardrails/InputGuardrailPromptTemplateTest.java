package io.quarkiverse.langchain4j.test.guardrails;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.guardrails.InputGuardrail;
import io.quarkiverse.langchain4j.guardrails.InputGuardrailResult;
import io.quarkiverse.langchain4j.guardrails.InputGuardrails;
import io.quarkiverse.langchain4j.runtime.aiservice.NoopChatMemory;
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusUnitTest;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

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
    
    @BeforeEach
    void setup() {
        Arc.container().requestContext().activate();
    }
    
    @AfterEach
    void tearDown() {
        Arc.container().requestContext().deactivate();
    }
    
    @Test
    void shouldWorkNoParameters() {
        aiService.getJoke();
        assertThat(guardrailValidation.spyPromptTemplate()).isEqualTo("Tell me a joke");
        assertThat(guardrailValidation.spyVariables()).isEmpty();
    }
    
    @Test
    void shouldWorkWithMemoryId() {
        aiService.getAnotherJoke("memory-id-001");
        assertThat(guardrailValidation.spyPromptTemplate()).isEqualTo("Tell me another joke");
        assertThat(guardrailValidation.spyVariables()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "memoryId", "memory-id-001",
                "it", "memory-id-001" // is this correct?
        ));
    }
    
    @Test
    void shouldWorkWithNoMemoryIdAndOneParameter() {
        aiService.sayHiToMyFriendNoMemory("Rambo");
        assertThat(guardrailValidation.spyPromptTemplate()).isEqualTo("Say hi to my friend {friend}!");
        assertThat(guardrailValidation.spyVariables())
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "friend", "Rambo",
                        "it", "Rambo"
                ));
    }
    
    @Test
    void shouldWorkWithMemoryIdAndOneParameter() {
        aiService.sayHiToMyFriend("1", "Chuck Norris");
        assertThat(guardrailValidation.spyPromptTemplate()).isEqualTo("Say hi to my friend {friend}!");
        assertThat(guardrailValidation.spyVariables())
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "friend", "Chuck Norris",
                        "mem", "1"
                ));
    }
    
    @Test
    void shouldWorkWithNoMemoryIdAndThreeParameters() {
        aiService.sayHiToMyFriends("Chuck Norris", "Jean-Claude Van Damme", "Silvester Stallone");
        assertThat(guardrailValidation.spyPromptTemplate()).isEqualTo("Tell me something about {topic1}, {topic2}, {topic3}!");
        assertThat(guardrailValidation.spyVariables())
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "topic1", "Chuck Norris",
                        "topic2", "Jean-Claude Van Damme",
                        "topic3", "Silvester Stallone"
                ));
    }
    
    @Test
    void shouldWorkWithNoUserMessage() {
        // This is a special case where the UserMessage annotation is not present
        // The prompt template doesn't exist in this case
        // But the current implementation use the parameter name as prompt template
        // Not sure if this is the correct behavior, should we always have @UserMessage?
        // I need some thoughts on this
        aiService.saySomething("Is this a parameter or a prompt?");
        assertThat(guardrailValidation.spyPromptTemplate()).isNull();
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
        
        public String spyPromptTemplate() {
            return params.promptTemplate();
        }
        
        public Map<String, Object> spyVariables() {
            return params.variables();
        }
    }
    
    
    public static class MyChatModelSupplier implements Supplier<ChatLanguageModel> {
        
        @Override
        public ChatLanguageModel get() {
            return new MyChatModel();
        }
    }
    
    public static class MyChatModel implements ChatLanguageModel {
        
        @Override
        public Response<AiMessage> generate(List<ChatMessage> messages) {
            return new Response<>(new AiMessage("Hi!"));
        }
    }
    
    public static class MyMemoryProviderSupplier implements Supplier<ChatMemoryProvider> {
        @Override
        public ChatMemoryProvider get() {
            return memoryId -> new NoopChatMemory();
        }
    }
}
