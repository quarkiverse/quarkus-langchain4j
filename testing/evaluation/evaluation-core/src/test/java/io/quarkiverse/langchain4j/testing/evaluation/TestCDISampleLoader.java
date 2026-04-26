package io.quarkiverse.langchain4j.testing.evaluation;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Custom SampleLoader for testing CDI injection.
 * <p>
 * This loader demonstrates that CDI-managed loaders can inject dependencies.
 * </p>
 */
@ApplicationScoped
public class TestCDISampleLoader implements SampleLoader<Object> {

    @Inject
    TestDependency dependency;

    public boolean wasInjected() {
        return dependency != null;
    }

    @Override
    public boolean supports(String source) {
        return source != null && source.endsWith(".custom");
    }

    @Override
    public Samples<Object> load(String source, Class<Object> outputType) {
        // Create a simple sample to prove CDI loading works
        Parameters params = new Parameters();
        params.add(new Parameter.UnnamedParameter("test"));

        EvaluationSample<Object> sample = new EvaluationSample<>(
                "CDI-Loaded Sample",
                params,
                "expected",
                List.of("cdi-test"));

        return new Samples<>(List.of(sample));
    }

    @Override
    public int priority() {
        return 10; // Higher priority to test priority ordering
    }
}
