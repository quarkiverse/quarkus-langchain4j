package io.quarkiverse.langchain4j.scorer.junit5;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.platform.commons.support.HierarchyTraversalMode;
import org.junit.platform.commons.support.ReflectionSupport;

import io.quarkiverse.langchain4j.testing.scorer.Samples;
import io.quarkiverse.langchain4j.testing.scorer.Scorer;
import io.quarkiverse.langchain4j.testing.scorer.YamlLoader;

public class ScorerExtension implements BeforeEachCallback, AfterEachCallback, ParameterResolver {
    private final List<Scorer> scorers = new CopyOnWriteArrayList<>();

    @Override
    public void beforeEach(ExtensionContext extensionContext) {
        Optional<Class<?>> maybeClass = extensionContext.getTestClass();
        if (maybeClass.isPresent()) {
            List<Field> fields = ReflectionSupport.findFields(maybeClass.get(),
                    field -> field.getType().isAssignableFrom(Scorer.class), HierarchyTraversalMode.TOP_DOWN);
            for (Field field : fields) {
                Scorer sc;
                if (field.isAnnotationPresent(ScorerConfiguration.class)) {
                    ScorerConfiguration annotation = field.getAnnotation(ScorerConfiguration.class);
                    sc = new Scorer(annotation.concurrency());
                } else {
                    sc = new Scorer();
                }
                scorers.add(sc);
                inject(sc, extensionContext.getRequiredTestInstance(), field);
            }
        }
    }

    private void inject(Scorer sc, Object instance, Field field) {
        try {
            field.setAccessible(true);
            field.set(instance, sc);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) {
        for (Scorer scorer : scorers) {
            scorer.close();
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        return (parameterContext.findAnnotation(SampleLocation.class).isPresent()
                && parameterContext.getParameter().getType().isAssignableFrom(Samples.class))
                || parameterContext.getParameter().getType().isAssignableFrom(Scorer.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        if (parameterContext.getParameter().getType().isAssignableFrom(Scorer.class)) {
            if (parameterContext.getParameter().isAnnotationPresent(ScorerConfiguration.class)) {
                ScorerConfiguration annotation = parameterContext.getParameter().getAnnotation(ScorerConfiguration.class);
                return new Scorer(annotation.concurrency());
            } else {
                return new Scorer();
            }
        } else {
            // List of data samples
            String path = parameterContext.findAnnotation(SampleLocation.class).orElseThrow().value();
            return YamlLoader.load(path);
        }
    }

}
