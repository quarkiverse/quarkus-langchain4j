package io.quarkiverse.langchain4j.runtime.tool.guardrails;

import java.util.function.BiFunction;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolExecutionResult;
import io.quarkiverse.langchain4j.guardrails.ToolGuardrailException;
import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrail;
import io.quarkiverse.langchain4j.guardrails.ToolInvocationContext;
import io.quarkiverse.langchain4j.guardrails.ToolOutputGuardrail;
import io.quarkiverse.langchain4j.runtime.BlockingToolNotAllowedException;
import io.quarkiverse.langchain4j.runtime.tool.QuarkusToolExecutor;
import io.quarkiverse.langchain4j.runtime.tool.ToolMethodCreateInfo;

/**
 * Wrapper that integrates tool guardrails into the tool execution flow.
 * <p>
 * The wrapper is registered as a CDI bean and automatically discovered by
 * {@link io.quarkiverse.langchain4j.runtime.tool.QuarkusToolExecutorFactory}.
 * </p>
 * <p>
 * <strong>Event Loop:</strong> When guardrails are invoked on the Vert.x event loop (e.g., with
 * reactive/streaming tools), the guardrail execution is automatically dispatched to a worker thread to prevent
 * blocking the event loop.
 * </p>
 *
 * @see ToolGuardrailService
 * @see ToolInputGuardrail
 * @see ToolOutputGuardrail
 */
@ApplicationScoped
public class ToolGuardrailsWrapper implements QuarkusToolExecutor.Wrapper {

    private static final Logger log = Logger.getLogger(ToolGuardrailsWrapper.class);

    private final ToolGuardrailService guardrailService;

    @Inject
    public ToolGuardrailsWrapper(ToolGuardrailService guardrailService) {
        this.guardrailService = guardrailService;
    }

    @Override
    public ToolExecutionResult wrap(
            ToolExecutionRequest toolExecutionRequest,
            InvocationContext invocationContext,
            BiFunction<ToolExecutionRequest, InvocationContext, ToolExecutionResult> next,
            QuarkusToolExecutor executor) {

        // Get the tool method metadata from the executor
        ToolMethodCreateInfo methodCreateInfo = executor.getMethodCreateInfo();

        // If no metadata found or no guardrails configured, just proceed
        if (methodCreateInfo == null
                || (!guardrailService.hasInputGuardrails(methodCreateInfo)
                        && !guardrailService.hasOutputGuardrails(methodCreateInfo))) {
            return next.apply(toolExecutionRequest, invocationContext);
        }

        // We cannot have guardrails if we are invoked on the event loop because guardrail may be blocking.

        // Check if we're on the Vert.x event loop
        // If so, dispatch guardrail execution to worker thread to prevent blocking
        if (io.vertx.core.Context.isOnEventLoopThread()) {
            throw new BlockingToolNotAllowedException(
                    "Cannot execute guardrails tools on the event loop thread. Make sure your tool function is marked or detected as blocking.");
        }

        // Execute guardrails directly
        return executeWithGuardrails(toolExecutionRequest, invocationContext, next, methodCreateInfo);
    }

    /**
     * Executes the tool with input and output guardrails.
     * This method contains the actual guardrail execution logic and is called either:
     * <ul>
     * <li>Directly from {@link #wrap(ToolExecutionRequest, InvocationContext, BiFunction, QuarkusToolExecutor)} when already on
     * a worker thread</li>
     * <li>Via Uni dispatch when on event loop (to prevent blocking)</li>
     * </ul>
     *
     * @param toolExecutionRequest the original tool execution request
     * @param invocationContext the invocation context
     * @param next the next function in the chain (actual tool execution)
     * @param methodCreateInfo the tool method metadata
     * @return the tool execution result (possibly modified by guardrails)
     */
    private ToolExecutionResult executeWithGuardrails(
            ToolExecutionRequest toolExecutionRequest,
            InvocationContext invocationContext,
            BiFunction<ToolExecutionRequest, InvocationContext, ToolExecutionResult> next,
            ToolMethodCreateInfo methodCreateInfo) {

        // Create invocation context for guardrails
        ToolInvocationContext context = new ToolInvocationContext(invocationContext);

        // Execute input guardrails (if any)
        ToolExecutionRequest processedRequest = toolExecutionRequest;
        if (guardrailService.hasInputGuardrails(methodCreateInfo)) {
            try {
                processedRequest = guardrailService.executeInputGuardrails(
                        toolExecutionRequest,
                        methodCreateInfo,
                        context);

                if (log.isDebugEnabled() && processedRequest != toolExecutionRequest) {
                    log.debugv("Input guardrails modified the request for tool {0}", toolExecutionRequest.name());
                }
            } catch (ToolGuardrailException e) {
                // Check if this is a fatal failure (has a cause)
                if (e.isFatal()) {
                    // Fatal failure - re-throw to stop execution
                    log.errorv("Input guardrail failed fatally for tool {0}: {1}",
                            toolExecutionRequest.name(), e.getMessage());
                    throw e;
                }

                // Non-fatal failure - return error result to LLM
                log.warnv("Input guardrail failed for tool {0}: {1}", toolExecutionRequest.name(), e.getMessage());
                return ToolExecutionResult.builder()
                        .isError(true)
                        .resultText("Input validation failed: " + e.getMessage())
                        .build();
            }
        }

        // Execute the actual tool
        ToolExecutionResult result;
        try {
            result = next.apply(processedRequest, invocationContext);
        } catch (Exception e) {
            // Tool execution failed - let it propagate or return error
            // No need to run output guardrails on errors
            if (log.isDebugEnabled() && processedRequest != toolExecutionRequest) {
                log.debugv("Tool {0} failed after input guardrail modifications. " +
                        "Original arguments: {1}, Modified arguments: {2}",
                        toolExecutionRequest.name(),
                        toolExecutionRequest.arguments(),
                        processedRequest.arguments());
            }
            throw e;
        }

        // Execute output guardrails (if any)
        if (guardrailService.hasOutputGuardrails(methodCreateInfo)) {
            try {
                result = guardrailService.executeOutputGuardrails(
                        result,
                        processedRequest,
                        methodCreateInfo,
                        context);

                if (log.isDebugEnabled()) {
                    log.debugv("Output guardrails processed result for tool {0}", toolExecutionRequest.name());
                }
            } catch (ToolGuardrailException e) {
                // Check if this is a fatal failure (has a cause)
                if (e.isFatal()) {
                    // Fatal failure - re-throw to stop execution
                    log.errorv("Output guardrail failed fatally for tool {0}: {1}",
                            toolExecutionRequest.name(), e.getMessage());
                    throw e;
                }

                // Non-fatal failure - return error result
                log.warnv("Output guardrail failed for tool {0}: {1}",
                        toolExecutionRequest.name(), e.getMessage());
                return ToolExecutionResult.builder()
                        .isError(true)
                        .resultText("Output validation failed: " + e.getMessage())
                        .build();
            }
        }

        return result;
    }

}
