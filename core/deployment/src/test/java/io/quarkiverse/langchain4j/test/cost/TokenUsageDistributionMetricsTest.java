package io.quarkiverse.langchain4j.test.cost;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.quarkiverse.langchain4j.runtime.listeners.MetricsChatModelListener;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Verifies that {@link MetricsChatModelListener} records per-request token usage as a
 * {@link DistributionSummary} alongside the existing {@link Counter} metrics, so operators can answer
 * per-request distribution questions (p50/p95/p99/max prompt size) that a monotonic Counter cannot.
 * <p>
 * Each test uses a distinct model name so its meter series are isolated, since the same listener and
 * the JVM-wide {@link Metrics#globalRegistry} are shared across the test methods of this deployment.
 */
class TokenUsageDistributionMetricsTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class));

    @Inject
    MetricsChatModelListener listener;

    @Inject
    MeterRegistry registry;

    @BeforeAll
    static void addSimpleRegistry() {
        Metrics.globalRegistry.add(new SimpleMeterRegistry());
    }

    @Test
    void recordsCounterAndDistributionSummaryForInputAndOutput() {
        String model = "happy-path-model";
        fire(model, new TokenUsage(100, 25));

        Counter inputCounter = registry.find("gen_ai.client.token.usage")
                .tag("gen_ai.request.model", model)
                .tag("gen_ai.token.type", "input")
                .counter();
        assertThat(inputCounter).isNotNull();
        assertThat(inputCounter.count()).isEqualTo(100.0);

        DistributionSummary inputDistribution = registry.find("gen_ai.client.token.usage.distribution")
                .tag("gen_ai.request.model", model)
                .tag("gen_ai.token.type", "input")
                .summary();
        assertThat(inputDistribution).isNotNull();
        assertThat(inputDistribution.count()).isEqualTo(1L);
        assertThat(inputDistribution.max()).isEqualTo(100.0);
        assertThat(inputDistribution.totalAmount()).isEqualTo(100.0);

        DistributionSummary outputDistribution = registry.find("gen_ai.client.token.usage.distribution")
                .tag("gen_ai.request.model", model)
                .tag("gen_ai.token.type", "output")
                .summary();
        assertThat(outputDistribution).isNotNull();
        assertThat(outputDistribution.count()).isEqualTo(1L);
        assertThat(outputDistribution.max()).isEqualTo(25.0);
        assertThat(outputDistribution.totalAmount()).isEqualTo(25.0);
    }

    @Test
    void recordsNothingForNullTokenCounts() {
        String model = "null-tokens-model";
        fire(model, new TokenUsage(null, null));

        DistributionSummary inputDistribution = registry.find("gen_ai.client.token.usage.distribution")
                .tag("gen_ai.request.model", model)
                .tag("gen_ai.token.type", "input")
                .summary();
        DistributionSummary outputDistribution = registry.find("gen_ai.client.token.usage.distribution")
                .tag("gen_ai.request.model", model)
                .tag("gen_ai.token.type", "output")
                .summary();

        // No NPE and no zero-sample pollution: the summary is never created for an absent token count.
        assertThat(inputDistribution).isNull();
        assertThat(outputDistribution).isNull();
    }

    @Test
    void distributionCapturesOutliersTheCounterHides() {
        String model = "outlier-model";
        fire(model, new TokenUsage(100, 10));
        fire(model, new TokenUsage(10000, 10));

        Counter inputCounter = registry.find("gen_ai.client.token.usage")
                .tag("gen_ai.request.model", model)
                .tag("gen_ai.token.type", "input")
                .counter();
        assertThat(inputCounter).isNotNull();
        // Counter only knows the total, hiding that one request was 100x larger than the other.
        assertThat(inputCounter.count()).isEqualTo(10100.0);

        DistributionSummary inputDistribution = registry.find("gen_ai.client.token.usage.distribution")
                .tag("gen_ai.request.model", model)
                .tag("gen_ai.token.type", "input")
                .summary();
        assertThat(inputDistribution).isNotNull();
        assertThat(inputDistribution.count()).isEqualTo(2L);
        // The distribution exposes the outlier the Counter total cannot.
        assertThat(inputDistribution.max()).isEqualTo(10000.0);
        assertThat(inputDistribution.totalAmount()).isEqualTo(10100.0);
    }

    private void fire(String modelName, TokenUsage tokenUsage) {
        Map<Object, Object> attributes = new HashMap<>();
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(UserMessage.from("hello")))
                .parameters(DefaultChatRequestParameters.builder().modelName(modelName).build())
                .build();
        ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("hi"))
                .modelName(modelName)
                .tokenUsage(tokenUsage)
                .build();

        listener.onRequest(new ChatModelRequestContext(request, ModelProvider.OTHER, attributes));
        listener.onResponse(new ChatModelResponseContext(response, request, ModelProvider.OTHER, attributes));
    }
}
