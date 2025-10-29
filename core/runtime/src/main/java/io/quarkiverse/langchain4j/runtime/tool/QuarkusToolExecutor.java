package io.quarkiverse.langchain4j.runtime.tool;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.function.BiFunction;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;

import dev.langchain4j.agent.tool.ReturnBehavior;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.exception.ToolExecutionException;
import dev.langchain4j.internal.Json;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import io.quarkiverse.langchain4j.QuarkusJsonCodecFactory;
import io.quarkiverse.langchain4j.runtime.prompt.Mappable;
import io.quarkus.virtual.threads.VirtualThreadsRecorder;
import io.smallrye.mutiny.Uni;

public class QuarkusToolExecutor implements ToolExecutor {

    private static final Logger log = Logger.getLogger(QuarkusToolExecutor.class);

    private final Context context;

    public record Context(Object tool, String toolInvokerName, String methodName, String argumentMapperClassName,
            ToolMethodCreateInfo.ExecutionModel executionModel, ReturnBehavior returnBehavior,
            boolean propagateToolExecutionExceptions) {
    }

    public interface Wrapper {

        ToolExecutionResult wrap(ToolExecutionRequest toolExecutionRequest, InvocationContext invocationContext,
                BiFunction<ToolExecutionRequest, InvocationContext, ToolExecutionResult> fun);
    }

    public QuarkusToolExecutor(Context context) {
        this.context = context;
    }

    public ReturnBehavior returnBehavior() {
        return context.returnBehavior;
    }

    @Override
    public String execute(ToolExecutionRequest toolExecutionRequest, Object memoryId) {
        return executeWithContext(toolExecutionRequest, InvocationContext.builder().chatMemoryId(memoryId).build())
                .resultText();
    }

    @Override
    public ToolExecutionResult executeWithContext(ToolExecutionRequest request, InvocationContext invocationContext) {
        log.debugv("About to execute {0}", request);

        // TODO Tools invocation are "imperative"
        // TODO This method is called from the caller thread
        // TODO So, we need to handle the dispatch here, depending on the caller thread and the tool invocation
        // TODO Note that we need to return a String in an imperative manner.
        // TODO We may have to check who's going to call this method from a non-blocking thread to handle the dispatch there.

        ToolInvoker invokerInstance = createInvokerInstance();
        Object[] params = prepareArguments(request, invokerInstance.methodMetadata(), invocationContext);
        // When required to block, we are invoked on a worker thread (stream with blocking tools).
        switch (context.executionModel) {
            case BLOCKING:
                if (io.vertx.core.Context.isOnEventLoopThread()) {
                    throw new IllegalStateException("Cannot execute blocking tools on event loop thread");
                }
                return invoke(params, invokerInstance);
            case NON_BLOCKING:
                return invoke(params, invokerInstance);
            case VIRTUAL_THREAD:
                if (io.vertx.core.Context.isOnEventLoopThread()) {
                    throw new IllegalStateException("Cannot execute virtual thread tools on event loop thread");
                }
                try {
                    return VirtualThreadsRecorder.getCurrent().submit(() -> invoke(params, invokerInstance))
                            .get();
                } catch (Exception e) {
                    if (e instanceof CompletionException) {
                        if (e.getCause() instanceof RuntimeException) {
                            throw (RuntimeException) e.getCause();
                        } else {
                            throw new RuntimeException(e);
                        }
                    } else if (e instanceof RuntimeException) {
                        throw (RuntimeException) e;
                    } else {
                        throw new RuntimeException(e);
                    }
                }
            default:
                throw new IllegalStateException("Unknown execution model: " + context.executionModel);
        }
    }

    private ToolExecutionResult invoke(Object[] params, ToolInvoker invokerInstance) {
        try {
            if (log.isDebugEnabled()) {
                log.debugv("Attempting to invoke tool {0} with parameters {1}", context.tool, Arrays.toString(params));
            }
            Object invocationResult = invokerInstance.invoke(context.tool, params);
            String result;
            if (invocationResult instanceof Uni<?>) { // TODO CS
                if (io.vertx.core.Context.isOnEventLoopThread()) {
                    throw new ToolExecutionException(
                            "Cannot execute tools returning Uni on event loop thread due to a tool executor limitation");
                }
                result = handleResult(invokerInstance, ((Uni<?>) invocationResult).await().indefinitely());
            } else {
                result = handleResult(invokerInstance, invocationResult);
            }
            log.debugv("Tool execution result: {0}", result);
            return ToolExecutionResult.builder().result(invocationResult).resultText(result).build();
        } catch (ToolExecutionException e) {
            throw e;
        } catch (Exception e) {
            if (context.propagateToolExecutionExceptions) {
                throw new ToolExecutionException(e);
            }
            log.error("Error while executing tool '" + context.tool.getClass() + "'", e);
            return ToolExecutionResult.builder().isError(true).resultText(e.getMessage()).build();
        }
    }

