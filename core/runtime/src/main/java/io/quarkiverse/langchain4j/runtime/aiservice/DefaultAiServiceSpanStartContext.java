package io.quarkiverse.langchain4j.runtime.aiservice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class DefaultAiServiceSpanStartContext implements AiServiceSpanStartContext {

    private final String interfaceName;
    private final String methodName;
    private final List<Object> methodArguments;

    DefaultAiServiceSpanStartContext(Builder builder) {
        this.interfaceName = builder.interfaceName;
        this.methodName = builder.methodName;
        // the arguments may contain nulls, which List.copyOf rejects
        this.methodArguments = (builder.methodArguments == null)
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(builder.methodArguments));
    }

    @Override
    public String interfaceName() {
        return interfaceName;
    }

    @Override
    public String methodName() {
        return methodName;
    }

    @Override
    public List<Object> methodArguments() {
        return methodArguments;
    }
}
