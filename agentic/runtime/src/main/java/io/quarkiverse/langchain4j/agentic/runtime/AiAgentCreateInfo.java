package io.quarkiverse.langchain4j.agentic.runtime;

public record AiAgentCreateInfo(String agentClassName, ChatModelInfo chatModelInfo, boolean hasInterceptorBindings) {

    public sealed interface ChatModelInfo permits ChatModelInfo.FromAnnotation, ChatModelInfo.FromBeanWithName {

        final class FromAnnotation implements ChatModelInfo {
        }

        record FromBeanWithName(String name) implements ChatModelInfo {
        }
    }
}
