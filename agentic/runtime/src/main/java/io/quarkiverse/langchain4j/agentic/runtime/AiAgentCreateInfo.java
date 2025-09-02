package io.quarkiverse.langchain4j.agentic.runtime;

public record AiAgentCreateInfo(String agentClassName, ChatModelInfo chatModelInfo) {

    public sealed interface ChatModelInfo permits ChatModelInfo.FromAnnotation, ChatModelInfo.FromBeanWithName {

        final class FromAnnotation implements ChatModelInfo {
        }

        record FromBeanWithName(String name) implements ChatModelInfo {
        }
    }
}
