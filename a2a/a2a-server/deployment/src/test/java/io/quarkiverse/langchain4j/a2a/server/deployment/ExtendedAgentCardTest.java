package io.quarkiverse.langchain4j.a2a.server.deployment;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import java.util.List;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import org.a2aproject.sdk.server.ExtendedAgentCard;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentSkill;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.a2a.server.ExposeA2AAgent;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

/**
 * An extended Agent Card is a two-part contract: the annotation advertises that one exists, and the application
 * produces it as a bean qualified {@link ExtendedAgentCard}. The extension only carries the advertisement, so what
 * this covers is that the two halves meet — a client asking for the extended card gets the application's, not the
 * public one.
 */
public class ExtendedAgentCardTest {

    private static final String EXTENDED_NAME = "Weather Agent (Extended)";

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest().withApplicationRoot(jar -> jar
            .addClasses(TestAgent.class, ExtendedCardProducer.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"))
            .overrideRuntimeConfigKey("quarkus.langchain4j.a2a.server.name", "Weather Agent");

    @RegisterAiService
    @ExposeA2AAgent(extendedAgentCard = true, skills = @ExposeA2AAgent.Skill(id = "weather_search", name = "Search weather", description = "Helps with weather", tags = "weather"))
    public interface TestAgent {

        String chat(@UserMessage String question);
    }

    @Singleton
    public static class ExtendedCardProducer {

        @Produces
        @ExtendedAgentCard
        public AgentCard extendedCard() {
            return AgentCard.builder()
                    .name(EXTENDED_NAME)
                    .description("Everything the public card carries, plus the skills reserved for known clients")
                    .version("1.0.0")
                    .capabilities(new AgentCapabilities(false, false, true, List.of()))
                    .supportedInterfaces(List.of(new AgentInterface("JSONRPC", "http://localhost:8081", null)))
                    .defaultInputModes(List.of("text/plain"))
                    .defaultOutputModes(List.of("text/plain"))
                    .skills(List.of(AgentSkill.builder()
                            .id("severe_weather_alerts")
                            .name("Severe weather alerts")
                            .description("Alerts reserved for authenticated clients")
                            .tags(List.of("weather"))
                            .build()))
                    .build();
        }
    }

    @Test
    public void testTheApplicationsExtendedCardIsWhatClientsReceive() {
        given()
                .contentType("application/json")
                .header("A2A-Version", "1.0")
                .body("{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"method\":\"GetExtendedAgentCard\",\"params\":{}}")
                .when().post("/")
                .then()
                .statusCode(200)
                .body("error", nullValue())
                .body("result.name", equalTo(EXTENDED_NAME))
                .body("result.skills[0].id", equalTo("severe_weather_alerts"));
    }

    /**
     * The public card stays what it was: advertising an extended card must not change what an anonymous client
     * discovers at the well-known URI.
     */
    @Test
    public void testThePublicCardIsUnaffected() {
        given()
                .when().get("/.well-known/agent-card.json")
                .then()
                .statusCode(200)
                .body("name", equalTo("Weather Agent"))
                .body("skills[0].id", equalTo("weather_search"));
    }
}
