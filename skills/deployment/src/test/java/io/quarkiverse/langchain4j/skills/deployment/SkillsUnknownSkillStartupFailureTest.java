package io.quarkiverse.langchain4j.skills.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.nio.file.Path;
import java.util.function.Supplier;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.skills.Skills;
import io.quarkus.test.QuarkusUnitTest;

public class SkillsUnknownSkillStartupFailureTest {

    private static final Path SKILLS_DIR = Path.of("src/test/resources/skills").toAbsolutePath();

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(ServiceWithUnknownSkill.class, NoOpChatModelSupplier.class, NoOpChatModel.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.skills.directories", SKILLS_DIR.toString())
            .assertException(e -> {
                assertThat(e).hasStackTraceContaining("nonexistent");
            });

    public static class NoOpChatModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            return ChatResponse.builder().aiMessage(new AiMessage("")).build();
        }
    }

    @Singleton
    public static class NoOpChatModelSupplier implements Supplier<ChatModel> {
        @Override
        public ChatModel get() {
            return new NoOpChatModel();
        }
    }

    @RegisterAiService(chatLanguageModelSupplier = NoOpChatModelSupplier.class, chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
    @Skills("nonexistent")
    public interface ServiceWithUnknownSkill {
        String chat(@UserMessage String msg);
    }

    @Inject
    ServiceWithUnknownSkill serviceWithUnknownSkill;

    @Test
    void shouldNotReachHere() {
        fail("Application should have failed to start due to unknown skill name");
    }
}
