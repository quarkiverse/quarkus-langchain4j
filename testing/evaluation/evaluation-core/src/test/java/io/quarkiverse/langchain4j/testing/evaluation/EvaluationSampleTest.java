package io.quarkiverse.langchain4j.testing.evaluation;

import static org.assertj.core.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

class EvaluationSampleTest {

    @Test
    void builderShouldCreateEvaluationSample() {
        Parameters parameters = Parameters.of(1, "test", 3.14);
        EvaluationSample<String> sample = EvaluationSample.<String> builder()
                .withName("Sample1")
                .withParameters(parameters)
                .withExpectedOutput("Output")
                .withTags("tag1", "tag2")
                .build();

        assertThat(sample.name()).isEqualTo("Sample1");
        assertThat(sample.parameters()).isEqualTo(parameters);
        assertThat(sample.expectedOutput()).isEqualTo("Output");
        assertThat(sample.tags()).containsExactly("tag1", "tag2");
    }

    @Test
    void builderShouldThrowExceptionIfNameIsNull() {
        Parameters parameters = Parameters.of(1, "test", 3.14);

        assertThatThrownBy(() -> EvaluationSample.<String> builder()
                .withParameters(parameters)
                .withExpectedOutput("Output")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Name must not be null");
    }

    @Test
    void builderShouldThrowExceptionIfParametersAreNull() {
        assertThatThrownBy(() -> EvaluationSample.<String> builder()
                .withName("Sample1")
                .withParameters(null)
                .withExpectedOutput("Output")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Parameters must not be null");
    }

    @Test
    void builderShouldThrowExceptionIfParametersAreEmpty() {
        assertThatThrownBy(() -> EvaluationSample.<String> builder()
                .withName("Sample1")
                .withParameters(new Parameters())
                .withExpectedOutput("Output")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Parameters must not be empty");
    }

    @Test
    void builderShouldThrowExceptionIfExpectedOutputIsNull() {
        Parameters parameters = Parameters.of(1, "test", 3.14);

        assertThatThrownBy(() -> EvaluationSample.<String> builder()
                .withName("Sample1")
                .withParameters(parameters)
                .withExpectedOutput(null)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Expected output must not be null");
    }

    @Test
    void builderWithParametersShouldAddParametersToEvaluationSample() {
        Parameters parameters = new Parameters().add(1).add("test");
        EvaluationSample<Integer> sample = EvaluationSample.<Integer> builder()
                .withName("Sample2")
                .withParameters(parameters)
                .withExpectedOutput(42)
                .build();

        assertThat(sample.parameters()).isEqualTo(parameters);
        assertThat(sample.parameters().get(0, Integer.class)).isEqualTo(1);
        assertThat(sample.parameters().get(1, String.class)).isEqualTo("test");
    }

    @Test
    void builderWithParameterShouldAddIndividualParameter() {
        EvaluationSample<Integer> sample = EvaluationSample.<Integer> builder()
                .withName("Sample3")
                .withParameter(new Parameter.UnnamedParameter("test"))
                .withExpectedOutput(42)
                .build();

        assertThat(sample.parameters().size()).isEqualTo(1);
        assertThat((String) sample.parameters().get(0)).isEqualTo("test");
    }

    @Test
    void builderWithParameterStringShouldAddStringParameter() {
        EvaluationSample<Integer> sample = EvaluationSample.<Integer> builder()
                .withName("Sample4")
                .withParameter("string-param")
                .withExpectedOutput(42)
                .build();

        assertThat(sample.parameters().size()).isEqualTo(1);
        assertThat((String) sample.parameters().get(0)).isEqualTo("string-param");
    }

    @Test
    void builderWithTagsShouldAddTagsToEvaluationSample() {
        EvaluationSample<String> sample = EvaluationSample.<String> builder()
                .withName("Sample5")
                .withParameters(Parameters.of(1, 2))
                .withExpectedOutput("result")
                .withTags(List.of("tag1", "tag2"))
                .build();

        assertThat(sample.tags()).containsExactly("tag1", "tag2");
    }

    @Test
    void builderWithTagShouldAddSingleTag() {
        EvaluationSample<String> sample = EvaluationSample.<String> builder()
                .withName("Sample6")
                .withParameters(Parameters.of(1, 2))
                .withExpectedOutput("result")
                .withTag("tag1")
                .build();

        assertThat(sample.tags()).containsExactly("tag1");
    }

    @Test
    void builderWithTagsVarargsShouldAddMultipleTags() {
        EvaluationSample<String> sample = EvaluationSample.<String> builder()
                .withName("Sample7")
                .withParameters(Parameters.of(1, 2))
                .withExpectedOutput("result")
                .withTags("tag1", "tag2", "tag3")
                .build();

        assertThat(sample.tags()).containsExactly("tag1", "tag2", "tag3");
    }
}
