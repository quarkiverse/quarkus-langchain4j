package io.quarkiverse.langchain4j.runtime.aiservice;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import io.quarkus.arc.All;
import io.quarkus.arc.Unremovable;

public class MethodImplementationSupportProducer {

    @Produces
    @Singleton
    @Unremovable
    AiServiceMethodImplementationSupport methodImplementationSupport(
            @All List<AiServiceMethodImplementationSupport.Wrapper> wrappers) {
        AiServiceMethodImplementationSupport base = new AiServiceMethodImplementationSupport();
        if (wrappers.isEmpty()) {
            return base;
        }
        return new AiServiceMethodImplementationSupport() {
            @Override
            public Object implement(Input input) {

                AtomicReference<Function<Input, Object>> funRef = new AtomicReference<>(new Function<Input, Object>() {
                    @Override
                    public Object apply(Input input) {
                        return base.implement(input);
                    }
                });

                for (Wrapper wrapper : wrappers) {
                    Function<Input, Object> currentFun = funRef.get();
                    wrapper.wrap(input, currentFun);
                    Function<Input, Object> newFunction = new Function<Input, Object>() {
                        @Override
                        public Object apply(Input input) {
                            return wrapper.wrap(input, currentFun);
                        }
                    };
                    funRef.set(newFunction);
                }

                return funRef.get().apply(input);
            }
        };
    }
}
