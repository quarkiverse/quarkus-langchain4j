package io.quarkiverse.langchain4j.agentic.runtime;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a parameter of a static method of an agent as a CDI bean, looked up at each invocation of the method. A
 * parameter annotated with a CDI qualifier is a CDI bean too, without this annotation.
 * <p>
 * It can be used on the methods annotated with any supplier annotation, like
 * {@link dev.langchain4j.agentic.declarative.ChatModelSupplier} or
 * {@link dev.langchain4j.agentic.declarative.ToolsSupplier}, on the methods annotated with
 * {@link dev.langchain4j.agentic.declarative.ExitCondition}, {@link dev.langchain4j.agentic.declarative.ActivationCondition},
 * {@link dev.langchain4j.agentic.declarative.Output}, {@link dev.langchain4j.agentic.declarative.BeforeCall},
 * {@link dev.langchain4j.agentic.declarative.ErrorHandler}, {@link dev.langchain4j.agentic.declarative.SupervisorRequest},
 * {@link dev.langchain4j.agentic.declarative.ParallelExecutor} and
 * {@link dev.langchain4j.agentic.declarative.A2AClientCustomizer}, on the static {@link dev.langchain4j.agentic.Agent}
 * methods of non-AI agents and on the {@link dev.langchain4j.agentic.declarative.HumanInTheLoop} methods.
 * <p>
 * The parameter is not read from the {@link dev.langchain4j.agentic.scope.AgenticScope} and is not an argument of
 * the agent.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface CdiBean {
}
