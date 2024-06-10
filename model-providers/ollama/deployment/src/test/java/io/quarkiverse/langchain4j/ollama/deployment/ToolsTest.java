package io.quarkiverse.langchain4j.ollama.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.logging.Log;
import io.quarkus.test.QuarkusUnitTest;

@Disabled("Integration tests that need an ollama server running")
public class ToolsTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.ollama.timeout", "60s")
            .overrideRuntimeConfigKey("quarkus.langchain4j.ollama.log-requests", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.ollama.log-responses", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.ollama.chat-model.temperature", "0")
            .overrideRuntimeConfigKey("quarkus.langchain4j.ollama.experimental-tools", "true");

    @Singleton
    @SuppressWarnings("unused")
    static class ExpenseService {
        @Tool("useful for when you need to lookup condominium expenses for given dates.")
        public String getExpenses(String condominium, String fromDate, String toDate) {
            String result = String.format("""
                    The Expenses for %s from %s to %s are:
                        - Expense hp12: 2800e
                        - Expense 2: 15000e
                    """, condominium, fromDate, toDate);
            Log.infof(result);
            return result;
        }
    }

    @RegisterAiService(tools = ExpenseService.class)
    public interface Assistant {
        @SystemMessage("""
                You are a property manager assistant, answering to co-owners requests.
                Format the date as YYYY-MM-DD and the time as HH:MM
                Today is {{current_date}} use this date as date time reference
                The co-owners is leaving in the folloaing conominium: {condominium}
                """)
        @UserMessage("""
                {{request}}
                """)
        String answer(String condominium, String request);
    }

    @Inject
    Assistant assistant;

    @Test
    @ActivateRequestContext
    void test_simple_tool() {
        String response = assistant.answer("Rives de Marne",
                "What are the expenses for this year ?");
        assertThat(response).contains("Expense hp12");
    }

    @Test
    @ActivateRequestContext
    void test_should_not_calls_tool() {
        String response = assistant.answer("Rives de Marne", "What time is it ?");
        assertThat(response).doesNotContain("Expense hp12");
    }

    @Singleton
    @SuppressWarnings("unused")
    public static class Calculator {
        @Tool("Calculates the length of a string")
        String stringLengthStr(String s) {
            return String.format("The length of the word %s is %d", s, s.length());
        }

        @Tool("Calculates the sum of two numbers")
        String addStr(int a, int b) {
            return String.format("The sum of %s and %s is %d", a, b, a + b);
        }

        @Tool("Calculates the square root of a number")
        String sqrtStr(int x) {
            return String.format("The square root of %s is %f", x, Math.sqrt(x));
        }
    }

    @RegisterAiService(tools = Calculator.class)
    public interface MathAssistant {
        String chat(String userMessage);
    }

    @Inject
    MathAssistant mathAssistant;

    @Test
    @ActivateRequestContext
    void test_multiple_tools() {
        String msg = "What is the square root of the sum of the numbers of letters in the words " +
                "\"hello\" and \"world\"";
        String response = mathAssistant.chat(msg);
        assertThat(response).contains("approximately 3.16");

    }

}
