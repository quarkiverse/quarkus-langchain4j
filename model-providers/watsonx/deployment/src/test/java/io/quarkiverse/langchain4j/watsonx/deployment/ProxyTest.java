package io.quarkiverse.langchain4j.watsonx.deployment;

import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.API_KEY;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.COS_PATH;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.PROJECT_ID;
import static io.quarkiverse.langchain4j.watsonx.deployment.WatsonxTestUtil.URL_WATSONX_CHAT_API;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.ibm.watsonx.ai.textprocessing.textextraction.TextExtractionService;

import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.mockapi.ChatReply;
import io.quarkus.test.QuarkusExtensionTest;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.RequestOptions;

class ProxyTest extends WatsonxMockTestBase {

    private static final String PROJECT_PATH = "/projects/v2/projects/" + PROJECT_ID;
    private static final String FILE_PATH = COS_PATH + "/bucket/document.pdf";

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-base-url",
                    "${quarkus.langchain4j.mock-api.url}/projects")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-id", PROJECT_ID)
            .overrideRuntimeConfigKey("quarkus.proxy.host", "localhost")
            .overrideRuntimeConfigKey("quarkus.proxy.port", "8091")
            .overrideRuntimeConfigKey("quarkus.proxy.non-proxy-hosts", "bypass.invalid")
            .setArchiveProducer(new Supplier<JavaArchive>() {
                @Override
                public JavaArchive get() {
                    return ShrinkWrap.create(JavaArchive.class).addClass(WatsonxTestUtil.class);
                }
            });

    @Inject
    ChatModel chatModel;

    @Inject
    TextExtractionService extraction;

    @Inject
    Vertx vertx;

    @Inject
    @ConfigProperty(name = "quarkus.langchain4j.mock-api.url")
    String mockUrl;

    private final List<String> forwardedUris = new CopyOnWriteArrayList<>();
    private HttpClient proxyClient;
    private HttpServer proxyServer;

    @BeforeEach
    void startProxy() {
        forwardedUris.clear();
        proxyClient = vertx.createHttpClient();
        proxyServer = await(vertx.createHttpServer().requestHandler(new Handler<HttpServerRequest>() {
            @Override
            public void handle(HttpServerRequest request) {
                forward(request);
            }
        }).listen(8091, "localhost"));
    }

    @AfterEach
    void stopProxy() {
        try {
            if (proxyServer != null) {
                await(proxyServer.close());
            }
        } finally {
            if (proxyClient != null) {
                await(proxyClient.close());
            }
        }
    }

    @Test
    void routesModelProjectsAndStorageRequestsThroughTheProxy() {
        mock.chat().reply(ChatReply.text("AI Response"));
        mock.raw().on("GET", PROJECT_PATH).reply(200, """
                {"entity":{"storage":{"properties":{
                  "bucket_name":"bucket","endpoint_url":"%s"}}}}
                """.formatted(mockUrl + COS_PATH));
        mock.raw().on("PUT", FILE_PATH).reply(200, "");

        assertThat(chatModel.chat("Hello")).isEqualTo("AI Response");
        assertThat(extraction.uploadFile(new ByteArrayInputStream(new byte[] { 1 }), "document.pdf")).isTrue();

        assertThat(requests("POST", URL_WATSONX_CHAT_API)).hasSize(1);
        assertWatsonxRequest(requests("POST", URL_WATSONX_CHAT_API).get(0), URL_WATSONX_CHAT_API);
        assertThat(requests("GET", PROJECT_PATH)).hasSize(1);
        assertThat(requests("GET", PROJECT_PATH).get(0).header("Authorization")).isEqualTo("Bearer " + MOCK_TOKEN);
        assertThat(requests("PUT", FILE_PATH)).hasSize(1);
        assertThat(requests("PUT", FILE_PATH).get(0).header("Authorization")).isEqualTo("Bearer " + MOCK_TOKEN);
        assertThat(requests("PUT", FILE_PATH).get(0).body().getBytes(StandardCharsets.ISO_8859_1)).containsExactly((byte) 1);

        List<String> forwardedPaths = new ArrayList<>();
        for (String forwardedUri : forwardedUris) {
            URI uri = URI.create(forwardedUri);
            assertThat(uri.isAbsolute()).isTrue();
            forwardedPaths.add(uri.getPath());
        }
        assertThat(forwardedPaths).contains(URL_WATSONX_CHAT_API, PROJECT_PATH, FILE_PATH);
    }

    private void forward(HttpServerRequest request) {
        forwardedUris.add(request.uri());
        request.body().onComplete(new Handler<AsyncResult<Buffer>>() {
            @Override
            public void handle(AsyncResult<Buffer> body) {
                if (body.failed()) {
                    fail(request, body.cause());
                    return;
                }
                forwardBody(request, body.result());
            }
        });
    }

    private void forwardBody(HttpServerRequest request, Buffer body) {
        proxyClient.request(new RequestOptions().setMethod(request.method())
                .setAbsoluteURI(request.uri()).setHeaders(withoutFraming(request.headers())))
                .onComplete(new Handler<AsyncResult<HttpClientRequest>>() {
                    @Override
                    public void handle(AsyncResult<HttpClientRequest> outgoing) {
                        if (outgoing.failed()) {
                            fail(request, outgoing.cause());
                            return;
                        }
                        outgoing.result().send(body).onComplete(new Handler<AsyncResult<HttpClientResponse>>() {
                            @Override
                            public void handle(AsyncResult<HttpClientResponse> response) {
                                if (response.failed()) {
                                    fail(request, response.cause());
                                    return;
                                }
                                relay(request, response.result());
                            }
                        });
                    }
                });
    }

    private static void relay(HttpServerRequest request, HttpClientResponse response) {
        // Read the body as soon as the response head arrives so the event loop cannot discard it between callbacks.
        response.body().onComplete(new Handler<AsyncResult<Buffer>>() {
            @Override
            public void handle(AsyncResult<Buffer> body) {
                if (body.failed()) {
                    fail(request, body.cause());
                    return;
                }
                request.response().setStatusCode(response.statusCode());
                request.response().headers().addAll(withoutFraming(response.headers()));
                request.response().end(body.result());
            }
        });
    }

    private static MultiMap withoutFraming(MultiMap headers) {
        return MultiMap.caseInsensitiveMultiMap().addAll(headers)
                .remove(HttpHeaders.CONTENT_LENGTH)
                .remove(HttpHeaders.TRANSFER_ENCODING);
    }

    private static void fail(HttpServerRequest request, Throwable failure) {
        request.response().setStatusCode(502).end(String.valueOf(failure));
    }

    private static <T> T await(Future<T> future) {
        return future.toCompletionStage().toCompletableFuture().orTimeout(10, TimeUnit.SECONDS).join();
    }
}
