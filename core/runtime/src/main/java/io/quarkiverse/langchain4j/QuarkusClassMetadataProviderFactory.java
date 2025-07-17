package io.quarkiverse.langchain4j;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import dev.langchain4j.service.guardrail.InputGuardrails;
import dev.langchain4j.service.guardrail.OutputGuardrails;
import dev.langchain4j.spi.classloading.ClassMetadataProviderFactory;
import io.quarkiverse.langchain4j.runtime.AiServicesRecorder;
import io.quarkiverse.langchain4j.runtime.aiservice.AiServiceClassCreateInfo;
import io.quarkiverse.langchain4j.runtime.aiservice.AiServiceMethodCreateInfo;

public class QuarkusClassMetadataProviderFactory implements ClassMetadataProviderFactory<AiServiceMethodCreateInfo> {
    @Override
    public <T extends Annotation> Optional<T> getAnnotation(AiServiceMethodCreateInfo method, Class<T> annotationClass) {
        return GuardrailType.fromAnnotationClass(annotationClass)
                .getAnnotation(method);
    }

    @Override
    public <T extends Annotation> Optional<T> getAnnotation(Class<?> clazz, Class<T> annotationClass) {
        return GuardrailType.fromAnnotationClass(annotationClass)
                .getAnnotation(clazz);
    }

    @Override
    public Iterable<AiServiceMethodCreateInfo> getNonStaticMethodsOnClass(Class<?> aiServiceClass) {
        return getClassMetadata(aiServiceClass)
                .map(AiServiceClassCreateInfo::methodMap)
                .map(Map::values)
                .orElseGet(Collections::emptyList);
    }

    private static Optional<AiServiceClassCreateInfo> getClassMetadata(Class<?> aiServiceClass) {
        return Optional.ofNullable(AiServicesRecorder.getMetadata().get(aiServiceClass.getName()));
    }

    private enum GuardrailType {
        INPUT(InputGuardrails.class) {
            @Override
            protected <T extends Annotation> Optional<T> getAnnotation(AiServiceMethodCreateInfo method) {
                return Optional.ofNullable((T) method.getInputGuardrails());
            }

            @Override
            protected <T extends Annotation> Optional<T> getAnnotation(Class<?> clazz) {
                return getClassMetadata(clazz)
                        .map(classCreateInfo -> (T) classCreateInfo.inputGuardrails());
            }
        },
        OUTPUT(OutputGuardrails.class) {
            @Override
            protected <T extends Annotation> Optional<T> getAnnotation(AiServiceMethodCreateInfo method) {
                return Optional.ofNullable((T) method.getOutputGuardrails());
            }

            @Override
            protected <T extends Annotation> Optional<T> getAnnotation(Class<?> clazz) {
                return getClassMetadata(clazz)
                        .map(classCreateInfo -> (T) classCreateInfo.outputGuardrails());
            }
        };

        private final Class<? extends Annotation> annotationClass;

        GuardrailType(Class<? extends Annotation> annotationClass) {
            this.annotationClass = annotationClass;
        }

        protected abstract <T extends Annotation> Optional<T> getAnnotation(AiServiceMethodCreateInfo method);

        protected abstract <T extends Annotation> Optional<T> getAnnotation(Class<?> clazz);

        public static GuardrailType fromAnnotationClass(Class<? extends Annotation> annotationClass) {
            return Arrays.stream(values())
                    .filter(type -> type.annotationClass.equals(annotationClass))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Unsupported guardrail annotation: %s".formatted(annotationClass.getName())));
        }
    }
}
