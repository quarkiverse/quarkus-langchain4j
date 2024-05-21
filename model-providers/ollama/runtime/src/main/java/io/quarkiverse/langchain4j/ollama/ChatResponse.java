package io.quarkiverse.langchain4j.ollama;

public record ChatResponse(String model, String createdAt, Message message, Boolean done, Integer promptEvalCount,
        Integer evalCount) {

    public static ChatResponse emptyNotDone() {
        return new ChatResponse(null, null, new Message(Role.ASSISTANT, "", null), true, null, null);
    }
}
