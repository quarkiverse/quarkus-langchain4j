package io.quarkiverse.langchain4j.chatscopes.passivation;

public interface ChatScopeSerializer {
    boolean isHandled(Object instance);

    String serialize(Object instance);

    Object deserialize(String json, Object instance);
}
