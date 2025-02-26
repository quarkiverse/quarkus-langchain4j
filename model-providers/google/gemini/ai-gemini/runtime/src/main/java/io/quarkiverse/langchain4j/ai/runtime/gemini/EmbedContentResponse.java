package io.quarkiverse.langchain4j.ai.runtime.gemini;

/**
 * The response body contains data.
 *
 * @param embedding The embedding generated from the input content.
 *
 * @see <a href="https://ai.google.dev/api/embeddings#response-body">EmbedContentResponse</a>
 */
public record EmbedContentResponse(Embedding embedding) {
    public record Embedding(float[] values) {
    }
}
