package io.quarkiverse.langchain4j.skills.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.function.Supplier;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.skills.Skills;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Verifies that invoking two AI service methods with different @Skills
 * in a single conversation (shared chat memory) does not duplicate or
 * corrupt the skills system message.
 */
public class SkillsChatMemoryTest {

    private static final Path SKILLS_DIR = Path.of("src/test/resources/skills").toAbsolutePath();

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(
                            MethodLevelSkillsWithMemoryService.class,
                            SkillsWithSystemMessageService.class,
                            CapturingChatModelSupplier.class,
                            CapturingChatModel.class,
                            TestChatMemoryProviderSupplier.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.skills.directories", SKILLS_DIR.toString());

    public static class CapturingChatModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            StringBuilder sb = new StringBuilder();
            for (ChatMessage msg : chatRequest.messages()) {
                if (msg instanceof SystemMessage sysMsg) {
                    sb.append("SYSTEM:").append(sysMsg.text());
                }
            }
            if (chatRequest.toolSpecifications() != null && !chatRequest.toolSpecifications().isEmpty()) {
                sb.append("|TOOLS:");
                chatRequest.toolSpecifications().forEach(t -> sb.append(t.name()).append(","));
            }
            return ChatResponse.builder()
                    .aiMessage(new AiMessage(sb.toString()))
                    .build();
        }
    }

    @Singleton
    public static class CapturingChatModelSupplier implements Supplier<ChatModel> {
        @Override
        public ChatModel get() {
            return new CapturingChatModel();
        }
    }

    public static class TestChatMemoryProviderSupplier implements Supplier<ChatMemoryProvider> {
        @Override
        public ChatMemoryProvider get() {
            return new ChatMemoryProvider() {
                @Override
                public ChatMemory get(Object memoryId) {
                    return MessageWindowChatMemory.builder()
                            .id(memoryId)
                            .maxMessages(20)
                            .build();
                }
            };
        }
    }

    @RegisterAiService(chatLanguageModelSupplier = CapturingChatModelSupplier.class, chatMemoryProviderSupplier = TestChatMemoryProviderSupplier.class)
    public interface MethodLevelSkillsWithMemoryService {
        @Skills("foobar-skill")
        String chatWithFoobar(@MemoryId String memoryId, @UserMessage String msg);

        @Skills("bazqux-skill")
        String chatWithBazqux(@MemoryId String memoryId, @UserMessage String msg);
    }

    @RegisterAiService(chatLanguageModelSupplier = CapturingChatModelSupplier.class, chatMemoryProviderSupplier = TestChatMemoryProviderSupplier.class)
    public interface SkillsWithSystemMessageService {
        @Skills("foobar-skill")
        @dev.langchain4j.service.SystemMessage("You are a travel assistant.")
        String chatWithFoobar(@MemoryId String memoryId, @UserMessage String msg);

        @Skills("bazqux-skill")
        @dev.langchain4j.service.SystemMessage("You are a cooking assistant.")
        String chatWithBazqux(@MemoryId String memoryId, @UserMessage String msg);
    }

    @Inject
    MethodLevelSkillsWithMemoryService service;

    @Inject
    SkillsWithSystemMessageService skillsWithSystemMessageService;

    @Test
    @ActivateRequestContext
    void differentMethodSkillsInSameConversationDoNotDuplicate() {
        String memoryId = "test-conversation";

        // First call — foobar-skill method
        String response1 = service.chatWithFoobar(memoryId, "hello foobar");
        assertThat(response1).contains("foobar-skill");
        assertThat(response1).doesNotContain("bazqux-skill");

        // Second call — bazqux-skill method, same conversation
        String response2 = service.chatWithBazqux(memoryId, "hello bazqux");
        assertThat(response2).contains("bazqux-skill");
        assertThat(response2).doesNotContain("foobar-skill");

        // Verify no duplication: the skills system message should appear exactly once
        long skillsMentions = countOccurrences(response2, "You have access to the following skills");
        assertThat(skillsMentions).isEqualTo(1);
    }

    @Test
    @ActivateRequestContext
    void callingTheSameMethodTwiceDoesNotDuplicateSkillsMessage() {
        String memoryId = "test-same-method";

        String response1 = service.chatWithFoobar(memoryId, "first call");
        assertThat(response1).contains("foobar-skill");

        String response2 = service.chatWithFoobar(memoryId, "second call");
        assertThat(response2).contains("foobar-skill");

        long skillsMentions = countOccurrences(response2, "You have access to the following skills");
        assertThat(skillsMentions).isEqualTo(1);
    }

    @Test
    @ActivateRequestContext
    void skillsAndSystemMessageMergedCorrectly() {
        String memoryId = "test-skills-sysmsg";

        // First call — foobar-skill + "You are a travel assistant."
        String response1 = skillsWithSystemMessageService.chatWithFoobar(memoryId, "plan a trip");
        assertThat(response1).contains("You are a travel assistant.");
        assertThat(response1).contains("foobar-skill");
        assertThat(response1).doesNotContain("bazqux-skill");
        assertThat(response1).doesNotContain("cooking assistant");

        // Second call — bazqux-skill + "You are a cooking assistant.", same conversation
        String response2 = skillsWithSystemMessageService.chatWithBazqux(memoryId, "make pasta");
        assertThat(response2).contains("You are a cooking assistant.");
        assertThat(response2).contains("bazqux-skill");
        assertThat(response2).doesNotContain("foobar-skill");
        assertThat(response2).doesNotContain("travel assistant");

        // Verify no duplication of skills preamble
        assertThat(countOccurrences(response2, "You have access to the following skills")).isEqualTo(1);
    }

    @Test
    @ActivateRequestContext
    void skillsAndSystemMessageNoDuplicationAcrossCalls() {
        String memoryId = "test-sysmsg-no-dup";

        String response1 = skillsWithSystemMessageService.chatWithFoobar(memoryId, "first call");
        assertThat(response1).contains("You are a travel assistant.");
        assertThat(response1).contains("foobar-skill");

        String response2 = skillsWithSystemMessageService.chatWithFoobar(memoryId, "second call");
        assertThat(response2).contains("You are a travel assistant.");
        assertThat(response2).contains("foobar-skill");
        assertThat(countOccurrences(response2, "You have access to the following skills")).isEqualTo(1);
        assertThat(countOccurrences(response2, "You are a travel assistant.")).isEqualTo(1);
    }

    private static long countOccurrences(String text, String substring) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(substring, idx)) != -1) {
            count++;
            idx += substring.length();
        }
        return count;
    }
}
