package io.quarkiverse.langchain4j.it;

import java.util.Collections;

import dev.ai4j.openai4j.chat.ChatCompletionRequest;

public class MessageUtil {

    public static ChatCompletionRequest createRequest(String userMessage) {
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