    private static String handleResult(ToolInvoker invokerInstance, Object invocationResult) {
        if (invokerInstance.methodMetadata().isReturnsVoid()) {
            return "Success";
        }
        return Json.toJson(invocationResult);
    }

    // TODO: cache
    private ToolInvoker createInvokerInstance() {
        ToolInvoker invokerInstance;
        try {
            invokerInstance = (ToolInvoker) Class.forName(context.toolInvokerName, true, Thread.currentThread()
                    .getContextClassLoader()).getConstructor().newInstance();
        } catch (ClassNotFoundException | NoSuchMethodException | InstantiationException | IllegalAccessException
                | InvocationTargetException e) {
            throw new IllegalStateException(
                    "Unable to create instance of '" + context.toolInvokerName
                            + "'. Please report this issue to the maintainers",
                    e);
        }
        return invokerInstance;
    }

    private Object[] prepareArguments(ToolExecutionRequest toolExecutionRequest,
            ToolInvoker.MethodMetadata methodMetadata, InvocationContext invocationContext) {
        String argumentsJsonStr = toolExecutionRequest.arguments();
        Map<String, Object> argumentsFromRequest;
        try {
            log.debugv("Attempting to convert {0} JSON string into args map", argumentsJsonStr);
            argumentsFromRequest = convertJsonToArguments(argumentsJsonStr);
            log.debugv("Converted {0} JSON string into args map {1}", argumentsJsonStr, argumentsFromRequest);
        } catch (JsonProcessingException e) {
            log.error(e);
            invalidMethodParams(argumentsJsonStr);
            return null; //keep the compiler happy
        }
        if (argumentsFromRequest.size() != methodMetadata.getNameToParamPosition().size()) {
            invalidMethodParams(argumentsJsonStr);
        }

        Object[] finalArgs = new Object[argumentsFromRequest.size()];

        for (var entry : argumentsFromRequest.entrySet()) {
            Integer pos = methodMetadata.getNameToParamPosition().get(entry.getKey());
            if (pos == null) {
                invalidMethodParams(argumentsJsonStr);
            } else {
                finalArgs[pos] = entry.getValue();
            }
        }
        Object memoryId = invocationContext.chatMemoryId();
        if (memoryId != null && methodMetadata.getMemoryIdParamPosition() != null) {
            finalArgs[methodMetadata.getMemoryIdParamPosition()] = memoryId;
        }
        Object invocationParams = invocationContext.invocationParameters();
        if (invocationParams != null && methodMetadata.getInvocationParamsParamPosition() != null) {
            finalArgs[methodMetadata.getInvocationParamsParamPosition()] = invocationParams;
        }
        return finalArgs;
    }

    private Map<String, Object> convertJsonToArguments(String argumentsJsonStr) throws JsonProcessingException {
        if (argumentsJsonStr == null || argumentsJsonStr.isEmpty()) {
            return Collections.emptyMap();
        }
        Mappable mappable = QuarkusJsonCodecFactory.ObjectMapperHolder.MAPPER.readValue(argumentsJsonStr, loadMapperClass());
        return mappable.obtainFieldValuesMap();
    }

    @SuppressWarnings("unchecked")
    private Class<? extends Mappable> loadMapperClass() {
        try {
            return (Class<? extends Mappable>) Class.forName(context.argumentMapperClassName, true, Thread.currentThread()
                    .getContextClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(
                    "Unable to load argument mapper of '" + context.toolInvokerName
                            + "'. Please report this issue to the maintainers",
                    e);
        }
    }

    private void invalidMethodParams(String argumentsJsonStr) {
        throw new IllegalArgumentException("params '" + argumentsJsonStr
                + "' from request do not map onto the parameters needed by '" + context.tool.getClass().getName() + "#"
                + context.methodName
                + "'");
    }

}
