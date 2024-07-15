package io.quarkiverse.langchain4j.ollama.tools;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.acme.example.openai.chat.ollama.PropertyManagerAssistant;
import org.acme.example.openai.chat.ollama.Tools;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkus.test.junit.QuarkusTest;

@Disabled("Integration tests that need an ollama server running")
@DisplayName("LLM Parallel Tools test")
@QuarkusTest
public class ToolsParallelIT {

    @RegisterAiService(tools = Tools.Calculator.class)
    public interface MathAssistant {
        String chat(String userMessage);
    }

    @Inject
    MathAssistant mathAssistant;

    @Test
    @ActivateRequestContext
    void square_of_sum_of_number_of_letters() {
        String msg = "What is the square root of the sum " +
                "of the numbers of characters in the words hello and world";
        String response = mathAssistant.chat(msg);
        assertThat(response).contains("3.16");
    }

    @Inject
    PropertyManagerAssistant assistant;

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

    @RegisterAiService(tools = Tools.EmailService.class)
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
        assertThat(response).contains("mail");
    }

    @RegisterAiService
    public interface PoemService2 {
        @SystemMessage("You are a professional poet")
        @UserMessage("""
                Write a poem about {topic}. The poem should be {lines} lines long.
                """)
        String writeAPoem(String topic, int lines);
    }

    @Inject
    PoemService2 poemService2;

    @Test
    @ActivateRequestContext
    void write_a_poem_without_tools() {
        String response = poemService2.writeAPoem("Condominium Rives de marne", 4);
        assertThat(response.split("\n").length).isGreaterThanOrEqualTo(4);
    }
}
