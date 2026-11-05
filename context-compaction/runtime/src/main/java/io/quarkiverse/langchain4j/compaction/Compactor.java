package io.quarkiverse.langchain4j.compaction;

import java.util.List;
import java.util.Map;

import dev.langchain4j.data.message.ChatMessage;
import io.smallrye.common.annotation.Experimental;

/**
 * Runs compaction strategies on demand, outside of chat memory: to compare strategies, to compact a transcript before
 * handing it to a sub-agent, or to preview what a strategy would do. Each call uses fresh strategy state, so nothing
 * is cached between calls.
 */
@Experimental("This feature is experimental and the API is subject to change")
public interface Compactor {

    /**
     * @param view the compacted messages
     * @param report what the strategy did
     */
    record Result(List<ChatMessage> view, CompactionReport report) {
    }

    /**
     * @return the identifiers of the available strategies and their descriptions
     */
    Map<String, String> strategies();

    /**
     * Compacts with the budget and token counter of the default profile.
     *
     * @throws CompactionException if the strategy is unknown
     */
    Result compact(String strategyId, List<ChatMessage> messages);

    /**
     * Compacts with the given budget and the token counter of the default profile.
     *
     * @throws CompactionException if the strategy is unknown
     */
    Result compact(String strategyId, List<ChatMessage> messages, TokenBudget budget);
}
