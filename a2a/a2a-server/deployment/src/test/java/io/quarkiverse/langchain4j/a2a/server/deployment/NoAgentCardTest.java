package io.quarkiverse.langchain4j.a2a.server.deployment;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;

import jakarta.enterprise.inject.Instance;

import org.a2aproject.sdk.server.PublicAgentCard;
import org.a2aproject.sdk.spec.AgentCard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;

/**
 * An application that exposes no agent must fail loudly rather than answer as a server without an identity. This
 * extension contributes no card, so the {@link PublicAgentCard} instance stays unsatisfied, and a request for the
 * card is answered with an error rather than with a card describing nothing.
 */
public class NoAgentCardTest {

    @RegisterExtension
    static final QuarkusUnitTest test = new QuarkusUnitTest().withEmptyApplication();

    @PublicAgentCard
    Instance<AgentCard> agentCard;

    @Test
    public void testNoCardIsContributed() {
        assertThat(agentCard.isResolvable()).isFalse();
    }

    @Test
    public void testFetchingTheCardIsAnError() {
        // the guarantee, rather than the SDK internals that currently deliver it
        given()
                .when().get("/.well-known/agent-card.json")
                .then().statusCode(not(200));
    }
}
