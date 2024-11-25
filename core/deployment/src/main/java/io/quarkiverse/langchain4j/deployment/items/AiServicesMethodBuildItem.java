package io.quarkiverse.langchain4j.deployment.items;

import java.util.ArrayList;
import java.util.List;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.DotName;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;

import io.quarkiverse.langchain4j.guardrails.OutputGuardrailAccumulator;
import io.quarkiverse.langchain4j.response.ResponseAugmenter;
import io.quarkiverse.langchain4j.runtime.aiservice.AiServiceMethodCreateInfo;
import io.quarkus.builder.item.MultiBuildItem;

/**
 * A build item representing a method from an AI service.
 */
public final class AiServicesMethodBuildItem extends MultiBuildItem {

    private final MethodInfo methodInfo;
    private final List<String> outputGuardrails;
    private final List<String> inputGuardrails;
    private final AiServiceMethodCreateInfo methodCreateInfo;
    private final String responseAugmenter;

    public AiServicesMethodBuildItem(MethodInfo methodInfo, List<String> inputGuardrails, List<String> outputGuardrails,
            String responseAugmenter,
            AiServiceMethodCreateInfo methodCreateInfo) {
        this.methodInfo = methodInfo;
        this.inputGuardrails = inputGuardrails;
        this.outputGuardrails = outputGuardrails;
        this.responseAugmenter = responseAugmenter;
        this.methodCreateInfo = methodCreateInfo;
    }

    public List<String> getOutputGuardrails() {
        return outputGuardrails;
    }

    public List<String> getInputGuardrails() {
        return inputGuardrails;
    }

    public MethodInfo getMethodInfo() {
        return methodInfo;
    }

    public AiServiceMethodCreateInfo getMethodCreateInfo() {
        return methodCreateInfo;
    }

    public String getResponseAugmenter() {
        return responseAugmenter;
    }

    public static List<String> gatherGuardrails(MethodInfo methodInfo, DotName annotation) {
        List<String> guardrails = new ArrayList<>();
        AnnotationInstance instance = methodInfo.annotation(annotation);
        if (instance == null) {
            // Check on class
            instance = methodInfo.declaringClass().declaredAnnotation(annotation);
        }
        if (instance != null) {
            Type[] array = instance.value().asClassArray();
            for (Type type : array) {
                // Make sure each guardrail is used only once
                if (!guardrails.contains(type.name().toString())) {
                    guardrails.add(type.name().toString());
                }
            }
        }
        return guardrails;
    }

    public static String gatherAccumulator(MethodInfo methodInfo) {
        DotName annotation = DotName.createSimple(OutputGuardrailAccumulator.class);
        AnnotationInstance instance = methodInfo.annotation(annotation);
        if (instance == null) {
            // Check on class
            instance = methodInfo.declaringClass().declaredAnnotation(annotation);
        }
        if (instance != null) {
            return instance.value().asClass().name().toString();
        }
        return null;
    }

    public static String gatherResponseAugmenter(MethodInfo methodInfo) {
        DotName annotation = DotName.createSimple(ResponseAugmenter.class);
        AnnotationInstance instance = methodInfo.annotation(annotation);
        if (instance == null) {
            // Check on class
            instance = methodInfo.declaringClass().declaredAnnotation(annotation);
        }
        if (instance != null) {
            return instance.value().asClass().name().toString();
        }
        return null;
    }
}
