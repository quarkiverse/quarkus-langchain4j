package io.quarkiverse.langchain4j.mockapi.internal.dialect;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;

import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkiverse.langchain4j.mockapi.ChatRequestView;
import io.quarkiverse.langchain4j.mockapi.DecisionRequestView;
import io.quarkiverse.langchain4j.mockapi.EmbeddingReply;
import io.quarkiverse.langchain4j.mockapi.ErrorReply;
import io.quarkiverse.langchain4j.mockapi.Operation;
import io.quarkiverse.langchain4j.mockapi.RawResponse;
import io.quarkiverse.langchain4j.mockapi.Reply;

/**
 * Translates between one provider's wire format and the provider-neutral model of the mock.
 * <p>
 * A test scripts replies and asserts on requests in neutral terms only: a {@link ChatReply}, an {@link EmbeddingReply},
 * a {@link ChatRequestView}. A provider's client speaks its own HTTP API: an OpenAI {@code chat/completions} body, an
 * Anthropic {@code messages} body, an Ollama NDJSON stream. A dialect is the one place that knows a given wire format,
 * so the server, the admin API and the tests never do. There is one dialect per wire format, all in this module (a
 * provider knows nothing about the mock), listed in resolution order by {@link DialectRegistry#load()}. Each dialect's
 * class comment names the provider documentation its format was written from, which is the source of truth, and any
 * point where the provider's real client is known to deviate from it. The examples in the user guide are generated
 * from the dialects and verified by a test, so they always show what a dialect actually sends.
 * <p>
 * The server ({@code ModelApiHandler}) drives a dialect in this order for every request that no raw stub claimed:
 * <ol>
 * <li>{@link #fixedResponse} is asked whether the request is one the dialect answers by itself, such as a token
 * endpoint; if so the answer is written and nothing else happens.</li>
 * <li>{@link #match} decides whether the request belongs to this dialect and which neutral {@link Operation} it is.
 * The first dialect whose {@link #pathPrefix()} the path starts with is the only one asked; a path under no prefix is
 * offered to the unprefixed dialects in turn.</li>
 * <li>{@link #parseChat} or {@link #parseDecision} turns the request body into the neutral view that is journaled
 * with the request and that the test reads back through {@link CapturedRequest#asChat()}.</li>
 * <li>The next scripted reply for the operation is taken from the state (or the default reply, or a 404 "no reply
 * configured" if there is none). An {@link ErrorReply} goes to {@link #renderError}, a {@link RawResponse} is written
 * as is, and every other reply goes to {@link #render}, which produces the provider's documented response or throws
 * {@link UnrenderableReply}, which the server answers with {@link #renderError} and status 500.</li>
 * <li>The {@link Rendered} result is written by {@code ResponseWriter}: a plain body, or a stream in the format the
 * dialect chose (server-sent events, newline-delimited JSON, or the AWS event stream encoding).</li>
 * </ol>
 * A dialect is stateless and is called from the single event loop of the mock server; it must not block.
 */
public interface Dialect {

    /**
     * The provider id, as in {@code quarkus.langchain4j.<kind>-model.provider}; it is journaled with every captured
     * request as {@link CapturedRequest#dialect()} and listed by the admin health endpoint.
     */
    String id();

    /**
     * The path segment the mock puts into the provider's base URL, so that the provider's requests arrive under a path
     * of their own: empty, or starting with {@code /} and without a trailing slash, for example {@code /anthropic}.
     * <p>
     * A path under a prefix belongs to that dialect alone: {@link #match} and {@link #fixedResponse} then see the path
     * with the prefix stripped, and a request the dialect rejects is a 404, never handed to another dialect. The
     * unprefixed dialect (OpenAI, whose paths other providers imitate) is tried last on every other path. The prefix
     * must match the one in the provider's {@code ProviderSpec}.
     */
    default String pathPrefix() {
        return "";
    }

