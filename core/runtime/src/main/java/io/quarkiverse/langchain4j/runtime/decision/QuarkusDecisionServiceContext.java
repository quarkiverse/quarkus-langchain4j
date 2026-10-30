package io.quarkiverse.langchain4j.runtime.decision;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

import dev.langchain4j.service.decision.internal.DecisionMethod;
import dev.langchain4j.service.decision.internal.DecisionServiceConfig;

/**
 * What the generated implementation of a Decision Service needs to implement its methods: the configuration of the
 * service and the analysis of each of its methods, which validates them.
 */
public class QuarkusDecisionServiceContext {

    private final DecisionServiceConfig config;
    private final Map<Method, DecisionMethod> decisionMethods;

    public QuarkusDecisionServiceContext(DecisionServiceConfig config) {
        this.config = config;
        Map<Method, DecisionMethod> methods = new HashMap<>();
        for (Method method : config.serviceInterface().getMethods()) {
            if (!method.isDefault() && !Modifier.isStatic(method.getModifiers())) {
                methods.put(method, DecisionMethod.from(method));
            }
        }
        this.decisionMethods = Map.copyOf(methods);
    }

    public DecisionServiceConfig config() {
        return config;
    }

    public DecisionMethod decisionMethod(Method method) {
        DecisionMethod decisionMethod = decisionMethods.get(method);
        if (decisionMethod == null) {
            throw new IllegalStateException("Method " + method + " is not a method of the Decision Service "
                    + config.serviceInterface().getName());
        }
        return decisionMethod;
    }
}
