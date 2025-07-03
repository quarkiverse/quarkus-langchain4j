package io.quarkiverse.langchain4j.gemini.common;

import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.withLoggingExceptions;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.jboss.resteasy.reactive.client.SseEvent;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.internal.ExceptionMapper;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.smallrye.mutiny.Multi;

public abstract class GeminiStreamingChatLanguageModel extends BaseGeminiChatModel implements StreamingChatModel {

    public GeminiStreamingChatLanguageModel(String modelId, Double temperature, Integer maxOutputTokens, Integer topK,
            Double topP, ResponseFormat responseFormat, List<ChatModelListener> listeners) {
        super(modelId, temperature, maxOutputTokens, topK, topP, responseFormat, listeners);
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

    @Override
    public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler aHandler) {
        ChatRequestParameters requestParameters = chatRequest.parameters();
        ResponseFormat effectiveResponseFormat = getOrDefault(requestParameters.responseFormat(), responseFormat);
        GenerationConfig generationConfig = GenerationConfig.builder()
                .maxOutputTokens(getOrDefault(requestParameters.maxOutputTokens(), this.maxOutputTokens))
                .responseMimeType(computeMimeType(effectiveResponseFormat))
                .responseSchema(effectiveResponseFormat != null
                        ? SchemaMapper.fromJsonSchemaToSchema(effectiveResponseFormat.jsonSchema())
                        : null)
                .stopSequences(requestParameters.stopSequences())
                .temperature(getOrDefault(requestParameters.temperature(), this.temperature))
                .topK(getOrDefault(requestParameters.topK(), this.topK))
                .topP(getOrDefault(requestParameters.topP(), this.topP))
                .build();
        GenerateContentRequest request = ContentMapper.map(chatRequest.messages(), chatRequest.toolSpecifications(),
                generationConfig);

        ChatRequest modelListenerRequest = createModelListenerRequest(request, chatRequest.messages(),
                chatRequest.toolSpecifications());
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        ChatModelRequestContext requestContext = new ChatModelRequestContext(modelListenerRequest, ModelProvider.OTHER,
                attributes);
        listeners.forEach(listener -> {
            try {
                listener.onRequest(requestContext);
            } catch (Exception e) {
                log.warn("Exception while calling model listener", e);
            }
        });
        try {
            GeminiStreamingResponseBuilder responseBuilder = new GeminiStreamingResponseBuilder();
            Multi<SseEvent<GenerateContentResponse>> event = generateStreamContext(request);
            event.subscribe().with(
                    new OnItemConsumer(responseBuilder, aHandler),
                    new OnErrorConsumer(aHandler),
                    new OnCompleteRunnable(responseBuilder, aHandler));
        } catch (RuntimeException e) {
            ChatModelErrorContext errorContext = new ChatModelErrorContext(
                    e,
                    modelListenerRequest,
                    null,
                    attributes);

            listeners.forEach(listener -> {
                try {
                    listener.onError(errorContext);
                } catch (Exception e2) {
                    log.warn("Exception while calling model listener", e2);
                }
            });

            throw e;
        }
    }

    protected abstract Multi<SseEvent<GenerateContentResponse>> generateStreamContext(GenerateContentRequest request);

    private ChatRequest createModelListenerRequest(GenerateContentRequest request,
            List<ChatMessage> messages,
            List<ToolSpecification> toolSpecifications) {
        var builder = ChatRequest.builder()
                .messages(messages)
                .parameters(ChatRequestParameters.builder()
                        .modelName(modelId)
                        .toolSpecifications(toolSpecifications)
                        .temperature(temperature)
                        .topP(topP)
                        .maxOutputTokens(maxOutputTokens)
                        .build());
        return builder.build();
    }

    private String computeMimeType(ResponseFormat responseFormat) {
        if (responseFormat == null || ResponseFormatType.TEXT.equals(responseFormat.type())) {
            return "text/plain";
        }

        if (ResponseFormatType.JSON.equals(responseFormat.type())
                && responseFormat.jsonSchema() != null
                && responseFormat.jsonSchema().rootElement() != null
                && responseFormat.jsonSchema().rootElement() instanceof JsonEnumSchema) {
            return "text/x.enum";
        }

        return "application/json";
    }

    private static class OnCompleteRunnable implements Runnable {

        private final GeminiStreamingResponseBuilder responseBuilder;
        private final StreamingChatResponseHandler handler;

        public OnCompleteRunnable(GeminiStreamingResponseBuilder responseBuilder, StreamingChatResponseHandler handler) {
            this.responseBuilder = responseBuilder;
            this.handler = handler;
        }

        @Override
        public void run() {
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

    private static class OnItemConsumer implements Consumer<SseEvent<GenerateContentResponse>> {

        private final GeminiStreamingResponseBuilder responseBuilder;
        private final StreamingChatResponseHandler handler;

        public OnItemConsumer(GeminiStreamingResponseBuilder responseBuilder, StreamingChatResponseHandler handler) {
            this.responseBuilder = responseBuilder;
            this.handler = handler;
        }

        @Override
        public void accept(SseEvent<GenerateContentResponse> t) {
            GenerateContentResponse response = t.data();
            Optional<String> maybeText = responseBuilder.append(response);
            maybeText.ifPresent(text -> {
                try {
                    handler.onPartialResponse(text);
                } catch (Exception ex) {
                    withLoggingExceptions(() -> handler.onError(ex));
                }
            });
        }
    }
}
