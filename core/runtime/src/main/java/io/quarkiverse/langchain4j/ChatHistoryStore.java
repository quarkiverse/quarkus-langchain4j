package io.quarkiverse.langchain4j;

/**
 * SPI that users implement to record the conversation history
 * of AI service methods annotated with {@link RecordChatHistory}.
 * <p>
 * The store is called with the resolved memory id (the value of
 * the {@code @MemoryId} parameter, or the default) and the
 * user/agent message content.
 * <p>
 * Streaming responses dispatch {@link #onAgentPartial(Object, String)}
 * for each chunk as it arrives, and exactly one terminal callback at the
 * end of the turn: {@link #onAgentMessage(Object, String)} +
 * {@link #onCompleted(Object)} on success,
 * {@link #onCancelled(Object, String)} when the subscriber cancels, or
 * {@link #onError(Object, Throwable, String)} when an error occurs.
 */
public interface ChatHistoryStore {

    /**
     * Called before the AI service method is invoked, with the
     * user message that will be sent to the LLM.
     *
     * @param memoryId the conversation / memory identifier
     * @param userMessage the text of the user message
     */
    void onUserMessage(Object memoryId, String userMessage);

    /**
     * Called when the agent response is fully available, with the
     * complete agent message text. For streaming responses this is
     * called once at the end with the accumulated text.
     *
     * @param memoryId the conversation / memory identifier
     * @param agentMessage the text of the agent response
     */
    void onAgentMessage(Object memoryId, String agentMessage);

    /**
     * Called for each chunk of a streaming agent response as it is
     * produced. Default implementation is a no-op; override to persist
     * the response incrementally.
     * <p>
     * Not called for synchronous responses.
     *
     * @param memoryId the conversation / memory identifier
     * @param chunk the text chunk produced by the model
     */
    default void onAgentPartial(Object memoryId, String chunk) {
    }

    /**
     * Called after the agent response has been recorded successfully,
     * signalling that the conversation turn is complete.
     *
     * @param memoryId the conversation / memory identifier
     */
    default void onCompleted(Object memoryId) {
    }

    /**
     * Called when a streaming response is cancelled by the subscriber
     * before completion. The partial message contains whatever text was
     * accumulated up to the cancellation point.
     *
     * @param memoryId the conversation / memory identifier
     * @param partialAgentMessage the agent response accumulated so far
     */
    default void onCancelled(Object memoryId, String partialAgentMessage) {
    }

    /**
     * Called when the AI service method fails. For streaming responses
     * the partial message contains whatever text was accumulated before
     * the failure.
     *
     * @param memoryId the conversation / memory identifier
     * @param error the failure that occurred
     * @param partialAgentMessage the agent response accumulated so far (may be empty)
     */
    default void onError(Object memoryId, Throwable error, String partialAgentMessage) {
    }
}
