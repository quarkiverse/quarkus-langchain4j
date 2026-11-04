package io.quarkiverse.langchain4j.compaction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;

/**
 * A structured view of a conversation history: system messages, atomic {@link Segment segments} and
 * {@link Turn turns}.
 * <p>
 * Strategies should drop or keep whole segments: a segment groups an assistant message requesting tools with the
 * results of those tools, so working at the segment level preserves tool call pairing by construction.
 */
public final class Conversation {

    public enum Kind {
        /** A user message. */
        USER,
        /** A summary inserted by compaction. */
        SUMMARY,
        /** An assistant message without tool calls. */
        AI,
        /** An assistant message with tool calls, followed by the tool results. */
        TOOL_EXCHANGE,
        /** A tool result without a matching tool call. Never valid in a prompt; strategies drop it. */
        ORPHAN_TOOL_RESULT,
        /** Any other message type. */
        OTHER
    }

    /**
     * An atomic group of messages that must be kept or dropped together.
     */
    public record Segment(Kind kind, List<ChatMessage> messages) {

        public Segment {
            messages = List.copyOf(messages);
        }

        public ChatMessage first() {
            return messages.get(0);
        }

        public String text() {
            StringBuilder sb = new StringBuilder();
            for (ChatMessage message : messages) {
                if (!sb.isEmpty()) {
                    sb.append('\n');
                }
                sb.append(CompactionMessages.text(message));
            }
            return sb.toString();
        }
    }

    /**
     * A user message (or a summary) and everything that follows until the next one.
     */
    public record Turn(int index, List<Segment> segments) {

        public Turn {
            segments = List.copyOf(segments);
        }

        public List<ChatMessage> messages() {
            List<ChatMessage> result = new ArrayList<>();
            segments.forEach(s -> result.addAll(s.messages()));
            return result;
        }

        public ChatMessage first() {
            return segments.get(0).first();
        }

        public String text() {
            StringBuilder sb = new StringBuilder();
            for (Segment segment : segments) {
                if (!sb.isEmpty()) {
                    sb.append('\n');
                }
                sb.append(segment.text());
            }
            return sb.toString();
        }

        public boolean isSummary() {
            return segments.get(0).kind() == Kind.SUMMARY;
        }
    }

    private final List<ChatMessage> system;
    private final List<Segment> segments;

    private Conversation(List<ChatMessage> system, List<Segment> segments) {
        this.system = Collections.unmodifiableList(system);
        this.segments = Collections.unmodifiableList(segments);
    }

    public static Conversation parse(List<ChatMessage> messages) {
        List<ChatMessage> system = new ArrayList<>();
        List<Segment> segments = new ArrayList<>();
        int i = 0;
        while (i < messages.size()) {
            ChatMessage message = messages.get(i);
            if (message instanceof SystemMessage s) {
                system.add(s);
                i++;
            } else if (message instanceof UserMessage u) {
                segments.add(new Segment(CompactionMessages.isSummary(u) ? Kind.SUMMARY : Kind.USER, List.of(u)));
                i++;
            } else if (message instanceof AiMessage ai && ai.hasToolExecutionRequests()) {
                List<ChatMessage> group = new ArrayList<>();
                group.add(ai);
                Set<String> ids = new HashSet<>();
                for (ToolExecutionRequest request : ai.toolExecutionRequests()) {
                    ids.add(request.id());
                }
                i++;
                while (i < messages.size() && messages.get(i) instanceof ToolExecutionResultMessage result
                        && (result.id() == null || ids.isEmpty() || ids.contains(result.id()))) {
                    group.add(result);
                    i++;
                }
                segments.add(new Segment(Kind.TOOL_EXCHANGE, group));
            } else if (message instanceof AiMessage ai) {
                segments.add(new Segment(Kind.AI, List.of(ai)));
                i++;
            } else if (message instanceof ToolExecutionResultMessage r) {
                segments.add(new Segment(Kind.ORPHAN_TOOL_RESULT, List.of(r)));
                i++;
            } else {
                segments.add(new Segment(Kind.OTHER, List.of(message)));
                i++;
            }
        }
        return new Conversation(system, segments);
    }

    public List<ChatMessage> system() {
        return system;
    }

    public List<Segment> segments() {
        return segments;
    }

    /**
     * Reconstructs the flat message list: system messages followed by all segment messages.
     */
    public List<ChatMessage> toMessages() {
        List<ChatMessage> result = new ArrayList<>(system);
        for (Segment segment : segments) {
            result.addAll(segment.messages());
        }
        return result;
    }

    /**
     * @return the segment holding the latest user message (summaries excluded), or {@code null} if there is none
     */
    public Segment latestQuestion() {
        for (int i = segments.size() - 1; i >= 0; i--) {
            if (segments.get(i).kind() == Kind.USER) {
                return segments.get(i);
            }
        }
        return null;
    }

    /**
     * Puts the latest user message back in front of {@code kept} when a window dropped it while keeping later segments
     * of the same turn, typically the tool calls made to answer it. {@code kept} must be a suffix of the segments. Without it,
     * the model would see tool results
     * without the question that triggered them.
     *
     * @param kept the segments a strategy keeps, in order; modified in place
     * @return {@code true} if the latest user message was added
     */
    public boolean keepLatestQuestion(List<Segment> kept) {
        Segment question = latestQuestion();
        if (question == null || kept.isEmpty() || kept.stream().anyMatch(s -> s == question)) {
            return false;
        }
        kept.add(0, question);
        return true;
    }

    /**
     * Groups segments into turns. A new turn starts at each user message or summary; segments before the first user
     * message form turn 0.
     */
    public List<Turn> turns() {
        List<Turn> turns = new ArrayList<>();
        List<Segment> current = new ArrayList<>();
        for (Segment segment : segments) {
            boolean startsTurn = segment.kind() == Kind.USER || segment.kind() == Kind.SUMMARY;
            if (startsTurn && !current.isEmpty()) {
                turns.add(new Turn(turns.size(), current));
                current = new ArrayList<>();
            }
            current.add(segment);
        }
        if (!current.isEmpty()) {
            turns.add(new Turn(turns.size(), current));
        }
        return turns;
    }

    /**
     * Rebuilds a message list: system messages first, then the given segments, skipping orphan tool results.
     */
    public List<ChatMessage> assemble(List<Segment> kept) {
        List<ChatMessage> result = new ArrayList<>(system);
        for (Segment segment : kept) {
            if (segment.kind() != Kind.ORPHAN_TOOL_RESULT) {
                result.addAll(segment.messages());
            }
        }
        return result;
    }

    /**
     * Rebuilds a message list: system messages first, then the messages of the given turns.
     */
    public List<ChatMessage> assembleTurns(List<Turn> kept) {
        List<Segment> segments = new ArrayList<>();
        kept.forEach(t -> segments.addAll(t.segments()));
        return assemble(segments);
    }
}
