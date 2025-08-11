package org.acme.example.openai;

import java.util.Collections;

import org.eclipse.microprofile.config.ConfigProvider;

import dev.langchain4j.model.openai.internal.chat.ChatCompletionRequest;
import dev.langchain4j.model.openai.internal.completion.CompletionRequest;
import dev.langchain4j.model.openai.internal.embedding.EmbeddingRequest;

public class MessageUtil {

    static String model;

    static {
        model = ConfigProvider.getConfig().getValue("quarkus.langchain4j.openai.chat-model.model-name", String.class);
    }

    public static CompletionRequest createCompletionRequest(String prompt) {
        return CompletionRequest.builder()
                .model(model)
                .logitBias(Collections.emptyMap())
                .maxTokens(100)
                .user("testing")
                .presencePenalty(0d)
                .frequencyPenalty(0d)
                .prompt(prompt)
                .build();
    }

    public static ChatCompletionRequest createChatCompletionRequest(String userMessage) {
        return ChatCompletionRequest.builder()
                .model(model)
                .logitBias(Collections.emptyMap())
                .maxTokens(100)
                .user("testing")
                .presencePenalty(0d)
                .frequencyPenalty(0d)
                .addUserMessage(userMessage).build();
    }

    public static EmbeddingRequest createEmbeddingRequest(String prompt) {
        return EmbeddingRequest.builder()
                .model("text-embedding-ada-002")
                .input(prompt)
                .build();
    }
}
