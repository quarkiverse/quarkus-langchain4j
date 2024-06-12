package io.quarkiverse.langchain4j.ollama.tools;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.junit.QuarkusTest;

@Disabled("Integration tests that need an ollama server running")
@DisplayName("LLM Tools test - " + Llama3ToolsTest.MODEL_NAME)
@QuarkusTest
public class Llama3ToolsTest {

    public static final String MODEL_NAME = "llama3";

    @RegisterAiService(tools = Tools.Calculator.class, modelName = MODEL_NAME)
    public interface MathAssistantLlama3 {
        String chat(String userMessage);
    }

    @Inject
    MathAssistantLlama3 mathAssistantLlama3;

    @Test
    @ActivateRequestContext
    void square_of_sum_of_number_of_letters() {
        String msg = "What is the square root with maximal precision of the sum " +
                "of the numbers of letters in the words hello and llama";
        String response = mathAssistantLlama3.chat(msg);
        assertThat(response).contains("3.1622776601683795");
    }

    @RegisterAiService(tools = Tools.ExpenseService.class, modelName = MODEL_NAME)
    public interface Assistant {
        @SystemMessage("""
                You are a property manager assistant, answering to co-owners requests.
                Format the date as YYYY-MM-DD and the time as HH:MM
                Today is {{current_date}} use this date as date time reference
                The co-owners is living in the following condominium: {condominium}
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
    void get_expenses() {
        String response = assistant.answer("Rives de Marne",
                "What are the expenses for this year ?");
        assertThat(response).contains("Expense hp12");
    }

    @Test
    @ActivateRequestContext
    void should_not_calls_tool() {
        String response = assistant.answer("Rives de Marne", "What time is it ?");
        assertThat(response).doesNotContain("Expense hp12");
    }

    @RegisterAiService(tools = Tools.EmailService.class, modelName = MODEL_NAME)
    public interface PoemService {
        @SystemMessage("You are a professional poet")
        @UserMessage("""
                Write a poem about {topic}. The poem should be {lines} lines long. Then send this poem by email.
                """)
        String writeAPoem(String topic, int lines);
    }

    @Inject
    PoemService poemService;

    @Test
    @ActivateRequestContext
    void send_a_poem() {
        String response = poemService.writeAPoem("Condominium Rives de marne", 4);
        assertThat(response).contains("Success");
    }
}
