package io.quarkiverse.langchain4j.runtime;

import jakarta.annotation.Priority;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

import dev.ai4j.openai4j.chat.ChatCompletionRequest;
import io.quarkiverse.langchain4j.NoStream;

@NoStream
@Interceptor
@Priority(Interceptor.Priority.LIBRARY_BEFORE + 10)
public class NoStreamInterceptor {

    @AroundInvoke
    Object logInvocation(InvocationContext context) throws Exception {
        Object[] parameters = context.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            Object parameter = parameters[i];
            if (parameter instanceof ChatCompletionRequest) {
                var req = ((ChatCompletionRequest) parameter);
                if (Boolean.TRUE.equals(req.stream())) {
                    parameters[i] = ChatCompletionRequest.builder().from(req).stream(null).build();
                }
            }
        }
        return context.proceed();
    }
}
