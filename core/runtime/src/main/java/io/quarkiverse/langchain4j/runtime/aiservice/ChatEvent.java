package io.quarkiverse.langchain4j.runtime.aiservice;

import java.util.List;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.service.tool.ToolExecution;

/**
 * Base class for chat-related events that occur during AI service interactions.
 * This class serves as the foundation for various event types that can be emitted
 * during the lifecycle of a chat conversation, including tool executions, partial
 * responses, and completion events.
 *
 * <p>
 * Events are typically used for monitoring, logging, or reactive processing
 * of AI service interactions.
 * </p>
 *
 * @see ChatCompletedEvent
 * @see ToolExecutedEvent
 * @see BeforeToolExecutionEvent
 * @see PartialResponseEvent
 * @see ContentFetchedEvent
 * @see AccumulatedResponseEvent
 * @see PartialThinkingEvent
 */
public class ChatEvent {

    /**
     * Enumeration of possible chat event types.
     */
    public enum ChatEventType {
        /**
         * Indicates that a chat conversation has been completed successfully.
         */
        Completed,
        /**
         * Indicates that a tool has been executed during the chat conversation.
         */
        ToolExecuted,
        /**
         * Indicates that a tool is about to be executed during the chat conversation.
         */
        BeforeToolExecution,
        /**
         * Indicates that a partial response chunk has been received (used in streaming scenarios).
         */
        PartialResponse,
        /**
         * Indicates that content has been fetched from a retrieval-augmented generation (RAG) source.
         */
        ContentFetched,
        /**
         * Indicates that responses have been accumulated when using Output Guardrails
         */
        AccumulatedResponse,
        /**
         * Indicates that a partial thinking/reasoning chunk has been received (experimental).
         */
        PartialThinking

    }

    private final ChatEventType eventType;

    /**
     * Constructs a new ChatEvent with the specified event type.
     *
     * @param eventType the type of chat event
     */
    public ChatEvent(ChatEventType eventType) {
        this.eventType = eventType;
    }

    /**
     * Returns the type of this chat event.
     *
     * @return the event type
     */
    public ChatEventType getEventType() {
        return eventType;
    }

    /**
     * Event emitted when a chat conversation has been completed.
     * This event contains the final chat response including the AI's message
     * and any associated metadata.
     */
    public static class ChatCompletedEvent extends ChatEvent {
        private final ChatResponse chatResponse;

        /**
         * Constructs a new ChatCompletedEvent with the given chat response.
         *
         * @param chatResponse the completed chat response containing the AI's message and metadata
         */
        public ChatCompletedEvent(ChatResponse chatResponse) {
            super(ChatEventType.Completed);
            this.chatResponse = chatResponse;
        }

        /**
         * Returns the completed chat response.
         *
         * @return the chat response containing the AI's message and metadata
         */
        public ChatResponse getChatResponse() {
            return chatResponse;
        }
    }

    /**
     * Event emitted when a tool has been executed during a chat conversation.
     * Tools are functions that the AI can call to perform specific actions or
     * retrieve information during the conversation.
     */
    public static class ToolExecutedEvent extends ChatEvent {
        private final ToolExecution execution;

        /**
         * Constructs a new ToolExecutedEvent with the given tool execution details.
         *
         * @param execution the tool execution information including the tool name,
         *        arguments, and execution result
         */
        public ToolExecutedEvent(ToolExecution execution) {
            super(ChatEventType.ToolExecuted);
            this.execution = execution;
        }

        /**
         * Returns the tool execution details.
         *
         * @return the tool execution information
         */
        public ToolExecution getExecution() {
            return execution;
        }
    }

    /**
     * Event emitted when a tool is about to be executed during a chat conversation.
     * This event is triggered before the tool execution begins, allowing for monitoring,
     * logging, or preparation steps before the actual tool invocation.
     */
    public static class BeforeToolExecutionEvent extends ChatEvent {
        private final ToolExecutionRequest request;

