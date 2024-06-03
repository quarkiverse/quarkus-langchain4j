package io.quarkiverse.langchain4j.runtime.aiservice;

import java.util.Map;

/**
 * @param methodMap the key is a methodId generated at build time
 */
public record AiServiceClassCreateInfo(Map<String, AiServiceMethodCreateInfo> methodMap, String implClassName) {
}
