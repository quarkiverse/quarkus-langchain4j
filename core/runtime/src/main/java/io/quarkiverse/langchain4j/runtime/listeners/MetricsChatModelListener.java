package io.quarkiverse.langchain4j.runtime.listeners;

import java.util.concurrent.TimeUnit;

import org.jboss.logging.Logger;

import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.quarkiverse.langchain4j.cost.Cost;
import io.quarkiverse.langchain4j.cost.CostEstimatorService;
import io.quarkiverse.langchain4j.runtime.ContextLocals;
import io.quarkiverse.langchain4j.runtime.aiservice.AiServiceConstants;

/**
 * Creates metrics that follow the
 * <a href="https://github.com/open-telemetry/semantic-conventions/blob/main/docs/gen-ai/gen-ai-spans.md">Semantic Conventions
 * for GenAI Metrics</a>
 */
public class MetricsChatModelListener implements ChatModelListener {

    private static final Logger log = Logger.getLogger(MetricsChatModelListener.class);

    public static final String START_TIME_KEY_NAME = "startTime";

    private final CostEstimatorService costEstimatorService;

    private final Meter.MeterProvider<Counter> inputTokenUsage;
    private final Meter.MeterProvider<Counter> outputTokenUsage;
    private final Meter.MeterProvider<Timer> duration;
    private final Meter.MeterProvider<Counter> estimatedCost;

    public MetricsChatModelListener(CostEstimatorService costEstimatorService) {
        this.costEstimatorService = costEstimatorService;

        this.inputTokenUsage = Counter.builder("gen_ai.client.token.usage")
                .description("Measures number of input tokens used")
                .tag("gen_ai.operation.name", "chat")
                .tag("gen_ai.token.type", "input")
                .withRegistry(Metrics.globalRegistry);
        this.outputTokenUsage = Counter.builder("gen_ai.client.token.usage")
                .description("Measures number of output tokens used")
                .tag("gen_ai.operation.name", "chat")
                .tag("gen_ai.token.type", "output")
                .withRegistry(Metrics.globalRegistry);
        this.duration = Timer.builder("gen_ai.client.operation.duration")
                .description("GenAI operation duration")
                .tag("gen_ai.operation.name", "chat")
                .withRegistry(Metrics.globalRegistry);
        this.estimatedCost = Counter.builder("gen_ai.client.estimated_cost")
                .description("Estimated cost of the request")
                .tag("gen_ai.operation.name", "chat")
                .tag("gen_ai.token.type", "output")
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

        ChatRequest request = responseContext.chatRequest();
        ChatResponse response = responseContext.chatResponse();
        String requestModel = request.parameters().modelName() != null
                ? request.parameters().modelName()
                : "none";
        String responseModel = response.metadata().modelName() != null
                ? response.metadata().modelName()
                : "none";

        String aiServiceClassName = "none";
        String aiServiceMethodName = "none";
        if (ContextLocals.duplicatedContextActive()) {
            String cls = ContextLocals.get(AiServiceConstants.AI_SERVICE_CLASS_NAME);
            if (cls != null) {
                aiServiceClassName = cls;
            }
            String mtd = ContextLocals.get(AiServiceConstants.AI_SERVICE_METHODNAME);
            if (mtd != null) {
                aiServiceMethodName = mtd;
            }
        }

        Tags tags = Tags.of("gen_ai.request.model", requestModel)
                .and("gen_ai.response.model", responseModel)
                .and("ai_service.class_name", aiServiceClassName)
                .and("ai_service.method_name", aiServiceMethodName)
                .and("error.type", "none");

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

        String requestModel = errorContext.chatRequest().parameters().modelName() != null
                ? errorContext.chatRequest().parameters().modelName()
                : "none";
        String errorType = errorContext.error() != null
                ? errorContext.error().getMessage()
                : "none";

        Tags tags = Tags.of("gen_ai.request.model", requestModel)
                .and("gen_ai.response.model", "none")
                .and("ai_service.class_name", "none")
                .and("ai_service.method_name", "none")
                .and("error.type", errorType);

        duration.withTags(tags).record(endTime - startTime, TimeUnit.NANOSECONDS);
    }

    private void recordTokenUsage(ChatModelResponseContext responseContext, Tags tags) {
        TokenUsage tokenUsage = responseContext.chatResponse().tokenUsage();
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
        if (inputTokenCount != null && outputTokenCount != null) {
            Cost costEstimate = costEstimatorService.estimate(responseContext);
            if (costEstimate != null) {
                estimatedCost.withTags(tags.and("currency", costEstimate.currencyCode()))
                        .increment(costEstimate.number().doubleValue());
            }
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
