package io.quarkiverse.langchain4j.samples.compression;

import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import io.quarkus.logging.Log;

@ApplicationScoped
public class CompressingChatMemoryStore implements ChatMemoryStore {

    /**
     * The delegate store that will hold the chat messages.
     * This could be a database-backed store, but for simplicity,
     * we are using an in-memory store here.
     */
    private final ChatMemoryStore delegate;

    /**
     * The chat model used for summarization.
     */
    private final ChatModel chatModel;

    /**
     * The threshold for the number of messages before compression is triggered.
     */
    private final int threshold;

    /**
     * The prefix used to identify the summary in the system message.
     * This is used to ensure that we can extract and update the summary correctly.
     */
    private static final String SUMMARY_PREFIX = "Context: The following is a summary of the previous conversation:";

    public CompressingChatMemoryStore(ChatModel model, // We use the default chat model, but you can select any chat model
            @ConfigProperty(name = "semantic-compression-threshold", defaultValue = "5") int threshold) {
        this.delegate = new InMemoryChatMemoryStore();
        this.chatModel = model;
        this.threshold = threshold;
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        // Extract the last message if any, as we do not want to compress during function calls
        if (messages.isEmpty()) {
            Log.warnf("No messages to compress for memory ID: %s", memoryId);
            return;
        }
        ChatMessage lastMessage = messages.get(messages.size() - 1);
        if (lastMessage.type() == ChatMessageType.AI && ((AiMessage) lastMessage).hasToolExecutionRequests()) {
            Log.infof("Skipping compression for memory ID: %s due to function call in the last message", memoryId);
            delegate.updateMessages(memoryId, messages);
            return;
        }
        // Also skip compression if the last message is a system message or a function call response
        if (lastMessage.type() == ChatMessageType.SYSTEM || lastMessage.type() == ChatMessageType.TOOL_EXECUTION_RESULT) {
            Log.infof(
                    "Skipping compression for memory ID: %s due to system message or function call response in the last message",
                    memoryId);
            delegate.updateMessages(memoryId, messages);
            return;
        }

        // If the number of messages exceeds the threshold, compress them
        if (messages.size() > threshold) {
            Log.infof("Triggering semantic compression for memory ID: %s with %d messages", memoryId, messages.size());
            List<ChatMessage> compressed = new ArrayList<>();

            // Retain the first system message if present
            SystemMessage systemMsg = (SystemMessage) messages.stream()
                    .filter(m -> m.type() == ChatMessageType.SYSTEM)
                    .findFirst().orElse(null);

            // Collect messages since last compression and extract any existing summary
            for (ChatMessage msg : messages) {
                if (msg.type() == ChatMessageType.SYSTEM) {
                    // We found a system message, we need to check if it contains a previous summary
                    extractSummaryFromSystemMessageIfAny((SystemMessage) msg, compressed);
                } else {
                    compressed.add(msg);
                }
            }
            // compressed now contains a "fake" system message with the previous summary if it existed, and all other messages.

            // Build compression prompt
            StringBuilder sb = new StringBuilder(
                    "Summarize the following dialogue into a brief summary, preserving context and tone:\n\n");
            for (ChatMessage msg : messages) {
                switch (msg.type()) {
                    case SYSTEM ->
                        // This is the previous summary
                        sb.append("Context: ").append(((SystemMessage) msg).text()).append("\n");
                    case USER -> sb.append("User: ").append(((UserMessage) msg).singleText()).append("\n");
                    case AI -> sb.append("Assistant: ").append(((AiMessage) msg).text()).append("\n");
                    default -> {
                        // Ignore other message types for compression
                    }
                }
            }
            String summary = chatModel.chat(sb.toString());
            systemMsg = appendSummaryToSystemMessage(systemMsg, summary);
            Log.infof("Generated system message with summary: %s", systemMsg.text());
            delegate.updateMessages(memoryId, List.of(systemMsg));
        } else {
            delegate.updateMessages(memoryId, messages);
        }
    }

    private SystemMessage appendSummaryToSystemMessage(SystemMessage systemMsg, String summary) {
        if (systemMsg == null) {
            // If no system message exists, create a new one with the summary
            return SystemMessage.systemMessage(SUMMARY_PREFIX + "\n" + summary);
        }
        // Check if the system message already contains a summary
        String content = systemMsg.text();
        if (content.contains(SUMMARY_PREFIX)) {
            // Replace the existing summary with the new one
            int startIndex = content.indexOf(SUMMARY_PREFIX) + SUMMARY_PREFIX.length();
            String newContent = content.substring(0, startIndex) + "\n\n";
            newContent = newContent + "\n\n" + SUMMARY_PREFIX + "\n" + summary;
            return SystemMessage.systemMessage(newContent);
        } else {
            // If no summary exists, append the new summary
            String newContent = content + "\n\n" + SUMMARY_PREFIX + "\n" + summary;
            return SystemMessage.systemMessage(newContent);
        }
    }

    private void extractSummaryFromSystemMessageIfAny(SystemMessage systemMsg, List<ChatMessage> compressed) {
        String content = systemMsg.text();
        if (content.contains(SUMMARY_PREFIX)) {
            // Extract the summary part
            int startIndex = content.indexOf(SUMMARY_PREFIX) + SUMMARY_PREFIX.length();
            String summary = content.substring(startIndex).trim();
            // Add the sanitized summary to the compressed messages
            compressed.add(SystemMessage.systemMessage(sanitize(summary)));
        }
        // Otherwise, do nothing, as we don't want to include the system message in the compressed messages.
    }

    private String sanitize(String text) {
        // Remove the previous summary if it exists
        int index = text.indexOf("Context: The following is a summary of the previous conversation:");
        if (index != -1) {
            return text.substring(0, index).trim();
        }
        return text.trim();
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        return delegate.getMessages(memoryId);
    }

    @Override
    public void deleteMessages(Object memoryId) {
        delegate.deleteMessages(memoryId);
    }
}
