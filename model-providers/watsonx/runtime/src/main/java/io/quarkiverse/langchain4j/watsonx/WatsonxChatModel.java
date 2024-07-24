package io.quarkiverse.langchain4j.watsonx;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.TokenCountEstimator;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import io.quarkiverse.langchain4j.watsonx.bean.Parameters;
import io.quarkiverse.langchain4j.watsonx.bean.Parameters.LengthPenalty;
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

        LengthPenalty lengthPenalty = null;
        if (Objects.nonNull(decayFactor) || Objects.nonNull(startIndex)) {
            lengthPenalty = new LengthPenalty(decayFactor, startIndex);
        }

        Parameters parameters = Parameters.builder()
                .decodingMethod(decodingMethod)
                .lengthPenalty(lengthPenalty)
                .minNewTokens(minNewTokens)
                .maxNewTokens(maxNewTokens)
                .randomSeed(randomSeed)
                .stopSequences(stopSequences)
                .temperature(temperature)
                .topP(topP)
                .topK(topK)
                .repetitionPenalty(repetitionPenalty)
                .truncateInputTokens(truncateInputTokens)
                .includeStopSequence(includeStopSequence)
                .build();

        TextGenerationRequest request = new TextGenerationRequest(modelId, projectId, toInput(messages), parameters);

        Result result = retryOn(new Callable<TextGenerationResponse>() {
            @Override
            public TextGenerationResponse call() throws Exception {
                return client.chat(request, version);
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

        var input = toInput(messages);
        var request = new TokenizationRequest(modelId, input, projectId);

        return retryOn(new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                return client.tokenization(request, version).result().tokenCount();
            }
        });
    }
}
