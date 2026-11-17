package io.quarkiverse.langchain4j.testing.evaluation.compaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.compaction.CompactionMessages;
import io.quarkiverse.langchain4j.testing.evaluation.compaction.CompactionEvaluationReport.Grade;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class CompactionEvaluationTest {

    /**
     * The default chat model, required by the compaction summarizer: the evaluation must not use it here.
     */
    @Singleton
    public static class DefaultModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest request) {
            throw new IllegalStateException("the default model must not be called");
        }
    }

    /**
     * Answers with the text of its whole context, so an answer holds a fact only if the context does.
     */
    @Singleton
    @ModelName("answers")
    public static class AnswerFake implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest request) {
            List<ChatMessage> messages = request.messages();
            assertThat(isJudging(messages)).as("grading request sent to the answering model").isFalse();
            String reply = messages.subList(0, messages.size() - 1).stream().map(CompactionMessages::text)
                    .collect(Collectors.joining("\n"));
            return ChatResponse.builder().aiMessage(AiMessage.from(reply)).build();
        }
    }

    /**
     * Grades by looking for the expected facts.
     */
    @Singleton
    @ModelName("judge")
    public static class JudgeFake implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest request) {
            assertThat(isJudging(request.messages())).as("question sent to the judge").isTrue();
            String grading = CompactionMessages.text(request.messages().getLast());
            String facts = grading.replaceAll("(?s).*Expected facts: ([^\n]*).*", "$1");
            String answer = grading.substring(grading.indexOf("Answer to grade: "));
            String reply = CompactionEvaluator.containsAll(answer, List.of(facts.split("; ")))
                    ? "VERDICT: CORRECT\nREASON: same facts"
                    : "VERDICT: WRONG\nREASON: missing " + facts;
            return ChatResponse.builder().aiMessage(AiMessage.from(reply)).build();
        }
    }

    static boolean isJudging(List<ChatMessage> messages) {
        return messages.getFirst() instanceof SystemMessage s && s.text().equals(CompactionEvaluator.JUDGE_INSTRUCTIONS);
    }

    @Inject
    @ModelName("answers")
    @AnswerModel
    ChatModel answers;

    @Inject
    @ModelName("judge")
    @JudgeModel
    ChatModel judge;

    @CompactionEvaluation(conversation = "conversations/support.jsonl", questions = "conversations/support.questions.json", repeats = 2, minRecall = 0.5)
    void comparesTheProfiles(CompactionEvaluationReport report) throws Exception {
        assertThat(report.results()).extracting(CompactionEvaluationReport.Result::name)
                .containsExactly("full-history", "default", "retention", "window", "no-history");
        assertThat(report.recall("full-history")).isEqualTo(1.0);
        assertThat(report.recall("no-history")).isEqualTo(0.0);

        // no compaction: same context, same cost as the full history
        assertThat(report.recall("default")).isEqualTo(1.0);
        assertThat(report.result("default").orElseThrow().savings()).isZero();

        // the window forgets the order number but keeps the recent weather, for fewer tokens
        CompactionEvaluationReport.Result window = report.result("window").orElseThrow();
        assertThat(window.recall()).isEqualTo(0.5);
        assertThat(window.compactions()).isPositive();
        assertThat(window.savings()).isPositive();
        assertThat(window.viewTokens()).isLessThan(report.result("full-history").orElseThrow().viewTokens());

        // the order number was given three hours before the end, on the recorded clock: past the one-hour retention
        assertThat(report.recall("retention")).isEqualTo(0.5);

        assertThat(report.answers()).filteredOn(a -> a.view().equals("window") && a.question().contains("order"))
                .hasSize(2).allSatisfy(a -> assertThat(a.grade()).isEqualTo(Grade.WRONG));

        String markdown = Files.readString(
                Path.of("target/compaction-evaluation/CompactionEvaluationTest-comparesTheProfiles.md"));
        assertThat(markdown).contains("| window | 50% |", "What is my order number?", "## Misses");
    }

    @Test
    void loadsTheConversation() {
        Scenario scenario = Scenario.load("conversations/support.jsonl", "conversations/support.questions.json");
        assertThat(scenario.messages()).hasSize(23).first().isInstanceOf(SystemMessage.class);
        assertThat(scenario.messages().get(20)).isInstanceOfSatisfying(AiMessage.class,
                m -> assertThat(m.toolExecutionRequests()).singleElement()
                        .satisfies(r -> assertThat(r.name()).isEqualTo("weather")));
        assertThat(scenario.messages().get(21)).isInstanceOf(ToolExecutionResultMessage.class);
        assertThat(scenario.timestamps()).doesNotContainNull();
        assertThat(scenario.questions()).hasSize(2);
    }

    @Test
    void printsTheSummariesOfTheFinalViews() {
        CompactionEvaluationReport report = new CompactionEvaluationReport("c", List.of(
                new CompactionEvaluationReport.Result("pipeline", 0.5, 10, 5, 0.5, 1, 0, List.of("- the user said: ORD-1"))),
                List.of());
        assertThat(report.toMarkdown()).contains("## Summaries kept by pipeline", "- the user said: ORD-1");
    }

    @Test
    void readsVerdicts() {
        assertThat(CompactionEvaluator.parse("VERDICT: partial\nREASON: amount missing"))
                .containsExactly("PARTIAL", "amount missing");
        assertThat(CompactionEvaluator.parse("Sure!")[0]).isEqualTo("WRONG");
    }
}
