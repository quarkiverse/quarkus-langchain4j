package io.quarkiverse.langchain4j.testing.evaluation.compaction;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import jakarta.enterprise.inject.spi.CDI;

import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.testing.evaluation.compaction.CompactionEvaluationReport.Result;

/**
 * Runs the {@link CompactionEvaluation} of a test method, writes its markdown report, hands it to the method, and
 * fails the test when a profile misses the thresholds.
 */
public class CompactionEvaluationExtension
        implements BeforeTestExecutionCallback, ParameterResolver, AfterTestExecutionCallback {

    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace
            .create(CompactionEvaluationExtension.class);

    @Override
    public void beforeTestExecution(ExtensionContext context) {
        report(context);
    }

    @Override
    public boolean supportsParameter(ParameterContext parameter, ExtensionContext context) {
        return parameter.getParameter().getType() == CompactionEvaluationReport.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameter, ExtensionContext context) {
        return report(context);
    }

    @Override
    public void afterTestExecution(ExtensionContext context) {
        if (context.getExecutionException().isPresent()) {
            return;
        }
        CompactionEvaluation evaluation = annotation(context);
        CompactionEvaluationReport report = report(context);
        List<String> failures = new ArrayList<>();
        for (Result r : report.results()) {
            if (r.name().equals(CompactionEvaluationReport.FULL_HISTORY)
                    || r.name().equals(CompactionEvaluationReport.NO_HISTORY)) {
                continue;
            }
            if (r.recall() < evaluation.minRecall()) {
                failures.add(String.format(Locale.ROOT, "%s: recall %.3f < %.3f", r.name(), r.recall(),
                        evaluation.minRecall()));
            }
            if (r.savings() < evaluation.minSavings()) {
                failures.add(String.format(Locale.ROOT, "%s: savings %.3f < %.3f", r.name(), r.savings(),
                        evaluation.minSavings()));
            }
        }
        if (!failures.isEmpty()) {
            throw new AssertionError("Compaction evaluation of " + report.conversation() + " failed: "
                    + String.join(", ", failures) + " (see " + reportFile(context, evaluation) + ")");
        }
    }

    // the parameter is resolved after beforeTestExecution: evaluate once per test
    private CompactionEvaluationReport report(ExtensionContext context) {
        return context.getStore(NAMESPACE).getOrComputeIfAbsent("report", k -> evaluate(context),
                CompactionEvaluationReport.class);
    }

    private CompactionEvaluationReport evaluate(ExtensionContext context) {
        CompactionEvaluation evaluation = annotation(context);
        if (evaluation.repeats() < 1) {
            throw new IllegalArgumentException("repeats must be at least 1");
        }
        Scenario scenario = Scenario.load(evaluation.conversation(), evaluation.questions());
        ChatModel answerer = model(context, AnswerModel.class);
        if (answerer == null) {
            answerer = CDI.current().select(ChatModel.class).get();
        }
        ChatModel judge = model(context, JudgeModel.class);
        CompactionEvaluationReport report = new CompactionEvaluator(answerer, judge != null ? judge : answerer)
                .evaluate(scenario, List.of(evaluation.profiles()), evaluation.repeats());
        Path file = reportFile(context, evaluation);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, report.toMarkdown());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return report;
    }

    /**
     * @return the chat model injected in the field of the test class carrying {@code marker}, {@code null} if none
     */
    private static ChatModel model(ExtensionContext context, Class<? extends Annotation> marker) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> c = context.getRequiredTestClass(); c != null; c = c.getSuperclass()) {
            Arrays.stream(c.getDeclaredFields()).filter(f -> f.isAnnotationPresent(marker)).forEach(fields::add);
        }
        if (fields.isEmpty()) {
            return null;
        }
        Field field = fields.getFirst();
        if (fields.size() > 1 || field.getType() != ChatModel.class) {
            throw new IllegalStateException("@" + marker.getSimpleName() + " must be on a single ChatModel field, found "
                    + fields);
        }
        // look the bean up rather than read the field: Quarkus may inject another instance of the test class
        ModelName name = field.getAnnotation(ModelName.class);
        return name == null ? CDI.current().select(ChatModel.class).get()
                : CDI.current().select(ChatModel.class, ModelName.Literal.of(name.value())).get();
    }

    private static CompactionEvaluation annotation(ExtensionContext context) {
        return context.getRequiredTestMethod().getAnnotation(CompactionEvaluation.class);
    }

    private static Path reportFile(ExtensionContext context, CompactionEvaluation evaluation) {
        return Path.of(evaluation.reportDir(),
                context.getRequiredTestClass().getSimpleName() + "-" + context.getRequiredTestMethod().getName()
                        + ".md");
    }
}
