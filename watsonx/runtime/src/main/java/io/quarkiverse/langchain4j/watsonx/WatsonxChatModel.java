package io.quarkiverse.langchain4j.watsonx;

import static java.util.stream.Collectors.joining;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.TokenCountEstimator;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import io.quarkiverse.langchain4j.watsonx.bean.Parameters;
import io.quarkiverse.langchain4j.watsonx.bean.TextGenerationRequest;
import io.quarkiverse.langchain4j.watsonx.bean.TextGenerationResponse;
import io.quarkiverse.langchain4j.watsonx.bean.TextGenerationResponse.Result;
import io.quarkiverse.langchain4j.watsonx.bean.TokenizationRequest;

public class WatsonxChatModel extends WatsonxModel implements ChatLanguageModel, TokenCountEstimator {

    public WatsonxChatModel(WatsonxModel.Builder config) {
        super(config);
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {

        Parameters parameters = Parameters.builder()
                .decodingMethod(decodingMethod)
                .minNewTokens(minNewTokens)
                .maxNewTokens(maxNewTokens)
                .randomSeed(randomSeed)
                .stopSequences(stopSequences)
                .temperature(temperature)
                .topP(topP)
                .topK(topK)
                .repetitionPenalty(repetitionPenalty)
                .build();

        TextGenerationRequest request = new TextGenerationRequest(modelId, projectId, toInput(messages), parameters);

        Result result = retryOn(new Callable<TextGenerationResponse>() {

            @Override
            public TextGenerationResponse call() throws Exception {
                var token = generateBearerToken().await().atMost(Duration.ofSeconds(10));
                return client.chat(request, token, version);
            }
        }).results().get(0);

        var finishReason = toFinishReason(result.stopReason());
        var content = AiMessage.from(result.generatedText());
        var tokenUsage = new TokenUsage(
                result.inputTokenCount(),
                result.generatedTokenCount());

        return Response.from(content, tokenUsage, finishReason);
    }

    @Override
    public int estimateTokenCount(List<ChatMessage> messages) {
        var input = messages
                .stream()
                .map(ChatMessage::text)
                .collect(joining(" "));

        var request = new TokenizationRequest(modelId, input, projectId);

        return retryOn(new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                var token = generateBearerToken().await().atMost(Duration.ofSeconds(10));
                return client.tokenization(request, token, version).result().tokenCount();
            }
        });
    }
}
