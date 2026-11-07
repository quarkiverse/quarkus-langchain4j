package io.quarkiverse.langchain4j.agentic.deployment;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

@SuppressWarnings("CdiInjectionPointsInspection")
public class PrimitiveIntAgentTest extends OpenAiBaseTest {

    public interface PrimitiveIntAgent {

        @UserMessage("""
                You are a calculator.
                Double the given number and return only the result.
                The number is {{number}}.
                """)
        @Agent(description = "Double a number", outputKey = "result")
        int doubleNumber(@V("number") int number);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return new Agents.FixedResponseChatModel("42");
        }
    }

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class)
                            .addClasses(PrimitiveIntAgent.class, Agents.FixedResponseChatModel.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    @Inject
    PrimitiveIntAgent primitiveIntAgent;

    @Test
    public void testPrimitiveIntAgent() {
        Assertions.assertNotNull(primitiveIntAgent);
        int result = primitiveIntAgent.doubleNumber(21);
        Assertions.assertEquals(42, result);
    }
}
