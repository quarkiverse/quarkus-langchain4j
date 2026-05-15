package io.quarkiverse.langchain4j.gpullama3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.beehive.gpullama3.model.format.ToolCallExtract;
import org.beehive.gpullama3.model.format.ToolCallParserUtils;

import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;

public class GPULlama3ResponseParser {

    private GPULlama3ResponseParser() {
        // Utility class - prevent instantiation
    }

    public static class ParsedResponse {
        private final String thinkingContent;
        private final String actualResponse;

        /**
         * Creates a new ParsedResponse.
         *
         * @param thinkingContent the thinking content including tags, or null if none
         * @param actualResponse the cleaned response content
         */
        public ParsedResponse(String thinkingContent, String actualResponse) {
            this.thinkingContent = thinkingContent;
            this.actualResponse = actualResponse;
        }

        /**
         * Returns the thinking content including &lt;think&gt; and &lt;/think&gt; tags.
         *
         * @return the thinking content with tags, or null if no thinking content was found
         */
        public String getThinkingContent() {
            return thinkingContent;
        }

        /**
         * Returns the actual response content with thinking tags removed.
         *
         * @return the cleaned response content
         */
        public String getActualResponse() {
            return actualResponse;
        }

        /**
         * Returns true if the response contained thinking content.
         *
         * @return true if thinking content was found, false otherwise
         */
        public boolean hasThinking() {
            return thinkingContent != null && !thinkingContent.trim().isEmpty();
        }
    }

    public static ParsedResponse parseResponse(String rawResponse) {
        if (rawResponse == null) {
            throw new IllegalArgumentException("Raw response cannot be null");
        }

        String thinking = null;
        String actualResponse = rawResponse;

        // Find <think> and </think> positions
        int thinkStart = rawResponse.indexOf("<think>");
        int thinkEnd = rawResponse.indexOf("</think>");

        if (thinkStart != -1 && thinkEnd != -1 && thinkEnd > thinkStart) {
            // Extract thinking content INCLUDING the tags
            thinking = rawResponse.substring(thinkStart, thinkEnd + 8).trim(); // Include </think>

            // Remove the entire thinking block from response
            String beforeThink = rawResponse.substring(0, thinkStart);
            String afterThink = rawResponse.substring(thinkEnd + 8); // Skip </think>
            actualResponse = (beforeThink + afterThink).trim();

            // Clean up any extra whitespace
            actualResponse = actualResponse.replaceAll("\\s+", " ").trim();
        }

        return new ParsedResponse(thinking, actualResponse);
    }

    public static String extractThinking(String rawResponse) {
        return parseResponse(rawResponse).getThinkingContent();
    }

    public static String extractResponse(String rawResponse) {
        return parseResponse(rawResponse).getActualResponse();
    }

    public static StreamingParser createStreamingParser(
            StreamingChatResponseHandler handler, org.beehive.gpullama3.model.Model model) {
        return new StreamingParser(handler, model);
    }

    /**
     * Parser for handling streaming responses with real-time thinking content separation.
     * <p>
     * This parser detects thinking content as tokens are generated and routes it to
     * the appropriate handler methods (onPartialThinking vs onPartialResponse).
     * The thinking tags are preserved and streamed as part of the thinking content.
     */
    public static class StreamingParser {
        private static final String TOOL_CALL_OPEN = "<tool_call>";
        private static final String TOOL_CALL_CLOSE = "</tool_call>";
        private static final String PYTHON_TAG = "<|python_tag|>";

        private final StreamingChatResponseHandler handler;
        private final org.beehive.gpullama3.model.Model model;
        private final StringBuilder buffer = new StringBuilder();
        private boolean insideThinking = false;
        private boolean insideToolCall = false;
        private boolean insidePythonTagCall = false;
        private final StringBuilder toolCallBuffer = new StringBuilder();
        private final StringBuilder thinkingAccumulator = new StringBuilder();
        private final List<ToolCallExtract> detectedToolCalls = new ArrayList<>();
        private int lastProcessedLength = 0;

        /**
         * Creates a new streaming parser.
         *
         * @param handler the streaming response handler
         * @param model the GPULlama3 model instance for token decoding
         */
        public StreamingParser(StreamingChatResponseHandler handler, org.beehive.gpullama3.model.Model model) {
            this.handler = handler;
            this.model = model;
        }

        /**
         * Processes each token as it's generated by the model.
         *
         * @param tokenId the token ID generated by the model
         */
        public void onToken(int tokenId) {
            // Check if this is a stop token and skip it
            if (model.chatFormat().getToolAwareStopTokens().contains(tokenId)) {
                return; // Don't stream stop tokens like <|im_end|> or <|eom_id|>
            }

            // Decode the token and add to buffer
            String tokenStr = model.tokenizer().decode(java.util.List.of(tokenId));
            buffer.append(tokenStr);

            String currentText = buffer.toString();

            // Process any new content since last time
            processNewContent(currentText);
        }

