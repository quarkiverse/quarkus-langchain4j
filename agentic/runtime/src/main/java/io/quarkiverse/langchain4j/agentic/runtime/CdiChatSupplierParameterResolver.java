package io.quarkiverse.langchain4j.agentic.runtime;

import java.lang.annotation.Annotation;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Set;

import dev.langchain4j.agentic.declarative.ChatSupplierParameterResolver;
import io.quarkus.arc.Arc;

public class CdiChatSupplierParameterResolver implements ChatSupplierParameterResolver {

    private final Set<String> qualifierNames;

    public CdiChatSupplierParameterResolver(Set<String> qualifierNames) {
        this.qualifierNames = qualifierNames;
    }

    @Override
    public boolean supports(Context context) {
        return context.parameter().isAnnotationPresent(CdiBean.class);
    }

    @Override
    public Object resolve(Context context) {
        Parameter parameter = context.parameter();
        Annotation[] qualifiers = Arrays.stream(parameter.getAnnotations())
                .filter(ann -> !ann.annotationType().equals(CdiBean.class))
                .filter(ann -> qualifierNames.contains(ann.annotationType().getName()))
                .toArray(Annotation[]::new);
        return Arc.container().select(parameter.getType(), qualifiers).get();
    }
}