        /**
         * Constructs a new BeforeToolExecutionEvent with the given tool execution request.
         *
         * @param request the tool execution request that is about to be executed
         */
        public BeforeToolExecutionEvent(ToolExecutionRequest request) {
            super(ChatEventType.BeforeToolExecution);
            this.request = request;
        }

        /**
         * Returns the tool execution request that is about to be executed.
         *
         * @return the tool execution request
         */
        public ToolExecutionRequest getRequest() {
            return request;
        }
    }

    /**
     * Event emitted when a partial response chunk is received during streaming.
     * This event is typically used when the AI service streams its response
     * incrementally rather than returning the complete response at once.
     */
    public static class PartialResponseEvent extends ChatEvent {
        private final String chunk;

        /**
         * Constructs a new PartialResponseEvent with the given response chunk.
         *
         * @param chunk a partial piece of the AI's response text
         */
        public PartialResponseEvent(String chunk) {
            super(ChatEventType.PartialResponse);
            this.chunk = chunk;
        }

        /**
         * Returns the partial response chunk.
         *
         * @return the response chunk text
         */
        public String getChunk() {
            return chunk;
        }
    }

    /**
     * Event emitted when content has been fetched from a retrieval-augmented
     * generation (RAG) source. This event contains the retrieved content that
     * will be used to augment the AI's response.
     */
    public static class ContentFetchedEvent extends ChatEvent {

        private final List<Content> content;

        /**
         * Constructs a new ContentFetchedEvent with the fetched content.
         *
         * @param content the list of content items retrieved from the RAG source
         */
        public ContentFetchedEvent(List<Content> content) {
            super(ChatEventType.ContentFetched);
            this.content = content;
        }

        /**
         * Returns the fetched content.
         *
         * @return the list of content items retrieved from the RAG source
         */
        public List<Content> getContent() {
            return content;
        }
    }

    /**
     * Event emitted when partial responses have been accumulated into a larget message.
     * This event is typically used in streaming scenarios where OutputGuardrails are used.
     */
    public static class AccumulatedResponseEvent extends ChatEvent {

        private final String message;
        private final ChatResponseMetadata metadata;

        /**
         * Constructs a new AccumulatedResponseEvent with the accumulated message and metadata.
         *
         * @param message the complete accumulated message text
         * @param metadata the metadata associated with the chat response, including
         *        token usage, model information, and other response details
         */
        public AccumulatedResponseEvent(String message, ChatResponseMetadata metadata) {
            super(ChatEventType.AccumulatedResponse);
            this.message = message;
            this.metadata = metadata;
        }

        /**
         * Returns the accumulated message.
         *
         * @return the complete message text
         */
        public String getMessage() {
            return message;
        }

        /**
         * Returns the chat response metadata.
         *
         * @return the metadata containing token usage, model information, and other details
         */
        public ChatResponseMetadata getMetadata() {
            return metadata;
        }
    }

    /**
     * Event emitted when a partial thinking/reasoning chunk is received during streaming.
     * This event is typically used when the AI service streams its internal reasoning
     * process separately from the main response content. This is an experimental feature
     * and may not be supported by all models.
     *
     * <p>
     * Thinking events allow observing the model's reasoning process, which can be useful
     * for debugging, auditing, or understanding how the model arrived at its response.
     * </p>
     */
    public static class PartialThinkingEvent extends ChatEvent {
        private final String text;

        /**
         * Constructs a new PartialThinkingEvent with the given thinking text.
         *
         * @param text a partial piece of the AI's thinking/reasoning text
         */
        public PartialThinkingEvent(String text) {
            super(ChatEventType.PartialThinking);
            this.text = text;
        }

        /**
         * Returns the partial thinking text.
         *
         * @return the thinking/reasoning text chunk
         */
        public String getText() {
            return text;
        }
    }

}
