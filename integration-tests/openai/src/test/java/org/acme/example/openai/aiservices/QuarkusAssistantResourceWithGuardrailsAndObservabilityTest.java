package org.acme.example.openai.aiservices;

import static io.restassured.RestAssured.get;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;

import java.time.Duration;

import jakarta.inject.Inject;

import org.acme.example.openai.aiservices.QuarkusAssistantResourceWithGuardrailsAndObservability.AbstractIGImplementingValidateWithParams;
import org.acme.example.openai.aiservices.QuarkusAssistantResourceWithGuardrailsAndObservability.AbstractIGImplementingValidateWithUserMessage;
import org.acme.example.openai.aiservices.QuarkusAssistantResourceWithGuardrailsAndObservability.AbstractOGImplementingValidateWithAiMessage;
import org.acme.example.openai.aiservices.QuarkusAssistantResourceWithGuardrailsAndObservability.AbstractOGImplementingValidateWithParams;
import org.acme.example.openai.aiservices.QuarkusAssistantResourceWithGuardrailsAndObservability.IGDirectlyImplementInputGuardrailWithParams;
import org.acme.example.openai.aiservices.QuarkusAssistantResourceWithGuardrailsAndObservability.IGDirectlyImplementInputGuardrailWithUserMessage;
import org.acme.example.openai.aiservices.QuarkusAssistantResourceWithGuardrailsAndObservability.OGDirectlyImplementOutputGuardrailWithAiMessage;
import org.acme.example.openai.aiservices.QuarkusAssistantResourceWithGuardrailsAndObservability.OGDirectlyImplementOutputGuardrailWithParams;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.quarkus.test.junit.QuarkusTest;

/**
 * @deprecated These tests will go away once the Quarkus-specific guardrail implementation has been fully removed
 */
@Deprecated(forRemoval = true)
@QuarkusTest
class QuarkusAssistantResourceWithGuardrailsAndObservabilityTest {
    @Inject
    MeterRegistry registry;

    @BeforeAll
    static void addSimpleRegistry() {
        Metrics.globalRegistry.add(new SimpleMeterRegistry());
        Metrics.globalRegistry.clear();
    }

    @Test
    void guardrailMetricsAvailable() {
        get("/quarkus-assistant-with-guardrails-observability").then()
                .statusCode(200)
                .body(containsString("MockGPT"));

        await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(
                        registry
                                .find("guardrail.invoked")
                                .tag("method", "validate")
                                .counters())
                        .hasSize(8)
                        .allSatisfy(c -> assertThat(c)
                                .isNotNull()
                                .extracting(
                                        Counter::count,
                                        ct -> ct.getId().getDescription())
                                .containsExactly(
                                        1.0, "Measures the number of times this guardrail was invoked"))
                        .map(c -> c.getId().getTag("class"))
                        .containsOnlyOnce(AbstractIGImplementingValidateWithUserMessage.class.getName())
                        .containsOnlyOnce(IGDirectlyImplementInputGuardrailWithParams.class.getName())
                        .containsOnlyOnce(AbstractIGImplementingValidateWithParams.class.getName())
                        .containsOnlyOnce(OGDirectlyImplementOutputGuardrailWithAiMessage.class.getName())
                        .containsOnlyOnce(AbstractOGImplementingValidateWithParams.class.getName())
                        .containsOnlyOnce(AbstractOGImplementingValidateWithAiMessage.class.getName())
                        .containsOnlyOnce(IGDirectlyImplementInputGuardrailWithUserMessage.class.getName())
                        .containsOnlyOnce(OGDirectlyImplementOutputGuardrailWithParams.class.getName()));

        await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(
                        registry
                                .find("guardrail.timed")
                                .tag("method", "validate")
                                .timers())
                        .hasSize(8)
                        .allSatisfy(t -> assertThat(t)
                                .isNotNull()
                                .extracting(
                                        Timer::count,
                                        ti -> ti.getId().getDescription())
                                .containsExactly(1L, "Measures the runtime of this guardrail"))
                        .map(c -> c.getId().getTag("class"))
                        .containsOnlyOnce(AbstractIGImplementingValidateWithUserMessage.class.getName())
                        .containsOnlyOnce(IGDirectlyImplementInputGuardrailWithParams.class.getName())
                        .containsOnlyOnce(AbstractIGImplementingValidateWithParams.class.getName())
                        .containsOnlyOnce(OGDirectlyImplementOutputGuardrailWithAiMessage.class.getName())
                        .containsOnlyOnce(AbstractOGImplementingValidateWithParams.class.getName())
                        .containsOnlyOnce(AbstractOGImplementingValidateWithAiMessage.class.getName())
                        .containsOnlyOnce(IGDirectlyImplementInputGuardrailWithUserMessage.class.getName())
                        .containsOnlyOnce(OGDirectlyImplementOutputGuardrailWithParams.class.getName()));

        await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(
                        registry
                                .find("guardrail.timed")
                                .tag("method", "validate")
                                .timers())
                        .hasSize(8)
                        .allSatisfy(t -> assertThat(t)
                                .isNotNull()
                                .extracting(
                                        Timer::count,
                                        ti -> ti.getId().getDescription())
                                .containsExactly(1L, "Measures the runtime of this guardrail"))
                        .map(c -> c.getId().getTag("class"))
                        .containsOnlyOnce(AbstractIGImplementingValidateWithUserMessage.class.getName())
                        .containsOnlyOnce(IGDirectlyImplementInputGuardrailWithParams.class.getName())
                        .containsOnlyOnce(AbstractIGImplementingValidateWithParams.class.getName())
                        .containsOnlyOnce(OGDirectlyImplementOutputGuardrailWithAiMessage.class.getName())
                        .containsOnlyOnce(AbstractOGImplementingValidateWithParams.class.getName())
                        .containsOnlyOnce(AbstractOGImplementingValidateWithAiMessage.class.getName())
                        .containsOnlyOnce(IGDirectlyImplementInputGuardrailWithUserMessage.class.getName())
                        .containsOnlyOnce(OGDirectlyImplementOutputGuardrailWithParams.class.getName()));
    }
}
