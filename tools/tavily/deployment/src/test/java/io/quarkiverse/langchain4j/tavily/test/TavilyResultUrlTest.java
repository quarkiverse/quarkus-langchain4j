package io.quarkiverse.langchain4j.tavily.test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;
import io.quarkiverse.langchain4j.testing.internal.WiremockAware;
import io.quarkus.test.QuarkusUnitTest;

public class TavilyResultUrlTest extends WiremockAware {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(
                    () -> ShrinkWrap.create(JavaArchive.class)
                            .addAsResource(new StringAsset(
                                    """
                                            quarkus.langchain4j.tavily.api-key=test
                                            quarkus.langchain4j.tavily.base-url=%s
                                            """.formatted(WiremockAware.wiremockUrlForConfig())),
                                    "application.properties"));

    @Inject
    WebSearchEngine webSearchEngine;

    @BeforeEach
    public void setup() {
        wiremock().register(
                post(urlEqualTo("/search"))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody(
                                        """
                                                {
                                                    "query": "manual",
                                                    "answer": null,
                                                    "results": [
                                                        {
                                                            "title": "Brackets",
                                                            "url": "https://example.com/files/manual[1].pdf",
                                                            "content": "brackets",
                                                            "score": 0.9
                                                        },
                                                        {
                                                            "title": "Space",
                                                            "url": "https://example.com/my file.pdf",
                                                            "content": "space",
                                                            "score": 0.8
                                                        },
                                                        {
                                                            "title": "Unparseable",
                                                            "url": "https://",
                                                            "content": "unparseable",
                                                            "score": 0.7
                                                        },
                                                        {
                                                            "title": "Valid",
                                                            "url": "https://example.com/a%20b/c?q=x%2By&lang=en#top",
                                                            "content": "valid",
                                                            "score": 0.6
                                                        },
                                                        {
                                                            "title": "Unicode",
                                                            "url": "https://example.com/café",
                                                            "content": "unicode",
                                                            "score": 0.5
                                                        },
                                                        {
                                                            "title": "Unicode with space",
                                                            "url": "https://example.com/café menu",
                                                            "content": "unicode with space",
                                                            "score": 0.4
                                                        }
                                                    ],
                                                    "response_time": 0.5
                                                }
                                                """)));
    }

    @Test
    public void testResultUrls() {
        WebSearchResults result = webSearchEngine.search(WebSearchRequest.from("manual"));

        assertThat(result.results())
                .extracting(WebSearchOrganicResult::title)
                .containsExactly("Brackets", "Space", "Valid", "Unicode", "Unicode with space");
        assertThat(result.results())
                .extracting(WebSearchOrganicResult::url)
                .containsExactly(
                        URI.create("https://example.com/files/manual%5B1%5D.pdf"),
                        URI.create("https://example.com/my%20file.pdf"),
                        URI.create("https://example.com/a%20b/c?q=x%2By&lang=en#top"),
                        URI.create("https://example.com/caf%C3%A9"),
                        URI.create("https://example.com/caf%C3%A9%20menu"));
        assertThat(result.results().get(2).url().toString())
                .isEqualTo("https://example.com/a%20b/c?q=x%2By&lang=en#top");
        assertThat(result.searchInformation().totalResults()).isEqualTo(5);
    }
}
