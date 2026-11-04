package io.quarkiverse.langchain4j.compaction.strategies;

import java.util.ArrayList;
import java.util.List;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import io.quarkiverse.langchain4j.compaction.CompactionContext;
import io.quarkiverse.langchain4j.compaction.CompactionMessages;
import io.quarkiverse.langchain4j.compaction.CompactionStrategy;
import io.quarkiverse.langchain4j.compaction.Conversation;
import io.quarkiverse.langchain4j.compaction.ToolOutputArchive;
import io.quarkiverse.langchain4j.compaction.runtime.CompactionConfig;
import io.smallrye.common.annotation.Identifier;

/**
 * Replaces large tool outputs of older turns with a short stub. The full output is stored in the
 * {@link ToolOutputArchive} under the tool call id, so it can be retrieved on demand.
 * <p>
 * This is usually the cheapest and most effective first step: tool outputs (search results, database rows, API
 * payloads) dominate the context of agentic conversations, and the model rarely needs them verbatim after it has
 * used them.
 */
@Singleton
@Identifier(Strategies.TOOL_OUTPUT_PRUNING)
public class ToolOutputPruningStrategy implements CompactionStrategy {

    public record Options(int keepLastTurns, int minChars, int previewChars) {
    }

    private final Options options;
    private final ToolOutputArchive archive;

    @Inject
    public ToolOutputPruningStrategy(CompactionConfig config, ToolOutputArchive archive) {
        this(config.defaultProfile(), archive);
    }

    public ToolOutputPruningStrategy(CompactionConfig.Profile profile, ToolOutputArchive archive) {
        this(new Options(profile.toolOutputPruning().keepLastTurns(), profile.toolOutputPruning().minChars(),
                profile.toolOutputPruning().previewChars()), archive);
    }

    public ToolOutputPruningStrategy(Options options, ToolOutputArchive archive) {
        this.options = options;
        this.archive = archive;
    }

    @Override
    public List<ChatMessage> compact(Conversation conversation, CompactionContext context) {
        List<Conversation.Turn> turns = conversation.turns();
        int protectedFrom = Math.max(0, turns.size() - options.keepLastTurns());
        List<Conversation.Segment> result = new ArrayList<>();
        int pruned = 0;
        long savedChars = 0;
        for (Conversation.Turn turn : turns) {
            for (Conversation.Segment segment : turn.segments()) {
                if (turn.index() >= protectedFrom || segment.kind() != Conversation.Kind.TOOL_EXCHANGE) {
                    result.add(segment);
                    continue;
                }
                List<ChatMessage> rewritten = new ArrayList<>();
                for (ChatMessage message : segment.messages()) {
                    if (message instanceof ToolExecutionResultMessage toolResult
                            && !CompactionMessages.isDerived(toolResult)
                            && toolResult.text() != null
                            && toolResult.text().length() >= options.minChars()) {
                        rewritten.add(prune(toolResult, context));
                        pruned++;
                        savedChars += toolResult.text().length();
                    } else {
                        rewritten.add(message);
                    }
                }
                result.add(new Conversation.Segment(segment.kind(), rewritten));
            }
        }
        if (pruned > 0) {
            context.note(Strategies.TOOL_OUTPUT_PRUNING,
                    "pruned " + pruned + " tool outputs (" + savedChars + " chars archived)");
        }
        return conversation.assemble(result);
    }

    private ToolExecutionResultMessage prune(ToolExecutionResultMessage result, CompactionContext context) {
        String text = result.text();
        String key = result.id() != null ? result.id() : result.toolName() + "#" + Integer.toHexString(text.hashCode());
        if (archive != null) {
            archive.put(context.memoryId(), key, text);
        }
        String preview = text.substring(0, Math.min(options.previewChars(), text.length())).replace('\n', ' ');
        String stub = "[Output of " + result.toolName() + " pruned from context (" + text.length()
                + " chars). Preview: " + preview + "... Archived as '" + key + "'.]";
        return CompactionMessages.replaceToolResult(result, stub, CompactionMessages.KIND_PRUNED);
    }

    @Override
    public String description() {
        return "Replaces tool outputs older than " + options.keepLastTurns() + " turns with an archived stub";
    }
}
