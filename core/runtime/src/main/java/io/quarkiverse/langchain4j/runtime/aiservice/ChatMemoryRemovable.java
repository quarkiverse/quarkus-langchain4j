package io.quarkiverse.langchain4j.runtime.aiservice;

import java.util.Collection;

/**
 * Interface implemented by each AiService that allows the removal of chat memories from an AiService
 */
public interface ChatMemoryRemovable {

    void remove(Object... ids);

    void removeAll();

    Collection<Object> getAllChatMemoryIds();
}
