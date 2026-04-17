package io.quarkiverse.langchain4j.chatscopes.internal;

import jakarta.annotation.Priority;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

@Interceptor
@InternalWireInvocationScoped
@Priority(Interceptor.Priority.PLATFORM_BEFORE + 100)
public class InvocationScopeInterceptor {

    @AroundInvoke
    Object aroundInvoke(InvocationContext ctx) throws Exception {
        boolean terminate = InvocationScopeInjectableContext.shouldTerminate();

        try {
            //System.out.println("InvocationScopeInterceptor.destroyOnExit: " + destroyOnExit);
            return ctx.proceed();
        } finally {
            if (terminate) {
                InvocationScopeInjectableContext.terminate();
            }
        }
    }

}
