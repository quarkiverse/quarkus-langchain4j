package io.quarkiverse.langchain4j.agentic.runtime;

import java.util.Set;

public record AiAgentCreateInfo(String agentClassName, ChatModelInfo chatModelInfo, boolean hasInterceptorBindings,
        Set<CdiSupplierType> cdiResolvedSuppliers, boolean hasMcpToolBox) {

    public sealed interface ChatModelInfo permits ChatModelInfo.FromAnnotation, ChatModelInfo.FromBeanWithName {

        final class FromAnnotation implements ChatModelInfo {
        }

        record FromBeanWithName(String name) implements ChatModelInfo {
        }
    }
}
