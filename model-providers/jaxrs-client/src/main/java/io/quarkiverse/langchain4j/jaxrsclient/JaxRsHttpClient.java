package io.quarkiverse.langchain4j.jaxrsclient;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.ByteBuffer;
import java.security.KeyStore;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;

import org.jboss.resteasy.reactive.client.BasicRestResponse;
import org.jboss.resteasy.reactive.client.RestMultiResponse;
import org.jboss.resteasy.reactive.client.SseEvent;
import org.jboss.resteasy.reactive.client.TlsConfig;
import org.jboss.resteasy.reactive.client.impl.ClientBuilderImpl;
import org.jboss.resteasy.reactive.client.impl.MultiInvoker;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.HttpErrorResponseReceived;
import dev.langchain4j.http.client.sse.HttpResponseReceived;
import dev.langchain4j.http.client.sse.HttpStreamingEvent;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventContext;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import dev.langchain4j.http.client.sse.ServerSentEventParsingHandle;
import io.quarkiverse.langchain4j.QuarkusHttpException;
import io.quarkus.tls.TlsConfiguration;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.Cancellable;
import io.smallrye.mutiny.subscription.MultiSubscriber;
import io.smallrye.mutiny.subscription.UniSubscriber;
import io.smallrye.mutiny.subscription.UniSubscription;
import io.vertx.core.net.KeyCertOptions;
import io.vertx.core.net.ProxyType;
import io.vertx.core.net.SSLOptions;
import io.vertx.core.net.TrustOptions;

public class JaxRsHttpClient implements HttpClient, AutoCloseable {

    private static final GenericType<SseEvent<String>> SSE_EVENT_TYPE = new GenericType<>() {
    };

    private static final GenericType<byte[]> BYTES_TYPE = new GenericType<>() {
    };

    private final Client delegate;

    public JaxRsHttpClient(JaxRsHttpClientBuilder builder) {
        ClientBuilderImpl clientBuilder = new ClientBuilderImpl();
        if (builder.connectTimeout() != null) {
            clientBuilder.connectTimeout(builder.connectTimeout().toMillis(), TimeUnit.MILLISECONDS);
        }
        if (builder.readTimeout() != null) {
            clientBuilder.readTimeout(builder.readTimeout().toMillis(), TimeUnit.MILLISECONDS);
        }
        clientBuilder.register(new RawBodyResponseFilter());
        for (Object provider : builder.clientProviders()) {
            clientBuilder.register(provider);
        }
        Proxy proxy = builder.proxy();
        if (proxy != null && proxy.type() != Proxy.Type.DIRECT) {
            InetSocketAddress address = (InetSocketAddress) proxy.address();
            clientBuilder.proxy(address.getHostString(), address.getPort());
            clientBuilder.proxyType(proxy.type() == Proxy.Type.SOCKS ? ProxyType.SOCKS5 : ProxyType.HTTP);
        }
        if (builder.tlsConfiguration() != null) {
            TlsConfiguration tlsConfiguration = builder.tlsConfiguration();
            clientBuilder.tlsConfig(new TlsConfig() {
                @Override
                public KeyStore getKeyStore() {
                    return tlsConfiguration.getKeyStore();
                }

                @Override
                public KeyCertOptions getKeyStoreOptions() {
                    return tlsConfiguration.getKeyStoreOptions();
                }

                @Override
                public KeyStore getTrustStore() {
                    return tlsConfiguration.getTrustStore();
                }

                @Override
                public TrustOptions getTrustStoreOptions() {
                    return tlsConfiguration.getTrustStoreOptions();
                }

                @Override
                public SSLOptions getSSLOptions() {
                    return tlsConfiguration.getSSLOptions();
                }

                @Override
                public SSLContext createSSLContext() throws Exception {
                    return tlsConfiguration.createSSLContext();
                }

                @Override
                public Optional<String> getHostnameVerificationAlgorithm() {
                    return tlsConfiguration.getHostnameVerificationAlgorithm();
                }

                @Override
                public boolean usesSni() {
                    return tlsConfiguration.usesSni();
                }

                @Override
                public boolean isTrustAll() {
                    return tlsConfiguration.isTrustAll();
                }

                @Override
                public Optional<String> getName() {
                    return Optional.ofNullable(tlsConfiguration.getName());
                }
            });
        }
        delegate = clientBuilder.build();
    }

