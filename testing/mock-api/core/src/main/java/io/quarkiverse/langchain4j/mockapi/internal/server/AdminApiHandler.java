package io.quarkiverse.langchain4j.mockapi.internal.server;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.quarkiverse.langchain4j.mockapi.Operation;
import io.quarkiverse.langchain4j.mockapi.Reply;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.Dialect;
import io.quarkiverse.langchain4j.mockapi.internal.dialect.DialectRegistry;
import io.quarkiverse.langchain4j.mockapi.internal.json.MockApiJson;
import io.quarkiverse.langchain4j.mockapi.internal.model.RawStub;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;

final class AdminApiHandler {

    private static final ObjectMapper MAPPER = MockApiJson.mapper();

    private final MockState state;
    private final DialectRegistry registry;

    AdminApiHandler(MockState state, DialectRegistry registry) {
        this.state = state;
        this.registry = registry;
    }

    boolean handles(String path) {
        return path.startsWith("/_admin");
    }

    void handle(HttpServerRequest request) {
        request.body().onSuccess(buf -> {
            try {
                dispatch(request, buf.toString(StandardCharsets.UTF_8));
            } catch (Exception e) {
                json(request.response(), 400, error(e.getMessage() != null ? e.getMessage() : e.toString()));
            }
        }).onFailure(t -> json(request.response(), 400, error(String.valueOf(t.getMessage()))));
    }

    private void dispatch(HttpServerRequest request, String body) throws Exception {
        String method = request.method().name();
        String[] seg = request.path().substring("/_admin".length()).split("/");
        // seg[0] is empty because the remainder starts with '/'
        HttpServerResponse resp = request.response();
        if (method.equals("POST") && seg.length == 3 && seg[1].equals("replies")) {
            state.enqueue(operation(seg[2]), MAPPER.readValue(body, new TypeReference<List<Reply>>() {
            }));
            noContent(resp);
        } else if (method.equals("PUT") && seg.length == 4 && seg[1].equals("replies") && seg[3].equals("default")) {
            state.setDefault(operation(seg[2]), MAPPER.readValue(body, Reply.class));
            noContent(resp);
        } else if (method.equals("POST") && seg.length == 2 && seg[1].equals("raw-stubs")) {
            state.addRawStub(MAPPER.readValue(body, RawStub.class));
            noContent(resp);
        } else if (method.equals("GET") && seg.length == 2 && seg[1].equals("requests")) {
            String operation = request.getParam("operation");
            Operation op = operation == null ? null : operation(operation);
            json(resp, 200, MAPPER.writeValueAsString(state.requests(op)));
        } else if (method.equals("POST") && seg.length == 2 && seg[1].equals("reset")) {
            state.reset();
            noContent(resp);
        } else if (method.equals("GET") && seg.length == 2 && seg[1].equals("health")) {
            ObjectNode node = MAPPER.createObjectNode().put("status", "UP");
            ArrayNode ids = node.putArray("dialects");
            for (Dialect d : registry.dialects()) {
                ids.add(d.id());
            }
            json(resp, 200, MAPPER.writeValueAsString(node));
        } else {
            json(resp, 404, error("unknown admin path"));
        }
    }

    private static Operation operation(String name) {
        return Operation.valueOf(name.toUpperCase(Locale.ROOT));
    }

    private static void noContent(HttpServerResponse resp) {
        resp.setStatusCode(204).end();
    }

    private static void json(HttpServerResponse resp, int status, String body) {
        resp.setStatusCode(status).putHeader("Content-Type", "application/json").end(Buffer.buffer(body));
    }

    private static String error(String message) {
        return MAPPER.createObjectNode().put("error", message).toString();
    }
}
