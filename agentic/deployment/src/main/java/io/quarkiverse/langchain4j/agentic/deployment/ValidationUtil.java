package io.quarkiverse.langchain4j.agentic.deployment;

import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.jboss.jandex.DotName;
import org.jboss.jandex.MethodInfo;

import dev.langchain4j.service.IllegalConfigurationException;

class ValidationUtil {

    static void validateStaticMethod(MethodInfo method, DotName annotationName) {
        if (!Modifier.isStatic(method.flags())) {
            throw new IllegalConfigurationException(
                    String.format("Methods annotated with '%s' must be static. Offending method is '%s' of class '%s'",
                            annotationName, method.name(), method.declaringClass().name().toString()));
        }
    }

    static void validateAllowedReturnTypes(MethodInfo method, Set<DotName> allowedReturnTypes, DotName annotationName) {
        if (!allowedReturnTypes.contains(method.returnType().name())) {
            throw new IllegalConfigurationException(
                    "Methods annotated with '%s' can only use the following return types: '%s'. Offending method is '%s' of class '%s'"
                            .formatted(
                                    annotationName,
                                    allowedReturnTypes.stream().map(DotName::withoutPackagePrefix)
                                            .collect(Collectors.joining(",")),
                                    method.name(),
                                    method.declaringClass().name().toString()));
        }
    }

    static void validateRequiredParameterTypes(MethodInfo method, List<DotName> requiredParameterTypes,
            DotName annotationName) {
        if (method.parameters().size() != requiredParameterTypes.size()) {
            throw new IllegalConfigurationException(
                    "Methods annotated with '%s' must use the following parameter types: '%s'. Offending method is '%s' of class '%s'"
                            .formatted(
                                    annotationName,
                                    requiredParameterTypes.stream().map(DotName::withoutPackagePrefix)
                                            .collect(Collectors.joining(",")),
                                    method.name(),
                                    method.declaringClass().name().toString()));
        }

        for (int i = 0; i < requiredParameterTypes.size(); i++) {
            DotName parameterTypeName = method.parameters().get(i).type().name();
            if (!parameterTypeName.equals(requiredParameterTypes.get(i))) {
                throw new IllegalConfigurationException(
                        "Methods annotated with '%s' must use the following parameter types: '%s'. Offending method is '%s' of class '%s'"
                                .formatted(
                                        annotationName,
                                        requiredParameterTypes.stream().map(DotName::withoutPackagePrefix)
                                                .collect(Collectors.joining(",")),
                                        method.name(),
                                        method.declaringClass().name().toString()));
            }
        }
    }

    static void validateNoMethodParameters(MethodInfo method, DotName annotationName) {
        if (!method.parameters().isEmpty()) {
            throw new IllegalConfigurationException(
                    "Methods annotated with '%s' cannot have any method parameters. Offending method is '%s' of class '%s'"
                            .formatted(
                                    annotationName,
                                    method.name(),
                                    method.declaringClass().name().toString()));
        }
    }
}
