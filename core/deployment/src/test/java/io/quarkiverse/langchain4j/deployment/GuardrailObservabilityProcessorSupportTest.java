package io.quarkiverse.langchain4j.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.Index;
import org.jboss.jandex.MethodInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.deployment.GuardrailObservabilityProcessorSupport.TransformType;
import io.quarkiverse.langchain4j.guardrails.Guardrail;
import io.quarkiverse.langchain4j.guardrails.GuardrailParams;
import io.quarkiverse.langchain4j.guardrails.GuardrailResult;
import io.quarkiverse.langchain4j.guardrails.InputGuardrail;
import io.quarkiverse.langchain4j.guardrails.InputGuardrailParams;
import io.quarkiverse.langchain4j.guardrails.InputGuardrailResult;
import io.quarkiverse.langchain4j.guardrails.InputGuardrails;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrail;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrailParams;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrailResult;
import io.quarkiverse.langchain4j.guardrails.OutputGuardrails;

class GuardrailObservabilityProcessorSupportTest {
    private record ClassInfoMapping(Class<?> clazz, boolean shouldHaveGuardrailValidateMethodWithParams,
            boolean shouldHaveInputGuardrailValidateMethodWithUserMessage,
            boolean shouldHaveOutputGuardrailValidateMethodWithAiMessage) {

        static ClassInfoMapping guardrailWithValidateMethodWithParams(Class<?> clazz) {
            return new ClassInfoMapping(clazz, true, false, false);
        }

        static ClassInfoMapping inputGuardrailWithValidateMethodWithUserMessage(Class<?> clazz) {
            return new ClassInfoMapping(clazz, false, true, false);
        }

        static ClassInfoMapping outputGuardrailWithValidateMethodWithAiMessage(Class<?> clazz) {
            return new ClassInfoMapping(clazz, false, false, true);
        }

        static ClassInfoMapping somethingElse(Class<?> clazz) {
            return new ClassInfoMapping(clazz, false, false, false);
        }

        boolean shouldHaveAtLeastOneMethodRewritten() {
            return !clazz.isInterface()
                    && (shouldHaveGuardrailValidateMethodWithParams || shouldHaveInputGuardrailValidateMethodWithUserMessage
                            || shouldHaveOutputGuardrailValidateMethodWithAiMessage);
        }
    }

    private static final List<ClassInfoMapping> CLASS_INFO_MAPPINGS = List.of(
            ClassInfoMapping.somethingElse(Assistant.class),
            ClassInfoMapping.guardrailWithValidateMethodWithParams(IGDirectlyImplementInputGuardrailWithParams.class),
            ClassInfoMapping
                    .inputGuardrailWithValidateMethodWithUserMessage(IGDirectlyImplementInputGuardrailWithUserMessage.class),
            ClassInfoMapping.somethingElse(IGExtendingValidateWithParams.class),
            ClassInfoMapping.somethingElse(IGExtendingValidateWithUserMessage.class),
            ClassInfoMapping.guardrailWithValidateMethodWithParams(OGDirectlyImplementOutputGuardrailWithParams.class),
            ClassInfoMapping
                    .outputGuardrailWithValidateMethodWithAiMessage(OGDirectlyImplementOutputGuardrailWithAiMessage.class),
            ClassInfoMapping.somethingElse(OGExtendingValidateWithParams.class),
            ClassInfoMapping.somethingElse(OGExtendingValidateWithAiMessage.class),
            ClassInfoMapping.guardrailWithValidateMethodWithParams(AbstractOGImplementingValidateWithParams.class),
            ClassInfoMapping.outputGuardrailWithValidateMethodWithAiMessage(AbstractOGImplementingValidateWithAiMessage.class),
            ClassInfoMapping.guardrailWithValidateMethodWithParams(AbstractIGImplementingValidateWithParams.class),
            ClassInfoMapping
                    .inputGuardrailWithValidateMethodWithUserMessage(AbstractIGImplementingValidateWithUserMessage.class),
            ClassInfoMapping.guardrailWithValidateMethodWithParams(InputGuardrail.class),
            ClassInfoMapping.guardrailWithValidateMethodWithParams(OutputGuardrail.class),
            new ClassInfoMapping(IGRedefiningBothValidateMethods.class, true, true, false),
            new ClassInfoMapping(OGRedefiningBothValidateMethods.class, true, false, true),
            ClassInfoMapping.somethingElse(Guardrail.class),
            ClassInfoMapping.somethingElse(GuardrailParams.class),
            ClassInfoMapping.somethingElse(GuardrailResult.class),
            ClassInfoMapping.somethingElse(OutputGuardrailParams.class),
            ClassInfoMapping.somethingElse(OutputGuardrailResult.class),
            ClassInfoMapping.somethingElse(InputGuardrailParams.class),
            ClassInfoMapping.somethingElse(InputGuardrailResult.class),
            ClassInfoMapping.somethingElse(InputGuardrails.class),
            ClassInfoMapping.somethingElse(OutputGuardrails.class),
            ClassInfoMapping.somethingElse(ClassWithTimedAnnotation.class),
            ClassInfoMapping.somethingElse(ClassWithCountedAnnotation.class),
            ClassInfoMapping.somethingElse(ClassWithSpanAnnotation.class));

