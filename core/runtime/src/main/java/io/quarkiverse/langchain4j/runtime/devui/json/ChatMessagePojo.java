package io.quarkiverse.langchain4j.runtime.devui.json;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;

// The representation of a chat message that it sent to the Dev UI as JSON
public class ChatMessagePojo {

    private MessageType type;
    private String message;
    private List<ToolExecutionRequestPojo> toolExecutionRequests;
    private ToolExecutionResultPojo toolExecutionResult;

    public MessageType getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    public List<ToolExecutionRequestPojo> getToolExecutionRequests() {
        return toolExecutionRequests;
    }

    public ToolExecutionResultPojo getToolExecutionResult() {
        return toolExecutionResult;
    }

    public static List<ChatMessagePojo> listFromMemory(ChatMemory memory, Map<ChatMessage, String> ragOriginalTexts) {
        return memory.messages()
                .stream()
                .map(message -> ChatMessagePojo.fromMessage(message, ragOriginalTexts))
                .collect(Collectors.toList());
    }

    public static ChatMessagePojo fromMessage(ChatMessage message, Map<ChatMessage, String> ragOriginalTexts) {
        ChatMessagePojo json = new ChatMessagePojo();
        switch (message.type()) {
            case SYSTEM:
                json.type = MessageType.SYSTEM;
                json.message = ((SystemMessage) message).text();
                break;
            case USER:
                json.type = MessageType.USER;
                UserMessage userMessage = (UserMessage) message;
                StringBuilder text = null;
                StringBuilder images = new StringBuilder();
                for (Content content : userMessage.contents()) {
                    if (content instanceof TextContent textContent) {
                        text = text == null ? new StringBuilder() : text.append("\n\n");
                        text.append(textContent.text());
                    } else if (content instanceof ImageContent imageContent) {
                        images.append("\n\n").append(toMarkdownImage(imageContent.image()));
                    }
                }
                String originalText = ragOriginalTexts.get(message);
                String formatted = text == null ? null
                        : originalText != null ? formatRagAugmentedText(originalText, text.toString()) : text.toString();
                if (!images.isEmpty()) {
                    formatted = formatted == null ? images.toString().strip() : formatted + images;
                }
                json.message = formatted;
                break;
            case AI:
                AiMessage aiMessage = (AiMessage) message;
                json.type = MessageType.AI;
                json.message = ((AiMessage) message).text();
                if (aiMessage.toolExecutionRequests() != null && !aiMessage.toolExecutionRequests().isEmpty()) {
                    json.toolExecutionRequests = ((AiMessage) message)
                            .toolExecutionRequests().stream()
                            .map(r -> new ToolExecutionRequestPojo(r.id(), r.name(), r.arguments()))
                            .collect(Collectors.toList());
                }
                break;
            case TOOL_EXECUTION_RESULT:
                json.type = MessageType.TOOL_EXECUTION_RESULT;
                json.message = null;
                json.toolExecutionResult = new ToolExecutionResultPojo(
                        ((ToolExecutionResultMessage) message).id(),
                        ((ToolExecutionResultMessage) message).toolName(),
                        ((ToolExecutionResultMessage) message).text());
                break;
        }
        return json;
    }

    /**
     * Wraps the RAG-inserted part of a user message in a dedicated {@code lc4j-rag-context} box so the Dev UI can
     * render it distinctly without colliding with regular Markdown blockquotes in other messages. The inserted part
     * is what remains after removing the common prefix and suffix; if those don't cover the whole original, the
     * augmentor rewrote it and {@code augmentedText} is returned unchanged.
     */
    public static String formatRagAugmentedText(String originalText, String augmentedText) {
        int prefixLength = commonPrefixLength(originalText, augmentedText);
        int suffixLength = commonSuffixLength(originalText, augmentedText, prefixLength);
        if (prefixLength + suffixLength != originalText.length()) {
            return augmentedText;
        }
        String ragContext = augmentedText.substring(prefixLength, augmentedText.length() - suffixLength).strip();
        if (ragContext.isEmpty()) {
            return augmentedText;
        }
        String box = "<blockquote class=\"lc4j-rag-context\"><strong>Retrieved context</strong><br>"
                + escapeHtml(ragContext).replace("\n", "<br>") + "</blockquote>";
        String prefix = originalText.substring(0, prefixLength).strip();
        String suffix = originalText.substring(originalText.length() - suffixLength).strip();
        StringBuilder result = new StringBuilder();
        if (!prefix.isEmpty()) {
            result.append(prefix).append("\n\n");
        }
        result.append(box);
        if (!suffix.isEmpty()) {
            result.append("\n\n").append(suffix);
        }
        return result.toString();
    }

    static String toMarkdownImage(Image image) {
        String src;
        if (image.base64Data() != null) {
            String mimeType = image.mimeType() != null ? image.mimeType() : "image/jpeg";
            src = "data:" + mimeType + ";base64," + image.base64Data();
        } else if (image.url() != null) {
            src = image.url().toString();
        } else {
            return "";
        }
        return "![image](" + src + ")";
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static int commonPrefixLength(String a, String b) {
        int max = Math.min(a.length(), b.length());
        int i = 0;
        while (i < max && a.charAt(i) == b.charAt(i)) {
            i++;
        }
        return i;
    }

    private static int commonSuffixLength(String a, String b, int prefixLength) {
        int max = Math.min(a.length(), b.length()) - prefixLength;
        int i = 0;
        while (i < max && a.charAt(a.length() - 1 - i) == b.charAt(b.length() - 1 - i)) {
            i++;
        }
        return i;
    }

}
