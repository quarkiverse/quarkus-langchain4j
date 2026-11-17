package io.quarkiverse.langchain4j.sample.compaction;

import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.inject.Inject;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.testing.evaluation.compaction.AnswerModel;
import io.quarkiverse.langchain4j.testing.evaluation.compaction.CompactionEvaluation;
import io.quarkiverse.langchain4j.testing.evaluation.compaction.CompactionEvaluationReport;
import io.quarkiverse.langchain4j.testing.evaluation.compaction.JudgeModel;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Compares the two compaction profiles of the application (the summarizing pipeline of the default profile, and the
 * token window of the {@code window} profile) on two recorded conversations. Reports are written to
 * {@code target/compaction-evaluation}.
 */
@QuarkusTest
@EnabledIfEnvironmentVariable(named = "QUARKUS_LANGCHAIN4J_OPENAI_API_KEY", matches = ".+")
class CompactionEvaluationTest {

    // the model of the assistants answers, with the full history and with each compacted view
    // this needs to be the model used by the application / AI Service, so that the evaluation is realistic.
    @Inject
    @AnswerModel
    ChatModel assistant;

    @Inject
    @ModelName("judge")
    @JudgeModel
    ChatModel judge;

    @CompactionEvaluation(conversation = "conversations/trip-planning.jsonl", // The conversation to evaluate, recorded in a JSONL format
            questions = "conversations/trip-planning.questions.json", // The recall questions to ask the assistant
            profiles = { "default", "window" }, // The compaction profiles to evaluate
            minSavings = 0.25 // The minimum savings in tokens that the compaction profile should achieve
    )
    void tripPlanning(CompactionEvaluationReport report) {
        // the window drops the beginning of the conversation: the booking reference, the constraints, the budget...
        assertTrue(report.recall("window") < report.recall("default"), "window recall");
    }

    @CompactionEvaluation(conversation = "conversations/incident.jsonl", questions = "conversations/incident.questions.json", profiles = {
            "default", "window" }, minSavings = 0.25)
    void incident(CompactionEvaluationReport report) {
        assertTrue(report.recall("window") < report.recall("default"), "window recall");
    }
}
