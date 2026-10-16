package io.quarkiverse.langchain4j.a2a.server.deployment;

import static io.quarkiverse.langchain4j.a2a.server.deployment.JsonRpc.sendMessage;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.a2a.server.ExposeA2AAgent;
import io.quarkus.test.QuarkusUnitTest;

/**
 * An application need not expose an agent without a tenant. Requests that name none then have no agent to answer
 * them, and have to be refused rather than routed to whichever tenant happens to be there.
 */
public class OnlyTenantedAgentsTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest().withApplicationRoot(jar -> jar
            .addClasses(JsonRpc.class, FakeChatModel.class, SunnyChatModel.class, WeatherAgent.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.a2a.server.name", "Weather Agent")
            .overrideRuntimeConfigKey("a2a.authorization.required", "false");

    @RegisterAiService(chatLanguageModelSupplier = SunnyChatModel.class)
    @ExposeA2AAgent(tenant = "weather", skills = @ExposeA2AAgent.Skill(id = "forecast", name = "Forecast", description = "Forecasts the weather", tags = "weather"))
    public interface WeatherAgent {

        String chat(@UserMessage String question);
    }

    @Test
    public void testTheTenantIsServed() {
        sendMessage("hello").tenant("weather").post()
                .body("error", nullValue())
                .body("result.task.artifacts[0].parts[0].text", equalTo("It is sunny."));
    }

    @Test
    public void testARequestNamingNoTenantIsRefused() {
        sendMessage("hello").post()
                .body("result", nullValue())
                .body("error", notNullValue());
    }

    @Test
    public void testThereIsNoCardAtTheWellKnownUri() {
        given()
                .when().get("/.well-known/agent-card.json")
                .then()
                .statusCode(404);
    }
}
