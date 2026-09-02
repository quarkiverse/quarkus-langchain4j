package io.quarkiverse.langchain4j.test.cost;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.quarkiverse.langchain4j.runtime.listeners.MetricsChatModelListener;
import io.quarkus.test.QuarkusUnitTest;

/**
 * Verifies that {@link MetricsChatModelListener#onError} records {@code gen_ai.client.operation.duration} with a
 * low-cardinality {@code error.type} and with exactly the same tag keys as
 * {@link MetricsChatModelListener#onResponse}, so registries that require a single tag-key set per meter name
 * (such as Prometheus) accept both the success and the error path.
 */
class OperationDurationErrorMetricsTest {

    private static final String DURATION_METER = "gen_ai.client.operation.duration";

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
    void errorPathUsesExceptionClassNameAndSameTagKeysAsResponsePath() {
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(UserMessage.from("hello")))
                .parameters(DefaultChatRequestParameters.builder().modelName("error-test-model").build())
                .build();

        // success path
        Map<Object, Object> responseAttributes = new HashMap<>();
        listener.onRequest(new ChatModelRequestContext(request, ModelProvider.OTHER, responseAttributes));
        listener.onResponse(new ChatModelResponseContext(
                ChatResponse.builder().aiMessage(AiMessage.from("hi")).modelName("error-test-model").build(),
                request, ModelProvider.OTHER, responseAttributes));

        // error path, with a message that embeds request-scoped values
        Map<Object, Object> errorAttributes = new HashMap<>();
        listener.onRequest(new ChatModelRequestContext(request, ModelProvider.OTHER, errorAttributes));
        listener.onError(new ChatModelErrorContext(
                new IllegalStateException("status_code: 400 request_id: c06b0882-2e48-4bcc-a20f-185c73b241a2"),
                request, ModelProvider.OTHER, errorAttributes));

        Timer successTimer = registry.find(DURATION_METER)
                .tag("gen_ai.request.model", "error-test-model")
                .tag("error.type", "none")
                .timer();
        Timer errorTimer = registry.find(DURATION_METER)
                .tag("gen_ai.request.model", "error-test-model")
                .tag("error.type", IllegalStateException.class.getName())
                .timer();

        assertThat(successTimer).isNotNull();
        assertThat(successTimer.count()).isEqualTo(1);
        assertThat(errorTimer).isNotNull();
        assertThat(errorTimer.count()).isEqualTo(1);

        assertThat(errorTimer.getId().getTag("gen_ai.provider.name")).isEqualTo("other");
        assertThat(errorTimer.getId().getTag("gen_ai.response.model")).isEqualTo("none");

        assertThat(tagKeys(errorTimer)).isEqualTo(tagKeys(successTimer));
    }

    @Test
    void errorPathWithoutProviderFallsBackToNone() {
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(UserMessage.from("hello")))
                .parameters(DefaultChatRequestParameters.builder().modelName("error-null-model").build())
                .build();

        Map<Object, Object> attributes = new HashMap<>();
        listener.onRequest(new ChatModelRequestContext(request, null, attributes));
        listener.onError(new ChatModelErrorContext(new RuntimeException("boom"), request, null, attributes));

        Timer errorTimer = registry.find(DURATION_METER)
                .tag("gen_ai.request.model", "error-null-model")
                .timer();

        assertThat(errorTimer).isNotNull();
        assertThat(errorTimer.getId().getTag("error.type")).isEqualTo(RuntimeException.class.getName());
        assertThat(errorTimer.getId().getTag("gen_ai.provider.name")).isEqualTo("none");
    }

    private static Set<String> tagKeys(Timer timer) {
        return timer.getId().getTags().stream().map(Tag::getKey).collect(Collectors.toSet());
    }
}
