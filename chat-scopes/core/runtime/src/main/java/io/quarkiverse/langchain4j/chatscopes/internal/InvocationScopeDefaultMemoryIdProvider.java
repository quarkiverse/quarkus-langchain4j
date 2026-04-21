package io.quarkiverse.langchain4j.chatscopes.internal;

import io.quarkiverse.langchain4j.spi.DefaultMemoryIdProvider;

public class InvocationScopeDefaultMemoryIdProvider implements DefaultMemoryIdProvider {
    // Quarkus Langchain4j uses default memory provider as follows:
    //  defaultId + "#" + interfaceName + "." + methodName ;

    @Override
    public Object getMemoryId() {
        if (InvocationScopeInjectableContext.current().get() == null) {
            return null;
        }
        return InvocationScopeInjectableContext.current().get().id();
    }
}