    /**
     * Decides whether this request belongs to the dialect and, if so, what it is.
     * <p>
     * Match on what the provider's documentation defines, typically the method and the path (for example
     * {@code POST /v1/messages}), and on the body only where the path is ambiguous (Bedrock's {@code invoke} serves
     * embeddings and text generation from the same path). Return empty for anything else, including the provider's
     * endpoints the mock does not model; those are left to raw stubs and should be listed in the class comment.
     *
     * @param path the request path with {@link #pathPrefix()} stripped
     * @param query the decoded query parameters, which some providers use for the API version or the stream flag
     * @param bodyOrNull the parsed JSON request body, or null when the body is absent or not JSON
     * @return the operation, whether the client asked for a streamed response, and a dialect-specific variant
     */
    Optional<Match> match(String method, String path, Map<String, List<String>> query, JsonNode bodyOrNull);

    /**
     * Answers a request that needs no scripted reply, such as a provider's token endpoint that must succeed before any
     * model call, or empty for every request {@link #match} should see.
     * <p>
     * Consulted only for dialects with a non-empty {@link #pathPrefix()}, with the prefix stripped, and after the raw
     * stubs, which take precedence, so a test can still override such an endpoint. The request is journaled without
     * an operation, so it appears in {@code requests()} but not in an operation's requests, and the response is written
     * at once: reply delays do not apply.
     *
     * @param bodyOrNull the request body as text, or null when absent; a token endpoint, for example, receives a form
     */
    default Optional<Rendered> fixedResponse(String method, String path, String bodyOrNull) {
        return Optional.empty();
    }

    /**
     * Reads a chat request into the neutral view a test asserts on: the model name, the messages with their roles and
     * text, the tool definitions and the tool choice. Called only for a {@link Operation#CHAT} match with a JSON body;
     * a failure to parse is journaled as a request without a view rather than failing the request.
     * <p>
     * Read defensively: the client may send the same thing in several documented shapes (a string or a list of content
     * parts, tool arguments as an object or as a JSON string), and the view should be the same whichever it sent. The
     * default returns null for dialects without a chat API.
     */
    default ChatRequestView parseChat(Match m, JsonNode body) {
        return null;
    }

    /**
     * The decision-API counterpart of {@link #parseChat}, called only for a {@link Operation#DECISION} match; the
     * default returns null for dialects without a decision API.
     */
    default DecisionRequestView parseDecision(Match m, JsonNode body) {
        return null;
    }

    /**
     * Produces the provider's documented response for a scripted reply.
     * <p>
     * The reply is what the test scripted for the matched operation, so its type follows the operation (a
     * {@link ChatReply} for a chat match, an {@link EmbeddingReply} for an embeddings match, and so on). For a reply of
     * another type, or one the format cannot express (too few vectors or scores, tool call arguments of the wrong
     * shape), throw {@link UnrenderableReply} naming the problem: the server answers it with {@link #renderError} and
     * status 500. Render only fields the provider documents, and all the client reads: a finish reason mapped to the
     * provider's own vocabulary, usage, tool calls with the arguments in the shape the provider defines.
     * <p>
     * When {@link Match#streaming()} is true, return a {@link Rendered#stream} (server-sent events, NDJSON, or the AWS
     * event stream) whose events follow the provider's streaming protocol end to end, from the first message event to
     * the terminator, with the text split into chunks by {@link Chunker} so that partial-response callbacks fire. The
     * captured request is passed for what the response has to echo or depend on: the model name, the number of
     * embedding inputs, the number of documents to score.
     */
    Rendered render(Match m, Reply reply, CapturedRequest req);

    /**
     * Produces the provider's documented error envelope with the given status and message, as sent for an
     * {@link ErrorReply} the test scripted and for the {@link UnrenderableReply} that {@link #render} throws. The client's
     * error handling
     * and retry logic react to this, so the shape and the status must be the ones the provider really sends.
     */
    Rendered renderError(Match m, int status, String message);

    /**
     * What {@link #match} recognized.
     *
     * @param operation the neutral operation the request is journaled under and whose scripted replies it consumes
     * @param streaming whether the client asked for a streamed response, which decides how {@link #render} answers
     * @param variant a dialect-specific refinement {@link #render} needs to tell sub-formats of one operation apart,
     *        such as {@code chat-completions} or {@code responses} for OpenAI, the model id in a Gemini path, or
     *        {@code deployment/<id>} for a watsonx deployment; null when the dialect has nothing to distinguish
     */
    record Match(Operation operation, boolean streaming, String variant) {
    }
}
