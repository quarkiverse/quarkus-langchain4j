package io.quarkiverse.langchain4j.agentic.runtime;

public record AiAgentCreateInfo(String agentClassName, ChatModelInfo chatModelInfo, boolean hasInterceptorBindings,
        boolean hasMcpToolBox) {

    public sealed interface ChatModelInfo
            permits ChatModelInfo.FromAnnotation, ChatModelInfo.FromBeanWithName, ChatModelInfo.NotNeeded {

        final class FromAnnotation implements ChatModelInfo {
        }

        record FromBeanWithName(String name) implements ChatModelInfo {
        }

        final class NotNeeded implements ChatModelInfo {
        }
    }
}
