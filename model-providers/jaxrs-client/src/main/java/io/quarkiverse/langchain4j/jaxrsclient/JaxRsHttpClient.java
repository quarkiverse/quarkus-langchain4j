package io.quarkiverse.langchain4j.jaxrsclient;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.security.KeyStore;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.GenericType;
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
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventContext;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import dev.langchain4j.http.client.sse.ServerSentEventParsingHandle;
import io.quarkiverse.langchain4j.QuarkusHttpException;
import io.quarkus.tls.TlsConfiguration;
import io.smallrye.mutiny.subscription.MultiSubscriber;
import io.smallrye.mutiny.subscription.UniSubscriber;
import io.smallrye.mutiny.subscription.UniSubscription;
import io.vertx.core.net.KeyCertOptions;
import io.vertx.core.net.ProxyType;
import io.vertx.core.net.SSLOptions;
import io.vertx.core.net.TrustOptions;

public class JaxRsHttpClient implements HttpClient {

    private static final GenericType<SseEvent<String>> SSE_EVENT_TYPE = new GenericType<>() {
    };

    private final Client delegate;

    public JaxRsHttpClient(JaxRsHttpClientBuilder builder) {
        ClientBuilderImpl clientBuilder = (ClientBuilderImpl) new ClientBuilderImpl()
                .connectTimeout(builder.connectTimeout().getSeconds(), TimeUnit.SECONDS)
                .readTimeout(builder.readTimeout().getSeconds(), TimeUnit.SECONDS);
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

    @Override
    public SuccessfulHttpResponse execute(HttpRequest request) {
        WebTarget target = delegate.target(request.url());
        Invocation.Builder invocationBuilder = target.request();

        for (var headers : request.headers().entrySet()) {
            List<String> values = headers.getValue();
            if ((values != null) && (!values.isEmpty())) {
                for (String value : values) {
                    invocationBuilder.header(headers.getKey(), value);
                }
            }
        }

        Response response = switch (request.method()) {
            case GET -> invocationBuilder.get();
            case POST -> invocationBuilder.post(Entity.json(request.body()));
            case DELETE -> invocationBuilder.delete();
        };

        return toSuccessfulHttpResponse(response);
    }

    @Override
    public CompletableFuture<SuccessfulHttpResponse> executeAsync(HttpRequest request) {
        WebTarget target = delegate.target(request.url());
        Invocation.Builder invocationBuilder = target.request();

        for (var headers : request.headers().entrySet()) {
            List<String> values = headers.getValue();
            if ((values != null) && (!values.isEmpty())) {
                for (String value : values) {
                    invocationBuilder.header(headers.getKey(), value);
                }
            }
        }

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
        WebTarget target = delegate.target(request.url());
        Invocation.Builder invocationBuilder = target.request();

        for (var headers : request.headers().entrySet()) {
            List<String> values = headers.getValue();
            if ((values != null) && (!values.isEmpty())) {
                for (String value : values) {
                    invocationBuilder.header(headers.getKey(), value);
                }
            }
        }

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
}
