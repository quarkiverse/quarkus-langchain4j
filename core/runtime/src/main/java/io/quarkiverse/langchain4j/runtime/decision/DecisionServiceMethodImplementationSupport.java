package io.quarkiverse.langchain4j.runtime.decision;

import java.lang.reflect.Method;

/**
 * Implements a method of a Decision Service, as the proxy created by
 * {@code dev.langchain4j.service.decision.DecisionServices} does. Invoked by the generated implementations.
 */
public final class DecisionServiceMethodImplementationSupport {

    private DecisionServiceMethodImplementationSupport() {
    }

    public static Object invoke(QuarkusDecisionServiceContext context, Method method, Object[] args) {
        return context.decisionMethod(method).invoke(context.config(), args == null ? new Object[0] : args);
    }
}
