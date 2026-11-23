package io.quarkiverse.langchain4j.gemini.common;

import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.withLoggingExceptions;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;

import java.util.*;
import java.util.function.Consumer;

import org.jboss.resteasy.reactive.client.SseEvent;

import dev.langchain4j.exception.ContentFilteredException;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.internal.ExceptionMapper;
import dev.langchain4j.internal.MappingTrackingStreamingChatResponseHandler;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.quarkiverse.langchain4j.QuarkusJsonCodecFactory;
import io.smallrye.mutiny.Multi;
import tools.jackson.core.JacksonException;

public abstract class GeminiStreamingChatLanguageModel extends BaseGeminiChatModel implements StreamingChatModel {

    public GeminiStreamingChatLanguageModel(String modelId, Double temperature, Integer maxOutputTokens, Integer topK,
            Double topP, ResponseFormat responseFormat, List<ChatModelListener> listeners, Long thinkingBudget,
            boolean includeThoughts, ThinkingLevel thinkingLevel, boolean useGoogleSearch) {
        super(modelId, temperature, maxOutputTokens, topK, topP, responseFormat, listeners, thinkingBudget,
                includeThoughts, thinkingLevel, useGoogleSearch);
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        Set<Capability> capabilities = new HashSet<>();
        // when response format is not null, it's JSON, either application/json or text/x.enum
        if (this.responseFormat != null && ResponseFormatType.JSON.equals(this.responseFormat.type())) {
            capabilities.add(RESPONSE_FORMAT_JSON_SCHEMA);
        } else if (responseFormat == null) { // when the responseFormat is not set, we default to supporting JSON as Google's models support this
            capabilities.add(RESPONSE_FORMAT_JSON_SCHEMA);
        }
        return capabilities;
    }

