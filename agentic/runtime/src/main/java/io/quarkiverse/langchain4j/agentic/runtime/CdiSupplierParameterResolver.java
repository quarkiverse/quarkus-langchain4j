package io.quarkiverse.langchain4j.agentic.runtime;

import java.lang.annotation.Annotation;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Set;

import jakarta.enterprise.inject.ResolutionException;

import dev.langchain4j.agentic.declarative.SupplierParameterResolver;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;

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
        // the type with its type arguments, as for an injection point: a bean like the one of a configuration value
        // reads them from the injection point
        Type type = parameter.getParameterizedType();
        InstanceHandle<Object> handle;
        try {
            handle = Arc.container().instance(type, qualifiers);
        } catch (ResolutionException e) {
            throw unresolved(context, type, e);
        }
        if (!handle.isAvailable()) {
            throw unresolved(context, type, null);
        }
        return handle.get();
    }

    private static IllegalStateException unresolved(Context context, Type type, ResolutionException cause) {
        return new IllegalStateException("Unable to resolve the CDI bean of type '" + type.getTypeName()
                + "' for parameter '" + context.parameter().getName() + "' of method '"
                + context.supplierMethod().getName() + "' of agent '" + context.declaringAgentClass().getName() + "'",
                cause);
    }
}