    public static JaxRsHttpClientBuilder builder() {
        return new JaxRsHttpClientBuilder();
    }

    /**
     * Closes the underlying JAX-RS client, together with the connections it holds.
     */
    @Override
    public void close() {
        delegate.close();
    }

    private Invocation.Builder invocationBuilder(HttpRequest request) {
        Invocation.Builder invocationBuilder = delegate.target(request.url()).request();
        for (var headers : request.headers().entrySet()) {
            List<String> values = headers.getValue();
            if ((values != null) && (!values.isEmpty())) {
                for (String value : values) {
                    invocationBuilder.header(headers.getKey(), value);
                }
            }
        }
        return invocationBuilder;
    }

    @Override
    public SuccessfulHttpResponse execute(HttpRequest request) {
        Invocation.Builder invocationBuilder = invocationBuilder(request);

        Response response = switch (request.method()) {
            case GET -> invocationBuilder.get();
            case POST -> invocationBuilder.post(Entity.json(request.body()));
            case DELETE -> invocationBuilder.delete();
        };

        return toSuccessfulHttpResponse(response);
    }

    @Override
    public CompletableFuture<SuccessfulHttpResponse> executeAsync(HttpRequest request) {
        Invocation.Builder invocationBuilder = invocationBuilder(request);

        String method = request.method().name();
        Entity<?> entity = (request.body() != null) ? Entity.json(request.body()) : null;

        return invocationBuilder.rx().method(method, entity)
                .thenApply(JaxRsHttpClient::toSuccessfulHttpResponse)
                .toCompletableFuture();
    }

    private static SuccessfulHttpResponse toSuccessfulHttpResponse(Response response) {
        if (response.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
            throw toHttpException(response);
        }

        return SuccessfulHttpResponse.builder()
                .statusCode(response.getStatus())
                .headers(response.getStringHeaders())
                .body(response.readEntity(String.class))
                .build();
    }

    private static SuccessfulHttpResponse toSuccessfulHttpResponse(BasicRestResponse response) {
        return SuccessfulHttpResponse.builder()
                .statusCode(response.status())
                .headers(response.headers())
                .build();
    }

    private static SuccessfulHttpResponse toSuccessfulHttpResponse(BasicRestResponse response, String contentType) {
        if (contentType == null) {
            return toSuccessfulHttpResponse(response);
        }
        Map<String, List<String>> headers = new LinkedHashMap<>(response.headers());
        headers.keySet().removeIf(HttpHeaders.CONTENT_TYPE::equalsIgnoreCase);
        headers.put(HttpHeaders.CONTENT_TYPE, List.of(contentType));
        return SuccessfulHttpResponse.builder()
                .statusCode(response.status())
                .headers(headers)
                .build();
    }

    private static QuarkusHttpException toHttpException(Response response) {
        return new QuarkusHttpException(response.getStatus(), response.readEntity(String.class),
                response.getStringHeaders());
    }

    /**
     * A non-2xx status on the streaming path surfaces as a {@link WebApplicationException} carrying the response. It is
     * converted to the same {@link QuarkusHttpException} the blocking path throws, so that LangChain4j's exception
     * mapper can turn it into the matching {@code InvalidRequestException}, {@code AuthenticationException}, ...
     */
    private static Throwable toHttpExceptionIfPossible(Throwable throwable) {
        if (throwable instanceof WebApplicationException wae && wae.getResponse() != null) {
            return toHttpException(wae.getResponse());
        }
        return throwable;
    }

    @Override
    public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
        Invocation.Builder invocationBuilder = invocationBuilder(request);

        String method = request.method().name();
        Entity<?> entity = (request.body() != null) ? Entity.json(request.body()) : null;

        MultiInvoker multiInvoker = invocationBuilder.rx(MultiInvoker.class);

