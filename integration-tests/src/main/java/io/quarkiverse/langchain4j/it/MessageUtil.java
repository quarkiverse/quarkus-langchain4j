package io.quarkiverse.langchain4j.it;

import java.util.Collections;

import dev.ai4j.openai4j.chat.ChatCompletionRequest;
import dev.ai4j.openai4j.completion.CompletionRequest;

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
}
