package io.quarkiverse.langchain4j.agentic.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.skills.Skills;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

public class AgentSkillsTest extends OpenAiBaseTest {

    private static final String FOOBAR_SKILL = """
            ---
            name: foobar-skill
            description: A foobar skill for testing purposes
            ---

            This is the foobar skill content.
            """;

    private static final String BAZQUX_SKILL = """
            ---
            name: bazqux-skill
            description: A bazqux skill for testing purposes
            ---

            This is the bazqux skill content.
            """;

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(
                            CapturingChatModel.class,
                            AllSkillsAgent.class,
                            FilteredSkillsAgent.class,
                            SkillsWithSystemMessageAgent.class)
                    .addAsResource(new StringAsset(FOOBAR_SKILL), "skills/foobar-skill/SKILL.md")
                    .addAsResource(new StringAsset(BAZQUX_SKILL), "skills/bazqux-skill/SKILL.md"))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"))
            .overrideRuntimeConfigKey("quarkus.langchain4j.skills.directories", "classpath:skills");

    public static class CapturingChatModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest chatRequest) {
            StringBuilder sb = new StringBuilder();
            for (ChatMessage msg : chatRequest.messages()) {
                if (msg instanceof dev.langchain4j.data.message.SystemMessage sysMsg) {
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

    public interface AllSkillsAgent {

        @UserMessage("{{request}}")
        @Agent(description = "Agent with all skills", outputKey = "answer")
        @Skills
        String assist(@V("request") String request);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return new CapturingChatModel();
        }
    }

    public interface FilteredSkillsAgent {

        @UserMessage("{{request}}")
        @Agent(description = "Agent with filtered skills", outputKey = "answer")
        @Skills("foobar-skill")
        String assist(@V("request") String request);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return new CapturingChatModel();
        }
    }

    public interface SkillsWithSystemMessageAgent {

        @UserMessage("{{request}}")
        @SystemMessage("You are a helpful assistant.")
        @Agent(description = "Agent with skills and system message", outputKey = "answer")
        @Skills
        String assist(@V("request") String request);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return new CapturingChatModel();
        }
    }

    @Inject
    AllSkillsAgent allSkillsAgent;

    @Inject
    FilteredSkillsAgent filteredSkillsAgent;

    @Inject
    SkillsWithSystemMessageAgent skillsWithSystemMessageAgent;

    @Test
    void allSkillsWiresToolProvider() {
        String result = allSkillsAgent.assist("hello");
        assertThat(result).contains("TOOLS:");
        assertThat(result).contains("activate_skill");
    }

    @Test
    void filteredSkillsWiresToolProviderWithoutDuplicates() {
        String result = filteredSkillsAgent.assist("hello");
        assertThat(result).contains("TOOLS:");
        assertThat(result).contains("activate_skill");
        int firstIdx = result.indexOf("activate_skill");
        int toolsSection = result.indexOf("|TOOLS:");
        String toolsList = result.substring(toolsSection);
        int count = 0;
        int idx = 0;
        while ((idx = toolsList.indexOf("activate_skill", idx)) != -1) {
            count++;
            idx += "activate_skill".length();
        }
        assertThat(count).as("activate_skill should appear exactly once in tools (no duplicates)").isEqualTo(1);
    }

    @Test
    void skillsWithExistingSystemMessagePreservesIt() {
        String result = skillsWithSystemMessageAgent.assist("hello");
        assertThat(result).contains("SYSTEM:");
        assertThat(result).contains("You are a helpful assistant.");
        assertThat(result).contains("activate_skill");
    }

}
