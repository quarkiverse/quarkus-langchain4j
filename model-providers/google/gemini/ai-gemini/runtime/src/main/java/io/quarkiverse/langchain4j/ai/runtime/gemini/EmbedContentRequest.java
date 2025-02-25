package io.quarkiverse.langchain4j.ai.runtime.gemini;

import io.quarkiverse.langchain4j.gemini.common.Content;

public record EmbedContentRequest(String model, Content content,
        TaskType taskType, String title, Integer outputDimensionality) {

    public enum TaskType {
        TASK_TYPE_UNSPECIFIED,
        RETRIEVAL_QUERY,
        RETRIEVAL_DOCUMENT,
        SEMANTIC_SIMILARITY,
        CLASSIFICATION,
        CLUSTERING,
        QUESTION_ANSWERING,
        FACT_VERIFICATION
    }

}
