package io.quarkiverse.langchain4j.mockapi.internal.dialect;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;

import io.quarkiverse.langchain4j.mockapi.internal.dialect.anthropic.AnthropicDialect;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.bedrock.BedrockDialect;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.cohere.CohereDialect;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.gemini.GeminiDialect;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.gemini.VertexAiGeminiDialect;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.huggingface.HuggingFaceDialect;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.mistral.MistralDialect;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.ollama.OllamaDialect;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.openai.OpenAiDialect;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.openshiftai.OpenshiftAiDialect;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.typesafe.TypeSafeDialect;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.vertexai.VertexAiDialect;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.watsonx.WatsonxDialect;

public final class DialectRegistry {

    private final List<Dialect> dialects;

    DialectRegistry(List<Dialect> dialects) {
        this.dialects = dialects;
    }

    /**
     * Every dialect the mock speaks, in resolution order: the prefixed ones own their path prefix, and the unprefixed
     * OpenAI dialect, which also serves Azure OpenAI, is tried last on any other path. The dialects all live in this
     * module, because a dialect knows a provider's wire format and the providers themselves know nothing about the mock.
     */
    public static DialectRegistry load() {
        return new DialectRegistry(List.of(
                new AnthropicDialect(),
                // the Gemini API as spoken by quarkus-langchain4j-google-genai and quarkus-langchain4j-ai-gemini
                new GeminiDialect("google-genai", "/google-genai"),
                new GeminiDialect("ai-gemini", "/ai-gemini"),
                new VertexAiGeminiDialect(),
                new VertexAiDialect(),
                new MistralDialect(),
                new OpenshiftAiDialect(),
                new OllamaDialect(),
                new CohereDialect(),
                new HuggingFaceDialect(),
                new TypeSafeDialect(),
                new WatsonxDialect(),
                new BedrockDialect(),
                new OpenAiDialect()));
    }

    public List<Dialect> dialects() {
        return dialects;
    }

    /**
     * A path under a dialect's path prefix belongs to that dialect alone, which sees it with the prefix stripped: if it
     * rejects the path, nothing matches. Other paths go to the dialects without a prefix, with the full path.
     */
    public Optional<Resolved> resolve(String method, String path, Map<String, List<String>> query, JsonNode body) {
        Optional<Dialect> owner = owner(path);
        if (owner.isPresent()) {
            return tryMatch(owner.get(), method, path.substring(owner.get().pathPrefix().length()), query, body);
        }
        for (Dialect dialect : dialects) {
            if (dialect.pathPrefix().isEmpty()) {
                Optional<Resolved> resolved = tryMatch(dialect, method, path, query, body);
                if (resolved.isPresent()) {
                    return resolved;
                }
            }
        }
        return Optional.empty();
    }

    /**
     * The fixed response of the dialect whose path prefix the path is under, see {@link Dialect#fixedResponse}.
     */
    public Optional<Fixed> fixedResponse(String method, String path, String bodyOrNull) {
        return owner(path).flatMap(dialect -> dialect
                .fixedResponse(method, path.substring(dialect.pathPrefix().length()), bodyOrNull)
                .map(rendered -> new Fixed(dialect, rendered)));
    }

    /**
     * The dialect whose path prefix the path is under.
     */
    private Optional<Dialect> owner(String path) {
        return dialects.stream()
                .filter(d -> !d.pathPrefix().isEmpty() && path.startsWith(d.pathPrefix() + "/"))
                .findFirst();
    }

    private static Optional<Resolved> tryMatch(Dialect dialect, String method, String path,
            Map<String, List<String>> query, JsonNode body) {
        return dialect.match(method, path, query, body).map(m -> new Resolved(dialect, m));
    }

    public record Resolved(Dialect dialect, Dialect.Match match) {
    }

    public record Fixed(Dialect dialect, Rendered rendered) {
    }
}
