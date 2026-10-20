package io.quarkiverse.langchain4j.mockapi.internal.server;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;

/**
 * Blocking HTTP client for the mock server tests.
 */
final class HttpTestClient implements AutoCloseable {

    record Resp(int statusCode, String contentType, String body) {
    }

    private final int port;
    private final Vertx vertx = Vertx.vertx();
    private final HttpClient client = vertx.createHttpClient();

    HttpTestClient(int port) {
        this.port = port;
    }

    Resp post(String path, String body) {
        return send(HttpMethod.POST, path, null, body);
    }

    Resp put(String path, String body) {
        return send(HttpMethod.PUT, path, null, body);
    }

    Resp get(String path) {
        return send(HttpMethod.GET, path, null, "");
    }

    // the body is read in the same future chain as the response head; reading it later from the test thread can
    // lose data that already arrived
    Resp send(HttpMethod method, String path, String contentType, String body) {
        return send(method, path, contentType, Buffer.buffer(body));
    }

    Resp send(HttpMethod method, String path, String contentType, Buffer body) {
        RequestOptions opts = new RequestOptions().setHost("localhost").setPort(port).setURI(path).setMethod(method);
        if (contentType != null) {
            opts.putHeader("Content-Type", contentType);
        }
        return Future.await(client.request(opts)
                .compose(req -> req.send(body)
                        .compose(resp -> resp.body()
                                .map(b -> new Resp(resp.statusCode(), resp.getHeader("Content-Type"), b.toString())))));
    }

    @Override
    public void close() {
        Future.await(client.close());
        Future.await(vertx.close());
    }
}
