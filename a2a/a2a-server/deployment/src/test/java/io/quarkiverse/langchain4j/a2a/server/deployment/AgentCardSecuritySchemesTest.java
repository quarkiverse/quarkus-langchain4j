package io.quarkiverse.langchain4j.a2a.server.deployment;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;

import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.HTTPAuthSecurityScheme;
import org.a2aproject.sdk.spec.SecurityRequirement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.a2a.server.AgentCardBuilderCustomizer;
import io.quarkiverse.langchain4j.a2a.server.ExposeA2AAgent;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

/**
 * An application that puts authentication in front of the endpoints has to say so on the Agent Card, because that is
 * where a client looks before it calls. The extension declares nothing on its own, so the customizer is the whole
 * mechanism, and this test is the guide's example.
 *
 * @see <a href="https://a2a-protocol.org/latest/specification/#4-authentication-and-authorization">A2A spec §4 —
 *      Authentication and Authorization</a>
 */
public class AgentCardSecuritySchemesTest extends OpenAiBaseTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest().withApplicationRoot(jar -> jar
            .addClasses(TestAgent.class, SecurityCustomizer.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "whatever")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url",
                    WiremockAware.wiremockUrlForConfig("/v1"))
            .overrideRuntimeConfigKey("quarkus.langchain4j.a2a.server.name", "Weather Agent");

    @ApplicationScoped
    public static class SecurityCustomizer implements AgentCardBuilderCustomizer {

        @Override
        public void customize(AgentCard.Builder cardBuilder) {
            cardBuilder.securitySchemes(Map.of("bearer", new HTTPAuthSecurityScheme("JWT", "bearer", null)));
            cardBuilder.securityRequirements(List.of(new SecurityRequirement(Map.of("bearer", List.of()))));
        }
    }

    @RegisterAiService
    @ExposeA2AAgent(skills = @ExposeA2AAgent.Skill(id = "weather_search", name = "Search weather", description = "Helps with weather", tags = "weather"))
    public interface TestAgent {

        String chat(@UserMessage String question);
    }

    @Test
    public void testTheCardCarriesTheDeclaredScheme() {
        given()
                .when().get("/.well-known/agent-card.json")
                .then()
                .statusCode(200)
                // SecurityScheme is a oneof in the specification, so the concrete scheme sits under its own key
                .body("securitySchemes.bearer.httpAuthSecurityScheme.scheme", equalTo("bearer"))
                .body("securitySchemes.bearer.httpAuthSecurityScheme.bearerFormat", equalTo("JWT"))
                .body("securityRequirements[0].schemes", hasKey("bearer"));
    }
}
