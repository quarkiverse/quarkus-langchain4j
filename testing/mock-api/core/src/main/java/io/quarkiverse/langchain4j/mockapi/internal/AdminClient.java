package io.quarkiverse.langchain4j.mockapi.internal;

import java.net.URI;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import io.quarkiverse.langchain4j.mockapi.internal.json.MockApiJson;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;

/**
 * Blocking client for the mock server's {@code /_admin} API.
 */
public final class AdminClient {

    private final HttpClient client;
    private final String host;
    private final int port;

    public AdminClient(Vertx vertx, String baseUrl) {
        URI uri = URI.create(baseUrl);
        this.host = uri.getHost();
        this.port = uri.getPort() != -1 ? uri.getPort() : ("https".equals(uri.getScheme()) ? 443 : 80);
        this.client = vertx.createHttpClient();
    }

    public JsonNode get(String path) {
        Result r = send(HttpMethod.GET, path, null);
        if (r.body() == null || r.body().isBlank()) {
            throw new IllegalStateException("GET " + path + " answered " + r.status() + " with an empty body");
        }
        try {
            return MockApiJson.mapper().readTree(r.body());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Invalid JSON from " + path + ": " + r.body(), e);
        }
    }

    public void postJson(String path, Object body) {
        send(HttpMethod.POST, path, toJson(body));
    }

    public void putJson(String path, Object body) {
        send(HttpMethod.PUT, path, toJson(body));
    }

    public void post(String path) {
        send(HttpMethod.POST, path, null);
    }

    public void close() {
        Future.await(client.close());
    }

    private static String toJson(Object body) {
        try {
            return MockApiJson.mapper().writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot serialize request body", e);
        }
    }

    // the body handler is attached inside the same future hop that delivers the response head: a compose chained one
    // level up runs on the caller's context, and under load the event loop delivers (and drops) the body before it runs
    private Result send(HttpMethod method, String path, String json) {
        RequestOptions opts = new RequestOptions().setHost(host).setPort(port).setURI(path).setMethod(method);
        Result r = Future.await(client.request(opts).compose(req -> {
            Future<HttpClientResponse> sent;
            if (json != null) {
                req.putHeader("Content-Type", "application/json");
                sent = req.send(Buffer.buffer(json));
            } else {
                sent = req.send();
            }
            return sent.compose(resp -> resp.body().map(buf -> new Result(resp.statusCode(), buf.toString())));
        }));
        if (r.status() < 200 || r.status() >= 300) {
            throw new IllegalStateException(
                    method + " " + path + " failed with status " + r.status() + ": " + r.body());
        }
        return r;
    }

    private record Result(int status, String body) {
    }
}
