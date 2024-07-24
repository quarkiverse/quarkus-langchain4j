package io.quarkiverse.langchain4j.test;

import static org.assertj.core.api.Assertions.assertThat;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.output.TokenUsage;
import io.quarkiverse.langchain4j.data.AiStatsMessage;
import io.quarkiverse.langchain4j.runtime.aiservice.VariableHandler;
import io.quarkus.test.QuarkusUnitTest;

public class VariableHandlerTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class));

    @Test
    void test_substitution_on_arguments() {
        VariableHandler variableHandler = new VariableHandler();

        String arguments = "{\"arg0\": 2.0, \"arg1\": $(id1)}";

        variableHandler.addVariable("id1", "3.1");

        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .arguments(arguments)
                .build();

        ToolExecutionRequest modifiedRequest = variableHandler.substituteVariables(request);

        assertThat(modifiedRequest.arguments()).isEqualTo("{\"arg0\": 2.0, \"arg1\": 3.1}");
    }

    @Test
    void test_substitution_on_ai_stats_message() {
        VariableHandler variableHandler = new VariableHandler();

        String text = "The expected result is $(result1).";

        variableHandler.addVariable("result1", "3.1");

        AiMessage aiMessage = AiMessage.aiMessage(text);
        AiStatsMessage aiStatsMessage = AiStatsMessage.from(aiMessage, new TokenUsage());
        AiMessage modifiedMessage = variableHandler.substituteVariables(aiStatsMessage);

        assertThat(modifiedMessage.text()).isEqualTo("The expected result is 3.1.");
    }

    @Test
    void test_substitution_on_ai_message() {
        VariableHandler variableHandler = new VariableHandler();

        String text = "The expected result is $(result1).";

        variableHandler.addVariable("result1", "3.1");

        AiMessage aiMessage = AiMessage.aiMessage(text);
        AiMessage modifiedMessage = variableHandler.substituteVariables(aiMessage);

        assertThat(modifiedMessage.text()).isEqualTo(text);
    }
}
