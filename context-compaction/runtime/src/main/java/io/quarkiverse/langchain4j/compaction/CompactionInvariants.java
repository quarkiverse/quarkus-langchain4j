package io.quarkiverse.langchain4j.compaction;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import io.smallrye.common.annotation.Experimental;

/**
 * Checks the invariants every compaction strategy must respect.
 * <ol>
 * <li>Every system message of the original history is present in the compacted view.</li>
 * <li>Every tool result follows the assistant message that requested it, and every tool request has its result.</li>
 * <li>Original messages kept in the view appear in their original relative order.</li>
 * <li>The latest user message of the original history is kept.</li>
 * <li>The first non-system message of the view is a user message (or a summary), when the original history has a user
 * message: several providers reject a conversation starting otherwise.</li>
 * </ol>
 */
@Experimental("This feature is experimental and the API is subject to change")
public final class CompactionInvariants {

    private CompactionInvariants() {
    }

    /**
     * @return the list of violations, empty if the view is valid
     */
    public static List<String> violations(List<ChatMessage> original, List<ChatMessage> compacted) {
        List<String> violations = new ArrayList<>();
        checkSystemMessages(original, compacted, violations);
        checkToolPairing(compacted, violations);
        checkOrder(original, compacted, violations);
        ChatMessage latestQuestion = latestQuestion(original);
        if (latestQuestion != null) {
            checkLatestQuestionKept(latestQuestion, compacted, violations);
            checkStartsWithUserMessage(compacted, violations);
        }
        return violations;
    }

    public static void verify(List<ChatMessage> original, List<ChatMessage> compacted) {
        List<String> violations = violations(original, compacted);
        if (!violations.isEmpty()) {
            throw new CompactionException("Compaction invariants violated: " + violations);
        }
    }

    private static void checkSystemMessages(List<ChatMessage> original, List<ChatMessage> compacted,
            List<String> violations) {
        for (ChatMessage message : original) {
            if (message instanceof SystemMessage && !compacted.contains(message)) {
                violations.add("system message dropped");
            }
        }
    }

    private static void checkToolPairing(List<ChatMessage> compacted, List<String> violations) {
        Set<String> pending = new HashSet<>();
        Set<String> requested = new HashSet<>();
        for (ChatMessage message : compacted) {
            if (message instanceof ToolExecutionResultMessage result) {
                if (result.id() != null && !requested.contains(result.id())) {
                    violations.add("tool result '" + result.id() + "' has no matching request");
                }
                pending.remove(result.id());
                continue;
            }
            if (!pending.isEmpty()) {
                violations.add("tool requests " + pending + " have no result");
                pending.clear();
            }
            if (message instanceof AiMessage ai && ai.hasToolExecutionRequests()) {
                requested.clear();
                for (ToolExecutionRequest request : ai.toolExecutionRequests()) {
                    if (request.id() != null) {
                        pending.add(request.id());
                        requested.add(request.id());
                    }
                }
            }
        }
        // Pending requests at the very end are legal: the model is waiting for tool results that are being computed.
    }

    private static void checkOrder(List<ChatMessage> original, List<ChatMessage> compacted, List<String> violations) {
        int cursor = -1;
        for (ChatMessage message : compacted) {
            if (CompactionMessages.isDerived(message) || message instanceof SystemMessage) {
                continue;
            }
            int index = indexOf(original, message, cursor + 1);
            if (index < 0) {
                if (original.contains(message)) {
                    violations.add("message out of order: " + abbreviate(message));
                } else {
                    violations.add("unknown message (not derived, not original): " + abbreviate(message));
                }
                continue;
            }
            cursor = index;
        }
    }

    private static ChatMessage latestQuestion(List<ChatMessage> messages) {
        for (ChatMessage message : messages.reversed()) {
            if (message instanceof UserMessage && !CompactionMessages.isSummary(message)) {
                return message;
            }
        }
        return null;
    }

    private static void checkLatestQuestionKept(ChatMessage latestQuestion, List<ChatMessage> compacted,
            List<String> violations) {
        if (!compacted.contains(latestQuestion)) {
            violations.add("latest user message dropped: " + abbreviate(latestQuestion));
        }
    }

    private static void checkStartsWithUserMessage(List<ChatMessage> compacted, List<String> violations) {
        for (ChatMessage message : compacted) {
            if (message instanceof SystemMessage) {
                continue;
            }
            if (!(message instanceof UserMessage)) {
                violations.add("view starts with " + abbreviate(message) + " instead of a user message");
            }
            return;
        }
    }

    private static int indexOf(List<ChatMessage> list, ChatMessage message, int from) {
        for (int i = from; i < list.size(); i++) {
            if (list.get(i).equals(message)) {
                return i;
            }
        }
        return -1;
    }

    private static String abbreviate(ChatMessage message) {
        String text = CompactionMessages.text(message);
        return message.type() + "(" + (text.length() > 40 ? text.substring(0, 40) + "..." : text) + ")";
    }
}
