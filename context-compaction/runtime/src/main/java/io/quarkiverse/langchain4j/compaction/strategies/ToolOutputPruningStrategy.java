package io.quarkiverse.langchain4j.compaction.strategies;

import java.util.ArrayList;
import java.util.List;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import io.quarkiverse.langchain4j.compaction.ArchivedToolOutputs;
import io.quarkiverse.langchain4j.compaction.CompactionContext;
import io.quarkiverse.langchain4j.compaction.CompactionMessages;
import io.quarkiverse.langchain4j.compaction.CompactionStrategy;
import io.quarkiverse.langchain4j.compaction.Conversation;
import io.quarkiverse.langchain4j.compaction.ToolOutputArchive;
import io.quarkiverse.langchain4j.compaction.runtime.CompactionConfig;
import io.smallrye.common.annotation.Experimental;
import io.smallrye.common.annotation.Identifier;

/**
 * Replaces large tool outputs of older turns with a short stub. The full output is stored in the
 * {@link ToolOutputArchive}, so the application can show it, and the model can read it again with
 * {@link ArchivedToolOutputs} when {@code tool-output-pruning.retrieval=tool}.
 * <p>
 * This is usually the cheapest and most effective first step: tool outputs (search results, database rows, API
 * payloads) dominate the context of agentic conversations, and the model rarely needs them verbatim after it has
 * used them.
 */
@Singleton
@Identifier(Strategies.TOOL_OUTPUT_PRUNING)
@Experimental("This feature is experimental and the API is subject to change")
public class ToolOutputPruningStrategy implements CompactionStrategy {

    /**
     * @param retrievalTool whether stubs tell the model to call {@link ArchivedToolOutputs#TOOL_NAME}
     */
    public record Options(int keepLastTurns, int minChars, int previewChars, boolean retrievalTool) {

        public Options(int keepLastTurns, int minChars, int previewChars) {
            this(keepLastTurns, minChars, previewChars, false);
        }
    }

    private final Options options;
    private final ToolOutputArchive archive;

    @Inject
    public ToolOutputPruningStrategy(CompactionConfig config, ToolOutputArchive archive) {
        this(config.defaultProfile(), archive);
    }

    public ToolOutputPruningStrategy(CompactionConfig.Profile profile, ToolOutputArchive archive) {
        this(new Options(profile.toolOutputPruning().keepLastTurns(), profile.toolOutputPruning().minChars(),
                profile.toolOutputPruning().previewChars(),
                profile.toolOutputPruning().retrieval() == CompactionConfig.Profile.ToolOutputPruning.Retrieval.TOOL),
                archive);
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
        String reference = archive == null ? null : archive.put(context.memoryId(), result.id(), text);
        String preview = text.substring(0, Math.min(options.previewChars(), text.length())).replace('\n', ' ');
        StringBuilder stub = new StringBuilder("[Output of ").append(result.toolName()).append(" pruned (")
                .append(text.length()).append(" chars). Preview: ").append(preview).append("...");
        if (options.retrievalTool() && reference != null) {
            stub.append(" Read it with ").append(ArchivedToolOutputs.TOOL_NAME).append(" reference=\"")
                    .append(reference).append('"');
        }
        stub.append(']');
        return CompactionMessages.replaceToolResult(result, stub.toString(), CompactionMessages.KIND_PRUNED);
    }

    @Override
    public String description() {
        return "Replaces tool outputs older than " + options.keepLastTurns() + " turns with an archived stub";
    }
}
