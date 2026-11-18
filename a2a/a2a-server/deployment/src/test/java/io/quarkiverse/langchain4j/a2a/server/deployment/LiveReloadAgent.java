package io.quarkiverse.langchain4j.a2a.server.deployment;

import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.a2a.server.ExposeA2AAgent;

/**
 * The agent {@link AgentCardAfterLiveReloadTest} exposes, kept in a file of its own so that dev mode can recompile it.
 */
@RegisterAiService(chatLanguageModelSupplier = SunnyChatModel.class)
@ExposeA2AAgent(skills = @ExposeA2AAgent.Skill(id = "weather_search", name = "Search weather", description = "Helps with weather", tags = "weather"))
public interface LiveReloadAgent {

    String chat(@UserMessage String question);
}
