package io.quarkiverse.langchain4j.runtime.tool.guardrails;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.AmbiguousResolutionException;
import jakarta.enterprise.inject.UnsatisfiedResolutionException;
import jakarta.enterprise.inject.spi.CDI;

import org.jboss.logging.Logger;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.service.tool.ToolExecutionResult;
import io.quarkiverse.langchain4j.guardrails.ToolGuardrailException;
import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrail;
import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrailRequest;
import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrailResult;
import io.quarkiverse.langchain4j.guardrails.ToolInputGuardrails;
import io.quarkiverse.langchain4j.guardrails.ToolInvocationContext;
import io.quarkiverse.langchain4j.guardrails.ToolMetadata;
import io.quarkiverse.langchain4j.guardrails.ToolOutputGuardrail;
import io.quarkiverse.langchain4j.guardrails.ToolOutputGuardrailRequest;
import io.quarkiverse.langchain4j.guardrails.ToolOutputGuardrailResult;
import io.quarkiverse.langchain4j.guardrails.ToolOutputGuardrails;
import io.quarkiverse.langchain4j.runtime.tool.ToolMethodCreateInfo;

/**
 * Service for managing and executing tool guardrails.
 * <p>
 * This service is responsible for:
 * </p>
 * <ul>
 * <li>Checking if a tool has input or output guardrails configured</li>
 * <li>Executing guardrails in the order they are declared</li>
 * <li>Handling guardrail failures with appropriate error handling</li>
 * <li>Managing request/result transformations from guardrails</li>
 * </ul>
 * <p>
 * Guardrails are executed with fail-fast semantics: the first guardrail that
 * returns a non-success result stops the chain and returns the failure.
 * </p>
 * <p>
 * Guardrail beans are looked up via CDI, enabling dependency injection and
 * support for different scopes (ApplicationScoped, RequestScoped, etc.).
 * </p>
 *
 * @see ToolInputGuardrail
 * @see ToolOutputGuardrail
 * @see ToolMethodCreateInfo
 */
@ApplicationScoped
public class ToolGuardrailService {

    private static final Logger log = Logger.getLogger(ToolGuardrailService.class);

    /**
     * Checks if the tool method has input guardrails configured.
     *
     * @param methodCreateInfo the tool method metadata
     * @return true if input guardrails are present, false otherwise
     */
    public boolean hasInputGuardrails(ToolMethodCreateInfo methodCreateInfo) {
        return methodCreateInfo.getInputGuardrails() != null
                && methodCreateInfo.getInputGuardrails().hasGuardrails();
    }

    /**
     * Checks if the tool method has output guardrails configured.
     *
     * @param methodCreateInfo the tool method metadata
     * @return true if output guardrails are present, false otherwise
     */
    public boolean hasOutputGuardrails(ToolMethodCreateInfo methodCreateInfo) {
        return methodCreateInfo.getOutputGuardrails() != null
                && methodCreateInfo.getOutputGuardrails().hasGuardrails();
    }

    /**
     * Executes input guardrails for a tool execution request.
     * <p>
     * Guardrails are executed in the order they appear in the {@link ToolInputGuardrails}
     * annotation. The first guardrail that returns a non-success result stops the chain.
     * </p>
     * <p>
     * If a guardrail returns a modified request via {@link ToolInputGuardrailResult#successWith},
     * that modified request is passed to subsequent guardrails and eventually returned.
     * </p>
     *
     * @param request the tool execution request to validate
     * @param methodCreateInfo the tool method metadata containing guardrail configuration
     * @param context the invocation context
     * @return the original or modified tool execution request
     * @throws ToolGuardrailException if a guardrail returns a fatal failure
     */
    public ToolExecutionRequest executeInputGuardrails(
            ToolExecutionRequest request,
            ToolMethodCreateInfo methodCreateInfo,
            ToolInvocationContext context) {

        Class<? extends ToolInputGuardrail>[] guardrailClasses = methodCreateInfo.getInputGuardrails().value();

        ToolExecutionRequest currentRequest = request;
        ToolMetadata toolMetadata = methodCreateInfo.getToolMetadata();

        for (Class<? extends ToolInputGuardrail> guardrailClass : guardrailClasses) {
            if (log.isDebugEnabled()) {
                log.debugv("Executing input guardrail {0} for tool {1}",
                        guardrailClass.getSimpleName(), request.name());
            }

            ToolInputGuardrail guardrail = lookupGuardrail(guardrailClass);

            ToolInputGuardrailRequest guardrailRequest = new ToolInputGuardrailRequest(
                    currentRequest,
                    toolMetadata,
                    context);

            ToolInputGuardrailResult result = guardrail.validate(guardrailRequest);

            if (!result.isSuccess()) {
                return handleInputGuardrailFailure(result, guardrailClass, request.name());
            }

            // Apply request modification if present
            if (result.modifiedRequest() != null) {
                if (log.isDebugEnabled()) {
                    log.debugv("Input guardrail {0} modified the request for tool {1}",
                            guardrailClass.getSimpleName(), request.name());
                }
                currentRequest = result.modifiedRequest();
            }
        }

        return currentRequest;
    }

