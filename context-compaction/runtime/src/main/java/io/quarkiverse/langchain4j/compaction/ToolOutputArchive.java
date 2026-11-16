package io.quarkiverse.langchain4j.compaction;

import java.util.Optional;

import io.smallrye.common.annotation.Experimental;

/**
 * Keeps the full text of tool outputs pruned from the context, so they can be retrieved on demand (see
 * {@link ArchivedToolOutputs}) and shown in inspection views.
 * <p>
 * The default implementation is in-memory. Declare a bean implementing this interface to store the outputs elsewhere,
 * for example in a database or an object store for horizontally scaled applications.
 */
@Experimental("This feature is experimental and the API is subject to change")
public interface ToolOutputArchive {

    /**
     * Archives a tool output and returns the reference to retrieve it. Implementations must return the same reference
     * when the same output of the same tool call is archived again (a conversation is compacted many times), and
     * different references for different outputs, even when providers reuse tool call ids or send empty ones.
     *
     * @param memoryId the conversation
     * @param toolCallId the id of the tool call, possibly {@code null} or empty
     * @param output the full output
     * @return the reference of the output
     */
    String put(Object memoryId, String toolCallId, String output);

    /**
     * @return the archived output, if any
     */
    Optional<String> get(Object memoryId, String reference);

    /**
     * Forgets the archived outputs of a conversation, when it is cleared or no longer active.
     */
    void clear(Object memoryId);

    /**
     * The default reference of an output: the tool call id when it is set and not used by another output, otherwise
     * the id (or {@code tool}) followed by a hash of the output.
     *
     * @param toolCallId the id of the tool call, possibly {@code null} or empty
     * @param output the output
     * @param taken whether a reference is already used by a different output
     */
    static String reference(String toolCallId, String output, java.util.function.Predicate<String> taken) {
        String base = toolCallId == null || toolCallId.isBlank() ? "tool" : toolCallId;
        if (!base.equals("tool") && !taken.test(base)) {
            return base;
        }
        return base + "#" + hash(output);
    }

    private static String hash(String output) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(output.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest, 0, 4);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
