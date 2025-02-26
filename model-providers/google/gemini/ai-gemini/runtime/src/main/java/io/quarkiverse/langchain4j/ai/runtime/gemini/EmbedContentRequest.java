package io.quarkiverse.langchain4j.ai.runtime.gemini;

import io.quarkiverse.langchain4j.gemini.common.Content;

/**
 * Request containing the Content for the model to embed.
 *
 * @param model The model's resource name.
 * @param content The content to embed.
 * @param taskType Optional task type for which the embeddings will be used
 * @param title An optional title for the text.
 * @param outputDimensionality Optional reduced dimension for the output embedding.
 *
 * @see <a href="https://ai.google.dev/api/embeddings#EmbedContentRequest">EmbedContentRequest</a>
 */
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
