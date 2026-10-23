package io.quarkiverse.langchain4j.a2a.server.deployment;

import static io.quarkiverse.langchain4j.a2a.server.deployment.JsonRpc.sendMessage;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Singleton;

import org.a2aproject.sdk.server.multitenancy.Tenant;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.a2a.server.AgentCardBuilderCustomizer;
import io.quarkiverse.langchain4j.a2a.server.ExposeA2AAgent;
import io.quarkus.test.QuarkusUnitTest;

/**
 * One application serving several agents, each under a tenant of its own, next to the agent that answers requests
 * naming none. A client reaches an agent through the tenant alone, so everything the agent has, from its card to its
 * memory, has to be found through it, and a tenant nobody serves must not be answered by another agent.
 */
public class MultiTenancyTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest().withApplicationRoot(jar -> jar
            .addClasses(JsonRpc.class, FakeChatModel.class, NamingChatModel.class, DefaultChatModel.class,
                    WeatherChatModel.class, StoryChatModel.class, DefaultAgent.class, WeatherAgent.class,
                    StoryAgent.class, ProviderCustomizer.class, WeatherIconCustomizer.class))
            .overrideRuntimeConfigKey("quarkus.langchain4j.a2a.server.name", "Default Agent")
            .overrideRuntimeConfigKey("quarkus.langchain4j.a2a.server.tenants.weather.name", "Weather Agent")
            .overrideRuntimeConfigKey("quarkus.langchain4j.a2a.server.tenants.weather.description", "Forecasts")
            .overrideRuntimeConfigKey("a2a.authorization.required", "false");

    /**
     * Answers with which agent it serves and how many user messages the request carried, which is what tells the
     * tests both who answered and what that agent remembered.
     */
    public abstract static class NamingChatModel extends FakeChatModel {

        private final String agent;

        NamingChatModel(String agent) {
            this.agent = agent;
        }

        @Override
        protected String answer(ChatRequest request) {
            return agent + " heard " + userMessageCount(request);
        }
    }

    public static class DefaultChatModel extends NamingChatModel {

        public DefaultChatModel() {
            super("default");
        }
    }

    public static class WeatherChatModel extends NamingChatModel {

        public WeatherChatModel() {
            super("weather");
        }
    }

    public static class StoryChatModel extends NamingChatModel {

        public StoryChatModel() {
            super("stories");
        }
    }

    @ApplicationScoped
    @RegisterAiService(chatLanguageModelSupplier = DefaultChatModel.class)
    @ExposeA2AAgent(skills = @ExposeA2AAgent.Skill(id = "chat", name = "Chat", description = "Chats", tags = "chat"))
    public interface DefaultAgent {

        String chat(@MemoryId String conversationId, @UserMessage String question);
    }

    @ApplicationScoped
    @RegisterAiService(chatLanguageModelSupplier = WeatherChatModel.class)
    @ExposeA2AAgent(tenant = "weather", streaming = true, skills = @ExposeA2AAgent.Skill(id = "forecast", name = "Forecast", description = "Forecasts the weather", tags = "weather"))
    public interface WeatherAgent {

        String chat(@MemoryId String conversationId, @UserMessage String question);
    }

    @ApplicationScoped
    @RegisterAiService(chatLanguageModelSupplier = StoryChatModel.class)
    @ExposeA2AAgent(tenant = "stories", skills = @ExposeA2AAgent.Skill(id = "write_story", name = "Write a story", description = "Writes stories", tags = "writing"))
    public interface StoryAgent {

        String chat(@MemoryId String conversationId, @UserMessage String question);
    }

    @Singleton
    public static class ProviderCustomizer implements AgentCardBuilderCustomizer {

        @Override
        public void customize(AgentCard.Builder cardBuilder) {
            cardBuilder.provider(new AgentProvider("Example Inc.", "https://example.com"));
        }
    }

    @Singleton
    @Tenant("weather")
    public static class WeatherIconCustomizer implements AgentCardBuilderCustomizer {

        @Override
        public void customize(AgentCard.Builder cardBuilder) {
            cardBuilder.iconUrl("https://example.com/weather.png");
        }
    }

    @Test
    public void testEachTenantIsServedByItsOwnAgent() {
        sendMessage("hello").contextId("routing-weather").tenant("weather").post()
                .body("result.task.artifacts[0].parts[0].text", equalTo("weather heard 1"));
        sendMessage("hello").contextId("routing-stories").tenant("stories").post()
                .body("result.task.artifacts[0].parts[0].text", equalTo("stories heard 1"));
        sendMessage("hello").contextId("routing-default").post()
                .body("result.task.artifacts[0].parts[0].text", equalTo("default heard 1"));
    }

    /**
     * With no extended card produced without a tenant, as here, the SDK refuses a tenant no agent is exposed under,
     * so a misspelt tenant reaches the client as an error. {@link UnknownTenantWithDefaultExtendedCardTest} covers
     * the other case.
     */
    @Test
    public void testAnUnknownTenantIsRefused() {
        sendMessage("hello").tenant("wether").post()
                .body("result", nullValue())
                .body("error.code", equalTo(-32602));
    }

    /**
     * The client chooses the {@code contextId}, and chat memories are kept by id in a store the agents share, so the
     * same {@code contextId} sent to two tenants has to reach two conversations rather than one.
     */
    @Test
    public void testTheSameContextReachesASeparateConversationPerTenant() {
        sendMessage("my name is Jane Roe").contextId("shared").tenant("weather").post()
                .body("result.task.artifacts[0].parts[0].text", equalTo("weather heard 1"));
        sendMessage("what is my name?").contextId("shared").tenant("stories").post()
                .body("result.task.artifacts[0].parts[0].text", equalTo("stories heard 1"));
        sendMessage("what is my name?").contextId("shared").tenant("weather").post()
                .body("result.task.artifacts[0].parts[0].text", equalTo("weather heard 2"));
    }

    @Test
    public void testEachTenantPublishesItsOwnCard() {
        given()
                .when().get("/.well-known/weather/agent-card.json")
                .then()
                .statusCode(200)
                .body("name", equalTo("Weather Agent"))
                .body("description", equalTo("Forecasts"))
                .body("capabilities.streaming", equalTo(true))
                .body("skills[0].id", equalTo("forecast"))
                .body("supportedInterfaces[0].tenant", equalTo("weather"))
                .body("iconUrl", equalTo("https://example.com/weather.png"))
                .body("provider.organization", equalTo("Example Inc."));
    }

    /**
     * A tenant with nothing configured of its own still has a card to publish, so it takes the server's identity,
     * and a customizer qualified for another tenant leaves it alone.
     */
    @Test
    public void testATenantWithoutConfigurationTakesTheServersIdentity() {
        given()
                .when().get("/.well-known/stories/agent-card.json")
                .then()
                .statusCode(200)
                .body("name", equalTo("Default Agent"))
                .body("skills[0].id", equalTo("write_story"))
                .body("supportedInterfaces[0].tenant", equalTo("stories"))
                .body("iconUrl", nullValue())
                .body("provider.organization", equalTo("Example Inc."));
    }

    @Test
    public void testTheCardOfTheAgentWithoutTenantStaysAtTheWellKnownUri() {
        given()
                .when().get("/.well-known/agent-card.json")
                .then()
                .statusCode(200)
                .body("name", equalTo("Default Agent"))
                .body("skills[0].id", equalTo("chat"))
                .body("supportedInterfaces[0].tenant", nullValue())
                .body("supportedInterfaces[0].url", notNullValue())
                .body("iconUrl", nullValue());
    }
}
