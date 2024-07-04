package io.quarkiverse.langchain4j.runtime.listeners;

import java.util.concurrent.TimeUnit;

import org.jboss.logging.Logger;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequest;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponse;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.output.TokenUsage;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

/**
 * Creates metrics that follow the
 * <a href="https://github.com/open-telemetry/semantic-conventions/blob/main/docs/gen-ai/gen-ai-spans.md">Semantic Conventions
 * for GenAI Metrics</a>
 */
public class MetricsChatModelListener implements ChatModelListener {

    private static final Logger log = Logger.getLogger(MetricsChatModelListener.class);

    public static final String START_TIME_KEY_NAME = "startTime";
    private final Meter.MeterProvider<Counter> inputTokenUsage;
    private final Meter.MeterProvider<Counter> outputTokenUsage;
    private final Meter.MeterProvider<Timer> duration;

    public MetricsChatModelListener() {
        inputTokenUsage = Counter.builder("gen_ai.client.token.usage")
                .description("Measures number of input tokens used")
                .tag("gen_ai.operation.name", "completion")
                .tag("gen_ai.token.type", "input")
                .withRegistry(Metrics.globalRegistry);
        outputTokenUsage = Counter.builder("gen_ai.client.token.usage")
                .description("Measures number of output tokens used")
                .tag("gen_ai.operation.name", "completion")
                .tag("gen_ai.token.type", "output")
                .withRegistry(Metrics.globalRegistry);
        duration = Timer.builder("gen_ai.client.operation.duration")
                .description("GenAI operation duration")
                .tag("gen_ai.operation.name", "completion")
                .withRegistry(Metrics.globalRegistry);
    }

    @Override
    public void onRequest(ChatModelRequestContext requestContext) {
        final long startTime = Clock.SYSTEM.monotonicTime();
        requestContext.attributes().put(START_TIME_KEY_NAME, startTime);
    }

    @Override
    public void onResponse(ChatModelResponseContext responseContext) {
        final long endTime = Clock.SYSTEM.monotonicTime();

        ChatModelRequest request = responseContext.request();
        ChatModelResponse response = responseContext.response();
        Tags tags = Tags.of(
                Tag.of("gen_ai.request.model", request.model()),
                Tag.of("gen_ai.response.model", response.model()));

        recordTokenUsage(responseContext, tags);
        recordDuration(responseContext, endTime, tags);
    }

    @Override
    public void onError(ChatModelErrorContext errorContext) {
        final long endTime = Clock.SYSTEM.monotonicTime();

        Long startTime = (Long) errorContext.attributes().get(START_TIME_KEY_NAME);
        if (startTime == null) {
            // should never happen
            log.warn("No start time found in response");
            return;
        }

        AiMessage aiMessage = errorContext.partialResponse().aiMessage();
        if (aiMessage == null) {
            return;
        }

        Tags tags = Tags.of(
                Tag.of("gen_ai.request.model", errorContext.request().model()),
                Tag.of("gen_ai.response.model", errorContext.partialResponse().model()),
                Tag.of("error.type", aiMessage.text()));
        duration.withTags(tags).record(endTime - startTime, TimeUnit.NANOSECONDS);
    }

    private void recordTokenUsage(ChatModelResponseContext responseContext, Tags tags) {
        TokenUsage tokenUsage = responseContext.response().tokenUsage();
        if (tokenUsage == null) {
            return;
        }

        Integer inputTokenCount = tokenUsage.inputTokenCount();
        if (inputTokenCount != null) {
            inputTokenUsage
                    .withTags(tags)
                    .increment(inputTokenCount);
        }
        Integer outputTokenCount = tokenUsage.outputTokenCount();
        if (outputTokenCount != null) {
            outputTokenUsage
                    .withTags(tags)
                    .increment(outputTokenCount);
        }
    }

    private void recordDuration(ChatModelResponseContext responseContext, long endTime, Tags tags) {
        Long startTime = (Long) responseContext.attributes().get(START_TIME_KEY_NAME);
        if (startTime == null) {
            // should never happen
            log.warn("No start time found in response");
            return;
        }
        duration.withTags(tags).record(endTime - startTime, TimeUnit.NANOSECONDS);
    }
}
