package io.quarkiverse.langchain4j.bam;

import static java.util.stream.Collectors.joining;

import java.util.List;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.TokenCountEstimator;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;

public class BamChatModel extends BamModel implements ChatLanguageModel, TokenCountEstimator {

    public BamChatModel(BamModel.Builder config) {
        super(config);
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {

        Parameters parameters = Parameters.builder()
                .decodingMethod(decodingMethod)
                .includeStopSequence(includeStopSequence)
                .minNewTokens(minNewTokens)
                .maxNewTokens(maxNewTokens)
                .randomSeed(randomSeed)
                .stopSequences(stopSequences)
                .temperature(temperature)
                .timeLimit(timeLimit)
                .topP(topP)
                .topK(topK)
                .typicalP(typicalP)
                .repetitionPenalty(repetitionPenalty)
                .truncateInputTokens(truncateInputTokens)
                .beamWidth(beamWidth)
                .build();

        TextGenerationRequest request = new TextGenerationRequest(modelId, toInput(messages), parameters);

        // The response will be always one.
        TextGenerationResponse.Results result = client.chat(request, token, version).results().get(0);

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

        var request = new TokenizationRequest(modelId, input);
        return client.tokenization(request, token, version).results().get(0).tokenCount();
    }
}
