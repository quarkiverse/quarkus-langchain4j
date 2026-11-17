package io.quarkiverse.langchain4j.testing.evaluation.compaction;

import static io.quarkiverse.langchain4j.testing.evaluation.compaction.CompactionEvaluationReport.FULL_HISTORY;
import static io.quarkiverse.langchain4j.testing.evaluation.compaction.CompactionEvaluationReport.NO_HISTORY;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.enterprise.inject.spi.CDI;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.compaction.CompactionMessages;
import io.quarkiverse.langchain4j.compaction.CompactionProfiles;
import io.quarkiverse.langchain4j.compaction.CompactionReport;
import io.quarkiverse.langchain4j.compaction.TokenCounter;
import io.quarkiverse.langchain4j.compaction.runtime.CompactionConfig;
import io.quarkiverse.langchain4j.compaction.runtime.DefaultCompactionProfiles;
import io.quarkiverse.langchain4j.testing.evaluation.compaction.CompactionEvaluationReport.Answer;
import io.quarkiverse.langchain4j.testing.evaluation.compaction.CompactionEvaluationReport.Grade;
import io.quarkiverse.langchain4j.testing.evaluation.compaction.CompactionEvaluationReport.Result;
import io.quarkiverse.langchain4j.testing.evaluation.compaction.Scenario.RecallQuestion;

/**
 * Replays a scenario through compaction profiles and grades the answers to its recall questions. Needs a running
 * Quarkus application ({@code @QuarkusTest}).
 */
final class CompactionEvaluator {

    static final String ANSWER_INSTRUCTIONS = "\nAnswer in one or two sentences, using only this conversation.";

    static final String JUDGE_INSTRUCTIONS = """
            You grade the answer an AI assistant gave to a question about an earlier conversation.
            Compare it with the reference answer and the expected facts, when given.
            - CORRECT: it states the same facts as the reference, possibly reworded or formatted differently.
            - PARTIAL: it states some of these facts, others are missing.
            - WRONG: it states none of them, contradicts one, or says it does not know.
            Reply with exactly two lines:
            VERDICT: CORRECT, PARTIAL or WRONG
            REASON: one short sentence""";

