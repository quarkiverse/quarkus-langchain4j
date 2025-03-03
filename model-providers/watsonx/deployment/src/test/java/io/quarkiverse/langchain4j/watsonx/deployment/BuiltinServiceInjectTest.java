package io.quarkiverse.langchain4j.watsonx.deployment;

import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.API_KEY;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.PROJECT_ID;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_IAM_SERVER;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WATSONX_SERVER;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WX_AGENT_TOOL_RUN;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_WX_SERVER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import java.util.List;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.langchain4j.watsonx.bean.GoogleSearchResult;
import io.quarkiverse.langchain4j.watsonx.bean.WebCrawlerResult;
import io.quarkiverse.langchain4j.watsonx.services.GoogleSearchService;
import io.quarkiverse.langchain4j.watsonx.services.WeatherService;
import io.quarkiverse.langchain4j.watsonx.services.WebCrawlerService;
import io.quarkus.test.QuarkusUnitTest;

public class BuiltinServiceInjectTest extends WireMockAbstract {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.built-in-service.base-url", URL_WX_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.base-url", URL_WATSONX_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.iam.base-url", URL_IAM_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-id", PROJECT_ID)
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(WireMockUtil.class));

    @Override
    void handlerBeforeEach() {
        mockServers.mockIAMBuilder(200)
                .grantType(langchain4jWatsonConfig.defaultConfig().iam().grantType())
                .response(WireMockUtil.BEARER_TOKEN, new Date())
                .build();
    }

    @Inject
    WebCrawlerService webCrawlerTool;

    @Inject
    GoogleSearchService googleSearchTool;

    @Inject
    WeatherService weatherTool;

    @Test
    void testWebCrawler() throws Exception {

        String body = """
                {
                    "tool_name": "WebCrawler",
                    "input": {
                        "url": "https://www.ibm.com/us-en"
                    }
                }""";

        String response = "{" +
                "\"output\": \"\\\"{\\\\\\\"url\\\\\\\":\\\\\\\"https://www.ibm.com/us-en\\\\\\\"," +
                "\\\\\\\"contentType\\\\\\\":\\\\\\\"text/html;charset=utf-8\\\\\\\"," +
                "\\\\\\\"content\\\\\\\":\\\\\\\"IBM - United States\\\\n\\\\nBoost developer productivity with AI..." +
                "\\\\n\\\\nExplore jobs\\\\n\\\\nStart learning\\\\\\\"}\\\"\"\n" +
                "}";

        mockServers.mockWxBuilder(URL_WX_AGENT_TOOL_RUN, 200)
                .body(body)
                .response(response)
                .build();

        WebCrawlerResult result = webCrawlerTool.process("https://www.ibm.com/us-en");
        assertEquals("https://www.ibm.com/us-en", result.url());
        assertEquals("text/html;charset=utf-8", result.contentType());
        assertTrue(result.content().startsWith("IBM - United States"));
    }

    @Test
    void testWeather() throws Exception {

        String body = """
                {
                    "tool_name" : "Weather",
                    "input" : {
                        "name": "naples",
                        "country": "italy"
                    }
                }""";

        String response = """
                {
                    "output": "Current weather in Naples:\\nTemperature: 12.1°C\\nRain: 0mm\\nRelative humidity: 94%\\nWind: 5km/h\\n"
                }
                """;

        mockServers.mockWxBuilder(URL_WX_AGENT_TOOL_RUN, 200)
                .body(body)
                .response(response)
                .build();

        String result = weatherTool.find("naples", "italy");
        assertTrue(result.startsWith("Current weather in Naples:"));
    }

    @Test
    void testGoogleSearch() throws Exception {

        String body = """
                {
                  "tool_name": "GoogleSearch",
                  "input": "What was the weather in Toronto on January 13th 2025?",
                  "config": {
                    "maxResults": 10
                  }
                }""";

        String response = """
                {
                  "output": "[{\\"title\\":\\"Toronto, Ontario, Canada Monthly Weather | AccuWeather\\",\\"description\\":\\"January. January February March April May June July August September October November December. 2025 ... 13°. 29. 37°. 18°. 30. 34°. 16°. 31. 36°. 18°. 1. 18°. 11 ...\\",\\"url\\":\\"https://www.accuweather.com/en/ca/toronto/m5h/january-weather/55488\\"},
                  {\\"title\\":\\"Anthony Slater on X: \\\\\\"Draymond Green missed the Warriors ...\\\\\\"\\",\\"description\\":\\"Draymond Green missed the Warriors shootaround in Toronto this morning. Under the weather. He is questionable tonight with an illness. 4:45 PM · Jan 13, ...\\",\\"url\\":\\"https://x.com/anthonyVslater/status/1878845945854730255\\"},
                  {\\"title\\":\\"Canada weather forecast for Tuesday, 13 January 2026\\",\\"description\\":\\"Weather in Canada during the last few years on January 13 ; 2025 - January 13, 32 ° / 26 °, 0 in ; 2024 - January 13, 39 ° / 26 °, 0.46 in ; 2023 - January 13, 32 ...\\",\\"url\\":\\"https://www.weather25.com/north-america/canada?page=date&date=13-1\\"}]"
                }
                """;

        mockServers.mockWxBuilder(URL_WX_AGENT_TOOL_RUN, 200)
                .body(body)
                .response(response)
                .build();

        List<GoogleSearchResult> results = googleSearchTool.search("What was the weather in Toronto on January 13th 2025?");
        assertEquals(3, results.size());
    }

    @Test
    void testGoogleSearchWithCustomParameters() throws Exception {

        String body = """
                {
                  "tool_name": "GoogleSearch",
                  "input": "What was the weather in Toronto on January 13th 2025?",
                  "config": {
                    "maxResults": 3
                  }
                }""";

        String response = """
                {
                  "output": "[{\\"title\\":\\"Toronto, Ontario, Canada Monthly Weather | AccuWeather\\",\\"description\\":\\"January. January February March April May June July August September October November December. 2025 ... 13°. 29. 37°. 18°. 30. 34°. 16°. 31. 36°. 18°. 1. 18°. 11 ...\\",\\"url\\":\\"https://www.accuweather.com/en/ca/toronto/m5h/january-weather/55488\\"},
                  {\\"title\\":\\"Anthony Slater on X: \\\\\\"Draymond Green missed the Warriors ...\\\\\\"\\",\\"description\\":\\"Draymond Green missed the Warriors shootaround in Toronto this morning. Under the weather. He is questionable tonight with an illness. 4:45 PM · Jan 13, ...\\",\\"url\\":\\"https://x.com/anthonyVslater/status/1878845945854730255\\"},
                  {\\"title\\":\\"Canada weather forecast for Tuesday, 13 January 2026\\",\\"description\\":\\"Weather in Canada during the last few years on January 13 ; 2025 - January 13, 32 ° / 26 °, 0 in ; 2024 - January 13, 39 ° / 26 °, 0.46 in ; 2023 - January 13, 32 ...\\",\\"url\\":\\"https://www.weather25.com/north-america/canada?page=date&date=13-1\\"}]"
                }
                """;

        mockServers.mockWxBuilder(URL_WX_AGENT_TOOL_RUN, 200)
                .body(body)
                .response(response)
                .build();

        List<GoogleSearchResult> results = googleSearchTool.search("What was the weather in Toronto on January 13th 2025?",
                3);
        assertEquals(3, results.size());
    }
}
