package io.quarkiverse.langchain4j.ollama;

import java.util.Collections;

public record ChatResponse(String model, String createdAt, Message message, Boolean done, Integer promptEvalCount,
        Integer evalCount) {

    public static ChatResponse emptyNotDone() {
        return new ChatResponse(null, null, new Message(Role.ASSISTANT, "", Collections.emptyList(), Collections.emptyList()),
                true, null, null);
    }
}
