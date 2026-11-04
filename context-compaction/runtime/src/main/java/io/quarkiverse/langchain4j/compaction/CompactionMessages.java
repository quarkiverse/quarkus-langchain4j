package io.quarkiverse.langchain4j.compaction;

import java.util.Map;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;

/**
 * Helpers to create and recognize messages produced by compaction (summaries, pruned tool outputs, truncated
 * messages).
 * <p>
 * Derived messages carry the {@value #MARKER} attribute so they can be told apart from original messages, for
 * example when verifying invariants or rendering an inspection view.
 */
public final class CompactionMessages {

    /**
     * Attribute key set on every message produced by compaction. The value is one of the {@code KIND_*} constants.
     */
    public static final String MARKER = "langchain4j.compaction";

    public static final String KIND_SUMMARY = "summary";
    public static final String KIND_PRUNED = "pruned";
    public static final String KIND_TRUNCATED = "truncated";
    public static final String KIND_COLLAPSED = "collapsed";

    /**
     * Name of the user message carrying the summary. Must match {@code ^[a-zA-Z0-9_-]+$} for OpenAI.
     */
    public static final String SUMMARY_NAME = "conversation_summary";

    static final String SUMMARY_HEADER = "Summary of the earlier conversation, generated automatically. "
            + "Treat it as context, not as a new request:\n\n";

    private CompactionMessages() {
    }

    public static UserMessage summary(String summary) {
        return UserMessage.builder()
                .name(SUMMARY_NAME)
                .contents(java.util.List.of(TextContent.from(SUMMARY_HEADER + summary)))
                .attributes(marker(KIND_SUMMARY))
                .build();
    }

    public static ToolExecutionResultMessage replaceToolResult(ToolExecutionResultMessage original, String text,
            String kind) {
        return ToolExecutionResultMessage.builder()
                .id(original.id())
                .toolName(original.toolName())
                .text(text)
                .isError(original.isError())
                .attributes(marker(kind))
                .build();
    }

    public static AiMessage replaceText(AiMessage original, String text, String kind) {
        return AiMessage.builder()
                .text(text)
                .thinking(original.thinking())
                .toolExecutionRequests(original.toolExecutionRequests())
                .attributes(marker(kind))
                .build();
    }

    public static UserMessage replaceText(UserMessage original, String text, String kind) {
        return UserMessage.builder()
                .name(original.name())
                .contents(java.util.List.of(dev.langchain4j.data.message.TextContent.from(text)))
                .attributes(marker(kind))
                .build();
    }

    /**
     * @return the compaction kind of the message, or {@code null} if it is an original message
     */
    public static String kind(ChatMessage message) {
        Object value;
        if (message instanceof UserMessage m) {
            value = m.attributes().get(MARKER);
        } else if (message instanceof AiMessage m) {
            value = m.attributes().get(MARKER);
        } else if (message instanceof ToolExecutionResultMessage m) {
            value = m.attributes().get(MARKER);
        } else {
            value = null;
        }
        return value == null ? null : value.toString();
    }

    public static boolean isDerived(ChatMessage message) {
        return kind(message) != null;
    }

    public static boolean isSummary(ChatMessage message) {
        return KIND_SUMMARY.equals(kind(message));
    }

    /**
     * @return the text of the message, or an empty string for messages without text
     */
    public static String text(ChatMessage message) {
        if (message instanceof UserMessage m) {
            return m.hasSingleText() ? m.singleText() : m.contents().toString();
        } else if (message instanceof AiMessage m) {
            StringBuilder sb = new StringBuilder();
            if (m.text() != null) {
                sb.append(m.text());
            }
            if (m.hasToolExecutionRequests()) {
                m.toolExecutionRequests().forEach(r -> sb.append(sb.isEmpty() ? "" : "\n")
                        .append("call ").append(r.name()).append(r.arguments()));
            }
            return sb.toString();
        } else if (message instanceof ToolExecutionResultMessage m) {
            return m.text() == null ? "" : m.text();
        } else if (message instanceof dev.langchain4j.data.message.SystemMessage m) {
            return m.text();
        } else {
            return "";
        }
    }

    private static Map<String, Object> marker(String kind) {
        return Map.of(MARKER, kind);
    }
}
