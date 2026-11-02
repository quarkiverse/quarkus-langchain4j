package io.quarkiverse.langchain4j.tavily;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpMethod;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;

public class TavilyAsyncCancellationTest {

    @Test
    public void cancellingTheOutermostFutureCancelsTheHttpCall() {
        PendingHttpClient transport = new PendingHttpClient();
        HttpClient client = new TavilyResultUrlSanitizer(new TavilyLoggingHttpClient(transport, true, true));

        CompletableFuture<SuccessfulHttpResponse> future = client.executeAsync(request());
        future.cancel(true);

        assertThat(transport.pending.isCancelled()).isTrue();
    }

    private static HttpRequest request() {
        return HttpRequest.builder()
                .method(HttpMethod.POST)
                .url("http://localhost/search")
                .build();
    }

    private static class PendingHttpClient implements HttpClient {

        final CompletableFuture<SuccessfulHttpResponse> pending = new CompletableFuture<>();

        @Override
        public SuccessfulHttpResponse execute(HttpRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<SuccessfulHttpResponse> executeAsync(HttpRequest request) {
            return pending;
        }

        @Override
        public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
            throw new UnsupportedOperationException();
        }
    }
}
