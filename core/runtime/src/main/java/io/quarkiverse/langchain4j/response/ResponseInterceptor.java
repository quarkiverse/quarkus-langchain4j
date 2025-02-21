package io.quarkiverse.langchain4j.response;

import java.util.Map;

import jakarta.annotation.Priority;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.listener.ChatModelResponse;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.Response;

/**
 * Simple (Chat)Response interceptor, to be applied directly on the model.
 */
@Interceptor
@ResponseInterceptorBinding
@Priority(0)
public class ResponseInterceptor extends ResponseInterceptorBase {

    @AroundInvoke
    public Object intercept(InvocationContext context) throws Exception {
        Object result = context.proceed();
        ResponseRecord rr = null;
        if (result instanceof Response<?> response) {
            Object content = response.content();
            if (content instanceof AiMessage am) {
                rr = new ResponseRecord(getModel(context.getTarget()), am, response.tokenUsage(), response.finishReason(),
                        response.metadata());
            }
        } else if (result instanceof ChatResponse response) {
            rr = new ResponseRecord(getModel(context.getTarget()), response.aiMessage(), response.tokenUsage(),
                    response.finishReason(), Map.of());
        } else if (result instanceof ChatModelResponse response) {
            rr = new ResponseRecord(response.model(), response.aiMessage(), response.tokenUsage(), response.finishReason(),
                    Map.of("id", response.id()));
        }
        if (rr != null) {
            for (ResponseListener l : getListeners()) {
                l.onResponse(rr);
            }
        }
        return result;
    }
}
