package io.quarkiverse.langchain4j.agentic.runtime;

import java.lang.annotation.Annotation;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Set;

import jakarta.enterprise.inject.ResolutionException;

import dev.langchain4j.agentic.declarative.SupplierParameterResolver;
import io.quarkus.arc.Arc;

/**
 * Resolves from CDI the parameters of the static methods of the agents that are annotated with {@link CdiBean} or with
 * a CDI qualifier. The bean is looked up at each invocation of the method.
 */
public class CdiSupplierParameterResolver implements SupplierParameterResolver {

    private final Set<String> qualifierNames;

    public CdiSupplierParameterResolver(Set<String> qualifierNames) {
        this.qualifierNames = qualifierNames;
    }

    @Override
    public boolean supports(Context context) {
        Parameter parameter = context.parameter();
        if (parameter.isAnnotationPresent(CdiBean.class)) {
            return true;
        }
        for (Annotation ann : parameter.getAnnotations()) {
            if (qualifierNames.contains(ann.annotationType().getName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Object resolve(Context context) {
        Parameter parameter = context.parameter();
        Annotation[] qualifiers = Arrays.stream(parameter.getAnnotations())
                .filter(ann -> !ann.annotationType().equals(CdiBean.class))
                .filter(ann -> qualifierNames.contains(ann.annotationType().getName()))
                .toArray(Annotation[]::new);
        try {
            return Arc.container().select(parameter.getType(), qualifiers).get();
        } catch (ResolutionException e) {
            throw new IllegalStateException("Unable to resolve the CDI bean of type '" + parameter.getType().getName()
                    + "' for parameter '" + parameter.getName() + "' of method '" + context.supplierMethod().getName()
                    + "' of agent '" + context.declaringAgentClass().getName() + "'", e);
        }
    }
}
