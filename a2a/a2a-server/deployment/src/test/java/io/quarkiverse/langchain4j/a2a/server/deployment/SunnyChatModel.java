package io.quarkiverse.langchain4j.a2a.server.deployment;

import dev.langchain4j.model.chat.request.ChatRequest;

/**
 * For tests about what surrounds the agent rather than about what it answers.
 */
public class SunnyChatModel extends FakeChatModel {

    @Override
    protected String answer(ChatRequest request) {
        return "It is sunny.";
    }
}
