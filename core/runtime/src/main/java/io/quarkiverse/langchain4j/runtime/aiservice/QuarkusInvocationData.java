package io.quarkiverse.langchain4j.runtime.aiservice;

import dev.langchain4j.invocation.LangChain4jManaged;

public sealed interface QuarkusInvocationData extends LangChain4jManaged
        permits AiServiceInvocationData {
}
