package io.quarkiverse.langchain4j.watsonx.deployment;

import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.API_KEY;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.PROJECT_ID;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_IAM_SERVER;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_SERVER;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WX_AGENT_TOOL_RUN;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WX_SERVER;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.time.Instant;
import java.util.Date;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.tomakehurst.wiremock.stubbing.Scenario;

import io.quarkiverse.langchain4j.watsonx.services.GoogleSearchService;
import io.quarkiverse.langchain4j.watsonx.services.WeatherService;
import io.quarkiverse.langchain4j.watsonx.services.WebCrawlerService;
import io.quarkus.test.QuarkusUnitTest;

public class BuiltinServiceCacheTokenTest extends WireMockAbstract {

    static int cacheTimeout = 2000;
    static String RESPONSE_401 = """
            {
                "code": 401,
                "error": "Unauthorized",
                "reason": "Unauthorized",
                "message": "Access denied",
                "description": "jwt expired"
            }
            """;

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.base-url", URL_WATSONX_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.built-in-service.base-url", URL_WX_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.iam.base-url", URL_IAM_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-id", PROJECT_ID)
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(WireMockUtil.class));

    @Override
    void handlerBeforeEach() throws Exception {
        // Return an expired token.
        mockIAMBuilder(200)
                .scenario(Scenario.STARTED, "retry")
                .response("expired_token", Date.from(Instant.now().minusSeconds(3)))
                .build();

        // Second call (retryOn) returns 200
        mockIAMBuilder(200)
                .scenario("retry", Scenario.STARTED)
                .response("my_super_token", Date.from(Instant.now().plusMillis(cacheTimeout)))
                .build();

        Thread.sleep(cacheTimeout);
    }

    @Inject
    WeatherService weatherTool;

    @Test
    void try_weather_tool_retry() throws InterruptedException {

        var response = """
                {
                    "output": "Current weather in Naples:\\nTemperature: 12.1°C\\nRain: 0mm\\nRelative humidity: 94%\\nWind: 5km/h\\n"
                }""";

        mockWXServer(response);
        assertDoesNotThrow(() -> weatherTool.find("naples", null));
    }

    @Inject
    WebCrawlerService webCrawlerTool;

    @Test
    void try_webcrawler_tool_retry() throws InterruptedException {

        String response = "{" +
                "\"output\": \"\\\"{\\\\\\\"url\\\\\\\":\\\\\\\"https://www.ibm.com/us-en\\\\\\\"," +
                "\\\\\\\"contentType\\\\\\\":\\\\\\\"text/html;charset=utf-8\\\\\\\"," +
                "\\\\\\\"content\\\\\\\":\\\\\\\"IBM - United States\\\\n\\\\nBoost developer productivity with AI..." +
                "\\\\n\\\\nExplore jobs\\\\n\\\\nStart learning\\\\\\\"}\\\"\"\n" +
                "}";

        mockWXServer(response);
        assertDoesNotThrow(() -> webCrawlerTool.process("http://supersite.com"));
    }

    @Inject
    GoogleSearchService googleSearchTool;

    @Test
    void try_googlesearh_tool_retry() throws InterruptedException {

        String response = """
                {
                  "output": "[{\\"title\\":\\"Quarkus - Supersonic Subatomic Java\\",\\"description\\":\\"Quarkus streamlines framework optimizations in the build phase to reduce runtime dependencies and improve efficiency. By precomputing metadata and optimizing ...\\",\\"url\\":\\"https://quarkus.io/\\"}]"
                }
                """;

        mockWXServer(response);
        assertDoesNotThrow(() -> googleSearchTool.search("quarkus", 1));
    }

    private void mockWXServer(String response) {
        mockWxBuilder(URL_WX_AGENT_TOOL_RUN, 401)
                .token("expired_token")
                .scenario(Scenario.STARTED, "retry")
                .response(RESPONSE_401)
                .build();

        mockWxBuilder(URL_WX_AGENT_TOOL_RUN, 200)
                .token("my_super_token")
                .scenario("retry", Scenario.STARTED)
                .response(response)
                .build();
    }
}
