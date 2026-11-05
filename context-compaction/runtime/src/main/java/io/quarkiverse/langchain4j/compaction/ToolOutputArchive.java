package io.quarkiverse.langchain4j.compaction;

import java.util.Optional;

import io.smallrye.common.annotation.Experimental;

/**
 * Keeps the full text of tool outputs pruned from the context, so they can be retrieved on demand and shown in
 * inspection views.
 * <p>
 * The default implementation is in-memory. Declare a bean implementing this interface to store the outputs elsewhere,
 * for example in a database or an object store for horizontally scaled applications.
 */
@Experimental("This feature is experimental and the API is subject to change")
public interface ToolOutputArchive {

    /**
     * Archives a tool output. The first output archived for a given tool call is kept.
     */
    void put(Object memoryId, String toolCallId, String output);

    /**
     * @return the archived output of the tool call, if any
     */
    Optional<String> get(Object memoryId, String toolCallId);

    /**
     * Forgets the archived outputs of a conversation, when it is cleared or no longer active.
     */
    void clear(Object memoryId);
}
