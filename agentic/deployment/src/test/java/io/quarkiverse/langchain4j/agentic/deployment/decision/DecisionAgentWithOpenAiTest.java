package io.quarkiverse.langchain4j.agentic.deployment.decision;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.V;
import dev.langchain4j.service.decision.Decide;
import io.quarkiverse.langchain4j.openai.testing.internal.OpenAiBaseTest;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusExtensionTest;

/**
 * A decision agent without supplier gets the decision model bean of the configured provider.
 */
public class DecisionAgentWithOpenAiTest extends OpenAiBaseTest {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class))
            .overrideConfigKey("quarkus.langchain4j.openai.decision-model.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.api-key", "decision-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url", WiremockAware.wiremockUrlForConfig("/v1"));

    public interface SpamGate {
        @Decide("Is this message spam?")
        @Agent(description = "Detects spam", outputKey = "spam")
        boolean isSpam(@V("message") String message);
    }

    @Inject
    SpamGate spamGate;

    @Test
    void decisionAgentUsesTheOpenAiDecisionModel() {
        wiremock().register(post(urlEqualTo("/v1/decisions"))
                .withHeader("Authorization", equalTo("Bearer decision-key"))
                .withRequestBody(matchingJsonPath("$.questions[0].instructions", equalTo("Is this message spam?")))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "model": "gpt-6-luna",
                                  "answers": [{"type": "predicate", "name": "isSpam", "probability": 0.93}]
                                }
                                """)));

        assertThat(spamGate.isSpam("Congratulations! You won a free cruise!")).isTrue();
        assertThat(wiremock().getServeEvents()).hasSize(1);
    }
}
