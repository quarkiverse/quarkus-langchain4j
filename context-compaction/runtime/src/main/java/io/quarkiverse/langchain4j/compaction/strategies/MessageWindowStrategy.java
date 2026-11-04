package io.quarkiverse.langchain4j.compaction.strategies;

import java.util.ArrayList;
import java.util.List;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import dev.langchain4j.data.message.ChatMessage;
import io.quarkiverse.langchain4j.compaction.CompactionContext;
import io.quarkiverse.langchain4j.compaction.CompactionStrategy;
import io.quarkiverse.langchain4j.compaction.Conversation;
import io.quarkiverse.langchain4j.compaction.runtime.CompactionConfig;
import io.smallrye.common.annotation.Identifier;

/**
 * Keeps the most recent messages, up to a maximum count. Unlike LangChain4j's {@code MessageWindowChatMemory}, the
 * window is aligned on segments, so a tool result is never kept without the request that produced it.
 */
@Singleton
@Identifier(Strategies.MESSAGE_WINDOW)
public class MessageWindowStrategy implements CompactionStrategy {

    private final int maxMessages;

    @Inject
    public MessageWindowStrategy(CompactionConfig config) {
        this(config.defaultProfile());
    }

    public MessageWindowStrategy(CompactionConfig.Profile profile) {
        this(profile.messageWindow().maxMessages());
    }

    public MessageWindowStrategy(int maxMessages) {
        this.maxMessages = maxMessages;
    }

    @Override
    public List<ChatMessage> compact(Conversation conversation, CompactionContext context) {
        List<Conversation.Segment> segments = conversation.segments();
        List<Conversation.Segment> kept = new ArrayList<>();
        int count = 0;
        for (int i = segments.size() - 1; i >= 0; i--) {
            Conversation.Segment segment = segments.get(i);
            if (count + segment.messages().size() > maxMessages && !kept.isEmpty()) {
                break;
            }
            kept.add(0, segment);
            count += segment.messages().size();
        }
        if (conversation.keepLatestQuestion(kept)) {
            context.note(Strategies.MESSAGE_WINDOW, "kept the latest user message ahead of its tool calls");
        }
        int dropped = segments.size() - kept.size();
        if (dropped > 0) {
            context.note(Strategies.MESSAGE_WINDOW, "dropped " + dropped + " oldest segments");
        }
        return conversation.assemble(kept);
    }

    @Override
    public String description() {
        return "Keeps the last " + maxMessages + " messages, aligned on tool call boundaries";
    }
}
