package io.quarkiverse.langchain4j.tavily.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.test.QuarkusExtensionTest;
import io.smallrye.certs.Format;
import io.smallrye.certs.junit5.Certificate;
import io.smallrye.certs.junit5.Certificates;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.net.PfxOptions;

@Certificates(baseDir = "target/certs", certificates = {
        @Certificate(name = "tavily", password = "secret", formats = Format.PKCS12)
})
public class TavilyTlsConfigurationTest {

    private static final int TLS_PORT = 8446;

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(new Supplier<>() {
                @Override
                public JavaArchive get() {
                    return ShrinkWrap.create(JavaArchive.class)
                            .addClass(TlsServer.class)
                            .addAsResource(new File("target/certs/tavily-truststore.p12"), "truststore.p12");
                }
            })
            .overrideConfigKey("quarkus.tls.tavily.trust-store.p12.path", "truststore.p12")
            .overrideConfigKey("quarkus.tls.tavily.trust-store.p12.password", "secret")
            .overrideConfigKey("quarkus.langchain4j.tavily.api-key", "tvly-key")
            .overrideConfigKey("quarkus.langchain4j.tavily.base-url", "https://localhost:" + TLS_PORT)
            .overrideConfigKey("quarkus.langchain4j.tavily.tls-configuration-name", "tavily");

    @Inject
    WebSearchEngine webSearchEngine;

    @Test
    public void testSearchTrustsServerCertificateOfNamedTlsConfiguration() {
        WebSearchResults result = webSearchEngine.search(WebSearchRequest.from("tls"));

        assertThat(result.results()).hasSize(1);
        assertThat(result.results().get(0).title()).isEqualTo("Secure");
    }

    @ApplicationScoped
    public static class TlsServer {

        private HttpServer server;

        void start(@Observes StartupEvent event, Vertx vertx) {
            HttpServerOptions options = new HttpServerOptions()
                    .setSsl(true)
                    .setPort(TLS_PORT)
                    .setKeyCertOptions(new PfxOptions().setPath("target/certs/tavily-keystore.p12").setPassword("secret"));
            server = vertx.createHttpServer(options)
                    .requestHandler(new Handler<HttpServerRequest>() {
                        @Override
                        public void handle(HttpServerRequest request) {
                            request.response()
                                    .putHeader("Content-Type", "application/json")
                                    .end("""
                                            {
                                                "query": "tls",
                                                "answer": null,
                                                "results": [
                                                    {
                                                        "title": "Secure",
                                                        "url": "https://example.com/secure",
                                                        "content": "secure",
                                                        "score": 0.9
                                                    }
                                                ],
                                                "response_time": 0.1
                                            }
                                            """);
                        }
                    })
                    .listen()
                    .result();
        }

        void stop(@Observes ShutdownEvent event) {
            if (server != null) {
                server.close();
            }
        }
    }
}
