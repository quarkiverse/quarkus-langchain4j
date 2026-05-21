package io.quarkiverse.langchain4j.agentic.runtime;

import dev.langchain4j.agentic.declarative.ChatSupplierParameterResolver;
import io.quarkus.arc.Arc;

public class CdiChatSupplierParameterResolver implements ChatSupplierParameterResolver {

    @Override
    public boolean supports(Context context) {
        return context.parameter().isAnnotationPresent(CdiBean.class);
    }

    @Override
    public Object resolve(Context context) {
        return Arc.container().select(context.parameter().getType()).get();
    }
}
