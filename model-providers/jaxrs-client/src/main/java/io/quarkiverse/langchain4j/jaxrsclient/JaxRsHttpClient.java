package io.quarkiverse.langchain4j.jaxrsclient;

import java.security.KeyStore;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;

import org.jboss.resteasy.reactive.client.TlsConfig;
import org.jboss.resteasy.reactive.client.impl.ClientBuilderImpl;

import dev.langchain4j.exception.HttpException;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import io.quarkus.tls.TlsConfiguration;
import io.vertx.core.net.KeyCertOptions;
import io.vertx.core.net.SSLOptions;
import io.vertx.core.net.TrustOptions;

public class JaxRsHttpClient implements HttpClient {

    private final Client delegate;

    public JaxRsHttpClient(JaxRsHttpClientBuilder builder) {
        ClientBuilderImpl clientBuilder = (ClientBuilderImpl) new ClientBuilderImpl()
                .connectTimeout(builder.connectTimeout().getSeconds(), TimeUnit.SECONDS)
                .readTimeout(builder.readTimeout().getSeconds(), TimeUnit.SECONDS);
        for (Object provider : builder.clientProviders()) {
            clientBuilder.register(provider);
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

                // TODO: when we bump to the next LTS, this needs to be implemented properly
                //for the time being it exists only to make the module compile against the SNAPSHOT version of Quarkus
                public String getName() {
                    throw new IllegalStateException("this should not be called");
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
                invocationBuilder.header(headers.getKey(), values);
            }
        }

        Response response = switch (request.method()) {
            case GET -> invocationBuilder.get();
            case POST -> invocationBuilder.post(Entity.json(request.body()));
            case DELETE -> invocationBuilder.delete();
        };

        if (response.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
            throw new HttpException(response.getStatus(), response.readEntity(String.class));
        }

        return SuccessfulHttpResponse.builder()
                .statusCode(response.getStatus())
                .headers(response.getStringHeaders())
                .body(response.readEntity(String.class))
                .build();
    }

    @Override
    public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {

    }
}
