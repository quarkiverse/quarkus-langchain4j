package io.quarkiverse.langchain4j.testing.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EvaluationTest {

    @Test
    void shouldCreateBuilderSuccessfully() {
        var builder = Evaluation.builder();
        assertThat(builder).isNotNull();
    }

    @Test
    void shouldEvaluateWithFluentAPI() {

        EvaluationSample<String> sample1 = new EvaluationSample<>(
                "Sample1",
                new Parameters().add("input1"),
                "expected1",
                List.of("tag1"));

        EvaluationSample<String> sample2 = new EvaluationSample<>(
                "Sample2",
                new Parameters().add("input2"),
                "expected2",
                List.of("tag2"));

        Samples<String> samples = new Samples<>(sample1, sample2);

        Function<Parameters, String> function = params -> {
            String input = params.get(0);
            return "expected" + input.substring(5); // Convert "input1" -> "expected1"
        };

        EvaluationStrategy<String> strategy = (sample, actual) -> EvaluationResult
                .fromBoolean(actual.equals(sample.expectedOutput()));

        EvaluationReport<String> report = Evaluation.<String> builder()
                .withSamples(samples)
                .evaluate(function)
                .using(strategy)
                .run();

        assertThat(report).isNotNull();
        assertThat(report.score()).isEqualTo(100.0);
        assertThat(report.evaluations()).hasSize(2);
        assertThat(report.evaluations().stream().allMatch(e -> e.passed())).isTrue();
    }

    @Test
    void shouldEvaluateWithConcurrency() {

        EvaluationSample<String> sample1 = new EvaluationSample<>(
                "Sample1",
                new Parameters().add("input1"),
                "expected1",
                List.of());

        EvaluationSample<String> sample2 = new EvaluationSample<>(
                "Sample2",
                new Parameters().add("input2"),
                "expected2",
                List.of());

        Samples<String> samples = new Samples<>(sample1, sample2);

        Function<Parameters, String> function = params -> {
            String input = params.get(0);
            return "expected" + input.substring(5);
        };

        EvaluationStrategy<String> strategy = (sample, actual) -> EvaluationResult
                .fromBoolean(actual.equals(sample.expectedOutput()));

        EvaluationReport<String> report = Evaluation.<String> builder()
                .withSamples(samples)
                .withConcurrency(4)
                .evaluate(function)
                .using(strategy)
                .run();

        assertThat(report).isNotNull();
        assertThat(report.score()).isEqualTo(100.0);
    }

    @Test
    void shouldFilterSamplesByTags() {
        EvaluationSample<String> sample1 = new EvaluationSample<>(
                "Sample1",
                new Parameters().add("input1"),
                "expected1",
                List.of("tag1", "smoke-test"));

        EvaluationSample<String> sample2 = new EvaluationSample<>(
                "Sample2",
                new Parameters().add("input2"),
                "expected2",
                List.of("tag2", "regression"));

        EvaluationSample<String> sample3 = new EvaluationSample<>(
                "Sample3",
                new Parameters().add("input3"),
                "expected3",
                List.of("smoke-test"));

        Samples<String> samples = new Samples<>(sample1, sample2, sample3);

        Function<Parameters, String> function = params -> {
            String input = params.get(0);
            return "expected" + input.substring(5);
        };

        EvaluationStrategy<String> strategy = (sample, actual) -> EvaluationResult
                .fromBoolean(actual.equals(sample.expectedOutput()));

        EvaluationReport<String> report = Evaluation.<String> builder()
                .withSamples(samples)
                .withTags("smoke-test")
                .evaluate(function)
                .using(strategy)
                .run();

        assertThat(report).isNotNull();
        assertThat(report.evaluations()).hasSize(2);
        assertThat(report.evaluations().stream()
                .map(e -> e.sample().name()))
                .containsExactlyInAnyOrder("Sample1", "Sample3");
    }

    @Test
    void shouldSupportMultipleStrategies() {
        EvaluationSample<String> sample = new EvaluationSample<>(
                "Sample1",
                new Parameters().add("input"),
                "expected",
                List.of());

        Samples<String> samples = new Samples<>(sample);

        Function<Parameters, String> function = params -> "expected";

        EvaluationStrategy<String> exactMatchStrategy = (s, actual) -> EvaluationResult
                .fromBoolean(actual.equals(s.expectedOutput()));
        EvaluationStrategy<String> lengthStrategy = (s, actual) -> EvaluationResult
                .fromBoolean(actual.length() == s.expectedOutput().length());

        EvaluationReport<String> report = Evaluation.<String> builder()
                .withSamples(samples)
                .evaluate(function)
                .using(exactMatchStrategy)
                .using(lengthStrategy)
                .run();

        assertThat(report).isNotNull();
        assertThat(report.evaluations()).hasSize(2);
        assertThat(report.score()).isEqualTo(100.0);
    }

    @Test
    void shouldThrowExceptionWhenSamplesNotSet() {
        Function<Parameters, String> function = params -> "result";
        EvaluationStrategy<String> strategy = (sample, actual) -> EvaluationResult.fromBoolean(true);

        assertThatThrownBy(() -> {
            Evaluation.<String> builder()
                    .evaluate(function)
                    .using(strategy)
                    .run();
        }).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Samples must be set");
    }

    @Test
    void shouldThrowExceptionWhenNoStrategiesSet() {
        EvaluationSample<String> sample = new EvaluationSample<>(
                "Sample1",
                new Parameters().add("input"),
                "expected",
                List.of());

        Samples<String> samples = new Samples<>(sample);
        Function<Parameters, String> function = params -> "result";

        assertThatThrownBy(() -> {
            Evaluation.<String> builder()
                    .withSamples(samples)
                    .evaluate(function)
                    .run();
        }).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("At least one strategy must be set");
    }

    @Test
    void shouldThrowExceptionForInvalidConcurrency() {
        assertThatThrownBy(() -> {
            Evaluation.<String> builder().withConcurrency(0);
        }).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Concurrency must be at least 1");

        assertThatThrownBy(() -> {
            Evaluation.<String> builder().withConcurrency(-1);
        }).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Concurrency must be at least 1");
    }

    @Test
    void shouldThrowExceptionForNullSamples() {
        assertThatThrownBy(() -> {
            Evaluation.<String> builder().withSamples((Samples<String>) null);
        }).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Samples must not be null");
    }

    @Test
    void shouldThrowExceptionForNullSource() {
        assertThatThrownBy(() -> {
            Evaluation.<String> builder().withSamples((String) null);
        }).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Source must not be null or blank");

        assertThatThrownBy(() -> {
            Evaluation.<String> builder().withSamples("");
        }).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Source must not be null or blank");
    }

    @Test
    void shouldThrowExceptionForNullFunction() {
        EvaluationSample<String> sample = new EvaluationSample<>(
                "Sample1",
                new Parameters().add("input"),
                "expected",
                List.of());

        assertThatThrownBy(() -> {
            Evaluation.<String> builder()
                    .withSamples(new Samples<>(sample))
                    .evaluate(null);
        }).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Function must not be null");
    }

    @Test
    void shouldThrowExceptionForNullStrategy() {
        EvaluationSample<String> sample = new EvaluationSample<>(
                "Sample1",
                new Parameters().add("input"),
                "expected",
                List.of());

        Function<Parameters, String> function = params -> "result";

        assertThatThrownBy(() -> {
            Evaluation.<String> builder()
                    .withSamples(new Samples<>(sample))
                    .evaluate(function)
                    .using(null);
        }).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Strategy must not be null");
    }

    @Test
    void shouldSaveReportToDirectory(@TempDir Path tempDir) throws IOException {
        EvaluationSample<String> sample = new EvaluationSample<>(
                "Sample1",
                new Parameters().add("input"),
                "expected",
                List.of());

        Samples<String> samples = new Samples<>(sample);
        Function<Parameters, String> function = params -> "expected";
        EvaluationStrategy<String> strategy = (s, actual) -> EvaluationResult.fromBoolean(actual.equals(s.expectedOutput()));

        EvaluationReport<String> report = Evaluation.<String> builder()
                .withSamples(samples)
                .evaluate(function)
                .using(strategy)
                .runAndSave(tempDir);

        assertThat(report).isNotNull();
        assertThat(report.score()).isEqualTo(100.0);

        List<Path> files = Files.list(tempDir)
                .filter(p -> p.getFileName().toString().startsWith("evaluation_report_"))
                .filter(p -> p.getFileName().toString().endsWith(".md"))
                .toList();

        assertThat(files).hasSize(1);

        String content = Files.readString(files.get(0));
        assertThat(content).contains("# Evaluation Report");
        assertThat(content).contains("Global Score");
        assertThat(content).contains("100.0");
    }

    @Test
    void shouldSaveReportWithCustomConfig(@TempDir Path tempDir) throws IOException {
        EvaluationSample<String> sample = new EvaluationSample<>(
                "Sample1",
                new Parameters().add("input"),
                "expected",
                List.of());

        Samples<String> samples = new Samples<>(sample);
        Function<Parameters, String> function = params -> "expected";
        EvaluationStrategy<String> strategy = (s, actual) -> EvaluationResult.fromBoolean(actual.equals(s.expectedOutput()));

        Map<String, Object> config = Map.of(
                "includeDetails", true,
                "includeScores", true);

        EvaluationReport<String> report = Evaluation.<String> builder()
                .withSamples(samples)
                .evaluate(function)
                .using(strategy)
                .runAndSave(tempDir, config);

        assertThat(report).isNotNull();

        List<Path> files = Files.list(tempDir)
                .filter(p -> p.getFileName().toString().startsWith("evaluation_report_"))
                .toList();

        assertThat(files).hasSize(1);
    }

    @Test
    void shouldHandleEmptyTagFilter() {
        EvaluationSample<String> sample = new EvaluationSample<>(
                "Sample1",
                new Parameters().add("input"),
                "expected",
                List.of("tag1"));

        Samples<String> samples = new Samples<>(sample);
        Function<Parameters, String> function = params -> "expected";
        EvaluationStrategy<String> strategy = (s, actual) -> EvaluationResult.fromBoolean(actual.equals(s.expectedOutput()));

        EvaluationReport<String> report = Evaluation.<String> builder()
                .withSamples(samples)
                .withTags("non-existent-tag")
                .evaluate(function)
                .using(strategy)
                .run();

        assertThat(report.evaluations()).isEmpty();
    }

    @Test
    void shouldChainMultipleTags() {
        EvaluationSample<String> sample1 = new EvaluationSample<>(
                "Sample1",
                new Parameters().add("input1"),
                "expected1",
                List.of("tag1"));

        EvaluationSample<String> sample2 = new EvaluationSample<>(
                "Sample2",
                new Parameters().add("input2"),
                "expected2",
                List.of("tag2"));

        EvaluationSample<String> sample3 = new EvaluationSample<>(
                "Sample3",
                new Parameters().add("input3"),
                "expected3",
                List.of("tag3"));

        Samples<String> samples = new Samples<>(sample1, sample2, sample3);
        Function<Parameters, String> function = params -> {
            String input = params.get(0);
            return "expected" + input.substring(5);
        };
        EvaluationStrategy<String> strategy = (s, actual) -> EvaluationResult.fromBoolean(actual.equals(s.expectedOutput()));

        EvaluationReport<String> report = Evaluation.<String> builder()
                .withSamples(samples)
                .withTags("tag1", "tag2")
                .evaluate(function)
                .using(strategy)
                .run();

        assertThat(report.evaluations()).hasSize(2);
        assertThat(report.evaluations().stream()
                .map(e -> e.sample().name()))
                .containsExactlyInAnyOrder("Sample1", "Sample2");
    }

    @Nested
    class EvaluationRunnerTests {

        @Test
        void shouldBuildReusableRunner() {
            EvaluationSample<String> sample = new EvaluationSample<>(
                    "Sample1",
                    new Parameters().add("input"),
                    "expected",
                    List.of());

            Samples<String> samples = new Samples<>(sample);
            Function<Parameters, String> function = params -> "expected";
            EvaluationStrategy<String> strategy = (s, actual) -> EvaluationResult
                    .fromBoolean(actual.equals(s.expectedOutput()));

            EvaluationRunner<String> runner = Evaluation.<String> builder()
                    .withSamples(samples)
                    .evaluate(function)
                    .using(strategy)
                    .build();

            assertThat(runner).isNotNull();

            EvaluationReport<String> report = runner.run();
            assertThat(report).isNotNull();
            assertThat(report.score()).isEqualTo(100.0);
        }

        @Test
        void shouldAllowMultipleExecutions() {
            EvaluationSample<String> sample = new EvaluationSample<>(
                    "Sample1",
                    new Parameters().add("input"),
                    "expected",
                    List.of());

            Samples<String> samples = new Samples<>(sample);

            final int[] executionCount = { 0 };
            Function<Parameters, String> function = params -> {
                executionCount[0]++;
                return "expected";
            };

            EvaluationStrategy<String> strategy = (s, actual) -> EvaluationResult
                    .fromBoolean(actual.equals(s.expectedOutput()));

            EvaluationRunner<String> runner = Evaluation.<String> builder()
                    .withSamples(samples)
                    .evaluate(function)
                    .using(strategy)
                    .build();

            EvaluationReport<String> report1 = runner.run();
            EvaluationReport<String> report2 = runner.run();
            EvaluationReport<String> report3 = runner.run();

            assertThat(report1.score()).isEqualTo(100.0);
            assertThat(report2.score()).isEqualTo(100.0);
            assertThat(report3.score()).isEqualTo(100.0);

            assertThat(executionCount[0]).isEqualTo(3);
        }

        @Test
        void shouldSupportRunnerWithAutoCloseable() {
            EvaluationSample<String> sample = new EvaluationSample<>(
                    "Sample1",
                    new Parameters().add("input"),
                    "expected",
                    List.of());

            Samples<String> samples = new Samples<>(sample);
            Function<Parameters, String> function = params -> "expected";
            EvaluationStrategy<String> strategy = (s, actual) -> EvaluationResult
                    .fromBoolean(actual.equals(s.expectedOutput()));

            try (EvaluationRunner<String> runner = Evaluation.<String> builder()
                    .withSamples(samples)
                    .evaluate(function)
                    .using(strategy)
                    .build()) {

                EvaluationReport<String> report = runner.run();
                assertThat(report.score()).isEqualTo(100.0);
            }
        }

        @Test
        void shouldSupportRunnerWithRunAndSave(@TempDir Path tempDir) {
            EvaluationSample<String> sample = new EvaluationSample<>(
                    "Sample1",
                    new Parameters().add("input"),
                    "expected",
                    List.of());

            Samples<String> samples = new Samples<>(sample);
            Function<Parameters, String> function = params -> "expected";
            EvaluationStrategy<String> strategy = (s, actual) -> EvaluationResult
                    .fromBoolean(actual.equals(s.expectedOutput()));

            EvaluationRunner<String> runner = Evaluation.<String> builder()
                    .withSamples(samples)
                    .evaluate(function)
                    .using(strategy)
                    .build();

            EvaluationReport<String> report = runner.runAndSave(tempDir);

            assertThat(report).isNotNull();
            assertThat(report.score()).isEqualTo(100.0);

            var files = List.of(tempDir.toFile().listFiles());
            assertThat(files).hasSizeGreaterThanOrEqualTo(1);
            assertThat(files.stream().anyMatch(f -> f.getName().startsWith("evaluation_report_"))).isTrue();
        }

        @Test
        void shouldSupportDeferredExecution() {
            EvaluationSample<String> sample = new EvaluationSample<>(
                    "Sample1",
                    new Parameters().add("input"),
                    "expected",
                    List.of());

            Samples<String> samples = new Samples<>(sample);
            Function<Parameters, String> function = params -> "expected";
            EvaluationStrategy<String> strategy = (s, actual) -> EvaluationResult
                    .fromBoolean(actual.equals(s.expectedOutput()));

            EvaluationRunner<String> runner = Evaluation.<String> builder()
                    .withSamples(samples)
                    .evaluate(function)
                    .using(strategy)
                    .build();

            List<EvaluationRunner<String>> runners = new ArrayList<>();
            runners.add(runner);

            EvaluationReport<String> report = runners.get(0).run();
            assertThat(report.score()).isEqualTo(100.0);
        }

        @Test
        void shouldPreserveRunnerConfiguration() {
            EvaluationSample<String> sample1 = new EvaluationSample<>(
                    "Sample1",
                    new Parameters().add("input1"),
                    "expected1",
                    List.of("tag1"));

            EvaluationSample<String> sample2 = new EvaluationSample<>(
                    "Sample2",
                    new Parameters().add("input2"),
                    "expected2",
                    List.of("tag2"));

            Samples<String> samples = new Samples<>(sample1, sample2);

            Function<Parameters, String> function = params -> {
                String input = params.get(0);
                return "expected" + input.substring(5);
            };

            EvaluationStrategy<String> strategy = (s, actual) -> EvaluationResult
                    .fromBoolean(actual.equals(s.expectedOutput()));

            EvaluationRunner<String> runner = Evaluation.<String> builder()
                    .withSamples(samples)
                    .withConcurrency(4)
                    .withTags("tag1")
                    .evaluate(function)
                    .using(strategy)
                    .build();

            EvaluationReport<String> report = runner.run();

            assertThat(report.evaluations()).hasSize(1);
            assertThat(report.evaluations().get(0).sample().name()).isEqualTo("Sample1");
        }

        @Test
        void shouldSupportRunnerWithMultipleStrategies() {
            EvaluationSample<String> sample = new EvaluationSample<>(
                    "Sample1",
                    new Parameters().add("input"),
                    "expected",
                    List.of());

            Samples<String> samples = new Samples<>(sample);
            Function<Parameters, String> function = params -> "expected";

            EvaluationStrategy<String> strategy1 = (s, actual) -> EvaluationResult
                    .fromBoolean(actual.equals(s.expectedOutput()));
            EvaluationStrategy<String> strategy2 = (s, actual) -> EvaluationResult.fromBoolean(actual.length() > 0);

            EvaluationRunner<String> runner = Evaluation.<String> builder()
                    .withSamples(samples)
                    .evaluate(function)
                    .using(strategy1)
                    .using(strategy2)
                    .build();

            EvaluationReport<String> report = runner.run();

            // Should have 2 evaluations (one per strategy)
            assertThat(report.evaluations()).hasSize(2);
            assertThat(report.score()).isEqualTo(100.0);
        }
    }
}
