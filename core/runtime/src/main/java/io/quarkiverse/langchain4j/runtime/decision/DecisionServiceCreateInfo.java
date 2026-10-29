package io.quarkiverse.langchain4j.runtime.decision;

/**
 * @param serviceClassName the name of the interface of the Decision Service
 * @param decisionModelName the name of its decision model
 */
public record DecisionServiceCreateInfo(String serviceClassName, String decisionModelName) {
}
