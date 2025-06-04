package io.quarkiverse.langchain4j.azure.openai;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.openai.internal.OpenAiUtils.toFunctions;
import static dev.langchain4j.model.openai.internal.OpenAiUtils.toOpenAiMessages;
import static io.quarkiverse.langchain4j.azure.openai.Consts.DEFAULT_USER_AGENT;
import static java.time.Duration.ofSeconds;

import java.net.Proxy;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.jboss.logging.Logger;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiStreamingResponseBuilder;
import dev.langchain4j.model.openai.internal.OpenAiClient;
import dev.langchain4j.model.openai.internal.chat.ChatCompletionChoice;
import dev.langchain4j.model.openai.internal.chat.ChatCompletionRequest;
import dev.langchain4j.model.openai.internal.chat.ChatCompletionResponse;
import dev.langchain4j.model.openai.internal.chat.Delta;
import dev.langchain4j.model.openai.internal.chat.ResponseFormat;
import dev.langchain4j.model.openai.internal.chat.ResponseFormatType;
import io.quarkiverse.langchain4j.openai.common.QuarkusOpenAiClient;

/**
 * Represents an OpenAI language model, hosted on Azure, that has a chat completion interface, such as gpt-3.5-turbo.
 * The model's response is streamed token by token and should be handled with {@link StreamingResponseHandler}.
 * <p>
 * Mandatory parameters for initialization are: {@code apiVersion}, {@code apiKey}, and either {@code endpoint} OR
 * {@code resourceName} and {@code deploymentName}.
 * <p>
 * There are two primary authentication methods to access Azure OpenAI:
 * <p>
 * 1. API Key Authentication: For this type of authentication, HTTP requests must include the
 * API Key in the "api-key" HTTP header.
 * <p>
 * 2. Azure Active Directory Authentication: For this type of authentication, HTTP requests must include the
 * authentication/access token in the "Authorization" HTTP header.
 * <p>
 * <a href="https://learn.microsoft.com/en-us/azure/ai-services/openai/reference">More information</a>
 * <p>
 * Please note, that currently, only API Key authentication is supported by this class,
 * second authentication option will be supported later.
 */
public class AzureOpenAiStreamingChatModel implements StreamingChatModel {

    private static final Logger log = Logger.getLogger(AzureOpenAiStreamingChatModel.class);

    private final OpenAiClient client;
    private final Double temperature;
    private final Double topP;
    private final Integer maxTokens;
    private final Double presencePenalty;
    private final Double frequencyPenalty;
    private final TokenCountEstimator tokenizer;
    private final ResponseFormat responseFormat;
    private final List<ChatModelListener> listeners;

    public AzureOpenAiStreamingChatModel(String endpoint,
            String apiVersion,
            String apiKey,
            String adToken,
            TokenCountEstimator tokenizer,
            Double temperature,
            Double topP,
            Integer maxTokens,
            Double presencePenalty,
            Double frequencyPenalty,
            Duration timeout,
            Proxy proxy,
            String responseFormat,
            Boolean logRequests,
            Boolean logResponses,
            String configName,
            List<ChatModelListener> listeners) {
        this.listeners = listeners;
        timeout = getOrDefault(timeout, ofSeconds(60));

        this.client = QuarkusOpenAiClient.builder()
                .baseUrl(ensureNotBlank(endpoint, "endpoint"))
                .apiVersion(apiVersion)
                .callTimeout(timeout)
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .writeTimeout(timeout)
                .proxy(proxy)
                .logRequests(logRequests)
                .logStreamingResponses(logResponses)
                .userAgent(DEFAULT_USER_AGENT)
                .azureAdToken(adToken)
                .azureApiKey(apiKey)
                .configName(configName)
                .build();
        this.temperature = getOrDefault(temperature, 0.7);
        this.topP = topP;
        this.maxTokens = maxTokens;
        this.presencePenalty = presencePenalty;
        this.frequencyPenalty = frequencyPenalty;
        this.tokenizer = tokenizer;
        this.responseFormat = responseFormat == null ? null
                : ResponseFormat.builder()
                        .type(ResponseFormatType.valueOf(responseFormat.toUpperCase(Locale.ROOT)))
                        .build();
    }

