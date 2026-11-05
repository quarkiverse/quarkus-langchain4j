package io.quarkiverse.langchain4j.a2a.server.deployment;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.a2a.server.ExposeA2AAgent;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Fetches the Agent Card the way a client would and validates the JSON on the wire.
 * <p>
 * The other tests inspect the {@code AgentCard} CDI bean, which cannot catch serialization problems: a card can
 * satisfy every assertion as a Java object and still reach clients malformed.
 */
public class AgentCardOverHttpTest {

    private static final String AGENT_CARD_PATH = "/.well-known/agent-card.json";

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest().withApplicationRoot(jar -> jar
            .addClasses(TestAgent.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"))
            .overrideRuntimeConfigKey("quarkus.langchain4j.a2a.server.name", "Weather Agent")
            .overrideRuntimeConfigKey("quarkus.langchain4j.a2a.server.description", "Helps with weather");

    @RegisterAiService
    @ExposeA2AAgent(skills = @ExposeA2AAgent.Skill(id = "weather_search", name = "Search weather", description = "Helps with weather in city, or states", tags = "weather", examples = "weather in LA, CA"))
    public interface TestAgent {

        String chat(@UserMessage String question);
    }

    @Test
    public void testCardIsServedAsJson() {
        given()
                .when().get(AGENT_CARD_PATH)
                .then()
                .statusCode(200)
                .contentType(containsString("application/json"));
    }

    @Test
    public void testRequiredFieldsArePresentOnTheWire() {
        given()
                .when().get(AGENT_CARD_PATH)
                .then()
                .statusCode(200)
                .body("name", equalTo("Weather Agent"))
                .body("description", equalTo("Helps with weather"))
                .body("version", notNullValue())
                .body("capabilities", notNullValue())
                .body("defaultInputModes", contains("text/plain"))
                .body("defaultOutputModes", contains("text/plain"))
                .body("skills[0].id", equalTo("weather_search"))
                .body("skills[0].tags", contains("weather"));
    }

    @Test
    public void testSupportedInterfacesCarryProtocolAndUrl() {
        given()
                .when().get(AGENT_CARD_PATH)
                .then()
                .statusCode(200)
                .body("supportedInterfaces[0].protocolBinding", equalTo("JSONRPC"))
                .body("supportedInterfaces[0].url", startsWith("http"))
                .body("supportedInterfaces.protocolVersion", everyItem(equalTo("1.0")));
    }

    /**
     * A field serialized as {@code null} is not the same as an absent one: a2a-java#945 reports exactly this
     * leaking into cards. Optional fields we never populate must simply not appear.
     */
    @Test
    public void testUnsetOptionalFieldsAreOmittedRatherThanNull() {
        given()
                .when().get(AGENT_CARD_PATH)
                .then()
                .statusCode(200)
                .body("$", not(hasKey("provider")))
                .body("$", not(hasKey("iconUrl")))
                .body("$", not(hasKey("signatures")))
                .body("supportedInterfaces[0]", not(hasKey("tenant")));
    }
}
