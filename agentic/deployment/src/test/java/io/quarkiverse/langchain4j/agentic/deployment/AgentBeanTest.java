package io.quarkiverse.langchain4j.agentic.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.QuarkusUnitTest;

public class AgentBeanTest {
    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class).addClasses(
                            Finisher.class, PoliteGreeter.class, GreetingSequence.class, TitleAgent.class,
                            TitleChatModel.class, Agents.FixedResponseChatModel.class));

    public static class TitleChatModel implements Supplier<ChatModel> {
        public TitleChatModel() {
        }

        @Override
        public ChatModel get() {
            return new Agents.FixedResponseChatModel("Mr.");
        }
    }

    @RegisterAiService(chatLanguageModelSupplier = TitleChatModel.class)
    public interface TitleAgent {
        @UserMessage("Guess at appropriate title for this name: {{name}}")
        @Agent(outputKey = "title")
        String title(@V("name") String name);
    }

    @ApplicationScoped
    public static class Finisher {

        @Agent(outputKey = "result")
        public String finish(AgenticScope scope) {
            return scope.readState("greeting", "no greeting");
        }
    }

    // ── Non-AI class with a static agent method ──

    @RequestScoped
    public static class PoliteGreeter {
        String greeting = "Hello";

        public void setGreeting(String greeting) {
            this.greeting = greeting;
        }

        @Agent(outputKey = "greeting")
        public String greet(@V("name") String name, @V("title") String title) {
            return greeting + " " + title + " " + name;
        }
    }

    public interface GreetingSequence {

        @SequenceAgent(outputKey = "result", subAgents = { TitleAgent.class, PoliteGreeter.class, Finisher.class })
        String run(@V("name") String name, @V("polite") boolean polite);
    }

    public interface SuperAgent {
        @SequenceAgent(outputKey = "result", subAgents = { GreetingSequence.class })
        String run(@V("name") String name, @V("polite") boolean polite);
    }

    @Inject
    GreetingSequence greetingSequence;

    @Inject
    SuperAgent superAgent;

    @Inject
    PoliteGreeter politeGreeter;

    @ActivateRequestContext
    @Test
    public void testBeans() {
        politeGreeter.setGreeting("Hi");
        String result = greetingSequence.run("Bob", true);
        assertThat(result).isEqualTo("Hi Mr. Bob");

        result = superAgent.run("Bob", true);
        assertThat(result).isEqualTo("Hi Mr. Bob");
    }
}