    private Index index;

    @BeforeEach
    public void setUp() throws IOException {
        this.index = Index.of(
                CLASS_INFO_MAPPINGS.stream()
                        .map(ClassInfoMapping::clazz)
                        .toArray(Class<?>[]::new));
    }

    @ParameterizedTest
    @ValueSource(classes = { ClassWithTimedAnnotation.class, ClassWithCountedAnnotation.class })
    void hasMetricsAnnotations(Class<?> clazz) {
        var methodInfo = getClassInfo(clazz).firstMethod("someMethod");

        assertThat(methodInfo)
                .isNotNull()
                .extracting(GuardrailObservabilityProcessorSupport::doesMethodHaveMetricsAnnotations)
                .isEqualTo(true);
    }

    @Test
    void hasSpanAnnotation() {
        var methodInfo = getClassInfo(ClassWithSpanAnnotation.class).firstMethod("someMethod");

        assertThat(methodInfo)
                .isNotNull()
                .extracting(GuardrailObservabilityProcessorSupport::doesMethodHaveSpanAnnotation)
                .isEqualTo(true);
    }

    @ParameterizedTest
    @MethodSource("allClassInfoMappingsWithoutClassesWithAnnotations")
    void isGuardrailValidateMethodWithParams(ClassInfoMapping classInfoMapping) {
        var allMethods = getAllMethods(classInfoMapping.clazz());

        assertThat(allMethods)
                .allSatisfy(method -> {
                    // None of the methods already have the metrics annotations
                    assertThat(method)
                            .isNotNull()
                            .extracting(GuardrailObservabilityProcessorSupport::doesMethodHaveMetricsAnnotations)
                            .isEqualTo(false);

                    // None of the methods already have the span annotation
                    assertThat(method)
                            .isNotNull()
                            .extracting(GuardrailObservabilityProcessorSupport::doesMethodHaveSpanAnnotation)
                            .isEqualTo(false);
                });

        var hasGuardrailValidateMethodWithParams = allMethods.stream()
                .filter(GuardrailObservabilityProcessorSupport::isGuardrailValidateMethodWithParams)
                .count() > 0;

        assertThat(hasGuardrailValidateMethodWithParams)
                .isEqualTo(classInfoMapping.shouldHaveGuardrailValidateMethodWithParams);
    }

    @ParameterizedTest
    @MethodSource("allClassInfoMappings")
    void shouldTransformMethod(ClassInfoMapping classInfoMapping) {
        var allMethods = getAllMethods(classInfoMapping.clazz());
        var allMethodsThatShouldHaveMetricsTransformed = allMethods.stream()
                .filter(methodInfo -> GuardrailObservabilityProcessorSupport.shouldTransformMethod(methodInfo, this.index,
                        TransformType.METRICS))
                .toList();
        var allMethodsThatShouldHaveSpanTransformed = allMethods.stream()
                .filter(methodInfo -> GuardrailObservabilityProcessorSupport.shouldTransformMethod(methodInfo, this.index,
                        TransformType.OTEL))
                .toList();

        if (classInfoMapping.shouldHaveAtLeastOneMethodRewritten()) {
            assertThat(allMethodsThatShouldHaveMetricsTransformed)
                    .isNotNull()
                    .hasSize(1);

            assertThat(allMethodsThatShouldHaveSpanTransformed)
                    .isNotNull()
                    .hasSize(1);
        } else {
            assertThat(allMethodsThatShouldHaveMetricsTransformed)
                    .isNotNull()
                    .isEmpty();

            assertThat(allMethodsThatShouldHaveSpanTransformed)
                    .isNotNull()
                    .isEmpty();
        }
    }

