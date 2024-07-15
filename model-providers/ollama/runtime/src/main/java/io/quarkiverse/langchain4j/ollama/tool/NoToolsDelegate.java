package io.quarkiverse.langchain4j.ollama.tool;

import static io.quarkiverse.langchain4j.ollama.OllamaMessagesUtils.toOllamaMessages;

import java.util.List;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import io.quarkiverse.langchain4j.ollama.ChatRequest;
import io.quarkiverse.langchain4j.ollama.ChatResponse;
import io.quarkiverse.langchain4j.ollama.OllamaClient;
import io.quarkiverse.langchain4j.ollama.Options;

public class NoToolsDelegate implements ChatLanguageModel {

    private final OllamaClient client;
    private final String modelName;
    private final Options options;
    private final String format;

    public NoToolsDelegate(OllamaClient client, String modelName, Options options, String format) {
        this.client = client;
        this.modelName = modelName;
        this.options = options;
        this.format = format;
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        ChatRequest request = ChatRequest.builder()
                .model(modelName)
                .options(options)
                .format(format)
                .stream(false)
                .messages(toOllamaMessages(messages))
                .build();

        ChatResponse response = client.chat(request);

        return Response.from(
                AiMessage.from(response.message().content()),
                new TokenUsage(response.promptEvalCount(), response.evalCount()));
    }
}
