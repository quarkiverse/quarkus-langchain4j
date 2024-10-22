package io.quarkiverse.langchain4j.openai.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.model.chat.ChatLanguageModel;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.test.QuarkusUnitTest;
import io.quarkus.tls.TlsConfiguration;
import io.quarkus.tls.TlsConfigurationRegistry;
import io.smallrye.certs.Format;
import io.smallrye.certs.junit5.Certificate;
import io.smallrye.certs.junit5.Certificates;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.KeyCertOptions;
import io.vertx.ext.web.Router;

@Certificates(baseDir = "target/certs", certificates = {
        @Certificate(name = "lc4j", password = "secret", formats = Format.PKCS12),
        @Certificate(name = "lc4jA", password = "secretA", formats = Format.PKCS12)
})
public class OpenAiChatLanguageModelTlsConfigurationTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .withApplicationRoot((jar) -> jar
                    .addClasses(Setup.class)
                    .addAsResource(new File("target/certs/lc4j-keystore.p12"), "keystore.p12")
                    .addAsResource(new File("target/certs/lc4j-truststore.p12"), "truststore.p12")
                    .addAsResource(new File("target/certs/lc4jA-keystore.p12"), "keystoreA.p12")
                    .addAsResource(new File("target/certs/lc4jA-truststore.p12"), "truststoreA.p12"))

            .overrideConfigKey("quarkus.tls.server.key-store.jks.path", "keystore.p12")
            .overrideConfigKey("quarkus.tls.server.key-store.jks.password", "secret")

            .overrideConfigKey("quarkus.tls.serverA.key-store.jks.path", "keystoreA.p12")
            .overrideConfigKey("quarkus.tls.serverA.key-store.jks.password", "secretA")

            .overrideConfigKey("quarkus.tls.client.trust-store.jks.path", "truststore.p12")
            .overrideConfigKey("quarkus.tls.client.trust-store.jks.password", "secret")

            .overrideConfigKey("quarkus.tls.clientA.trust-store.jks.path", "truststoreA.p12")
            .overrideConfigKey("quarkus.tls.clientA.trust-store.jks.password", "secretA")

            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.tls-configuration-name", "client")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.base-url", "https://localhost:8444/s")

            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.clientA.tls-configuration-name", "clientA")
            .overrideRuntimeConfigKey("quarkus.langchain4j.openai.clientA.base-url", "https://localhost:8445/sA");

    @Inject
    ChatLanguageModel defaultChatLanguageModel;

    @ModelName("clientA")
    ChatLanguageModel clientAChatLanguageModel;

    @Test
    void test() {
        assertThat(defaultChatLanguageModel.generate("hello")).isEqualTo("Hello");

        assertThat(clientAChatLanguageModel.generate("hello")).isEqualTo("HelloA");
    }

    @ApplicationScoped
    public static class Setup {

        private final List<HttpServer> servers = Collections.synchronizedList(new ArrayList<>());

        public void start(@Observes StartupEvent ev, Vertx vertx, TlsConfigurationRegistry tlsConfigurationRegistry) {
            // server for default client
            servers.add(createServerAndListen(vertx, prepareServer(vertx, tlsConfigurationRegistry, "server", 8444,
                    "/s", "Hello")));

            // server for clientA
            servers.add(createServerAndListen(vertx, prepareServer(vertx, tlsConfigurationRegistry, "serverA", 8445,
                    "/sA", "HelloA")));
        }

        private HttpServer createServerAndListen(Vertx vertx, ServerPrepareResult serverPrepareResult) {
            return vertx.createHttpServer(serverPrepareResult.serverOptions())
                    .requestHandler(serverPrepareResult.router())
                    .listen().result();
        }

        private ServerPrepareResult prepareServer(Vertx vertx, TlsConfigurationRegistry tlsConfigurationRegistry,
                String tlsConfigurationName, int port, String basePath,
                String chatResponse) {
            Router router = Router.router(vertx);
            router.post(basePath + "/chat/completions").handler(rc -> {
                rc.response().end("""
                        {
                          "id": "chatcmpl-123",
                          "object": "chat.completion",
                          "created": 1677652288,
                          "model": "gpt-4o-mini",
                          "system_fingerprint": "fp_44709d6fcb",
                          "choices": [
                            {
                              "index": 0,
                              "message": {
                                "role": "assistant",
                                "content": "REPLACE_ME"
                              },
                              "finish_reason": "stop"
                            }
                          ],
                          "usage": {
                            "prompt_tokens": 9,
                            "completion_tokens": 12,
                            "total_tokens": 21,
                            "completion_tokens_details": {
                              "reasoning_tokens": 0
                            }
                          }
                        }
                        """.replace("REPLACE_ME", chatResponse));
            });

            TlsConfiguration bucket = tlsConfigurationRegistry.get(tlsConfigurationName).orElseThrow();
            KeyCertOptions keyStoreOptions = bucket.getKeyStoreOptions();
            HttpServerOptions serverOptions = new HttpServerOptions();

            serverOptions.setSsl(true);
            serverOptions.setPort(port);
            serverOptions.setKeyCertOptions(keyStoreOptions);
            var other = bucket.getSSLOptions();
            serverOptions.setSslHandshakeTimeout(other.getSslHandshakeTimeout());
            serverOptions.setSslHandshakeTimeoutUnit(other.getSslHandshakeTimeoutUnit());
            for (String suite : other.getEnabledCipherSuites()) {
                serverOptions.addEnabledCipherSuite(suite);
            }
            for (Buffer buffer : other.getCrlValues()) {
                serverOptions.addCrlValue(buffer);
            }
            if (!other.isUseAlpn()) {
                serverOptions.setUseAlpn(false);
            }
            serverOptions.setEnabledSecureTransportProtocols(other.getEnabledSecureTransportProtocols());
            return new ServerPrepareResult(router, serverOptions);
        }

        public void shutdown(@Observes ShutdownEvent ev) {
            for (HttpServer server : servers) {
                if (server != null) {
                    server.close();
                }
            }
        }

        private record ServerPrepareResult(Router router, HttpServerOptions serverOptions) {
        }
    }
}
