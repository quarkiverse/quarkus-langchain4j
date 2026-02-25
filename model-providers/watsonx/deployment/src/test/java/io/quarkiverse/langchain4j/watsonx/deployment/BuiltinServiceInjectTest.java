package io.quarkiverse.langchain4j.watsonx.deployment;

import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.API_KEY;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.BEARER_TOKEN;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.PROJECT_ID;
import static io.quarkiverse.langchain4j.watsonx.deployment.WireMockUtil.URL_IAM_SERVER;
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

import com.ibm.watsonx.ai.tool.builtin.GoogleSearchTool;
import com.ibm.watsonx.ai.tool.builtin.GoogleSearchTool.GoogleSearchResult;
import com.ibm.watsonx.ai.tool.builtin.PythonInterpreterTool;
import com.ibm.watsonx.ai.tool.builtin.RAGQueryTool;
import com.ibm.watsonx.ai.tool.builtin.TavilySearchTool;
import com.ibm.watsonx.ai.tool.builtin.TavilySearchTool.TavilySearchResult;
import com.ibm.watsonx.ai.tool.builtin.WeatherTool;
import com.ibm.watsonx.ai.tool.builtin.WebCrawlerTool;
import com.ibm.watsonx.ai.tool.builtin.WikipediaTool;

import io.quarkus.test.QuarkusUnitTest;