        // wrapping as RestMultiResponse gives access to the status and headers as soon as they arrive,
        // which is what ServerSentEventListener.onOpen expects.
        // Subscribing to response() before the stream itself guarantees onOpen is delivered before the first event.
        // A non-2xx response fails the stream instead, which onFailure below turns into onError,
        // so a failed response() is deliberately ignored here.
        RestMultiResponse<SseEvent<String>> stream = (RestMultiResponse<SseEvent<String>>) multiInvoker.method(method,
                entity, SSE_EVENT_TYPE, true);
        stream.response().subscribe().withSubscriber(new UniSubscriber<BasicRestResponse>() {
            @Override
            public void onSubscribe(UniSubscription subscription) {
            }

            @Override
            public void onItem(BasicRestResponse response) {
                listener.onOpen(toSuccessfulHttpResponse(response));
            }

            @Override
            public void onFailure(Throwable failure) {
            }
        });

        stream
                .subscribe().withSubscriber(new MultiSubscriber<SseEvent<String>>() {

                    private volatile Flow.Subscription subscription;

                    private final ServerSentEventParsingHandle parsingHandle = new ServerSentEventParsingHandle() {
                        private volatile boolean cancelled = false;

                        @Override
                        public void cancel() {
                            cancelled = true;
                            if (subscription != null) {
                                subscription.cancel();
                            }
                        }

                        @Override
                        public boolean isCancelled() {
                            return cancelled;
                        }
                    };

                    private final ServerSentEventContext context = new ServerSentEventContext(parsingHandle);

                    @Override
                    public void onSubscribe(Flow.Subscription subscription) {
                        this.subscription = subscription;
                        subscription.request(Long.MAX_VALUE);
                    }

                    @Override
                    public void onItem(SseEvent<String> event) {
                        if (!parsingHandle.isCancelled()) {
                            listener.onEvent(new ServerSentEvent(event.name(), (String) event.data()), context);
                        }
                    }

                    @Override
                    public void onFailure(Throwable throwable) {
                        listener.onError(toHttpExceptionIfPossible(throwable));
                        listener.onClose();
                    }

                    @Override
                    public void onCompletion() {
                        listener.onClose();
                    }
                });
    }

    /**
     * Unlike {@link #execute(HttpRequest, ServerSentEventParser, ServerSentEventListener)}, which lets RESTEasy parse an
     * event stream, this passes the response body to the supplied parser as raw bytes, as they arrive and whatever the
     * content type. The caller can then decide from the {@link HttpResponseReceived} headers how to read the body, which
     * is needed when the server may answer with either a single JSON document or an event stream.
     */
    @Override
    @SuppressWarnings("unchecked")
    public Flow.Publisher<HttpStreamingEvent> stream(HttpRequest request, ServerSentEventParser parser) {
        return Multi.createFrom().emitter(emitter -> {
            RawBodyResponseFilter.OriginalContentType originalContentType = new RawBodyResponseFilter.OriginalContentType();
            Invocation.Builder invocationBuilder = invocationBuilder(request)
                    .property(RawBodyResponseFilter.PROPERTY, originalContentType);
            String method = request.method().name();
            Entity<?> entity = (request.body() != null) ? Entity.json(request.body()) : null;

            RestMultiResponse<byte[]> stream = (RestMultiResponse<byte[]>) invocationBuilder.rx(MultiInvoker.class)
                    .method(method, entity, BYTES_TYPE, true);
            // as in execute(..., listener) above, subscribing to response() first delivers the headers before the body;
            // a non-2xx response fails the body stream instead, which is handled below
            stream.response().subscribe().with(
                    response -> emitter.emit(
                            new HttpResponseReceived(toSuccessfulHttpResponse(response, originalContentType.value))),
                    ignored -> {
                    });

            ServerSentEventParser.Incremental incremental = parser.incremental();
            // Unbounded demand, as in execute(..., listener) above: the events of a chunk are buffered by the emitter.
            Cancellable body = stream.subscribe().with(
                    chunk -> incremental.feed(ByteBuffer.wrap(chunk)).forEach(emitter::emit),
                    failure -> {
                        if (failure instanceof WebApplicationException wae && wae.getResponse() != null) {
                            Response response = wae.getResponse();
                            emitter.emit(new HttpErrorResponseReceived(response.getStatus(), response.getStringHeaders()));
                        }
                        emitter.fail(toHttpExceptionIfPossible(failure));
                    },
                    () -> {
                        incremental.flush().forEach(emitter::emit);
                        emitter.complete();
                    });
            // Only on a cancellation from downstream: MultiInvoker cancels by closing the connection, which may already
            // carry the next request once the response has completed.
            emitter.onCancellation(body::cancel);
        });
    }
}
