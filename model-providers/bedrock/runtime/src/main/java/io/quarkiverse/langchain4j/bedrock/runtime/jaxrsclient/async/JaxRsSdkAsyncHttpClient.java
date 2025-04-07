package io.quarkiverse.langchain4j.bedrock.runtime.jaxrsclient.async;

import static io.quarkiverse.langchain4j.bedrock.runtime.jaxrsclient.JaxRsSdkHttpClientHelper.createAndPrepareInvocationBuilder;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Invocation;

import io.quarkiverse.langchain4j.bedrock.runtime.jaxrsclient.DefaultJaxRsSdkClientBuilder;
import io.quarkiverse.langchain4j.bedrock.runtime.jaxrsclient.JaxRsSdkClientBuilder;
import io.quarkus.tls.TlsConfiguration;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.async.AsyncExecuteRequest;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.utils.AttributeMap;

public class JaxRsSdkAsyncHttpClient implements SdkAsyncHttpClient {

    private static final String CLIENT_NAME = "RestEasyReactive";

    private final Client delegate;

    public JaxRsSdkAsyncHttpClient(final Client client) {
        delegate = client;
    }

    @Override
    public CompletableFuture<Void> execute(final AsyncExecuteRequest executeRequest) {
        final SdkHttpRequest request = executeRequest.request();

        Invocation.Builder invocationBuilder = createAndPrepareInvocationBuilder(delegate, request);

        final CompletableFuture<Void> executeFuture = new CompletableFuture<>();

        final JaxRsSdkAsyncHttpClientSubscriber subscriber = new JaxRsSdkAsyncHttpClientSubscriber(executeRequest,
                invocationBuilder, executeFuture);

        executeRequest.requestContentPublisher().subscribe(subscriber);

        return executeFuture;
    }

    @Override
    public void close() {
        delegate.close();
    }

    @Override
    public String clientName() {
        return CLIENT_NAME;
    }

    public static Builder builder() {
        return new DefaultBuilder();
    }

    public interface Builder extends SdkAsyncHttpClient.Builder<JaxRsSdkAsyncHttpClient.Builder>, JaxRsSdkClientBuilder {

        Builder connectionTimeout(Duration timeout);

        Builder readTimeout(Duration readTimeout);

        Builder proxyAddress(String proxyAddress);

        Builder proxyUser(String proxyUser);

        Builder proxyPassword(String proxyPassword);

        Builder nonProxyHosts(String nonProxyHosts);

        Builder disableContextualErrorMessages(Boolean disableContextualErrorMessages);

        Builder connectionTTL(Integer connectionTTL);

        Builder connectionPoolSize(Integer connectionPoolSize);

        Builder keepAliveEnabled(Boolean keepAliveEnabled);

        Builder hostnameVerifier(String hostnameVerifier);

        Builder verifyHost(Boolean verifyHost);

        Builder trustStore(String trustStore);

        Builder trustStorePassword(String trustStorePassword);

        Builder trustStoreType(String trustStoreType);

        Builder keyStore(String keyStore);

        Builder keyStorePassword(String keyStorePassword);

        Builder keyStoreType(String keyStoreType);

        Builder tlsConfig(TlsConfiguration tlsConfig);
    }

    private static final class DefaultBuilder extends DefaultJaxRsSdkClientBuilder implements Builder {

        @Override
        public Builder readTimeout(final Duration readTimeout) {
            super.readTimeout(readTimeout);
            return this;
        }

        @Override
        public Builder connectionTimeout(Duration timeout) {
            super.connectionTimeout(timeout);
            return this;
        }

        @Override
        public Builder proxyAddress(final String proxyAddress) {
            super.proxyAddress(proxyAddress);
            return this;
        }

        @Override
        public Builder proxyUser(final String proxyUser) {
            super.proxyUser(proxyUser);
            return this;
        }

        @Override
        public Builder proxyPassword(final String proxyPassword) {
            super.proxyPassword(proxyPassword);
            return this;
        }

        @Override
        public Builder nonProxyHosts(final String nonProxyHosts) {
            super.nonProxyHosts(nonProxyHosts);
            return this;
        }

        @Override
        public Builder disableContextualErrorMessages(final Boolean disableContextualErrorMessages) {
            super.disableContextualErrorMessages(disableContextualErrorMessages);
            return this;
        }

        @Override
        public Builder connectionTTL(final Integer connectionTTL) {
            super.connectionTTL(connectionTTL);
            return this;
        }

        @Override
        public Builder connectionPoolSize(final Integer connectionPoolSize) {
            super.connectionPoolSize(connectionPoolSize);
            return this;
        }

        @Override
        public Builder keepAliveEnabled(final Boolean keepAliveEnabled) {
            super.keepAliveEnabled(keepAliveEnabled);
            return this;
        }

        @Override
        public Builder hostnameVerifier(final String hostnameVerifier) {
            super.hostnameVerifier(hostnameVerifier);
            return this;
        }

        @Override
        public Builder verifyHost(final Boolean verifyHost) {
            super.verifyHost(verifyHost);
            return this;
        }

        @Override
        public Builder trustStore(final String trustStore) {
            super.trustStore(trustStore);
            return this;
        }

        @Override
        public Builder trustStorePassword(final String trustStorePassword) {
            super.trustStorePassword(trustStorePassword);
            return this;
        }

        @Override
        public Builder trustStoreType(final String trustStoreType) {
            super.trustStoreType(trustStoreType);
            return this;
        }

        @Override
        public Builder keyStore(final String keyStore) {
            super.keyStore(keyStore);
            return this;
        }

        @Override
        public Builder keyStorePassword(final String keyStorePassword) {
            super.keyStorePassword(keyStorePassword);
            return this;
        }

        @Override
        public Builder keyStoreType(final String keyStoreType) {
            super.keyStoreType(keyStoreType);
            return this;
        }

        @Override
        public Builder tlsConfig(final TlsConfiguration tlsConfig) {
            super.tlsConfig(tlsConfig);
            return this;
        }

        @Override
        public SdkAsyncHttpClient buildWithDefaults(final AttributeMap serviceDefaults) {
            return new JaxRsSdkAsyncHttpClient(this.buildClientWithDefaults(serviceDefaults));
        }
    }
}
