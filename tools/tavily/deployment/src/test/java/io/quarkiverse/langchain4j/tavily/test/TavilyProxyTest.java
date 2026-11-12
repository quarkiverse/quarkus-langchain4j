package io.quarkiverse.langchain4j.tavily.test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Supplier;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusExtensionTest;

public class TavilyProxyTest extends WiremockAware {

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(new Supplier<>() {
                @Override
                public JavaArchive get() {
                    return ShrinkWrap.create(JavaArchive.class);
                }
            })
            .overrideConfigKey("quarkus.langchain4j.tavily.api-key", "tvly-key")
            .overrideConfigKey("quarkus.langchain4j.tavily.base-url", "http://tavily.invalid")
            .overrideConfigKey("quarkus.langchain4j.tavily.proxy-configuration-name", "tavily")
            .overrideConfigKey("quarkus.proxy.\"tavily\".host", "localhost")
            .overrideConfigKey("quarkus.proxy.\"tavily\".port", "${quarkus.wiremock.devservices.port}");

    @Inject
    WebSearchEngine webSearchEngine;

    @BeforeEach
    public void setup() {
        resetRequests();
        wiremock().register(
                post(urlEqualTo("/search"))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody("""
                                        {
                                            "query": "proxy",
                                            "answer": null,
                                            "results": [
                                                {
                                                    "title": "Proxied",
                                                    "url": "https://example.com/proxied",
                                                    "content": "proxied",
                                                    "score": 0.9
                                                }
                                            ],
                                            "response_time": 0.1
                                        }
                                        """)));
    }

    @Test
    public void testSearchGoesThroughConfiguredProxy() {
        WebSearchResults result = webSearchEngine.search(WebSearchRequest.from("proxy"));

        assertThat(result.results()).hasSize(1);
        assertThat(result.results().get(0).title()).isEqualTo("Proxied");
    }
}