public class BuiltinServiceInjectTest extends WireMockAbstract {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.built-in-tool.base-url", URL_WX_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.iam.base-url", URL_IAM_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-id", PROJECT_ID)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.built-in-tool.tavily-search.api-key", "tavily-api-key")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.built-in-tool.python-interpreter.deployment-id",
                    "deployment-id")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.built-in-tool.rag-query.vector-index-ids",
                    "vector-index-1")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.built-in-tool.log-requests", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.built-in-tool.log-responses", "true")

            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(WireMockUtil.class));

    @Override
    void handlerBeforeEach() {
        mockIAMBuilder(200)
                .grantType(langchain4jWatsonConfig.defaultConfig().iam().grantType().orElse(null))
                .response(BEARER_TOKEN, new Date())
                .build();
    }

    @Inject
    WebCrawlerTool webCrawlerTool;

    @Inject
    GoogleSearchTool googleSearchTool;

    @Inject
    WeatherTool weatherTool;

    @Inject
    WikipediaTool wikipediaTool;

    @Inject
    TavilySearchTool tavilySearchTool;

    @Inject
    PythonInterpreterTool pythonInterpreterTool;

    @Inject
    RAGQueryTool ragQueryTool;

    @Test
    void check_config() throws Exception {
        assertEquals(true, langchain4jWatsonConfig.builtInTool().logRequests().orElse(false));
        assertEquals(true, langchain4jWatsonConfig.builtInTool().logResponses().orElse(false));
    }

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

        mockWxBuilder(URL_WX_AGENT_TOOL_RUN, 200)
                .body(body)
                .response(response)
                .build();

        String result = webCrawlerTool.process("https://www.ibm.com/us-en");
        assertTrue(result.startsWith("IBM - United States"));
    }

    @Test
    void testWeather() throws Exception {

        String body = """
                {
                    "tool_name" : "Weather",
                    "input" : {
                        "location": "naples",
                        "country": "italy"
                    }
                }""";

        String response = """
                {
                    "output": "Current weather in Naples:\\nTemperature: 12.1°C\\nRain: 0mm\\nRelative humidity: 94%\\nWind: 5km/h\\n"
                }
                """;

        mockWxBuilder(URL_WX_AGENT_TOOL_RUN, 200)
                .body(body)
                .response(response)
                .build();

        String result = weatherTool.find("naples", "italy");
        assertTrue(result.startsWith("Current weather in Naples:"));
    }

    @Test
    void testWikipedia() throws Exception {

        String body = """
                {
                    "tool_name" : "Wikipedia",
                    "input" : {
                        "query": "pc"
                    }
                }
                """;

        String response = """
                {
                  "output": "Page: PC\\nSummary: PC or pc may refer to:\\n\\n\\n== Arts and entertainment"
                }
                """;

        mockWxBuilder(URL_WX_AGENT_TOOL_RUN, 200)
                .body(body)
                .response(response)
                .build();

        String result = wikipediaTool.search("pc");
        assertEquals("Page: PC\nSummary: PC or pc may refer to:\n\n\n== Arts and entertainment", result);
    }

    @Test
    void testTavilySearch() throws Exception {

        String body = """
                {
                    "tool_name" : "TavilySearch",
                    "input" : {
                        "query": "test"
                    },
                    "config": {
                        "apiKey": "tavily-api-key",
                        "maxResults": 2
                    }
                }
                """;

        String response = """
                {
                    "output": "[{\\"url\\":\\"https://example.com/test1\\",\\"title\\":\\"Title 1\\",\\"content\\":\\"Test 1\\",\\"score\\":0.63,\\"raw_content\\":null},{\\"url\\":\\"https://example.com/test2\\",\\"title\\":\\"Title 2\\",\\"content\\":\\"Test 2\\",\\"score\\":0.55,\\"raw_content\\":null}]"
                }""";

        mockWxBuilder(URL_WX_AGENT_TOOL_RUN, 200)
                .body(body)
                .response(response)
                .build();

        List<TavilySearchResult> result = tavilySearchTool.search("test", 2);
        assertEquals(2, result.size());
        assertEquals("Title 1", result.get(0).title());
        assertEquals("Test 1", result.get(0).content());
        assertEquals(0.63, result.get(0).score());
        assertEquals("https://example.com/test1", result.get(0).url());
        assertEquals("Title 2", result.get(1).title());
        assertEquals("Test 2", result.get(1).content());
        assertEquals(0.55, result.get(1).score());
        assertEquals("https://example.com/test2", result.get(1).url());
    }

    @Test
    void testPythonInterpreter() throws Exception {

        String body = """
                {
                    "tool_name" : "PythonInterpreter",
                    "input" : {
                        "code": "print(\\"Hello World\\")"
                    },
                    "config": {
                        "deploymentId": "deployment-id"
                    }
                }
                """;

        String response = """
                {
                  "output": "Hello World!"
                }""";

        mockWxBuilder(URL_WX_AGENT_TOOL_RUN, 200)
                .body(body)
                .response(response)
                .build();

        var result = pythonInterpreterTool.run("print(\"Hello World\")");
        assertEquals(result, "Hello World!");
    }

    @Test
    void testGoogleSearch() throws Exception {

        String body = """
                {
                  "tool_name": "GoogleSearch",
                  "input": {
                    "q": "What was the weather in Toronto on January 13th 2025?"
                  },
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

        mockWxBuilder(URL_WX_AGENT_TOOL_RUN, 200)
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
                  "input": {
                    "q": "What was the weather in Toronto on January 13th 2025?"
                  },
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

        mockWxBuilder(URL_WX_AGENT_TOOL_RUN, 200)
                .body(body)
                .response(response)
                .build();

        List<GoogleSearchResult> results = googleSearchTool.search("What was the weather in Toronto on January 13th 2025?",
                3);
        assertEquals(3, results.size());
    }

    @Test
    void testRagQueryTool() throws Exception {

        String body = """
                {
                  "tool_name" : "RAGQuery",
                  "input" : "input",
                  "config" : {
                    "vectorIndexId" : "vector-index-1",
                    "projectId" : "%s"
                  }
                }""".formatted(PROJECT_ID);

        String response = """
                {
                  "output": "output"
                }
                """;

        mockWxBuilder(URL_WX_AGENT_TOOL_RUN, 200)
                .body(body)
                .response(response)
                .build();

        String result = ragQueryTool.query("input");
        assertEquals("output", result);
    }
}
