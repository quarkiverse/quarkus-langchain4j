package io.quarkiverse.langchain4j.evaluation.junit5;

import java.util.UUID;

import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestPlan;

import io.quarkiverse.langchain4j.testing.evaluation.FileBaselineStore;

/**
 * Marks each execution of a test plan as a new run, so that comparing the same evaluation id again in a later
 * execution in the same JVM, such as Quarkus continuous testing or a Surefire rerun, is not taken for a duplicate.
 * <p>
 * Registered with the JUnit Platform launcher through {@code META-INF/services}.
 * </p>
 */
public class EvaluationRunListener implements TestExecutionListener {

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        System.setProperty(FileBaselineStore.RUN_PROPERTY, UUID.randomUUID().toString());
    }
}
