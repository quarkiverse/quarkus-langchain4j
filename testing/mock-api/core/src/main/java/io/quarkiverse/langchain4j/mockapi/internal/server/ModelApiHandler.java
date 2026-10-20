package io.quarkiverse.langchain4j.mockapi.internal.server;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.ChatRequestView;
import io.quarkiverse.langchain4j.mockapi.DecisionRequestView;
import io.quarkiverse.langchain4j.mockapi.ErrorReply;
import io.quarkiverse.langchain4j.mockapi.Operation;
import io.quarkiverse.langchain4j.mockapi.RawResponse;
import io.quarkiverse.langchain4j.mockapi.Reply;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Dialect;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.DialectRegistry;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Rendered;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.UnrenderableReply;
import io.quarkiverse.langchain4j.mockapi.internal.json.MockApiJson;
import io.quarkiverse.langchain4j.mockapi.internal.model.CapturedRequestImpl;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;

final class ModelApiHandler implements Handler<HttpServerRequest> {

    private static final ObjectMapper MAPPER = MockApiJson.mapper();

    private final MockState state;
    private final DialectRegistry registry;
    private final Vertx vertx;

    ModelApiHandler(MockState state, DialectRegistry registry, Vertx vertx) {
        this.state = state;
        this.registry = registry;
        this.vertx = vertx;
    }

    @Override
    public void handle(HttpServerRequest request) {
        request.body().onSuccess(buf -> {
            try {
                process(request, buf);
            } catch (Exception e) {
                writeError(request.response(), 500, "internal error: " + e);
            }
        }).onFailure(t -> writeError(request.response(), 500, "could not read body: " + t));
    }

    private record Body(String text, JsonNode json) {
    }

    private void process(HttpServerRequest request, Buffer buf) {
        String method = request.method().name();
        String path = request.path();
        Map<String, List<String>> query = toMap(request.params());
        Map<String, List<String>> headers = toMap(request.headers());
        long index = state.nextIndex();
        String contentType = request.getHeader("Content-Type");
        Body body = classifyBody(buf, contentType == null ? null : contentType.toLowerCase());
        JsonNode json = body.json();

        Optional<MockState.CompiledStub> raw = state.matchRaw(method, path, json);
        if (raw.isPresent()) {
            state.record(capture(index, request, headers, body, null, null, null, null));
            RawResponse response = raw.get().next();
            writeLater(request.response(), response.delay(), toRendered(response));
            return;
        }

        Optional<DialectRegistry.Fixed> fixed = registry.fixedResponse(method, path, body.text());
        if (fixed.isPresent()) {
            state.record(capture(index, request, headers, body, null, fixed.get().dialect().id(), null, null));
            ResponseWriter.write(request.response(), fixed.get().rendered());
            return;
        }

        Optional<DialectRegistry.Resolved> resolved = registry.resolve(method, path, query, json);
        if (resolved.isEmpty()) {
            state.record(capture(index, request, headers, body, null, null, null, null));
            ObjectNode node = MAPPER.createObjectNode().put("error", "no dialect matched").put("path", path);
            ArrayNode ids = node.putArray("dialects");
            registry.dialects().forEach(d -> ids.add(d.id()));
            writeJson(request.response(), 404, node.toString());
            return;
        }

        Dialect dialect = resolved.get().dialect();
        Dialect.Match match = resolved.get().match();
        Operation op = match.operation();
        ChatRequestView chat = op == Operation.CHAT && json != null ? parseSafely(() -> dialect.parseChat(match, json))
                : null;
        DecisionRequestView decision = op == Operation.DECISION && json != null
                ? parseSafely(() -> dialect.parseDecision(match, json))
                : null;
        CapturedRequest captured = capture(index, request, headers, body, op, dialect.id(), chat, decision);
        state.record(captured);

        Reply reply = state.next(op);
        if (reply == null) {
            ObjectNode node = MAPPER.createObjectNode().put("error", "no reply configured").put("operation", op.name());
            writeJson(request.response(), 404, node.toString());
            return;
        }
        Rendered rendered;
        if (reply instanceof ErrorReply e) {
            rendered = dialect.renderError(match, e.status(), e.message());
        } else if (reply instanceof RawResponse r) {
            rendered = toRendered(r);
        } else {
            try {
                rendered = dialect.render(match, reply, captured);
            } catch (UnrenderableReply e) {
                rendered = dialect.renderError(match, 500, e.getMessage());
            }
        }
        writeLater(request.response(), reply.delay(), rendered);
    }

