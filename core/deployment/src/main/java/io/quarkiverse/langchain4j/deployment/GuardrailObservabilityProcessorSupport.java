package io.quarkiverse.langchain4j.deployment;

import java.util.Optional;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;
import org.jboss.logging.Logger;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailRequest;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailResult;

final class GuardrailObservabilityProcessorSupport {
    private static final Logger LOG = Logger.getLogger(GuardrailObservabilityProcessorSupport.class);
    private static final DotName INPUT_GUARDRAIL_REQUEST = DotName.createSimple(InputGuardrailRequest.class);
    private static final DotName INPUT_GUARDRAIL_RESULT = DotName.createSimple(InputGuardrailResult.class);
    private static final DotName OUTPUT_GUARDRAIL_REQUEST = DotName.createSimple(OutputGuardrailRequest.class);
    private static final DotName OUTPUT_GUARDRAIL_RESULT = DotName.createSimple(OutputGuardrailResult.class);
    private static final DotName INPUT_GUARDRAIL = DotName.createSimple(InputGuardrail.class);
    private static final DotName OUTPUT_GUARDRAIL = DotName.createSimple(OutputGuardrail.class);

    static final DotName MICROMETER_TIMED = DotName.createSimple("io.micrometer.core.annotation.Timed");
    static final DotName MICROMETER_COUNTED = DotName.createSimple("io.micrometer.core.annotation.Counted");
    static final DotName WITH_SPAN = DotName.createSimple("io.opentelemetry.instrumentation.annotations.WithSpan");
    private static final DotName USER_MESSAGE = DotName.createSimple(UserMessage.class);
    private static final String VALIDATE_METHOD_NAME = "validate";

    enum TransformType {
        METRICS,
        OTEL
    }

    enum GuardrailType {
        INPUT,
        OUTPUT;

        static Optional<GuardrailType> from(IndexView indexView, ClassInfo classToCheck) {
            if (indexView.getAllKnownImplementors(INPUT_GUARDRAIL).contains(classToCheck)) {
                return Optional.of(INPUT);
            } else if (indexView.getAllKnownImplementors(OUTPUT_GUARDRAIL).contains(classToCheck)) {
                return Optional.of(OUTPUT);
            }

            return Optional.empty();
        }
    }

    static boolean doesMethodHaveMetricsAnnotations(MethodInfo methodInfo) {
        return !methodInfo.annotations(MICROMETER_TIMED).isEmpty() || !methodInfo.annotations(MICROMETER_COUNTED).isEmpty();
    }

    static boolean doesMethodHaveSpanAnnotation(MethodInfo methodInfo) {
        return !methodInfo.annotations(WITH_SPAN).isEmpty();
    }

    static boolean shouldTransformMethod(MethodInfo methodInfo, IndexView indexView,
            TransformType transformType) {

        // First check whether the method name is even "validate"
        if (VALIDATE_METHOD_NAME.equals(methodInfo.name())) {
            var methodClass = methodInfo.declaringClass();

            // Then ensure the method's declaring class is NOT an interface
            if (!methodClass.isInterface()) {
                // Then check that the class the method is on is an implementation of InputGuardrail/OutputGuardrail
                var shouldTransform = GuardrailType.from(indexView, methodClass)
                        .map(guardrailType -> shouldTransformGuardrailValidateMethod(methodInfo, methodClass, transformType,
                                guardrailType))
                        .orElse(false);

                if (shouldTransform) {
                    LOG.debugf("Transforming guardrail method %s on class %s", methodInfo, methodClass);
                }

                return shouldTransform;
            }
        }

        return false;
    }

    private static boolean doesMethodAlreadyHaveTransformationAnnotation(MethodInfo methodInfo, TransformType transformType) {
        return switch (transformType) {
            case METRICS -> doesMethodHaveMetricsAnnotations(methodInfo);
            case OTEL -> doesMethodHaveSpanAnnotation(methodInfo);
        };
    }

    private static boolean shouldTransformGuardrailValidateMethod(MethodInfo methodInfo, ClassInfo methodDeclaringClass,
            TransformType transformType, GuardrailType guardrailType) {

        if (isGuardrailValidateMethodWithParams(methodInfo)) {
            // If the method is the validate method with the params, then just check that
            // the method doesn't already have the annotations
            return !doesMethodAlreadyHaveTransformationAnnotation(methodInfo, transformType);
        }

        var isOtherValidateMethodVariant = switch (guardrailType) {
            case INPUT -> isInputGuardrailValidateMethodWithUserMessage(methodInfo);
            case OUTPUT -> isOutputGuardrailValidateMethodWithAiMessage(methodInfo);
        };

        if (isOtherValidateMethodVariant && !doesMethodAlreadyHaveTransformationAnnotation(methodInfo, transformType)) {
            // If this is the other method variant, we need to ensure that the
            // variant with the params isn't also present on the method's declaring class
            var paramType = switch (guardrailType) {
                case INPUT -> Type.parse(INPUT_GUARDRAIL_REQUEST.toString());
                case OUTPUT -> Type.parse(OUTPUT_GUARDRAIL_REQUEST.toString());
            };

            var otherValidateMethod = methodDeclaringClass.method("validate", paramType);

            // Only transform this method if the other one isn't present on the class
            // If the other variant is present, it'll be picked up by this process later
            // In a separate iteration
            return otherValidateMethod == null;
        }

        return false;
    }