    @Override
    public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {

        List<ChatMessage> messages = chatRequest.messages();
        List<ToolSpecification> toolSpecifications = chatRequest.toolSpecifications();

        ChatCompletionRequest.Builder requestBuilder = ChatCompletionRequest.builder()
                .stream(true)
                .messages(toOpenAiMessages(messages))
                .temperature(temperature)
                .topP(topP)
                .maxTokens(maxTokens)
                .presencePenalty(presencePenalty)
                .frequencyPenalty(frequencyPenalty)
                .responseFormat(responseFormat);

        Integer inputTokenCount = tokenizer == null ? null : tokenizer.estimateTokenCountInMessages(messages);

        if (!isNullOrEmpty(toolSpecifications)) {
            requestBuilder.functions(toFunctions(toolSpecifications));
        }

        ChatCompletionRequest request = requestBuilder.build();

        ChatRequest modelListenerRequest = createModelListenerRequest(request, messages, toolSpecifications);
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

        OpenAiStreamingResponseBuilder responseBuilder = new OpenAiStreamingResponseBuilder();

        AtomicReference<String> responseId = new AtomicReference<>();
        AtomicReference<String> responseModel = new AtomicReference<>();

        client.chatCompletion(request)
                .onPartialResponse(partialResponse -> {
                    responseBuilder.append(partialResponse);
                    handle(partialResponse, handler);
                    if (!isNullOrBlank(partialResponse.id())) {
                        responseId.set(partialResponse.id());
                    }
                    if (!isNullOrBlank(partialResponse.model())) {
                        responseModel.set(partialResponse.model());
                    }
                })
                .onComplete(() -> {
                    ChatResponse response = responseBuilder.build();

                    ChatResponse modelListenerResponse = createModelListenerResponse(
                            responseId.get(),
                            responseModel.get(),
                            response);
                    ChatModelResponseContext responseContext = new ChatModelResponseContext(
                            modelListenerResponse,
                            modelListenerRequest,
                            ModelProvider.OTHER,
                            attributes);
                    listeners.forEach(listener -> {
                        try {
                            listener.onResponse(responseContext);
                        } catch (Exception e) {
                            log.warn("Exception while calling model listener", e);
                        }
                    });

                    handler.onCompleteResponse(response);
                })
                .onError((error) -> {
                    ChatResponse response = responseBuilder.build();

                    ChatResponse modelListenerPartialResponse = createModelListenerResponse(
                            responseId.get(),
                            responseModel.get(),
                            response);

                    ChatModelErrorContext errorContext = new ChatModelErrorContext(
                            error,
                            modelListenerRequest,
                            ModelProvider.OTHER,
                            attributes);

                    listeners.forEach(listener -> {
                        try {
                            listener.onError(errorContext);
                        } catch (Exception e) {
                            log.warn("Exception while calling model listener", e);
                        }
                    });

                    handler.onError(error);
                })
                .execute();
    }

    private static void handle(ChatCompletionResponse partialResponse,
            StreamingChatResponseHandler handler) {
        List<ChatCompletionChoice> choices = partialResponse.choices();
        if (choices == null || choices.isEmpty()) {
            return;
        }
        Delta delta = choices.get(0).delta();
        String content = delta.content();
        if (content != null) {
            handler.onPartialResponse(content);
        }
    }

    private ChatRequest createModelListenerRequest(ChatCompletionRequest request,
            List<ChatMessage> messages,
            List<ToolSpecification> toolSpecifications) {
        return ChatRequest.builder()
                .messages(messages)
                .parameters(ChatRequestParameters.builder()
                        .modelName(request.model())
                        .temperature(request.temperature())
                        .topP(request.topP())
                        .maxOutputTokens(request.maxTokens())
                        .toolSpecifications(toolSpecifications)
                        .build())
                .build();
    }

    private ChatResponse createModelListenerResponse(String responseId,
            String responseModel,
            ChatResponse response) {
        if (response == null) {
            return null;
        }

        return ChatResponse.builder()
                .aiMessage(response.aiMessage())
                .finishReason(response.finishReason())
                .metadata(ChatResponseMetadata.builder().id(responseId).modelName(responseModel)
                        .tokenUsage(response.tokenUsage()).build())
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String endpoint;
        private String apiVersion;
        private String apiKey;
        private String adToken;
        private TokenCountEstimator tokenizer;
        private Double temperature;
        private Double topP;
        private Integer maxTokens;
        private Double presencePenalty;
        private Double frequencyPenalty;
        private Duration timeout;
        private Proxy proxy;
        private String responseFormat;
        private Boolean logRequests;
        private Boolean logResponses;
        private String configName;
        private List<ChatModelListener> listeners = Collections.emptyList();

        /**
         * Sets the Azure OpenAI endpoint. This is a mandatory parameter.
         *
         * @param endpoint The Azure OpenAI endpoint in the format:
         *        https://{resource-name}.openai.azure.com/openai/deployments/{deployment-name}
         * @return builder
         */
        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        /**
         * Sets the Azure OpenAI API version. This is a mandatory parameter.
         *
         * @param apiVersion The Azure OpenAI api version in the format: 2023-05-15
         * @return builder
         */
        public Builder apiVersion(String apiVersion) {
            this.apiVersion = apiVersion;
            return this;
        }

        /**
         * Sets the Azure OpenAI API key. This is a mandatory parameter.
         *
         * @param apiKey The Azure OpenAI API key.
         * @return builder
         */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder adToken(String adToken) {
            this.adToken = adToken;
            return this;
        }

        public Builder tokenizer(TokenCountEstimator tokenizer) {
            this.tokenizer = tokenizer;
            return this;
        }

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder presencePenalty(Double presencePenalty) {
            this.presencePenalty = presencePenalty;
            return this;
        }

        public Builder frequencyPenalty(Double frequencyPenalty) {
            this.frequencyPenalty = frequencyPenalty;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder proxy(Proxy proxy) {
            this.proxy = proxy;
            return this;
        }

        public Builder responseFormat(String responseFormat) {
            this.responseFormat = responseFormat;
            return this;
        }

        public Builder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public Builder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public Builder configName(String configName) {
            this.configName = configName;
            return this;
        }

        public Builder listeners(List<ChatModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        public AzureOpenAiStreamingChatModel build() {
            return new AzureOpenAiStreamingChatModel(endpoint,
                    apiVersion,
                    apiKey,
                    adToken,
                    tokenizer,
                    temperature,
                    topP,
                    maxTokens,
                    presencePenalty,
                    frequencyPenalty,
                    timeout,
                    proxy,
                    responseFormat,
                    logRequests,
                    logResponses,
                    configName,
                    listeners);
        }
    }
}
