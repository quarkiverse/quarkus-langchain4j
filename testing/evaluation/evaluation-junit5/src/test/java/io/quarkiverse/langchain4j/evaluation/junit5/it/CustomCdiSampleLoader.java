package io.quarkiverse.langchain4j.evaluation.junit5.it;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.langchain4j.testing.evaluation.EvaluationSample;
import io.quarkiverse.langchain4j.testing.evaluation.Parameter;
import io.quarkiverse.langchain4j.testing.evaluation.Parameters;
import io.quarkiverse.langchain4j.testing.evaluation.SampleLoadException;
import io.quarkiverse.langchain4j.testing.evaluation.SampleLoader;
import io.quarkiverse.langchain4j.testing.evaluation.Samples;

/**
 * Custom CDI-based SampleLoader for testing.
 * <p>
 * This loader demonstrates that custom sample loaders can be created as CDI beans
 * and will be automatically discovered when running in a Quarkus context.
 * </p>
 * <p>
 * This loader supports sources starting with "custom:" and programmatically creates
 * samples using the Samples API rather than loading from external files.
 * </p>
 */
@ApplicationScoped
public class CustomCdiSampleLoader implements SampleLoader<String> {

    @Override
    public boolean supports(String source) {
        return source != null && source.startsWith("custom:");
    }

    @Override
    public Samples<String> load(String source, Class<String> outputType) throws SampleLoadException {
        if (!supports(source)) {
            throw new SampleLoadException("CustomCdiSampleLoader only supports sources starting with 'custom:'");
        }

        // Extract the identifier from the source (e.g., "custom:greetings" -> "greetings")
        String identifier = source.substring("custom:".length());

        return switch (identifier) {
            case "greetings" -> createGreetingSamples();
            case "math" -> createMathSamples();
            case "programming" -> createProgrammingSamples();
            default -> throw new SampleLoadException("Unknown custom source: " + identifier);
        };
    }

    @Override
    public int priority() {
        // Higher priority to ensure this loader is selected for custom: sources
        return 100;
    }

    private Samples<String> createGreetingSamples() {
        Parameters params1 = new Parameters();
        params1.add(new Parameter.UnnamedParameter("Hello"));

        Parameters params2 = new Parameters();
        params2.add(new Parameter.UnnamedParameter("Bonjour"));

        Parameters params3 = new Parameters();
        params3.add(new Parameter.UnnamedParameter("Hola"));

        List<EvaluationSample<String>> samples = List.of(
                new EvaluationSample<>("Greeting1", params1, "Hello, how can I help you?",
                        List.of("greeting", "english")),
                new EvaluationSample<>("Greeting2", params2, "Bonjour, comment puis-je vous aider?",
                        List.of("greeting", "french")),
                new EvaluationSample<>("Greeting3", params3, "Hola, ¿cómo puedo ayudarte?",
                        List.of("greeting", "spanish")));

        return new Samples<>(samples);
    }

    private Samples<String> createMathSamples() {
        Parameters params1 = new Parameters();
        params1.add(new Parameter.UnnamedParameter("What is 2 + 2?"));

        Parameters params2 = new Parameters();
        params2.add(new Parameter.UnnamedParameter("What is 10 * 5?"));

        List<EvaluationSample<String>> samples = List.of(
                new EvaluationSample<>("Math1", params1, "4", List.of("math", "addition")),
                new EvaluationSample<>("Math2", params2, "50", List.of("math", "multiplication")));

        return new Samples<>(samples);
    }

    private Samples<String> createProgrammingSamples() {
        Parameters params1 = new Parameters();
        params1.add(new Parameter.UnnamedParameter("What is a CDI bean?"));

        Parameters params2 = new Parameters();
        params2.add(new Parameter.UnnamedParameter("What is dependency injection?"));

        List<EvaluationSample<String>> samples = List.of(
                new EvaluationSample<>("Programming1", params1,
                        "A CDI bean is a managed component in Jakarta EE that supports dependency injection.",
                        List.of("programming", "cdi")),
                new EvaluationSample<>("Programming2", params2,
                        "Dependency injection is a design pattern where dependencies are provided to a component rather than created by it.",
                        List.of("programming", "design-pattern")));

        return new Samples<>(samples);
    }
}
