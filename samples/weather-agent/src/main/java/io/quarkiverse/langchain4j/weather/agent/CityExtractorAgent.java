package io.quarkiverse.langchain4j.weather.agent;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@RegisterAiService(chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
public interface CityExtractorAgent {

    @UserMessage("""
        You are given one question and you have to extract city name from it
        Only reply the city name if it exists or reply 'unknown_city' if there is no city name in question

        Here is the question: {question}
        """)
    @Tool("Extracts the city from a question")
    String extractCity(String question);

}
