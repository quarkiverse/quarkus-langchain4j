package org.acme.example.openai;

import java.util.Collections;

import dev.langchain4j.model.openai.internal.chat.ChatCompletionRequest;
import dev.langchain4j.model.openai.internal.completion.CompletionRequest;
import dev.langchain4j.model.openai.internal.embedding.EmbeddingRequest;

public class MessageUtil {

    public static CompletionRequest createCompletionRequest(String prompt) {
        return CompletionRequest.builder()
                .model("gpt-3.5-turbo")
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
                .model("gpt-3.5-turbo")
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