    private static final Pattern VERDICT = Pattern.compile("VERDICT:\\s*(CORRECT|PARTIAL|WRONG)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern REASON = Pattern.compile("REASON:\\s*(.+)", Pattern.CASE_INSENSITIVE);

    private final CompactionProfiles profiles;
    private final TokenCounter counter;
    private final Replay replay;
    private final ChatModel answerer;
    private final ChatModel judge;

    /**
     * @param answerer answers with every view, so the compacted answers compare with the full-history one
     */
    CompactionEvaluator(ChatModel answerer, ChatModel judge) {
        CDI<Object> cdi = CDI.current();
        this.profiles = cdi.select(CompactionProfiles.class).get();
        this.counter = cdi.select(TokenCounter.class).get();
        this.replay = cdi.select(Replay.class).get();
        this.answerer = answerer;
        this.judge = judge;
    }

    CompactionEvaluationReport evaluate(Scenario scenario, List<String> requested, int repeats) {
        List<String> names = new ArrayList<>(requested);
        if (names.isEmpty()) {
            names.add(DefaultCompactionProfiles.DEFAULT);
            names.addAll(profiles.names());
        }
        List<ChatMessage> full = scenario.messages();
        List<ChatMessage> system = full.stream().filter(m -> m instanceof SystemMessage).toList();
        long fullTokens = 0;
        int calls = 0;
        for (int i = 0; i < full.size(); i++) {
            if (full.get(i) instanceof AiMessage) {
                fullTokens += counter.count(full.subList(0, i));
                calls++;
            }
        }
        long fullPrompt = fullTokens;

        Map<String, Replayed> runs = new LinkedHashMap<>();
        for (String name : names) {
            runs.put(name, replay(scenario, name));
        }

        List<Answer> answers = new ArrayList<>();
        for (int repeat = 0; repeat < repeats; repeat++) {
            for (RecallQuestion question : scenario.questions()) {
                String reference = answer(full, question);
                if (!question.expected().isEmpty()) {
                    answers.add(grade(question, repeat, FULL_HISTORY, null, reference));
                }
                for (Map.Entry<String, Replayed> run : runs.entrySet()) {
                    answers.add(grade(question, repeat, run.getKey(), reference,
                            answer(run.getValue().view(), question)));
                }
                answers.add(grade(question, repeat, NO_HISTORY, reference, answer(system, question)));
            }
        }

        List<Result> results = new ArrayList<>();
        results.add(
                new Result(FULL_HISTORY, recall(answers, FULL_HISTORY), fullPrompt, counter.count(full), 0, 0, 0, List.of()));
        runs.forEach((name, run) -> results.add(new Result(name, recall(answers, name), run.promptTokens(),
                counter.count(run.view()), savings(run.promptTokens(), fullPrompt), run.reports().size(),
                (int) run.reports().stream().filter(CompactionReport::fallback).count(),
                run.view().stream().filter(CompactionMessages::isSummary).map(CompactionMessages::text).toList())));
        long noPrompt = (long) calls * counter.count(system);
        results.add(new Result(NO_HISTORY, recall(answers, NO_HISTORY), noPrompt, counter.count(system),
                savings(noPrompt, fullPrompt), 0, 0, List.of()));
        return new CompactionEvaluationReport(scenario.name(), results, answers);
    }

    private record Replayed(List<ChatMessage> view, long promptTokens, List<CompactionReport> reports) {
    }

    private Replayed replay(Scenario scenario, String name) {
        String profile = DefaultCompactionProfiles.DEFAULT.equals(name) ? CompactionConfig.DEFAULT_PROFILE : name;
        String memoryId = "compaction-evaluation/" + name + "/" + UUID.randomUUID();
        ChatMemory memory = profiles.provider(profile).get(memoryId);
        replay.start(scenario.timestamps().stream().filter(Objects::nonNull).findFirst().orElse(Instant.now()));
        try {
            long prompt = 0;
            for (int i = 0; i < scenario.messages().size(); i++) {
                Instant recorded = scenario.timestamps().get(i);
                if (recorded != null) {
                    replay.moveTo(recorded);
                }
                ChatMessage message = scenario.messages().get(i);
                if (message instanceof AiMessage) {
                    // the model was called with the context it had before answering
                    prompt += counter.count(memory.messages());
                }
                memory.add(message);
            }
            List<ChatMessage> view = List.copyOf(memory.messages());
            List<CompactionReport> reports = replay.stop().stream()
                    .filter(r -> memoryId.equals(r.memoryId())).toList();
            return new Replayed(view, prompt, reports);
        } finally {
            replay.stop();
            memory.clear();
        }
    }

    private String answer(List<ChatMessage> view, RecallQuestion question) {
        List<ChatMessage> prompt = new ArrayList<>(view);
        prompt.add(UserMessage.from(question.question() + ANSWER_INSTRUCTIONS));
        String text = answerer.chat(prompt).aiMessage().text();
        return text == null ? "" : text;
    }

    /**
     * @param reference the full-history answer, {@code null} to grade against the expected facts only
     */
    private Answer grade(RecallQuestion question, int repeat, String view, String reference, String answer) {
        if (!question.expected().isEmpty() && containsAll(answer, question.expected())) {
            // no need to pay for a judge call
            return new Answer(question.question(), repeat, view, answer, Grade.CORRECT, "all expected facts found");
        }
        StringBuilder request = new StringBuilder("Question: ").append(question.question()).append('\n');
        if (!question.expected().isEmpty()) {
            request.append("Expected facts: ").append(String.join("; ", question.expected())).append('\n');
        }
        if (reference != null) {
            request.append("Reference answer: ").append(reference).append('\n');
        }
        request.append("Answer to grade: ").append(answer);
        String reply = judge.chat(SystemMessage.from(JUDGE_INSTRUCTIONS), UserMessage.from(request.toString()))
                .aiMessage().text();
        String[] verdict = parse(reply);
        return new Answer(question.question(), repeat, view, answer, Grade.valueOf(verdict[0]), verdict[1]);
    }

    /**
     * @return the grade name and the reason; an unreadable reply is a wrong answer
     */
    static String[] parse(String reply) {
        String text = reply == null ? "" : reply;
        Matcher verdict = VERDICT.matcher(text);
        if (!verdict.find()) {
            return new String[] { Grade.WRONG.name(), "unreadable verdict: " + text.strip() };
        }
        Matcher reason = REASON.matcher(text);
        return new String[] { verdict.group(1).toUpperCase(Locale.ROOT), reason.find() ? reason.group(1).strip() : "" };
    }

    static boolean containsAll(String answer, List<String> facts) {
        String normalized = normalize(answer);
        return facts.stream().allMatch(f -> normalized.contains(normalize(f)));
    }

    private static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
    }

    private static double recall(List<Answer> answers, String view) {
        return answers.stream().filter(a -> a.view().equals(view)).mapToDouble(a -> a.grade().score).average()
                .orElse(Double.NaN);
    }

    private static double savings(long prompt, long fullPrompt) {
        return fullPrompt == 0 ? 0 : 1 - (double) prompt / fullPrompt;
    }
}
