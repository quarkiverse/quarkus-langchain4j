package io.quarkiverse.langchain4j.compaction.strategies;

import java.util.ArrayList;
import java.util.List;

import jakarta.inject.Singleton;

import dev.langchain4j.data.message.ChatMessage;
import io.quarkiverse.langchain4j.compaction.CompactionContext;
import io.quarkiverse.langchain4j.compaction.CompactionStrategy;
import io.quarkiverse.langchain4j.compaction.Conversation;
import io.smallrye.common.annotation.Identifier;

/**
 * Keeps the most recent segments that fit the token target. The last segment is always kept, even if it alone
 * exceeds the target. Used as the last step of pipelines and as the fallback when another strategy fails.
 */
@Singleton
@Identifier(Strategies.TOKEN_WINDOW)
public class TokenWindowStrategy implements CompactionStrategy {

    @Override
    public List<ChatMessage> compact(Conversation conversation, CompactionContext context) {
        List<Conversation.Segment> segments = conversation.segments();
        int target = context.budget().target();
        // the latest user message is always kept (see Conversation#keepLatestQuestion): reserve its tokens up front
        Conversation.Segment question = conversation.latestQuestion();
        int reserved = question == null ? 0 : context.tokens(question.messages());
        int tokens = context.tokens(conversation.system()) + reserved;
        List<Conversation.Segment> kept = new ArrayList<>();
        for (int i = segments.size() - 1; i >= 0; i--) {
            Conversation.Segment segment = segments.get(i);
            int segmentTokens = segment == question ? 0 : context.tokens(segment.messages());
            if (tokens + segmentTokens > target && !kept.isEmpty()) {
                break;
            }
            kept.add(0, segment);
            tokens += segmentTokens;
        }
        if (conversation.keepLatestQuestion(kept)) {
            context.note(Strategies.TOKEN_WINDOW, "kept the latest user message ahead of its tool calls");
        }
        int dropped = segments.size() - kept.size();
        if (dropped > 0) {
            context.note(Strategies.TOKEN_WINDOW, "dropped " + dropped + " oldest segments to fit " + target + " tokens");
        }
        return conversation.assemble(kept);
    }

    @Override
    public String description() {
        return "Keeps the most recent segments that fit the token target";
    }
}
