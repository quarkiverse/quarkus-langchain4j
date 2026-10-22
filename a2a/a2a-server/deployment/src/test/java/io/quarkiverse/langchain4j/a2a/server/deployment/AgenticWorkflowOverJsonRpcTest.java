package io.quarkiverse.langchain4j.a2a.server.deployment;

import static io.quarkiverse.langchain4j.a2a.server.deployment.JsonRpc.sendMessage;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.a2a.server.ExposeA2AAgent;
import io.quarkus.test.QuarkusUnitTest;

/**
 * A workflow exposed as an agent is served end to end: the client's text becomes the workflow's input, each step
 * runs on what the one before it produced, and what the last one produces is the artifact the client receives.
 * Each model marks what it was given, so the answer itself shows the steps ran, in order, on each other's output.
 */
public class AgenticWorkflowOverJsonRpcTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest().withApplicationRoot(jar -> jar
            .addClasses(JsonRpc.class, StoryWriter.class, StoryPolisher.class, StoryCreator.class, FakeChatModel.class,
                    PrefixingChatModel.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever")
            .overrideRuntimeConfigKey("quarkus.langchain4j.a2a.server.name", "Story Agent")
            .overrideRuntimeConfigKey("a2a.authorization.required", "false");

    public static class PrefixingChatModel extends FakeChatModel {

        private final String prefix;

        PrefixingChatModel(String prefix) {
            this.prefix = prefix;
        }

        @Override
        protected String answer(ChatRequest request) {
            return prefix + lastUserText(request);
        }
    }

    public interface StoryWriter {

        @UserMessage("{{topic}}")
        @Agent(description = "Write a story about a topic", outputKey = "story")
        String write(@V("topic") String topic);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return new PrefixingChatModel("A story about ");
        }
    }

    public interface StoryPolisher {

        @UserMessage("{{story}}")
        @Agent(description = "Polish a story", outputKey = "story")
        String polish(@V("story") String story);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return new PrefixingChatModel("Polished: ");
        }
    }

    @ExposeA2AAgent(skills = @ExposeA2AAgent.Skill(id = "write_story", name = "Write a story", description = "Writes and polishes a story about a topic", tags = "writing"))
    public interface StoryCreator {

        @SequenceAgent(outputKey = "story", subAgents = { StoryWriter.class, StoryPolisher.class })
        String create(@V("topic") String topic);
    }

    @Test
    public void testTheWorkflowAnswersOverJsonRpc() {
        sendMessage("dragons").post()
                .body("error", nullValue())
                .body("result.task.status.state", equalTo("TASK_STATE_COMPLETED"))
                .body("result.task.artifacts[0].parts[0].text", equalTo("Polished: A story about dragons"));
    }
}