    static Stream<ClassInfoMapping> allClassInfoMappingsWithoutClassesWithAnnotations() {
        return CLASS_INFO_MAPPINGS.stream()
                .filter(classInfo -> !classInfo.clazz().equals(ClassWithTimedAnnotation.class) &&
                        !classInfo.clazz().equals(ClassWithCountedAnnotation.class) &&
                        !classInfo.clazz().equals(ClassWithSpanAnnotation.class));
    }

    static List<ClassInfoMapping> allClassInfoMappings() {
        return CLASS_INFO_MAPPINGS;
    }

    private List<MethodInfo> getAllMethods(Class<?> clazz) {
        return getClassInfo(clazz).methods();
    }

    private ClassInfo getClassInfo(Class<?> clazz) {
        return this.index.getClassByName(clazz);
    }

    @RegisterAiService
    interface Assistant {
        @InputGuardrails({ IGDirectlyImplementInputGuardrailWithParams.class,
                IGDirectlyImplementInputGuardrailWithUserMessage.class, IGExtendingValidateWithParams.class,
                IGExtendingValidateWithUserMessage.class, IGRedefiningBothValidateMethods.class })
        @OutputGuardrails({ OGDirectlyImplementOutputGuardrailWithParams.class,
                OGDirectlyImplementOutputGuardrailWithAiMessage.class, OGExtendingValidateWithParams.class,
                OGExtendingValidateWithAiMessage.class, OGRedefiningBothValidateMethods.class })
        String chat(String message);
    }

    @ApplicationScoped
    public static class IGDirectlyImplementInputGuardrailWithUserMessage implements InputGuardrail {
        @Override
        public InputGuardrailResult validate(UserMessage userMessage) {
            return success();
        }
    }

    @ApplicationScoped
    public static class IGDirectlyImplementInputGuardrailWithParams implements InputGuardrail {
        @Override
        public InputGuardrailResult validate(InputGuardrailParams params) {
            return success();
        }
    }

    @ApplicationScoped
    public static class OGDirectlyImplementOutputGuardrailWithParams implements OutputGuardrail {
        @Override
        public OutputGuardrailResult validate(OutputGuardrailParams params) {
            return success();
        }
    }

    @ApplicationScoped
    public static class OGDirectlyImplementOutputGuardrailWithAiMessage implements OutputGuardrail {
        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            return success();
        }
    }

    @ApplicationScoped
    public static class OGExtendingValidateWithParams extends AbstractOGImplementingValidateWithParams {
    }

    @ApplicationScoped
    public static class OGExtendingValidateWithAiMessage extends AbstractOGImplementingValidateWithAiMessage {
    }

    @ApplicationScoped
    public static class IGExtendingValidateWithParams extends AbstractIGImplementingValidateWithParams {
    }

    @ApplicationScoped
    public static class IGExtendingValidateWithUserMessage extends AbstractIGImplementingValidateWithUserMessage {
    }

    @ApplicationScoped
    public static class IGRedefiningBothValidateMethods implements InputGuardrail {
        @Override
        public InputGuardrailResult validate(UserMessage userMessage) {
            return success();
        }

        @Override
        public InputGuardrailResult validate(InputGuardrailParams params) {
            return success();
        }
    }

    @ApplicationScoped
    public static class OGRedefiningBothValidateMethods implements OutputGuardrail {
        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            return success();
        }

        @Override
        public OutputGuardrailResult validate(OutputGuardrailParams params) {
            return success();
        }
    }

    public static abstract class AbstractOGImplementingValidateWithParams implements OutputGuardrail {
        @Override
        public OutputGuardrailResult validate(OutputGuardrailParams params) {
            return success();
        }
    }

    public static abstract class AbstractOGImplementingValidateWithAiMessage implements OutputGuardrail {
        @Override
        public OutputGuardrailResult validate(AiMessage responseFromLLM) {
            return success();
        }
    }

    public static abstract class AbstractIGImplementingValidateWithParams implements InputGuardrail {
        @Override
        public InputGuardrailResult validate(InputGuardrailParams params) {
            return success();
        }
    }

    public static abstract class AbstractIGImplementingValidateWithUserMessage implements InputGuardrail {
        @Override
        public InputGuardrailResult validate(UserMessage userMessage) {
            return success();
        }
    }

    public static class ClassWithTimedAnnotation {
        @Timed
        public void someMethod() {

        }
    }

    public static class ClassWithCountedAnnotation {
        @Counted
        public void someMethod() {

        }
    }

    public static class ClassWithSpanAnnotation {
        @WithSpan
        public void someMethod() {

        }
    }
}
