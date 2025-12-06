package io.quarkiverse.langchain4j.a2a.server.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.a2a.server.PublicAgentCard;
import io.a2a.server.agentexecution.AgentExecutor;
import io.a2a.spec.AgentCard;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.a2a.server.ExposeA2AAgent;
import io.quarkiverse.langchain4j.a2a.server.runtime.executor.QuarkusBaseAgentExecutor;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;
import io.quarkus.test.common.http.TestHTTPResource;

public class AgentSmokeTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest().withApplicationRoot(jar -> jar
            .addClasses(TestAgent.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"));

    @PublicAgentCard
    Instance<AgentCard> agentCardInstance;

    @Inject
    Instance<AgentExecutor> agentExecutorInstance;

    @TestHTTPResource
    URL url;

    @RegisterAiService
    @ExposeA2AAgent(name = "Weather Agent", description = "Helps with weather", skills = @ExposeA2AAgent.Skill(id = "weather_search", name = "Search weather", description = "Helps with weather in city, or states", tags = "weather", examples = "weather in LA, CA"))
    public interface TestAgent {

        @SystemMessage("""
                You are a specialized weather forecast assistant. Your primary
                function is to utilize the provided tools to retrieve and relay
                weather information in response to user queries. You must rely
                exclusively on these tools for data and refrain from inventing
                information. Ensure that all responses include the detailed output
                from the tools used and are formatted in Markdown.
                """)
        String chat(@UserMessage String question);
    }

    @Test
    public void testCard() {
        assertThat(agentCardInstance.isResolvable()).isTrue();
        AgentCard agentCard = agentCardInstance.get();
        assertThat(agentCard.name()).isEqualTo("Weather Agent");
        assertThat(agentCard.description()).isEqualTo("Helps with weather");
        assertThat(agentCard.version()).isEqualTo("1.0.0");
        assertThat(agentCard.url()).isEqualTo(url.toString().substring(0, url.toString().lastIndexOf('/')));
        assertThat(agentCard.skills()).singleElement().satisfies(skill -> {
            assertThat(skill.id()).isEqualTo("weather_search");
            assertThat(skill.name()).isEqualTo("Search weather");
            assertThat(skill.description()).isEqualTo("Helps with weather in city, or states");
            assertThat(skill.tags()).containsOnly("weather");
            assertThat(skill.examples()).containsOnly("weather in LA, CA");
        });
    }

    @Test
    public void testExecutor() {
        assertThat(agentExecutorInstance.isResolvable()).isTrue();
        AgentExecutor agentExecutor = agentExecutorInstance.get();
        assertThat(agentExecutor).isInstanceOf(QuarkusBaseAgentExecutor.class);
    }
}
