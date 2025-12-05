package io.quarkiverse.langchain4j.testing.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;

class EvaluationResultTest {

    @Test
    void shouldCreatePassedResultWithScore() {
        EvaluationResult result = EvaluationResult.passed(0.95);

        assertThat(result.passed()).isTrue();
        assertThat(result.score()).isEqualTo(0.95);
        assertThat(result.explanation()).isNull();
        assertThat(result.metadata()).isEmpty();
    }

    @Test
    void shouldCreateFailedResultWithScoreAndExplanation() {
        EvaluationResult result = EvaluationResult.failed(0.75, "Score too low");

        assertThat(result.passed()).isFalse();
        assertThat(result.score()).isEqualTo(0.75);
        assertThat(result.explanation()).isEqualTo("Score too low");
        assertThat(result.metadata()).isEmpty();
    }

    @Test
    void shouldCreateFailedResultWithExplanationOnly() {
        EvaluationResult result = EvaluationResult.failed("Invalid format");

        assertThat(result.passed()).isFalse();
        assertThat(result.score()).isEqualTo(0.0);
        assertThat(result.explanation()).isEqualTo("Invalid format");
        assertThat(result.metadata()).isEmpty();
    }

    @Test
    void shouldCreateResultFromBoolean() {
        EvaluationResult passed = EvaluationResult.fromBoolean(true);
        assertThat(passed.passed()).isTrue();
        assertThat(passed.score()).isEqualTo(1.0);
        assertThat(passed.explanation()).isNull();
        assertThat(passed.metadata()).isEmpty();

        EvaluationResult failed = EvaluationResult.fromBoolean(false);
        assertThat(failed.passed()).isFalse();
        assertThat(failed.score()).isEqualTo(0.0);
        assertThat(failed.explanation()).isNull();
        assertThat(failed.metadata()).isEmpty();
    }

    @Test
    void shouldCreateResultWithMetadata() {
        Map<String, Object> metadata = Map.of(
                "similarity", 0.87,
                "threshold", 0.90);

        EvaluationResult result = new EvaluationResult(
                false,
                0.87,
                "Below threshold",
                metadata);

        assertThat(result.passed()).isFalse();
        assertThat(result.score()).isEqualTo(0.87);
        assertThat(result.explanation()).isEqualTo("Below threshold");
        assertThat(result.metadata()).containsEntry("similarity", 0.87);
        assertThat(result.metadata()).containsEntry("threshold", 0.90);
    }

    @Test
    void shouldAddMetadataToExistingResult() {
        EvaluationResult original = EvaluationResult.passed(0.95);

        EvaluationResult withMetadata = original.withMetadata(
                Map.of("strategy", "semantic-similarity"));

        assertThat(withMetadata.passed()).isTrue();
        assertThat(withMetadata.score()).isEqualTo(0.95);
        assertThat(withMetadata.explanation()).isNull();
        assertThat(withMetadata.metadata()).containsEntry("strategy", "semantic-similarity");

        // Original should be unchanged
        assertThat(original.metadata()).isEmpty();
    }

    @Test
    void shouldAddExplanationToExistingResult() {
        EvaluationResult original = EvaluationResult.passed(0.85);

        EvaluationResult withExplanation = original.withExplanation("Good match");

        assertThat(withExplanation.passed()).isTrue();
        assertThat(withExplanation.score()).isEqualTo(0.85);
        assertThat(withExplanation.explanation()).isEqualTo("Good match");
        assertThat(withExplanation.metadata()).isEmpty();

        // Original should be unchanged
        assertThat(original.explanation()).isNull();
    }

    @Test
    void shouldReplaceExplanationWhenUsingWithExplanation() {
        EvaluationResult original = EvaluationResult.failed(0.5, "Original explanation");

        EvaluationResult replaced = original.withExplanation("New explanation");

        assertThat(replaced.explanation()).isEqualTo("New explanation");
        assertThat(original.explanation()).isEqualTo("Original explanation");
    }

