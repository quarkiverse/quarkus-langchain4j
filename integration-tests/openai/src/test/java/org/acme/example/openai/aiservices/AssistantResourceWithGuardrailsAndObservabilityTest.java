package org.acme.example.openai.aiservices;

import static io.restassured.RestAssured.get;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;

import jakarta.inject.Inject;

import org.acme.example.openai.TestUtils;
import org.acme.example.openai.aiservices.AssistantResourceWithGuardrailsAndObservability.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class AssistantResourceWithGuardrailsAndObservabilityTest {
    @Inject
    MeterRegistry registry;

    @BeforeEach
    void addSimpleRegistry() {
        Metrics.globalRegistry.add(new SimpleMeterRegistry());
        Metrics.globalRegistry.clear();
    }

    @Test
    void guardrailMetricsAvailable() {
        get("assistant-with-guardrails-observability").then()
                .statusCode(200)
                .body(TestUtils.containsStringOrMock("test"));

        await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(
                        registry
                                .find("guardrail.invoked")
                                .tag("operation", "chat")
                                .tag("aiservice",
                                        "org.acme.example.openai.aiservices.AssistantResourceWithGuardrailsAndObservability$Assistant")
                                .counters())
                        .hasSize(8)
                        .allSatisfy(c -> assertThat(c).isNotNull().extracting(Counter::count).isEqualTo(1.0))
                        .allSatisfy(c -> assertThat(c).isNotNull().extracting(x -> x.getId().getTag("outcome"))
                                .isEqualTo("success"))
                        .allSatisfy(c -> assertThat(c).isNotNull().extracting(x -> x.getId().getTag("guardrail.type"))
                                .isIn("input", "output"))
                        .map(c -> c.getId().getTag("guardrail"))
                        .containsOnlyOnce(
                                "org.acme.example.openai.aiservices.AssistantResourceWithGuardrailsAndObservability$IGDirectlyImplementInputGuardrailWithUserMessage")
                        .containsOnlyOnce(
                                "org.acme.example.openai.aiservices.AssistantResourceWithGuardrailsAndObservability$OGExtendingValidateWithAiMessage")
                        .containsOnlyOnce(
                                "org.acme.example.openai.aiservices.AssistantResourceWithGuardrailsAndObservability$OGDirectlyImplementOutputGuardrailWithAiMessage")
                        .containsOnlyOnce(
                                "org.acme.example.openai.aiservices.AssistantResourceWithGuardrailsAndObservability$IGExtendingValidateWithParams")
                        .containsOnlyOnce(
                                "org.acme.example.openai.aiservices.AssistantResourceWithGuardrailsAndObservability$OGExtendingValidateWithParams")
                        .containsOnlyOnce(
                                "org.acme.example.openai.aiservices.AssistantResourceWithGuardrailsAndObservability$OGDirectlyImplementOutputGuardrailWithParams")
                        .containsOnlyOnce(
                                "org.acme.example.openai.aiservices.AssistantResourceWithGuardrailsAndObservability$IGDirectlyImplementInputGuardrailWithParams")
                        .containsOnlyOnce(
                                "org.acme.example.openai.aiservices.AssistantResourceWithGuardrailsAndObservability$IGExtendingValidateWithUserMessage"));
        await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(
                        registry
                                .find("guardrail.timed")
                                .tag("operation", "chat")
                                .tag("aiservice",
                                        "org.acme.example.openai.aiservices.AssistantResourceWithGuardrailsAndObservability$Assistant")
                                .timers())
                        .hasSize(8)
                        .allSatisfy(c -> assertThat(c).isNotNull().extracting(Timer::count).isEqualTo(1L))
                        .allSatisfy(c -> assertThat(c).isNotNull().extracting(x -> x.getId().getTag("outcome"))
                                .isEqualTo("success"))
                        .allSatisfy(c -> assertThat(c).isNotNull().extracting(x -> x.getId().getTag("guardrail.type"))
                                .isIn("input", "output"))
                        .map(c -> c.getId().getTag("guardrail"))
                        .containsOnlyOnce(
                                "org.acme.example.openai.aiservices.AssistantResourceWithGuardrailsAndObservability$IGDirectlyImplementInputGuardrailWithUserMessage")
                        .containsOnlyOnce(
                                "org.acme.example.openai.aiservices.AssistantResourceWithGuardrailsAndObservability$OGExtendingValidateWithAiMessage")
                        .containsOnlyOnce(
                                "org.acme.example.openai.aiservices.AssistantResourceWithGuardrailsAndObservability$OGDirectlyImplementOutputGuardrailWithAiMessage")
                        .containsOnlyOnce(
                                "org.acme.example.openai.aiservices.AssistantResourceWithGuardrailsAndObservability$IGExtendingValidateWithParams")
                        .containsOnlyOnce(
                                "org.acme.example.openai.aiservices.AssistantResourceWithGuardrailsAndObservability$OGExtendingValidateWithParams")
                        .containsOnlyOnce(
                                "org.acme.example.openai.aiservices.AssistantResourceWithGuardrailsAndObservability$OGDirectlyImplementOutputGuardrailWithParams")
                        .containsOnlyOnce(
                                "org.acme.example.openai.aiservices.AssistantResourceWithGuardrailsAndObservability$IGDirectlyImplementInputGuardrailWithParams")
                        .containsOnlyOnce(
                                "org.acme.example.openai.aiservices.AssistantResourceWithGuardrailsAndObservability$IGExtendingValidateWithUserMessage"));

        await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(
                        registry
                                .find("guardrail.timed")
                                .tag("operation", "chat")
                                .tag("aiservice",
                                        "org.acme.example.openai.aiservices.AssistantResourceWithGuardrailsAndObservability$Assistant")
                                .timers())
                        .hasSize(8)
                        .allSatisfy(c -> assertThat(c).isNotNull().extracting(Timer::count).isEqualTo(1L))
                        .allSatisfy(c -> assertThat(c).isNotNull().extracting(x -> x.getId().getTag("outcome"))
                                .isEqualTo("success"))
                        .allSatisfy(c -> assertThat(c).isNotNull().extracting(x -> x.getId().getTag("guardrail.type"))
                                .isIn("input", "output"))
                        .map(c -> c.getId().getTag("guardrail"))
                        .containsOnlyOnce(
                                "org.acme.example.openai.aiservices.AssistantResourceWithGuardrailsAndObservability$IGDirectlyImplementInputGuardrailWithUserMessage")
                        .containsOnlyOnce(
                                "org.acme.example.openai.aiservices.AssistantResourceWithGuardrailsAndObservability$OGExtendingValidateWithAiMessage")
                        .containsOnlyOnce(
                                "org.acme.example.openai.aiservices.AssistantResourceWithGuardrailsAndObservability$OGDirectlyImplementOutputGuardrailWithAiMessage")
                        .containsOnlyOnce(
                                "org.acme.example.openai.aiservices.AssistantResourceWithGuardrailsAndObservability$IGExtendingValidateWithParams")
                        .containsOnlyOnce(
                                "org.acme.example.openai.aiservices.AssistantResourceWithGuardrailsAndObservability$OGExtendingValidateWithParams")
                        .containsOnlyOnce(
                                "org.acme.example.openai.aiservices.AssistantResourceWithGuardrailsAndObservability$OGDirectlyImplementOutputGuardrailWithParams")
                        .containsOnlyOnce(
                                "org.acme.example.openai.aiservices.AssistantResourceWithGuardrailsAndObservability$IGDirectlyImplementInputGuardrailWithParams")
                        .containsOnlyOnce(
                                "org.acme.example.openai.aiservices.AssistantResourceWithGuardrailsAndObservability$IGExtendingValidateWithUserMessage"));

    }

    @Test
    void deprecatedGuardrailMetricsAvailable() {
        get("assistant-with-guardrails-observability").then()
                .statusCode(200)
                .body(TestUtils.containsStringOrMock("test"));

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
                                        1.0, "Measures the number of times this guardrail was invoked (deprecated)"))
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
                                .containsExactly(1L, "Measures the runtime of this guardrail (deprecated)"))
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
                                .containsExactly(1L, "Measures the runtime of this guardrail (deprecated)"))
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