    /**
     * Executes output guardrails for a tool execution result.
     * <p>
     * Guardrails are executed in the order they appear in the {@link ToolOutputGuardrails}
     * annotation. The first guardrail that returns a non-success result stops the chain.
     * </p>
     * <p>
     * If a guardrail returns a modified result via {@link ToolOutputGuardrailResult#successWith},
     * that modified result is passed to subsequent guardrails and eventually returned.
     * </p>
     *
     * @param result the tool execution result to validate
     * @param request the original tool execution request
     * @param methodCreateInfo the tool method metadata containing guardrail configuration
     * @param context the invocation context
     * @return the original or modified tool execution result
     * @throws ToolGuardrailException if a guardrail returns a fatal failure
     */
    public ToolExecutionResult executeOutputGuardrails(
            ToolExecutionResult result,
            ToolExecutionRequest request,
            ToolMethodCreateInfo methodCreateInfo,
            ToolInvocationContext context) {

        Class<? extends ToolOutputGuardrail>[] guardrailClasses = methodCreateInfo.getOutputGuardrails().value();

        ToolExecutionResult currentResult = result;
        ToolMetadata toolMetadata = methodCreateInfo.getToolMetadata();

        for (Class<? extends ToolOutputGuardrail> guardrailClass : guardrailClasses) {
            if (log.isDebugEnabled()) {
                log.debugv("Executing output guardrail {0} for tool {1}",
                        guardrailClass.getSimpleName(), request.name());
            }

            ToolOutputGuardrail guardrail = lookupGuardrail(guardrailClass);

            ToolOutputGuardrailRequest guardrailRequest = new ToolOutputGuardrailRequest(
                    currentResult,
                    request,
                    toolMetadata,
                    context);

            ToolOutputGuardrailResult guardrailResult;
            try {
                guardrailResult = guardrail.validate(guardrailRequest);
            } catch (Exception e) {
                guardrailResult = ToolOutputGuardrailResult.fatal(e);
            }

            if (!guardrailResult.isSuccess()) {
                return handleOutputGuardrailFailure(guardrailResult, guardrailClass, request.name());
            }

            // Apply result modification if present
            if (guardrailResult.modifiedResult() != null) {
                if (log.isDebugEnabled()) {
                    log.debugv("Output guardrail {0} modified the result for tool {1}",
                            guardrailClass.getSimpleName(), request.name());
                }
                currentResult = guardrailResult.modifiedResult();
            }
        }

        return currentResult;
    }

    /**
     * Looks up a guardrail bean via CDI.
     *
     * @param guardrailClass the guardrail class
     * @param <T> the guardrail type
     * @return the guardrail instance
     * @throws IllegalStateException if the guardrail bean cannot be found
     */
    private <T> T lookupGuardrail(Class<T> guardrailClass) {
        try {
            return CDI.current().select(guardrailClass).get();
        } catch (UnsatisfiedResolutionException e) {
            throw new IllegalStateException(
                    String.format("Failed to lookup guardrail bean '%s'. " +
                            "Common causes:\n" +
                            "  1. Missing CDI scope annotation (@ApplicationScoped, @RequestScoped, etc.)\n" +
                            "  2. Class not included in Jandex index\n" +
                            "  3. Bean explicitly removed by Arc optimization\n" +
                            "Verify the guardrail is a valid CDI bean.",
                            guardrailClass.getName()),
                    e);
        } catch (AmbiguousResolutionException e) {
            throw new IllegalStateException(
                    String.format("Multiple CDI beans found for guardrail '%s'. " +
                            "Ensure only one implementation exists or use @Priority for disambiguation.",
                            guardrailClass.getName()),
                    e);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Unexpected error looking up guardrail bean: " + guardrailClass.getName(),
                    e);
        }
    }

    /**
     * Handles input guardrail validation failure.
     *
     * @param result the guardrail result indicating failure
     * @param guardrailClass the guardrail class that failed
     * @param toolName the tool name
     * @return a tool execution request (never actually returns, always throws)
     * @throws ToolGuardrailException if the failure has a cause (fatal failure)
     */
    private ToolExecutionRequest handleInputGuardrailFailure(
            ToolInputGuardrailResult result,
            Class<? extends ToolInputGuardrail> guardrailClass,
            String toolName) {

        String errorMessage = result.errorMessage() != null
                ? result.errorMessage()
                : "Input validation failed";

        if (result.isFatalFailure()) {
            // Fatal failure - throw exception
            log.errorv("Input guardrail {0} failed fatally for tool {1}: {2}",
                    guardrailClass.getSimpleName(), toolName, errorMessage);
            throw new ToolGuardrailException(errorMessage, result.cause(), true);
        }

        // Non-fatal failure - this will be caught by the wrapper and converted to a ToolExecutionResult
        log.warnv("Input guardrail {0} failed for tool {1}: {2}",
                guardrailClass.getSimpleName(), toolName, errorMessage);
        throw new ToolGuardrailException(errorMessage);
    }

    /**
     * Handles output guardrail validation failure.
     *
     * @param result the guardrail result indicating failure
     * @param guardrailClass the guardrail class that failed
     * @param toolName the tool name
     * @return a tool execution result containing the error message
     */
    private ToolExecutionResult handleOutputGuardrailFailure(
            ToolOutputGuardrailResult result,
            Class<? extends ToolOutputGuardrail> guardrailClass,
            String toolName) {

        String errorMessage = result.errorMessage() != null
                ? result.errorMessage()
                : "Output validation failed";

        if (result.isFatalFailure()) {
            // Fatal failure - throw exception
            log.errorv("Output guardrail {0} failed fatally for tool {1}: {2}",
                    guardrailClass.getSimpleName(), toolName, errorMessage);
            throw new ToolGuardrailException(errorMessage, result.cause(), true);
        }

        // Non-fatal failure - return error as tool result
        log.warnv("Output guardrail {0} failed for tool {1}: {2}",
                guardrailClass.getSimpleName(), toolName, errorMessage);
        return ToolExecutionResult.builder()
                .isError(true)
                .resultText("Output validation failed: " + errorMessage)
                .build();
    }
}
