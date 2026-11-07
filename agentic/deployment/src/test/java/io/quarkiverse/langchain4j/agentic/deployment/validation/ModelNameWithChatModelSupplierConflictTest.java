package io.quarkiverse.langchain4j.agentic.deployment.validation;

import static org.junit.jupiter.api.Assertions.fail;

import org.assertj.core.api.Assertions;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.IllegalConfigurationException;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkus.test.QuarkusUnitTest;

public class ModelNameWithChatModelSupplierConflictTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class).addClasses(ConflictingAgent.class))
            .assertException(
                    throwable -> Assertions.assertThat(throwable).isInstanceOf(IllegalConfigurationException.class)
                            .hasMessageContaining("@ChatModelSupplier")
                            .hasMessageContaining("@ModelName"));

    @Test
    public void test() {
        fail("should never be called");
    }

    public interface ConflictingAgent {

        @UserMessage("""
                Analyze the following request: {{request}}.
                """)
        @Agent("An agent with conflicting model configuration")
        @ModelName("mymodel")
        String analyze(@V("request") String request);

        @ChatModelSupplier
        static ChatModel chatModel() {
            return null;
        }
    }
}