    private static CapturedRequest capture(long index, HttpServerRequest request, Map<String, List<String>> headers,
            Body body, Operation op, String dialectId, ChatRequestView chat, DecisionRequestView decision) {
        return new CapturedRequestImpl(index, request.method().name(), request.path(), request.query(), headers, body.text(),
                op, dialectId, chat, decision);
    }

    private static Body classifyBody(Buffer buf, String contentType) {
        boolean textual = contentType == null || contentType.contains("json") || contentType.contains("text")
                || contentType.contains("xml") || contentType.contains("x-www-form-urlencoded");
        String bodyText = buf.toString(StandardCharsets.UTF_8);
        JsonNode json = parseJson(bodyText, contentType);
        if (buf.length() == 0) {
            return new Body(null, json);
        }
        if (textual || json != null) {
            return new Body(bodyText, json);
        }
        // a binary body (a multipart upload) is kept with one character per byte, so its textual parts can be asserted on
        // and the bytes recovered with ISO-8859-1
        return new Body(buf.toString(StandardCharsets.ISO_8859_1), json);
    }

    private static <T> T parseSafely(Supplier<T> parse) {
        try {
            return parse.get();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void writeLater(HttpServerResponse response, Duration delay, Rendered rendered) {
        if (delay == null || delay.isZero() || delay.isNegative()) {
            ResponseWriter.write(response, rendered);
        } else {
            vertx.setTimer(delay.toMillis(), id -> {
                // the client may have given up while the reply was delayed
                if (response.closed()) {
                    return;
                }
                try {
                    ResponseWriter.write(response, rendered);
                } catch (Exception e) {
                    writeError(response, 500, "internal error: " + e);
                }
            });
        }
    }

    private static Rendered toRendered(RawResponse r) {
        Map<String, String> headers = r.headers() != null ? r.headers() : Map.of();
        if (r.events() != null && !r.events().isEmpty()) {
            return new Rendered(r.status(), headers, null, r.events(), Rendered.StreamFormat.SSE);
        }
        byte[] bytes = null;
        if (r.bodyBase64() != null) {
            bytes = Base64.getDecoder().decode(r.bodyBase64());
        } else if (r.body() != null) {
            bytes = r.body().getBytes(StandardCharsets.UTF_8);
        }
        return new Rendered(r.status(), headers, bytes, List.of(), Rendered.StreamFormat.NONE);
    }

    private static JsonNode parseJson(String text, String contentType) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        boolean looksJson = (contentType != null && contentType.contains("json")) || trimmed.startsWith("{")
                || trimmed.startsWith("[");
        if (!looksJson) {
            return null;
        }
        try {
            return MAPPER.readTree(trimmed);
        } catch (Exception e) {
            return null;
        }
    }

    private static Map<String, List<String>> toMap(MultiMap multiMap) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : multiMap.entries()) {
            result.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(e.getValue());
        }
        return result;
    }

    private static void writeError(HttpServerResponse response, int status, String message) {
        if (response.ended() || response.closed()) {
            return;
        }
        if (response.headWritten()) {
            response.end();
            return;
        }
        writeJson(response, status, MAPPER.createObjectNode().put("error", message).toString());
    }

    private static void writeJson(HttpServerResponse response, int status, String body) {
        response.setStatusCode(status).putHeader("Content-Type", "application/json").end(Buffer.buffer(body));
    }
}