    /**
     * Checks the method meets <strong>ALL</strong> the following conditions:
     * <ul>
     * <li>The method's name is {@link #VALIDATE_METHOD_NAME}</li>
     * <li><strong>IF</strong> the method's single parameter's type is {@link InputGuardrailRequest} then the return type must
     * be
     * {@link InputGuardrailResult}</li>
     * <li><strong>IF</strong> the method's single parameter's type is {@link OutputGuardrailRequest} then the return type must
     * be {@link OutputGuardrailResult}</li>
     * </ul>
     */
    static boolean isGuardrailValidateMethodWithParams(MethodInfo methodInfo) {
        return VALIDATE_METHOD_NAME.equals(methodInfo.name()) && doesValidateMethodWithParamsHaveCorrectSignature(methodInfo);
    }

    /**
     * Checks the method meets <strong>ALL</strong> the following conditions:
     * <ul>
     * <li>The method's name is {@link #VALIDATE_METHOD_NAME}</li>
     * <li>The method's return type is {@link InputGuardrailResult}</li>
     * <li>The method's single parameter's type is {@link dev.langchain4j.data.message.UserMessage}</li>
     * </ul>
     */
    private static boolean isInputGuardrailValidateMethodWithUserMessage(MethodInfo methodInfo) {
        return VALIDATE_METHOD_NAME.equals(methodInfo.name())
                && doesValidateMethodWithoutParamsHaveCorrectSignature(methodInfo, USER_MESSAGE);
    }

    /**
     * Checks the method meets <strong>ALL</strong> the following conditions:
     * <ul>
     * <li>The method's name is {@link #VALIDATE_METHOD_NAME}</li>
     * <li>The method's return type is {@link OutputGuardrailResult}</li>
     * <li>The method's single parameter's type is {@link dev.langchain4j.data.message.AiMessage}</li>
     * </ul>
     */
    private static boolean isOutputGuardrailValidateMethodWithAiMessage(MethodInfo methodInfo) {
        return VALIDATE_METHOD_NAME.equals(methodInfo.name())
                && doesValidateMethodWithoutParamsHaveCorrectSignature(methodInfo, LangChain4jDotNames.AI_MESSAGE);
    }

    /**
     * Checks the method meets <strong>ALL</strong> the following conditions:
     * <ul>
     * <li><strong>IF</strong> the method's single parameter's type is {@link InputGuardrailRequest} then the return type must
     * be
     * {@link InputGuardrailResult}</li>
     * <li><strong>IF</strong> the method's single parameter's type is {@link OutputGuardrailRequest} then the return type must
     * be {@link OutputGuardrailResult}</li>
     * </ul>
     */
    private static boolean doesValidateMethodWithParamsHaveCorrectSignature(MethodInfo methodInfo) {
        // First check the parameters
        var parameters = methodInfo.parameters();

        if (parameters.size() == 1) {
            // Only a single parameter, so check against the return type
            // i.e. if parameter type == InputGuardrailParams, then return type should be InputGuardrailResult
            // i.e. if parameter type == OutputGuardrailParams, then return type should be OututGuardrailResult
            var paramTypeName = parameters.get(0).type().name();

            // Also check the return type
            var returnType = methodInfo.returnType().name();

            return (INPUT_GUARDRAIL_REQUEST.equals(paramTypeName) && INPUT_GUARDRAIL_RESULT.equals(returnType)) ||
                    (OUTPUT_GUARDRAIL_REQUEST.equals(paramTypeName) && OUTPUT_GUARDRAIL_RESULT.equals(returnType));
        }

        return false;
    }

    private static boolean doesValidateMethodWithoutParamsHaveCorrectSignature(MethodInfo methodInfo, DotName paramType) {
        // First check the parameters
        var parameters = methodInfo.parameters();

        if (parameters.size() == 1) {
            // Only a single parameter, so check against the return type
            // i.e. if parameter type == paramType, then return type should be InputGuardrailResult/OutputGuardrailResult
            var paramTypeName = parameters.get(0).type().name();

            // Also check the return type
            var returnType = methodInfo.returnType().name();

            return paramType.equals(paramTypeName) &&
                    (INPUT_GUARDRAIL_RESULT.equals(returnType) || OUTPUT_GUARDRAIL_RESULT.equals(returnType));
        }

        return false;
    }
}