        /**
         * Processes new content in the buffer, detecting thinking and tool-call state
         * transitions and routing content to the appropriate handler methods.
         *
         * <p>
         * Tool-call markers handled:
         * <ul>
         * <li>{@code <tool_call>} / {@code </tool_call>} — LLaMA 3.2 and Qwen3</li>
         * <li>{@code <|python_tag|>} — LLaMA 3.1 (no explicit close tag; resolved in
         * {@link #finish()})</li>
         * </ul>
         * Characters inside a tool-call block are buffered rather than forwarded to the
         * handler, so the client never sees raw tool-call JSON as a partial response.
         */
        private void processNewContent(String currentText) {
            if (currentText.length() <= lastProcessedLength) {
                return; // No new content
            }

            String newContent = currentText.substring(lastProcessedLength);

            // Process each character in the new content
            for (int i = 0; i < newContent.length(); i++) {
                int pos = lastProcessedLength + i;

                // Inside <tool_call>…</tool_call>
                if (insideToolCall) {
                    if (regionMatches(currentText, pos, TOOL_CALL_CLOSE)) {
                        ToolCallParserUtils.parseToolCallResponse(
                                TOOL_CALL_OPEN + toolCallBuffer + TOOL_CALL_CLOSE)
                                .ifPresent(detectedToolCalls::add);
                        toolCallBuffer.setLength(0);
                        insideToolCall = false;
                        i += TOOL_CALL_CLOSE.length() - 1;
                    } else {
                        toolCallBuffer.append(newContent.charAt(i));
                    }
                    continue;
                }

                // Inside <|python_tag|>… (no close tag, resolved in finish())
                if (insidePythonTagCall) {
                    toolCallBuffer.append(newContent.charAt(i));
                    continue;
                }

                // Thinking open
                if (!insideThinking && isStartOfThinkTag(currentText, pos)) {
                    insideThinking = true;
                    // Stream the opening tag as thinking
                    thinkingAccumulator.append("<think>");
                    handler.onPartialThinking(new PartialThinking("<think>"));
                    i += 6; // Skip the rest of "<think>"
                    continue;
                }

                // Thinking close
                if (insideThinking && isStartOfEndThinkTag(currentText, pos)) {
                    // Stream the closing tag as thinking
                    thinkingAccumulator.append("</think>");
                    handler.onPartialThinking(new PartialThinking("</think>"));
                    insideThinking = false;
                    i += 7; // Skip the rest of "</think>"
                    continue;
                }

                // Tool call open (<tool_call>)
                if (!insideThinking && regionMatches(currentText, pos, TOOL_CALL_OPEN)) {
                    insideToolCall = true;
                    i += TOOL_CALL_OPEN.length() - 1;
                    continue;
                }

                // LLaMA 3.1 python tag (<|python_tag|>)
                if (!insideThinking && regionMatches(currentText, pos, PYTHON_TAG)) {
                    insidePythonTagCall = true;
                    i += PYTHON_TAG.length() - 1;
                    continue;
                }

                // Route the character to appropriate handler
                char c = newContent.charAt(i);
                if (insideThinking) {
                    thinkingAccumulator.append(c);
                    handler.onPartialThinking(new PartialThinking(String.valueOf(c)));
                } else {
                    handler.onPartialResponse(String.valueOf(c));
                }
            }

            lastProcessedLength = currentText.length();
        }

        /**
         * Must be called after the model finishes generating tokens.
         * Parses any buffered {@code <|python_tag|>} tool call (LLaMA 3.1), which has
         * no explicit close tag and is only complete when generation stops.
         *
         * @return unmodifiable list of all tool calls detected during this generation
         */
        public List<ToolCallExtract> finish() {
            if (insidePythonTagCall && !toolCallBuffer.isEmpty()) {
                ToolCallParserUtils.parseToolCallResponse(PYTHON_TAG + toolCallBuffer)
                        .ifPresent(detectedToolCalls::add);
            }
            return Collections.unmodifiableList(detectedToolCalls);
        }

        /**
         * Returns the thinking content accumulated during generation (including
         * {@code <think>} and {@code </think>} tags), or {@code null} if no
         * thinking block was emitted.
         */
        public String getThinkingContent() {
            String s = thinkingAccumulator.toString().strip();
            return s.isEmpty() ? null : s;
        }

        private static boolean regionMatches(String text, int start, String marker) {
            return start + marker.length() <= text.length()
                    && text.regionMatches(start, marker, 0, marker.length());
        }

        /**
         * Checks if the text at the given position starts with "&lt;think&gt;".
         */
        private boolean isStartOfThinkTag(String text, int position) {
            return position + 7 <= text.length() && text.regionMatches(position, "<think>", 0, 7);
        }

        /**
         * Checks if the text at the given position starts with "&lt;/think&gt;".
         */
        private boolean isStartOfEndThinkTag(String text, int position) {
            return position + 8 <= text.length() && text.regionMatches(position, "</think>", 0, 8);
        }
    }
}
