package io.quarkiverse.langchain4j.compaction;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;

/**
 * Generates valid, varied conversations from a seed, to check properties every strategy must have: short and long
 * turns, parallel tool calls, tool calls with and without text, large outputs, repeated polling calls, a summary left
 * by an earlier compaction, a last turn without answer. The same seed always gives the same conversation.
 */
public final class RandomConversations {

    private static final String[] WORDS = { "order", "refund", "kettle", "grinder", "delivery", "address", "invoice",
            "warranty", "tracking", "customer", "email", "status", "shipped", "returned", "parcel", "label", "courier",
            "the", "is", "my", "when", "where", "please", "check", "again", "today", "tomorrow", "late", "broken" };

    private RandomConversations() {
    }

    public record Generated(List<ChatMessage> messages, List<Instant> timestamps) {
    }

    public static List<ChatMessage> generate(long seed) {
        return generate(seed, Instant.EPOCH).messages();
    }

    /**
     * @param now the time of the last message; earlier messages get increasing ages, from seconds to hours
     */
    public static Generated generate(long seed, Instant now) {
        Random random = new Random(seed);
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(Fixtures.SYSTEM);
        if (random.nextInt(5) == 0) {
            messages.add(CompactionMessages.summary("- customer CUST-" + random.nextInt(1000) + " asked about ORD-1"));
        }
        int turns = 1 + random.nextInt(25);
        int call = 0;
        for (int t = 0; t < turns; t++) {
            messages.add(UserMessage.from("Turn " + t + ": " + words(random, 3 + random.nextInt(40))));
            int exchanges = random.nextInt(3);
            for (int e = 0; e < exchanges; e++) {
                if (random.nextInt(4) == 0) {
                    // polling: identical calls with identical outputs, only the ids differ
                    String output = random.nextBoolean() ? "DOWN" : "pending " + "z".repeat(random.nextInt(600));
                    int repeats = 2 + random.nextInt(3);
                    for (int k = 0; k < repeats; k++) {
                        String id = "call-" + call++;
                        messages.add(AiMessage.from(request(id, "health", "{}")));
                        messages.add(ToolExecutionResultMessage.from(id, "health", output));
                    }
                    continue;
                }
                int parallel = random.nextInt(4) == 0 ? 2 + random.nextInt(2) : 1;
                List<ToolExecutionRequest> requests = new ArrayList<>();
                for (int k = 0; k < parallel; k++) {
                    requests.add(request("call-" + call++, "lookupOrder", "{\"order\":" + random.nextInt(9999) + "}"));
                }
                String text = random.nextInt(4) == 0 ? "Let me check that." : null;
                messages.add(AiMessage.builder().text(text).toolExecutionRequests(requests).build());
                for (ToolExecutionRequest request : requests) {
                    int size = random.nextInt(3) == 0 ? 500 + random.nextInt(3000) : random.nextInt(150);
                    messages.add(ToolExecutionResultMessage.from(request,
                            "result for " + request.arguments() + " " + "x".repeat(size)));
                }
            }
            boolean lastWithoutAnswer = t == turns - 1 && random.nextInt(8) == 0;
            if (!lastWithoutAnswer) {
                messages.add(AiMessage.from("Answer " + t + ": " + words(random, 3 + random.nextInt(60))));
            }
        }

        List<Instant> timestamps = new ArrayList<>();
        Instant time = now;
        for (int i = messages.size() - 1; i >= 0; i--) {
            timestamps.add(0, time);
            time = time.minus(Duration.ofSeconds(random.nextInt(1800)));
        }
        return new Generated(messages, timestamps);
    }

    private static ToolExecutionRequest request(String id, String name, String arguments) {
        return ToolExecutionRequest.builder().id(id).name(name).arguments(arguments).build();
    }

    private static String words(Random random, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(random.nextInt(10) == 0 ? "ORD-" + random.nextInt(9999) : WORDS[random.nextInt(WORDS.length)]);
        }
        return sb.toString();
    }
}
