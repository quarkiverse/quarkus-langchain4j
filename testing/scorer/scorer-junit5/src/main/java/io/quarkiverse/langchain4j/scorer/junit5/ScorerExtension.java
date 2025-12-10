package io.quarkiverse.langchain4j.scorer.junit5;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

import io.quarkiverse.langchain4j.testing.scorer.Samples;
import io.quarkiverse.langchain4j.testing.scorer.YamlLoader;

public class ScorerExtension implements ParameterResolver {

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        return (parameterContext.findAnnotation(SampleLocation.class).isPresent()
                && parameterContext.getParameter().getType().isAssignableFrom(Samples.class));
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        // List of data samples
        String path = parameterContext.findAnnotation(SampleLocation.class).orElseThrow().value();
        return YamlLoader.load(path);
    }

}
