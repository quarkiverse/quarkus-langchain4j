package io.quarkiverse.langchain4j.openai.test.proxy;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.RequestOptions;

/**
 * A plain HTTP forward proxy on a free port that records the absolute URI of every request it forwards. The mock always
 * takes over the OpenAI base URL, so a request reaches it with or without a proxy; the recorded URIs prove the client
 * went through this proxy.
 * <p>
 * {@code QuarkusExtensionTest} loads the test class, and this class with it, twice: in the system class loader, where the
 * static extension field and its config are built, and in the application class loader, where the test methods run. The
 * proxy runs in the system class loader copy and the application copy delegates to it, so both see the same proxy.
 */
final class ForwardProxy {

    private static final List<String> forwarded = new CopyOnWriteArrayList<>();
    private static Vertx vertx;
    private static HttpServer server;
    private static HttpClient client;

    private ForwardProxy() {
    }

    static synchronized int port() {
        if (!isSystemCopy()) {
            return (int) callSystemCopy("port");
        }
        if (vertx == null) {
            forwarded.clear();
            vertx = Vertx.vertx();
            client = vertx.createHttpClient();
            server = await(vertx.createHttpServer().requestHandler(ForwardProxy::forward).listen(0));
        }
        return server.actualPort();
    }

    @SuppressWarnings("unchecked")
    static List<String> forwardedUris() {
        if (!isSystemCopy()) {
            return (List<String>) callSystemCopy("forwardedUris");
        }
        return forwarded;
    }

    static synchronized void stop() {
        if (!isSystemCopy()) {
            callSystemCopy("stop");
            return;
        }
        if (vertx != null) {
            await(vertx.close());
            vertx = null;
        }
    }

    private static void forward(HttpServerRequest request) {
        forwarded.add(request.uri());
        // the upstream body is read inside the same future hop that delivers its head; a compose chained one level
        // up runs later, and under load the event loop can deliver and drop the body before it runs
        request.body()
                .compose(body -> client.request(new RequestOptions().setMethod(request.method())
                        .setAbsoluteURI(request.uri()).setHeaders(withoutFraming(request.headers())))
                        .compose(outgoing -> outgoing.send(body)
                                .compose(response -> response.body().map(payload -> {
                                    request.response().setStatusCode(response.statusCode());
                                    request.response().headers().addAll(withoutFraming(response.headers()));
                                    return payload;
                                }))))
                .onSuccess(payload -> request.response().end(payload))
                .onFailure(failure -> request.response().setStatusCode(502).end(String.valueOf(failure)));
    }

    // the proxy buffers whole bodies, so Vert.x sets the length of what it sends itself
    private static MultiMap withoutFraming(MultiMap headers) {
        return MultiMap.caseInsensitiveMultiMap().addAll(headers)
                .remove(HttpHeaders.CONTENT_LENGTH)
                .remove(HttpHeaders.TRANSFER_ENCODING);
    }

    private static boolean isSystemCopy() {
        return ForwardProxy.class.getClassLoader() == ClassLoader.getSystemClassLoader();
    }

    private static Object callSystemCopy(String method) {
        try {
            var systemMethod = ClassLoader.getSystemClassLoader().loadClass(ForwardProxy.class.getName())
                    .getDeclaredMethod(method);
            systemMethod.setAccessible(true);
            return systemMethod.invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static <T> T await(Future<T> future) {
        return future.toCompletionStage().toCompletableFuture().orTimeout(10, TimeUnit.SECONDS).join();
    }
}
