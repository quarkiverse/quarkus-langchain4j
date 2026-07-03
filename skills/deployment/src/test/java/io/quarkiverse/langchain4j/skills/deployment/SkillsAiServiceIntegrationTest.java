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
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.skills.Skills;
import io.quarkus.test.QuarkusUnitTest;

public class SkillsAiServiceIntegrationTest {

    private static final Path SKILLS_DIR = Path.of("src/test/resources/skills").toAbsolutePath();

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(
                            AllSkillsService.class,
                            FilteredSkillsService.class,
                            SkillsWithSystemMessageService.class,
                            SkillsWithoutSystemMessageService.class,
                            CapturingChatModelSupplier.class,
                            CapturingChatModel.class))
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

    @RegisterAiService(chatLanguageModelSupplier = CapturingChatModelSupplier.class, chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    @Skills
    public interface AllSkillsService {
        String chat(@UserMessage String msg);
    }

    @RegisterAiService(chatLanguageModelSupplier = CapturingChatModelSupplier.class, chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    @Skills("foobar-skill")
    public interface FilteredSkillsService {
        String chat(@UserMessage String msg);
    }

    @RegisterAiService(chatLanguageModelSupplier = CapturingChatModelSupplier.class, chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    @Skills
    public interface SkillsWithSystemMessageService {
        @dev.langchain4j.service.SystemMessage("You are a helpful travel assistant.")
        String chat(@UserMessage String msg);
    }

    @RegisterAiService(chatLanguageModelSupplier = CapturingChatModelSupplier.class, chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    @Skills
    public interface SkillsWithoutSystemMessageService {
        String chat(@UserMessage String msg);
    }

    @Inject
    AllSkillsService allSkillsService;

    @Inject
    FilteredSkillsService filteredSkillsService;

    @Inject
    SkillsWithSystemMessageService skillsWithSystemMessageService;

    @Inject
    SkillsWithoutSystemMessageService skillsWithoutSystemMessageService;

    @Test
    @ActivateRequestContext
    void allSkillsWiresToolProviderAndSystemMessage() {
        String response = allSkillsService.chat("hello");
        assertThat(response).contains("SYSTEM:");
        assertThat(response).contains("You have access to the following skills");
        assertThat(response).contains("foobar-skill");
        assertThat(response).contains("bazqux-skill");
        assertThat(response).contains("activate_skill");
        assertThat(response).contains("TOOLS:");
        assertThat(response).contains("activate_skill,");
    }

    @Test
    @ActivateRequestContext
    void filteredSkillsWiresOnlyNamedSkills() {
        String response = filteredSkillsService.chat("hello");
        assertThat(response).contains("SYSTEM:");
        assertThat(response).contains("foobar-skill");
        assertThat(response).doesNotContain("bazqux-skill");
        assertThat(response).contains("TOOLS:");
        assertThat(response).contains("activate_skill,");
    }

    @Test
    @ActivateRequestContext
    void skillsSystemMessageAppendsToExistingSystemMessage() {
        String response = skillsWithSystemMessageService.chat("hello");
        assertThat(response).contains("SYSTEM:");
        assertThat(response).contains("You are a helpful travel assistant.");
        assertThat(response).contains("You have access to the following skills");
        assertThat(response).contains("foobar-skill");
        assertThat(response).contains("bazqux-skill");
    }

    @Test
    @ActivateRequestContext
    void skillsSystemMessageWorksWithoutExistingSystemMessage() {
        String response = skillsWithoutSystemMessageService.chat("hello");
        assertThat(response).contains("SYSTEM:");
        assertThat(response).contains("You have access to the following skills");
        assertThat(response).contains("foobar-skill");
    }

}
