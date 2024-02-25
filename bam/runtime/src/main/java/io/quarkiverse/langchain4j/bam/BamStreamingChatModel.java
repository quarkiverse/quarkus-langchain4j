package io.quarkiverse.langchain4j.bam;

import static java.util.stream.Collectors.joining;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.TokenCountEstimator;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import io.quarkiverse.langchain4j.QuarkusJsonCodecFactory;
import io.quarkiverse.langchain4j.bam.TextGenerationResponse.Results;
import io.smallrye.mutiny.Context;

public class BamStreamingChatModel extends BamModel implements StreamingChatLanguageModel, TokenCountEstimator {

    private final ObjectMapper mapper = QuarkusJsonCodecFactory.SnakeCaseObjectMapperHolder.MAPPER;

    public BamStreamingChatModel(Builder config) {
        super(config);
    }

    @Override
    public void generate(List<ChatMessage> messages, StreamingResponseHandler<AiMessage> handler) {

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
        Context context = Context.of("response", new ArrayList<TextGenerationResponse>());

        client.chatStreaming(request, token, version)
                .subscribe()
                .with(context,
                        new Consumer<String>() {
                            @Override
                            @SuppressWarnings("unchecked")
                            public void accept(String response) {
                                try {

                                    // Skip empty responses from LLM.
                                    if (response == null || response.isBlank())
                                        return;

                                    var obj = mapper.readValue(response, TextGenerationResponse.class);
                                    ((List<TextGenerationResponse>) context.get("response")).add(obj);
                                    handler.onNext(obj.results().get(0).generatedText());

                                } catch (Exception e) {
                                    handler.onError(e);
                                }
                            }
                        },
                        new Consumer<Throwable>() {
                            @Override
                            public void accept(Throwable error) {
                                handler.onError(error);
                            }
                        },
                        new Runnable() {
                            @Override
                            @SuppressWarnings("unchecked")
                            public void run() {
                                var list = ((List<TextGenerationResponse>) context.get("response"));

                                int inputTokenCount = 0;
                                int outputTokenCount = 0;
                                String stopReason = null;
                                StringBuilder builder = new StringBuilder();

                                for (int i = 0; i < list.size(); i++) {

                                    Results response = list.get(i).results().get(0);

                                    if (i == 0)
                                        inputTokenCount = response.inputTokenCount();

                                    if (i == list.size() - 1) {
                                        outputTokenCount = response.generatedTokenCount();
                                        stopReason = response.stopReason();
                                    }

                                    builder.append(response.generatedText());
                                }

                                AiMessage message = new AiMessage(builder.toString());
                                TokenUsage tokenUsage = new TokenUsage(inputTokenCount, outputTokenCount);
                                FinishReason finishReason = toFinishReason(stopReason);
                                handler.onComplete(Response.from(message, tokenUsage, finishReason));
                            }
                        });
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
