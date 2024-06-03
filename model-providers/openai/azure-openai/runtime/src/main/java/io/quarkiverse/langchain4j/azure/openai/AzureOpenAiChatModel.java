package io.quarkiverse.langchain4j.azure.openai;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.*;
import static java.time.Duration.ofSeconds;
import static java.util.Collections.singletonList;

import java.net.Proxy;
import java.time.Duration;
import java.util.List;

import dev.ai4j.openai4j.OpenAiClient;
import dev.ai4j.openai4j.chat.ChatCompletionRequest;
import dev.ai4j.openai4j.chat.ChatCompletionResponse;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.TokenCountEstimator;
import dev.langchain4j.model.output.Response;
import io.quarkiverse.langchain4j.openai.QuarkusOpenAiClient;

/**
 * Represents an OpenAI language model, hosted on Azure, that has a chat completion interface, such as gpt-3.5-turbo.
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
public class AzureOpenAiChatModel implements ChatLanguageModel, TokenCountEstimator {

    private final OpenAiClient client;
    private final Double temperature;
    private final Double topP;
    private final Integer maxTokens;
    private final Double presencePenalty;
    private final Double frequencyPenalty;
    private final Integer maxRetries;
    private final Tokenizer tokenizer;
    private final String responseFormat;

    public AzureOpenAiChatModel(String endpoint,
            String apiVersion,
            String apiKey,
            String adToken,
            Tokenizer tokenizer,
            Double temperature,
            Double topP,
            Integer maxTokens,
            Double presencePenalty,
            Double frequencyPenalty,
            Duration timeout,
            Integer maxRetries,
            Proxy proxy,
            String responseFormat,
            Boolean logRequests,
            Boolean logResponses,
            String configName) {

        timeout = getOrDefault(timeout, ofSeconds(60));

        this.client = ((QuarkusOpenAiClient.Builder) OpenAiClient.builder()
                .baseUrl(ensureNotBlank(endpoint, "endpoint"))
                .apiVersion(apiVersion)
                .callTimeout(timeout)
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .writeTimeout(timeout)
                .proxy(proxy)
                .logRequests(logRequests)
                .logResponses(logResponses))
                .userAgent(Consts.DEFAULT_USER_AGENT)
                .azureAdToken(adToken)
                .azureApiKey(apiKey)
                .configName(configName)
                .build();

        this.temperature = getOrDefault(temperature, 0.7);
        this.topP = topP;
        this.maxTokens = maxTokens;
        this.presencePenalty = presencePenalty;
        this.frequencyPenalty = frequencyPenalty;
        this.maxRetries = getOrDefault(maxRetries, 1);
        if (this.maxRetries < 1) {
            throw new IllegalArgumentException("max-retries must be at least 1");
        }
        this.tokenizer = tokenizer;
        this.responseFormat = responseFormat;
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        return generate(messages, null, null);
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
        return generate(messages, toolSpecifications, null);
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, ToolSpecification toolSpecification) {
        return generate(messages, singletonList(toolSpecification), toolSpecification);
    }

    private Response<AiMessage> generate(List<ChatMessage> messages,
            List<ToolSpecification> toolSpecifications,
            ToolSpecification toolThatMustBeExecuted) {
        ChatCompletionRequest.Builder requestBuilder = ChatCompletionRequest.builder()
                .messages(toOpenAiMessages(messages))
                .temperature(temperature)
                .topP(topP)
                .maxTokens(maxTokens)
                .presencePenalty(presencePenalty)
                .frequencyPenalty(frequencyPenalty)
                .responseFormat(responseFormat);

        if (toolSpecifications != null && !toolSpecifications.isEmpty()) {
            requestBuilder.functions(toFunctions(toolSpecifications));
        }
        if (toolThatMustBeExecuted != null) {
            requestBuilder.functionCall(toolThatMustBeExecuted.name());
        }

        ChatCompletionRequest request = requestBuilder.build();

        ChatCompletionResponse response = withRetry(() -> client.chatCompletion(request).execute(), maxRetries);

        return Response.from(
                aiMessageFrom(response),
                tokenUsageFrom(response.usage()),
                finishReasonFrom(response.choices().get(0).finishReason()));
    }

    @Override
    public int estimateTokenCount(List<ChatMessage> messages) {
        return tokenizer.estimateTokenCountInMessages(messages);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String endpoint;
        private String apiVersion;
        private String apiKey;
        private String adToken;
        private Tokenizer tokenizer;
        private Double temperature;
        private Double topP;
        private Integer maxTokens;
        private Double presencePenalty;
        private Double frequencyPenalty;
        private Duration timeout;
        private Integer maxRetries;
        private Proxy proxy;
        private String responseFormat;
        private Boolean logRequests;
        private Boolean logResponses;
        private String configName;

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

        public Builder configName(String configName) {
            this.configName = configName;
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

        public Builder tokenizer(Tokenizer tokenizer) {
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

        public Builder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
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

        public AzureOpenAiChatModel build() {
            return new AzureOpenAiChatModel(endpoint,
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
                    maxRetries,
                    proxy,
                    responseFormat,
                    logRequests,
                    logResponses,
                    configName);
        }
    }
}