    @Test
    void shouldCombineMetadataWhenUsingWithMetadata() {
        EvaluationResult original = new EvaluationResult(
                true,
                0.9,
                null,
                Map.of("key1", "value1"));

        EvaluationResult combined = original.withMetadata(
                Map.of("key2", "value2"));

        assertThat(combined.metadata()).containsEntry("key1", "value1");
        assertThat(combined.metadata()).containsEntry("key2", "value2");
        assertThat(combined.metadata()).hasSize(2);
    }

    @Test
    void shouldOverrideMetadataKeysWhenCombining() {
        EvaluationResult original = new EvaluationResult(
                true,
                0.9,
                null,
                Map.of("key", "original"));

        EvaluationResult overridden = original.withMetadata(
                Map.of("key", "new"));

        assertThat(overridden.metadata()).containsEntry("key", "new");
        assertThat(overridden.metadata()).hasSize(1);
    }

    @Test
    void shouldRejectInvalidScores() {
        assertThatThrownBy(() -> new EvaluationResult(true, -0.1, null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Score must be between 0.0 and 1.0");

        assertThatThrownBy(() -> new EvaluationResult(true, 1.1, null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Score must be between 0.0 and 1.0");

        assertThatThrownBy(() -> EvaluationResult.passed(-0.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Score must be between 0.0 and 1.0");

        assertThatThrownBy(() -> EvaluationResult.failed(1.5, "Invalid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Score must be between 0.0 and 1.0");
    }

    @Test
    void shouldAcceptBoundaryScores() {
        EvaluationResult min = EvaluationResult.passed(0.0);
        assertThat(min.score()).isEqualTo(0.0);

        EvaluationResult max = EvaluationResult.passed(1.0);
        assertThat(max.score()).isEqualTo(1.0);
    }

    @Test
    void shouldMakeMetadataImmutable() {
        Map<String, Object> mutableMetadata = new java.util.HashMap<>();
        mutableMetadata.put("key", "value");

        EvaluationResult result = new EvaluationResult(true, 1.0, null, mutableMetadata);

        // Modifying the original map should not affect the result
        mutableMetadata.put("new-key", "new-value");

        assertThat(result.metadata()).hasSize(1);
        assertThat(result.metadata()).doesNotContainKey("new-key");

        // The metadata in result should be immutable
        assertThatThrownBy(() -> result.metadata().put("another-key", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldHandleNullMetadata() {
        EvaluationResult result = new EvaluationResult(true, 1.0, null, null);

        assertThat(result.metadata()).isNotNull();
        assertThat(result.metadata()).isEmpty();
    }

    @Test
    void shouldAllowNullExplanation() {
        EvaluationResult result = EvaluationResult.passed(0.95);

        assertThat(result.explanation()).isNull();

        EvaluationResult withNull = result.withExplanation(null);
        assertThat(withNull.explanation()).isNull();
    }

    @Test
    void shouldPreserveAllFieldsWhenAddingExplanation() {
        EvaluationResult original = new EvaluationResult(
                false,
                0.75,
                null,
                Map.of("key", "value"));

        EvaluationResult withExplanation = original.withExplanation("Failed due to low score");

        assertThat(withExplanation.passed()).isEqualTo(original.passed());
        assertThat(withExplanation.score()).isEqualTo(original.score());
        assertThat(withExplanation.explanation()).isEqualTo("Failed due to low score");
        assertThat(withExplanation.metadata()).isEqualTo(original.metadata());
    }

    @Test
    void shouldPreserveAllFieldsWhenAddingMetadata() {
        EvaluationResult original = new EvaluationResult(
                true,
                0.92,
                "Excellent match",
                Map.of());

        EvaluationResult withMetadata = original.withMetadata(
                Map.of("duration", 150));

        assertThat(withMetadata.passed()).isEqualTo(original.passed());
        assertThat(withMetadata.score()).isEqualTo(original.score());
        assertThat(withMetadata.explanation()).isEqualTo(original.explanation());
        assertThat(withMetadata.metadata()).containsEntry("duration", 150);
    }
}
