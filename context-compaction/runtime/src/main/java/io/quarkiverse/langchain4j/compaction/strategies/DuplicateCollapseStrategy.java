package io.quarkiverse.langchain4j.compaction.strategies;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.inject.Singleton;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import io.quarkiverse.langchain4j.compaction.CompactionContext;
import io.quarkiverse.langchain4j.compaction.CompactionMessages;
import io.quarkiverse.langchain4j.compaction.CompactionStrategy;
import io.quarkiverse.langchain4j.compaction.Conversation;
import io.smallrye.common.annotation.Identifier;

/**
 * Collapses repeated, identical tool calls: when the same tool was called with the same arguments and returned the
 * same output several times, only the last exchange is kept and its output is annotated with the repeat count.
 * <p>
 * Repeated calls are typical of agents that poll or are stuck in a loop; this strategy pairs with loop control
 * (stasis detection) in the application.
 */
@Singleton
@Identifier(Strategies.DUPLICATE_COLLAPSE)
public class DuplicateCollapseStrategy implements CompactionStrategy {

    @Override
    public List<ChatMessage> compact(Conversation conversation, CompactionContext context) {
        List<Conversation.Segment> segments = conversation.segments();

        Map<String, Integer> occurrences = new HashMap<>();
        Map<String, Integer> lastIndex = new HashMap<>();
        Map<String, Integer> keyTokens = new HashMap<>();
        List<String> keys = new ArrayList<>(segments.size());
        for (int i = 0; i < segments.size(); i++) {
            Conversation.Segment segment = segments.get(i);
            String key = key(segment);
            keys.add(key);
            if (key != null) {
                occurrences.merge(key, 1, Integer::sum);
                lastIndex.put(key, i);
                keyTokens.merge(key, context.tokens(segment.messages()), Integer::sum);
            }
        }

        // collapsing must save tokens: tiny repeated calls can cost less than the annotation replacing them
        Set<String> collapsible = new HashSet<>();
        lastIndex.forEach((key, index) -> {
            int total = occurrences.get(key);
            if (total < 2) {
                return;
            }
            Conversation.Segment last = segments.get(index);
            int lastTokens = context.tokens(last.messages());
            int saved = keyTokens.get(key) - lastTokens;
            int cost = context.tokens(annotate(last, total).messages()) - lastTokens;
            if (saved > cost) {
                collapsible.add(key);
            }
        });

        Map<String, Integer> seen = new HashMap<>();
        List<Conversation.Segment> kept = new ArrayList<>();
        int removed = 0;
        for (int i = 0; i < segments.size(); i++) {
            String key = keys.get(i);
            Conversation.Segment segment = segments.get(i);
            if (key == null || !collapsible.contains(key)) {
                kept.add(segment);
                continue;
            }
            int total = occurrences.get(key);
            int index = seen.merge(key, 1, Integer::sum);
            if (index < total) {
                removed++;
                continue;
            }
            kept.add(annotate(segment, total));
        }
        if (removed > 0) {
            context.note(Strategies.DUPLICATE_COLLAPSE, "collapsed " + removed + " repeated tool calls");
        }
        return conversation.assemble(kept);
    }

    /**
     * @return a key identifying the call and its result, or {@code null} if the segment is not a collapsible
     *         single-call tool exchange
     */
    private static String key(Conversation.Segment segment) {
        if (segment.kind() != Conversation.Kind.TOOL_EXCHANGE || segment.messages().size() != 2) {
            return null;
        }
        AiMessage ai = (AiMessage) segment.messages().get(0);
        if (ai.text() != null && !ai.text().isBlank() || ai.toolExecutionRequests().size() != 1) {
            return null;
        }
        if (!(segment.messages().get(1) instanceof ToolExecutionResultMessage result)) {
            return null;
        }
        ToolExecutionRequest request = ai.toolExecutionRequests().get(0);
        return request.name() + "\u0000" + request.arguments() + "\u0000" + result.text();
    }

    private static Conversation.Segment annotate(Conversation.Segment segment, int total) {
        ToolExecutionResultMessage result = (ToolExecutionResultMessage) segment.messages().get(1);
        String text = result.text() + "\n[Collapsed " + (total - 1)
                + " earlier identical calls to this tool with the same arguments and output.]";
        return new Conversation.Segment(segment.kind(), List.of(segment.messages().get(0),
                CompactionMessages.replaceToolResult(result, text, CompactionMessages.KIND_COLLAPSED)));
    }

    @Override
    public String description() {
        return "Collapses repeated identical tool calls into the last one";
    }
}
