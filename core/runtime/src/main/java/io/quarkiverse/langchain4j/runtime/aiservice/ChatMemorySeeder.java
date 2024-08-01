package io.quarkiverse.langchain4j.runtime.aiservice;

import java.util.List;

import dev.langchain4j.data.message.ChatMessage;

/**
 * Provides a way for an AiService to get its chat memory seeded.
 * This is useful for creating few-shot prompts instead of hard coding the examples in the prompt itself.
 * <p>
 * Two important points must be noted about this:
 * <ul>
 * <li>There must be an even number of messages that alternate between UserMessage and AiMessage</li>
 * <li>Implementations only get invoked if the chat memory for the specified memory id either doesn't exist or is empty</li>
 * <li>Whatever messages are created by the seed, do end up getting added to the chat memory (if it exists)</li>
 * </ul>
 */
public interface ChatMemorySeeder {

    List<ChatMessage> seed(Context context);

    record Context(String methodName) {

    }
}
