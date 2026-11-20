package io.quarkiverse.langchain4j.mockapi.internal.server;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.ChatRequestView;
import io.quarkiverse.langchain4j.mockapi.Operation;
import io.quarkiverse.langchain4j.mockapi.RawResponse;
import io.quarkiverse.langchain4j.mockapi.Reply;
import io.quarkiverse.langchain4j.mockapi.internal.model.RawStub;
import io.quarkiverse.langchain4j.mockapi.internal.model.ReplyRule;
import tools.jackson.core.JsonPointer;
import tools.jackson.databind.JsonNode;

/**
 * In-memory, thread-safe state of the mock server: reply queues, defaults, raw stubs and the request journal.
 */
final class MockState {

    private final Map<Operation, Reply> builtInDefaults;
    private final Map<Operation, ConcurrentLinkedQueue<Reply>> queues = new ConcurrentHashMap<>();
    private final Map<Operation, Reply> userDefaults = new ConcurrentHashMap<>();
    private final List<CompiledStub> rawStubs = new CopyOnWriteArrayList<>();
    private final List<CompiledRule> rules = new CopyOnWriteArrayList<>();
    private final ConcurrentSkipListMap<Long, CapturedRequest> journal = new ConcurrentSkipListMap<>();
    private final AtomicLong index = new AtomicLong();

    MockState(Map<Operation, Reply> builtInDefaults) {
        this.builtInDefaults = new EnumMap<>(Operation.class);
        this.builtInDefaults.putAll(builtInDefaults);
    }

    void enqueue(Operation op, List<Reply> replies) {
        queues.computeIfAbsent(op, o -> new ConcurrentLinkedQueue<>()).addAll(replies);
    }

    void setDefault(Operation op, Reply reply) {
        userDefaults.put(op, reply);
    }

    void addRule(ReplyRule rule) {
        if (rule.userMessageContains() == null || rule.replies() == null || rule.replies().isEmpty()) {
            throw new IllegalArgumentException("a rule needs a text to match and at least one reply");
        }
        rules.add(new CompiledRule(rule));
    }

    /**
     * The reply for a request: the first rule matching its last user message, else the next queued reply, else the
     * default.
     */
    Reply next(Operation op, ChatRequestView chat) {
        if (op == Operation.CHAT && chat != null) {
            String userMessage = lastUserMessage(chat);
            if (userMessage != null) {
                for (CompiledRule rule : rules) {
                    if (userMessage.contains(rule.rule().userMessageContains())) {
                        return rule.next();
                    }
                }
            }
        }
        return next(op);
    }

    private static String lastUserMessage(ChatRequestView chat) {
        List<ChatRequestView.Message> messages = chat.messages();
        for (int i = messages.size() - 1; i >= 0; i--) {
            if ("user".equals(messages.get(i).role())) {
                return messages.get(i).text();
            }
        }
        return null;
    }

    Reply next(Operation op) {
        ConcurrentLinkedQueue<Reply> queue = queues.get(op);
        if (queue != null) {
            Reply reply = queue.poll();
            if (reply != null) {
                return reply;
            }
        }
        Reply userDefault = userDefaults.get(op);
        return userDefault != null ? userDefault : builtInDefaults.get(op);
    }

    void addRawStub(RawStub r) {
        if (r.responses() == null || r.responses().isEmpty()) {
            throw new IllegalArgumentException("a raw stub needs at least one response");
        }
        // compile the pointer up front so a malformed one is rejected here instead of failing every model request
        JsonPointer pointer = r.bodyJsonPointer() == null ? null : JsonPointer.compile(r.bodyJsonPointer());
        rawStubs.add(new CompiledStub(r, globToPattern(r.pathGlob()), pointer));
    }

    Optional<CompiledStub> matchRaw(String method, String path, JsonNode bodyOrNull) {
        for (CompiledStub c : rawStubs) {
            if (c.stub().method().equalsIgnoreCase(method) && c.pattern().matcher(path).matches()
                    && c.bodyMatches(bodyOrNull)) {
                return Optional.of(c);
            }
        }
        return Optional.empty();
    }

    long nextIndex() {
        return index.incrementAndGet();
    }

    void record(CapturedRequest r) {
        journal.put(r.index(), r);
    }

    List<CapturedRequest> requests(Operation filterOrNull) {
        return journal.values().stream()
                .filter(r -> filterOrNull == null || r.operation() == filterOrNull)
                .toList();
    }

    void reset() {
        queues.clear();
        userDefaults.clear();
        rawStubs.clear();
        rules.clear();
        journal.clear();
    }

    private static Pattern globToPattern(String glob) {
        StringBuilder regex = new StringBuilder();
        int i = 0;
        while (i < glob.length()) {
            if (glob.startsWith("**", i)) {
                regex.append(".*");
                i += 2;
            } else if (glob.charAt(i) == '*') {
                regex.append("[^/]*");
                i++;
            } else {
                int next = i;
                while (next < glob.length() && glob.charAt(next) != '*') {
                    next++;
                }
                regex.append(Pattern.quote(glob.substring(i, next)));
                i = next;
            }
        }
        return Pattern.compile(regex.toString());
    }

    private static final class CompiledRule {

        private final ReplyRule rule;
        private final Deque<Reply> replies;

        private CompiledRule(ReplyRule rule) {
            this.rule = rule;
            this.replies = new ArrayDeque<>(rule.replies());
        }

        ReplyRule rule() {
            return rule;
        }

        /**
         * The replies are consumed in order and the last one repeats.
         */
        synchronized Reply next() {
            return replies.size() > 1 ? replies.poll() : replies.peek();
        }
    }

    static final class CompiledStub {

        private final RawStub stub;
        private final Pattern pattern;
        private final JsonPointer bodyJsonPointer;
        private final Deque<RawResponse> responses;

        private CompiledStub(RawStub stub, Pattern pattern, JsonPointer bodyJsonPointer) {
            this.stub = stub;
            this.pattern = pattern;
            this.bodyJsonPointer = bodyJsonPointer;
            this.responses = new ArrayDeque<>(stub.responses());
        }

        RawStub stub() {
            return stub;
        }

        Pattern pattern() {
            return pattern;
        }

        /**
         * The response for the current request: the responses are consumed in order and the last one repeats.
         */
        synchronized RawResponse next() {
            return responses.size() > 1 ? responses.poll() : responses.peek();
        }

        boolean bodyMatches(JsonNode body) {
            if (bodyJsonPointer == null) {
                return true;
            }
            if (body == null) {
                return false;
            }
            JsonNode node = body.at(bodyJsonPointer);
            return !node.isMissingNode() && node.asString().equals(stub.bodyExpected());
        }
    }
}
