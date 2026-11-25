package io.quarkiverse.langchain4j.runtime.aiservice;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.logging.Logger;

import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.Result;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Scope;
import io.quarkiverse.langchain4j.QuarkusJsonCodecFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * The span of one AI service invocation.
 * <p>
 * It records the arguments and the result when configured to, notifies the {@link AiServiceSpanContributor}s, and ends
 * exactly once, whichever terminal signal arrives first. A streaming invocation can reach its end several ways
 * (completion, failure, cancellation), possibly on different threads, so ending is guarded. Recording never throws: a
 * failure to record is logged and the invocation carries on.
 */
final class AiServiceSpan {

    private static final Logger LOG = Logger.getLogger(AiServiceSpan.class);

    /**
     * The methods for which an unserializable argument was already reported at WARN level.
     */
    private static final Set<String> METHODS_WITH_UNSERIALIZABLE_ARGUMENTS = ConcurrentHashMap.newKeySet();

    private final Span span;
    private final AiServiceMethodCreateInfo.SpanInfo spanInfo;
    private final AiServiceSpanStartContext startContext;
    private final boolean includeArguments;
    private final boolean includeResult;
    private final List<AiServiceSpanContributor> contributors;
    private final AtomicBoolean ended = new AtomicBoolean(false);
    private final StringBuilder streamedText = new StringBuilder();
    private volatile String finalText;

    AiServiceSpan(Span span, AiServiceMethodCreateInfo.SpanInfo spanInfo, AiServiceMethodCreateInfo createInfo,
            Object[] methodArgs, boolean includeArguments, boolean includeResult,
            List<AiServiceSpanContributor> contributors) {
        this.span = span;
        this.spanInfo = spanInfo;
        this.startContext = AiServiceSpanStartContext.builder()
                .interfaceName(createInfo.getInterfaceName())
                .methodName(createInfo.getMethodName())
                .methodArguments((methodArgs == null) ? List.of() : Arrays.asList(methodArgs))
                .build();
        this.includeArguments = includeArguments;
        this.includeResult = includeResult;
        this.contributors = contributors;
    }

    Scope makeCurrent() {
        return span.makeCurrent();
    }

    SpanContext spanContext() {
        return span.getSpanContext();
    }

    void start(Object[] methodArgs) {
        if (includeArguments) {
            recordArguments(methodArgs);
        }
        for (AiServiceSpanContributor contributor : contributors) {
            try {
                contributor.onStart(startContext, span);
            } catch (Exception e) {
                recordContributorFailure(e);
            }
        }
    }

    /**
     * Ends the span of a method that returned a value, or no value at all.
     */
    void complete(Object returnedValue) {
        Object recordedValue = null;
        if (spanInfo.resultKind() == AiServiceMethodCreateInfo.SpanResultKind.VALUE) {
            recordedValue = returnedValue;
        } else if ((spanInfo.resultKind() == AiServiceMethodCreateInfo.SpanResultKind.RESULT)
                && (returnedValue instanceof Result<?> result)) {
            recordedValue = result.content();
        }
        end(recordedValue, returnedValue);
    }

    /**
     * Ends the span of a streaming method once its stream has completed.
     */
    void completeStream() {
        String text = streamedResult();
        end(text, text);
    }

    void fail(Throwable failure) {
        if (ended.compareAndSet(false, true)) {
            span.recordException(failure);
            AiServiceSpanErrorContext errorContext = AiServiceSpanErrorContext.builder()
                    .startContext(startContext)
                    .error(failure)
                    .build();
            for (AiServiceSpanContributor contributor : contributors) {
                try {
                    contributor.onError(errorContext, span);
                } catch (Exception e) {
                    recordContributorFailure(e);
                }
            }
            span.end();
        }
    }

    /**
     * Ends the span of a streaming method whose subscription was canceled. There is no result.
     */
    void cancel() {
        end(null, null);
    }

    /**
     * Collects the result of a {@code Multi} or {@code Flow.Publisher} as its items are emitted.
     */
    void onStreamItem(Object item) {
        if (includeResult) {
            if ((spanInfo.resultKind() == AiServiceMethodCreateInfo.SpanResultKind.TEXT_STREAM)
                    && (item instanceof String text)) {
                synchronized (streamedText) {
                    streamedText.append(text);
                }
            } else if ((spanInfo.resultKind() == AiServiceMethodCreateInfo.SpanResultKind.EVENT_STREAM)
                    && (item instanceof ChatEvent.ChatCompletedEvent completedEvent)) {
                onFinalResponse(completedEvent.getChatResponse());
            }
        }
    }

