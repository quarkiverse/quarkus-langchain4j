package io.quarkiverse.langchain4j.testing.evaluation.compaction;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Evaluates compaction profiles on a recorded conversation, in a {@code @QuarkusTest}.
 * <p>
 * The conversation is replayed through the chat memory of each profile, then each recall question is asked to the
 * same model with the compacted view, the full history and no history: the {@link AnswerModel} field of the test
 * class, or the default chat model. The {@link JudgeModel} field, or the answering model, grades the compacted
 * answers against the full-history answer, and the full-history answer against the expected facts. The test fails
 * when a profile is below {@link #minRecall()} or {@link #minSavings()}; the method can declare a
 * {@link CompactionEvaluationReport} parameter to assert more.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Test
@ExtendWith(CompactionEvaluationExtension.class)
public @interface CompactionEvaluation {

    /**
     * Classpath resource of the conversation, one JSON message per line: {@code role} (system, user, assistant,
     * tool_call or tool_result), {@code text}, {@code name} and {@code id} (tools), {@code arguments} (tool_call) and
     * an optional ISO-8601 {@code ts}.
     */
    String conversation();

    /**
     * Classpath resource of the recall questions: a JSON array of {@code {"question": "...", "expected": ["..."]}},
     * {@code expected} being optional.
     */
    String questions();

    /**
     * The compaction profiles to evaluate ({@code default} for the default one). Empty: the default profile and every
     * configured profile.
     */
    String[] profiles() default {};

    /**
     * How many times each question is asked, to smooth the variance of the models.
     */
    int repeats() default 1;

    /**
     * Minimum recall (0 to 1) of every profile.
     */
    double minRecall() default 0.0;

    /**
     * Minimum share (0 to 1) of prompt tokens every profile saves over the full history.
     */
    double minSavings() default 0.0;

    /**
     * Directory of the markdown report.
     */
    String reportDir() default "target/compaction-evaluation";
}
