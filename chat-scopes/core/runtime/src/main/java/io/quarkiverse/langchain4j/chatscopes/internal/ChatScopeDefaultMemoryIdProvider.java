package io.quarkiverse.langchain4j.chatscopes.internal;

import io.quarkiverse.langchain4j.chatscopes.ChatScope;
import io.quarkiverse.langchain4j.spi.DefaultMemoryIdProvider;

public class ChatScopeDefaultMemoryIdProvider implements DefaultMemoryIdProvider {
    // Quarkus Langchain4j uses default memory provider as follows:
    //  defaultId + "#" + interfaceName + "." + methodName ;

    @Override
    public Object getMemoryId() {
        if (!ChatScope.isActive()) {
            return null;
        }
        return ChatScope.id();
    }
}
