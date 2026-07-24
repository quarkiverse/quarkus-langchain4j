package io.quarkiverse.langchain4j.skills.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
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
import dev.langchain4j.service.tool.ToolProvider;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.runtime.skills.SkillsConfigurator;
import io.quarkiverse.langchain4j.skills.Skills;
import io.quarkiverse.langchain4j.skills.runtime.DefaultSkillsConfigurator;
import io.quarkus.test.QuarkusUnitTest;

public class SkillsCustomConfiguratorTest {

    private static final Path SKILLS_DIR = Path.of("src/test/resources/skills").toAbsolutePath();

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(
                            CustomConfigurator.class,
                            SkillsService.class,
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

    @ApplicationScoped
    public static class CustomConfigurator implements SkillsConfigurator {

        @Inject
        DefaultSkillsConfigurator delegate;

        @Override
        public ToolProvider createToolProvider(List<String> skillNames) {
            return delegate.createToolProvider(skillNames);
        }

        @Override
        public String formatAvailableSkills(List<String> skillNames) {
            return delegate.formatAvailableSkills(skillNames);
        }

        @Override
        public String buildSkillsSystemMessage(List<String> skillNames) {
            return "CUSTOM_MESSAGE: " + formatAvailableSkills(skillNames);
        }
    }

    @RegisterAiService(chatLanguageModelSupplier = CapturingChatModelSupplier.class, chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    @Skills
    public interface SkillsService {
        String chat(@UserMessage String msg);
    }

    @Inject
    SkillsService skillsService;

    @Test
    @ActivateRequestContext
    void customConfiguratorOverridesDefault() {
        String response = skillsService.chat("hello");
        assertThat(response).contains("CUSTOM_MESSAGE:");
        assertThat(response).contains("foobar-skill");
        assertThat(response).doesNotContain("You have access to the following skills");
    }
}
