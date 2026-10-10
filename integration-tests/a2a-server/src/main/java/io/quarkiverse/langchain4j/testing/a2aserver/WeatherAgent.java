package io.quarkiverse.langchain4j.testing.a2aserver;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.a2a.server.ExposeA2AAgent;

@RegisterAiService(chatLanguageModelSupplier = EchoingChatModelSupplier.class)
@ExposeA2AAgent(skills = @ExposeA2AAgent.Skill(id = "weather_search", name = "Search weather", description = "Answers questions about the weather in a city or state", tags = {
        "weather", "forecast" }, examples = "weather in LA, CA"))
public interface WeatherAgent {

    @SystemMessage("You are a specialized weather forecast assistant.")
    String chat(@UserMessage String question);
}
