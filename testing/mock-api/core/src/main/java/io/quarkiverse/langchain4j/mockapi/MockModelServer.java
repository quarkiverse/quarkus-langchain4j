package io.quarkiverse.langchain4j.mockapi;

import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;

import io.quarkiverse.langchain4j.mockapi.internal.AdminClient;
import io.quarkiverse.langchain4j.mockapi.internal.json.MockApiJson;
import io.vertx.core.Future;
import io.vertx.core.Vertx;

/**
 * Test-facing client for the mock model API dev service.
 */
public final class MockModelServer implements AutoCloseable {

    private final AdminClient admin;
    private final Vertx ownedVertx;

    private MockModelServer(AdminClient admin, Vertx ownedVertx) {
        this.admin = admin;
        this.ownedVertx = ownedVertx;
    }

    public static MockModelServer connect(Vertx vertx, String url) {
        return new MockModelServer(new AdminClient(vertx, url), null);
    }

    public static MockModelServer connect(String url) {
        Vertx vertx = Vertx.vertx();
        return new MockModelServer(new AdminClient(vertx, url), vertx);
    }

    public OperationScope chat() {
        return scope(Operation.CHAT);
    }

    public OperationScope embeddings() {
        return scope(Operation.EMBEDDINGS);
    }

    public OperationScope moderation() {
        return scope(Operation.MODERATION);
    }

    public OperationScope scoring() {
        return scope(Operation.SCORING);
    }

    public OperationScope decision() {
        return scope(Operation.DECISION);
    }

    public RawStubs raw() {
        return new RawStubs(admin);
    }

    public List<CapturedRequest> requests() {
        return parseRequests(admin.get("/_admin/requests"));
    }

    public void reset() {
        admin.post("/_admin/reset");
    }

    @Override
    public void close() {
        admin.close();
        if (ownedVertx != null) {
            Future.await(ownedVertx.close());
        }
    }

    private OperationScope scope(Operation operation) {
        return new OperationScope(admin, operation);
    }

    static List<CapturedRequest> parseRequests(JsonNode array) {
        // the admin API always answers with an array; anything else (an empty body, a JSON null) is a transport fault
        // that must surface with what was received, not as a null list
        if (array == null || !array.isArray()) {
            throw new IllegalStateException("The mock's request journal is not a JSON array: " + array);
        }
        return MockApiJson.mapper().convertValue(array, new TypeReference<List<CapturedRequest>>() {
        });
    }
}
