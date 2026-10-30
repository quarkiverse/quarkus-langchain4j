package io.quarkiverse.langchain4j.deployment;

import java.util.List;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.MethodInfo;

import io.quarkus.builder.item.MultiBuildItem;

/**
 * A Decision Service, an interface annotated with {@code @RegisterDecisionService}.
 */
public final class DecisionServiceBuildItem extends MultiBuildItem {

    private final ClassInfo serviceInterface;
    private final List<MethodInfo> methods;
    private final String decisionModelName;
    private final DotName scope;
    private final boolean booleanAnswer;

    public DecisionServiceBuildItem(ClassInfo serviceInterface, List<MethodInfo> methods, String decisionModelName,
            DotName scope, boolean booleanAnswer) {
        this.serviceInterface = serviceInterface;
        this.methods = methods;
        this.decisionModelName = decisionModelName;
        this.scope = scope;
        this.booleanAnswer = booleanAnswer;
    }

    public ClassInfo getServiceInterface() {
        return serviceInterface;
    }

    /**
     * The abstract methods of the interface and of its superinterfaces, each implemented by a call to the decision
     * model.
     */
    public List<MethodInfo> getMethods() {
        return methods;
    }

    public String getDecisionModelName() {
        return decisionModelName;
    }

    public DotName getScope() {
        return scope;
    }

    /**
     * Whether one of the methods has a {@code boolean} answer, the only kind a threshold applies to.
     */
    public boolean hasBooleanAnswer() {
        return booleanAnswer;
    }
}
