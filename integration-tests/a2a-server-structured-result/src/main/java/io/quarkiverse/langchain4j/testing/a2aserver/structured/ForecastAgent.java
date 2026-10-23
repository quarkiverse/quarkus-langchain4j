package io.quarkiverse.langchain4j.testing.a2aserver.structured;

import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.a2a.server.ExposeA2AAgent;

@RegisterAiService(chatLanguageModelSupplier = FixedForecastChatModelSupplier.class)
@ExposeA2AAgent(skills = @ExposeA2AAgent.Skill(id = "forecast", name = "Forecast", description = "Answers with a structured forecast", tags = {
        "weather", "forecast" }, examples = "forecast for LA"))
public interface ForecastAgent {

    Forecast chat(@UserMessage String question);
}
