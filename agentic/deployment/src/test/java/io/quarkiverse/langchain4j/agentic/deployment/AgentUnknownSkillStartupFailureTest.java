package io.quarkiverse.langchain4j.agentic.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.skills.Skills;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

public class AgentUnknownSkillStartupFailureTest extends OpenAiBaseTest {

    private static final String FOOBAR_SKILL = """
            ---
            name: foobar-skill
            description: A foobar skill for testing purposes
            ---

            This is the foobar skill content.
            """;

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(NoOpChatModel.class, AgentWithUnknownSkill.class)
                    .addAsResource(new StringAsset(FOOBAR_SKILL), "skills/foobar-skill/SKILL.md"))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"))
            .overrideRuntimeConfigKey("quarkus.langchain4j.skills.directories", "classpath:skills")
            .assertException(e -> {
                assertThat(e).hasStackTraceContaining("nonexistent");
            });

    public static class NoOpChatModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            return ChatResponse.builder().aiMessage(new AiMessage("")).build();
        }
    }

    public interface AgentWithUnknownSkill {

        @UserMessage("{{request}}")
        @Agent(description = "Agent with unknown skill", outputKey = "answer")
        @Skills("nonexistent")
        String assist(@V("request") String request);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return new NoOpChatModel();
        }
    }

    @Inject
    AgentWithUnknownSkill agentWithUnknownSkill;

    @Test
    void shouldNotReachHere() {
        fail("Application should have failed to start due to unknown skill name");
    }
}