    /**
     * Listener notification is left to
     * {@link StreamingChatModel#chat(ChatRequest, dev.langchain4j.model.chat.ChatRequestOptions, StreamingChatResponseHandler)},
     * which wraps the handler passed here - that is what carries the current invocation id, the per-call listener
     * attributes and the globally registered observability listeners to the {@link ChatModelListener}s.
     * <p>
     * In langchain4j 1.24.0 that default method calls {@code onRequest} and then this method without guarding it, so a
     * failure thrown here synchronously - typically from mapping the request - would leave the listeners with an
     * {@code onRequest} and no terminal callback. It is therefore reported to the handler, whose {@code onError} is
     * what notifies the listeners, instead of being thrown.
     */
    @Override
    public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler aHandler) {
        // Wraps the user handler so that, for each SSE frame, we can tell whether it mapped to a typed callback
        // and, if not, forward it via onUnmappedRawEvent - mirroring langchain4j's own client.
        MappingTrackingStreamingChatResponseHandler trackingHandler = new MappingTrackingStreamingChatResponseHandler(
                aHandler);
        try {
            doChat(chatRequest, trackingHandler);
        } catch (RuntimeException e) {
            new OnErrorConsumer(trackingHandler).accept(e);
        }
    }

    private void doChat(ChatRequest chatRequest, MappingTrackingStreamingChatResponseHandler trackingHandler) {
        ChatRequestParameters requestParameters = chatRequest.parameters();
        ResponseFormat effectiveResponseFormat = getOrDefault(requestParameters.responseFormat(), responseFormat);
        Schema schema = detectSchema(effectiveResponseFormat);
        Map<String, Object> rawSchema = detectRawSchema(effectiveResponseFormat);

        GenerationConfig.Builder generationConfigBuilder = GenerationConfig.builder()
                .maxOutputTokens(getOrDefault(requestParameters.maxOutputTokens(), this.maxOutputTokens))
                .responseMimeType(computeMimeType(effectiveResponseFormat))
                .responseSchema(schema)
                .responseJsonSchema(rawSchema)
                .stopSequences(requestParameters.stopSequences())
                .temperature(getOrDefault(requestParameters.temperature(), this.temperature))
                .topK(getOrDefault(requestParameters.topK(), this.topK))
                .topP(getOrDefault(requestParameters.topP(), this.topP));
        applyThinkingConfig(generationConfigBuilder);
        GenerationConfig generationConfig = generationConfigBuilder.build();
        GenerateContentRequest request = ContentMapper.map(chatRequest.messages(), chatRequest.toolSpecifications(),
                requestParameters.toolChoice(),
                generationConfig, this.modelId, this.useGoogleSearch);

        GeminiStreamingResponseBuilder responseBuilder = new GeminiStreamingResponseBuilder();
        Multi<SseEvent<String>> event = generateStreamContext(request);
        event.subscribe().with(
                new OnItemConsumer(responseBuilder, trackingHandler),
                new OnErrorConsumer(trackingHandler),
                new OnCompleteRunnable(responseBuilder, trackingHandler));
    }

    protected abstract Multi<SseEvent<String>> generateStreamContext(GenerateContentRequest request);

    private static class OnCompleteRunnable implements Runnable {

        private final GeminiStreamingResponseBuilder responseBuilder;
        private final StreamingChatResponseHandler handler;

        public OnCompleteRunnable(GeminiStreamingResponseBuilder responseBuilder, StreamingChatResponseHandler handler) {
            this.responseBuilder = responseBuilder;
            this.handler = handler;
        }

        @Override
        public void run() {
            ContentFilteredException blocked = responseBuilder.blockedException();
            if (blocked != null) {
                new OnErrorConsumer(handler).accept(blocked);
                return;
            }
            ChatResponse chatResponse = responseBuilder.build();
            try {
                handler.onCompleteResponse(chatResponse);
            } catch (Exception e) {
                withLoggingExceptions(() -> handler.onError(e));
            }
        }
    }

    private static class OnErrorConsumer implements Consumer<Throwable> {

        private final StreamingChatResponseHandler handler;

        public OnErrorConsumer(StreamingChatResponseHandler handler) {
            this.handler = handler;
        }

        @Override
        public void accept(Throwable x) {
            RuntimeException mappedError = ExceptionMapper.DEFAULT.mapException(x);
            withLoggingExceptions(() -> handler.onError(mappedError));
        }
    }

    private static class OnItemConsumer implements Consumer<SseEvent<String>> {

        private final GeminiStreamingResponseBuilder responseBuilder;
        private final MappingTrackingStreamingChatResponseHandler handler;

        public OnItemConsumer(GeminiStreamingResponseBuilder responseBuilder,
                MappingTrackingStreamingChatResponseHandler handler) {
            this.responseBuilder = responseBuilder;
            this.handler = handler;
        }

        @Override
        public void accept(SseEvent<String> t) {
            try {
                handle(t);
            } catch (Exception e) {
                // Mutiny drops exceptions thrown from the item callback and cancels the stream without notifying the handler
                try {
                    handler.onError(e);
                } catch (Exception e2) {
                    log.warn("Exception while calling onError of the streaming response handler", e2);
                }
            }
        }

        private void handle(SseEvent<String> t) {
            handler.resetMappingTracking();

            ServerSentEvent rawEvent = new ServerSentEvent(t.name(), t.data());

            GenerateContentResponse response;
            try {
                response = QuarkusJsonCodecFactory.ObjectMapperHolder.MAPPER.readValue(t.data(),
                        GenerateContentResponse.class);
            } catch (JacksonException e) {
                withLoggingExceptions(() -> handler.onError(e));
                return;
            }

            Optional<String> maybeText = responseBuilder.append(response);
            maybeText.ifPresent(text -> {
                try {
                    handler.onPartialResponse(text);
                } catch (Exception ex) {
                    withLoggingExceptions(() -> handler.onError(ex));
                }
            });

            // A frame that did not map to a typed callback is surfaced as a raw event, matching langchain4j.
            if (!handler.wasMapped()) {
                handler.onUnmappedRawEvent(rawEvent);
            }
        }
    }
}
