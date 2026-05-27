package io.quarkiverse.langchain4j.bedrock.runtime.client;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.RequestOptions;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.http.ContentStreamProvider;
import software.amazon.awssdk.http.ExecutableHttpRequest;
import software.amazon.awssdk.http.HttpExecuteRequest;
import software.amazon.awssdk.http.HttpExecuteResponse;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.SdkHttpResponse;

public class VertxSdkHttpClient implements SdkHttpClient {

    private static final String CLIENT_NAME = "Quarkus LangChain4j";

    private final HttpClient vertxHttpClient;
    private final Duration readTimeout;

    public VertxSdkHttpClient(HttpClient client, Duration readTimeout) {
        this.vertxHttpClient = client;
        this.readTimeout = readTimeout;
    }

    @Override
    public ExecutableHttpRequest prepareRequest(HttpExecuteRequest httpExecuteRequest) {
        RequestOptions requestOptions = VertxSdkHttpClientHelper.buildRequestOptions(httpExecuteRequest.httpRequest());
        return new VertxExecutableHttpRequest(requestOptions, httpExecuteRequest);
    }

    @Override
    public void close() {
        vertxHttpClient.close();
    }

    @Override
    public String clientName() {
        return CLIENT_NAME;
    }

    private class VertxExecutableHttpRequest implements ExecutableHttpRequest {
        private final RequestOptions requestOptions;
        private final HttpExecuteRequest executeRequest;
        private final CompletableFuture<HttpExecuteResponse> responseFuture = new CompletableFuture<>();
        private final AtomicBoolean aborted = new AtomicBoolean(false);

        VertxExecutableHttpRequest(RequestOptions requestOptions, HttpExecuteRequest executeRequest) {
            this.requestOptions = requestOptions;
            this.executeRequest = executeRequest;
        }

        @Override
        public HttpExecuteResponse call() throws IOException {
            if (aborted.get()) {
                throw new IOException("Request has been aborted");
            }

            Buffer body = readRequestBody();
            vertxHttpClient.request(requestOptions)
                    .onComplete(new SyncRequestHandler(body, responseFuture, aborted));

            try {
                return responseFuture.get(readTimeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Request interrupted", e);
            } catch (ExecutionException e) {
                throw new IOException("Request failed", e.getCause());
            } catch (TimeoutException e) {
                throw new IOException("Request timed out after " + readTimeout, e);
            }
        }

        @Override
        public void abort() {
            aborted.set(true);
            responseFuture.completeExceptionally(new IOException("Request aborted"));
        }

        private Buffer readRequestBody() throws IOException {
            Buffer body = Buffer.buffer();
            if (executeRequest.contentStreamProvider().isPresent()) {
                ContentStreamProvider provider = executeRequest.contentStreamProvider().get();
                try (InputStream inputStream = provider.newStream()) {
                    body = Buffer.buffer(inputStream.readAllBytes());
                }
            }
            return body;
        }
    }

    private static class SyncRequestHandler implements Handler<AsyncResult<HttpClientRequest>> {
        private final Buffer body;
        private final CompletableFuture<HttpExecuteResponse> responseFuture;
        private final AtomicBoolean aborted;

        SyncRequestHandler(Buffer body, CompletableFuture<HttpExecuteResponse> responseFuture,
                AtomicBoolean aborted) {
            this.body = body;
            this.responseFuture = responseFuture;
            this.aborted = aborted;
        }

        @Override
        public void handle(AsyncResult<HttpClientRequest> reqResult) {
            if (aborted.get()) {
                return;
            }
            if (reqResult.failed()) {
                responseFuture.completeExceptionally(reqResult.cause());
                return;
            }
            reqResult.result().send(body)
                    .onComplete(new SyncResponseHandler(responseFuture, aborted));
        }
    }

    private static class SyncResponseHandler implements Handler<AsyncResult<HttpClientResponse>> {
        private final CompletableFuture<HttpExecuteResponse> responseFuture;
        private final AtomicBoolean aborted;

        SyncResponseHandler(CompletableFuture<HttpExecuteResponse> responseFuture, AtomicBoolean aborted) {
            this.responseFuture = responseFuture;
            this.aborted = aborted;
        }

        @Override
        public void handle(AsyncResult<HttpClientResponse> respResult) {
            if (aborted.get()) {
                return;
            }
            if (respResult.failed()) {
                responseFuture.completeExceptionally(respResult.cause());
                return;
            }
            respResult.result().body()
                    .onComplete(new SyncBodyHandler(respResult.result(), responseFuture));
        }
    }

    private static class SyncBodyHandler implements Handler<AsyncResult<Buffer>> {
        private final HttpClientResponse response;
        private final CompletableFuture<HttpExecuteResponse> responseFuture;

        SyncBodyHandler(HttpClientResponse response, CompletableFuture<HttpExecuteResponse> responseFuture) {
            this.response = response;
            this.responseFuture = responseFuture;
        }

        @Override
        public void handle(AsyncResult<Buffer> bodyResult) {
            if (bodyResult.failed()) {
                responseFuture.completeExceptionally(bodyResult.cause());
                return;
            }

            SdkHttpResponse sdkResponse = SdkHttpResponse.builder()
                    .statusCode(response.statusCode())
                    .statusText(response.statusMessage())
                    .headers(VertxSdkHttpClientHelper.convertHeaders(response))
                    .build();

            byte[] bytes = bodyResult.result().getBytes();
            AbortableInputStream responseBody = AbortableInputStream.create(new ByteArrayInputStream(bytes));

            responseFuture.complete(HttpExecuteResponse.builder()
                    .response(sdkResponse)
                    .responseBody(responseBody)
                    .build());
        }
    }
}
