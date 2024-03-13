package io.quarkiverse.langchain4j.runtime.tool;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.function.BiFunction;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolExecutor;
import dev.langchain4j.internal.Json;
import io.quarkiverse.langchain4j.QuarkusJsonCodecFactory;
import io.quarkiverse.langchain4j.runtime.prompt.Mappable;

public class QuarkusToolExecutor implements ToolExecutor {

    private static final Logger log = Logger.getLogger(QuarkusToolExecutor.class);

    private final Context context;

    public record Context(Object tool, String toolInvokerName, String methodName, String argumentMapperClassName) {
    }

    public interface Wrapper {

        String wrap(ToolExecutionRequest toolExecutionRequest, Object memoryId,
                BiFunction<ToolExecutionRequest, Object, String> fun);
    }

    public QuarkusToolExecutor(Context context) {
        this.context = context;
    }

    public String execute(ToolExecutionRequest toolExecutionRequest, Object memoryId) {
        log.debugv("About to execute {0}", toolExecutionRequest);

        ToolInvoker invokerInstance = createInvokerInstance();

        Object[] params = prepareArguments(toolExecutionRequest, invokerInstance.methodMetadata(), memoryId);
        try {
            if (log.isDebugEnabled()) {
                log.debugv("Attempting to invoke tool {0} with parameters {1}", context.tool, Arrays.toString(params));
            }
            Object invocationResult = invokerInstance.invoke(context.tool,
                    params);
            String result = handleResult(invokerInstance, invocationResult);
            log.debugv("Tool execution result: {0}", result);
            return result;
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) e;
            }
            log.error("Error while executing tool '" + context.tool.getClass() + "'", e);
            return e.getMessage();
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
            ToolInvoker.MethodMetadata methodMetadata, Object memoryId) {
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
        if (memoryId != null && methodMetadata.getMemoryIdParamPosition() != null) {
            finalArgs[methodMetadata.getMemoryIdParamPosition()] = memoryId;
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
