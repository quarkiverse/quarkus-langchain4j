package io.quarkiverse.langchain4j.vertexai.runtime;

import java.util.List;

public record PredictRequest(List<ChatInstance> instances, Parameters parameters) {

    public record ChatInstance(String context, List<Message> messages) {

    }

    public record Message(String author, String content) {

    }

}