    /**
     * Collects the result of a {@code TokenStream}, or of a stream of {@code ChatEvent}, from its final response.
     */
    void onFinalResponse(ChatResponse response) {
        if (includeResult && (response != null) && (response.aiMessage() != null)) {
            finalText = response.aiMessage().text();
        }
    }

    private String streamedResult() {
        String text = null;
        if (includeResult) {
            if (spanInfo.resultKind() == AiServiceMethodCreateInfo.SpanResultKind.TEXT_STREAM) {
                synchronized (streamedText) {
                    text = streamedText.toString();
                }
            } else if ((spanInfo.resultKind() == AiServiceMethodCreateInfo.SpanResultKind.EVENT_STREAM)
                    || (spanInfo.resultKind() == AiServiceMethodCreateInfo.SpanResultKind.TOKEN_STREAM)) {
                text = finalText;
            }
        }
        return text;
    }

    /**
     * @param recordedValue the value recorded in the result attribute, if any
     * @param contributedResult the result handed to the contributors, if any
     */
    private void end(Object recordedValue, Object contributedResult) {
        if (ended.compareAndSet(false, true)) {
            if (includeResult && (recordedValue != null)) {
                recordResult(recordedValue);
            }
            AiServiceSpanCompleteContext completeContext = AiServiceSpanCompleteContext.builder()
                    .startContext(startContext)
                    .result(contributedResult)
                    .build();
            for (AiServiceSpanContributor contributor : contributors) {
                try {
                    contributor.onComplete(completeContext, span);
                } catch (Exception e) {
                    recordContributorFailure(e);
                }
            }
            span.end();
        }
    }

    private void recordArguments(Object[] methodArgs) {
        try {
            var mapper = QuarkusJsonCodecFactory.ObjectMapperHolder.MAPPER;
            ObjectNode arguments = mapper.createObjectNode();
            for (AiServiceMethodCreateInfo.RecordedParameter parameter : spanInfo.recordedParameters()) {
                Object argument = ((methodArgs != null) && (parameter.position() < methodArgs.length))
                        ? methodArgs[parameter.position()]
                        : null;
                if (argument == null) {
                    arguments.putNull(parameter.name());
                } else {
                    try {
                        arguments.set(parameter.name(), mapper.valueToTree(argument));
                    } catch (Exception e) {
                        reportUnserializableArgument(parameter, argument, e);
                        arguments.set(parameter.name(), mapper.createObjectNode()
                                .put(AiServiceSpanAttributes.UNSERIALIZABLE_MARKER, argument.getClass().getName()));
                    }
                }
            }
            span.setAttribute(AiServiceSpanAttributes.ARGUMENTS,
                    QuarkusJsonCodecFactory.ObjectMapperHolder.COMPACT_WRITER.writeValueAsString(arguments));
        } catch (Exception e) {
            LOG.warnf(e, "Unable to record the arguments of %s on its span", methodKey());
        }
    }

    private void recordResult(Object value) {
        try {
            String recorded = (value instanceof String text)
                    ? text
                    : QuarkusJsonCodecFactory.ObjectMapperHolder.COMPACT_WRITER.writeValueAsString(value);
            span.setAttribute(AiServiceSpanAttributes.RESULT, recorded);
        } catch (Exception e) {
            LOG.warnf(e, "Unable to record the result of %s on its span", methodKey());
        }
    }

    private void reportUnserializableArgument(AiServiceMethodCreateInfo.RecordedParameter parameter, Object argument,
            Exception e) {
        String message = "Argument '%s' of %s (%s) cannot be serialized to JSON and is recorded on the span as %s";
        if (METHODS_WITH_UNSERIALIZABLE_ARGUMENTS.add(methodKey())) {
            LOG.warnf(e, message, parameter.name(), methodKey(), argument.getClass().getName(),
                    AiServiceSpanAttributes.UNSERIALIZABLE_MARKER);
        } else {
            LOG.debugf(e, message, parameter.name(), methodKey(), argument.getClass().getName(),
                    AiServiceSpanAttributes.UNSERIALIZABLE_MARKER);
        }
    }

    private void recordContributorFailure(Exception e) {
        span.recordException(e);
        LOG.warnf(e, "An AiServiceSpanContributor failed on the span of %s", methodKey());
    }

    private String methodKey() {
        return startContext.interfaceName() + "#" + startContext.methodName();
    }
}
