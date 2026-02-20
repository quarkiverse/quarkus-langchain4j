package io.quarkiverse.langchain4j.watsonx.deployment.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.ibm.watsonx.ai.tool.builtin.GoogleSearchTool;
import com.ibm.watsonx.ai.tool.builtin.GoogleSearchTool.GoogleSearchResult;
import com.ibm.watsonx.ai.tool.builtin.PythonInterpreterTool;
import com.ibm.watsonx.ai.tool.builtin.TavilySearchTool;
import com.ibm.watsonx.ai.tool.builtin.TavilySearchTool.TavilySearchResult;
import com.ibm.watsonx.ai.tool.builtin.WeatherTool;
import com.ibm.watsonx.ai.tool.builtin.WebCrawlerTool;
import com.ibm.watsonx.ai.tool.builtin.WikipediaTool;

import io.quarkus.test.QuarkusUnitTest;

@EnabledIfEnvironmentVariable(named = "WATSONX_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "WATSONX_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "TAVILY_SEARCH_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "PYTHON_INTERPRETER_DEPLOYMENT_ID", matches = ".+")
public class BultinToolITTest {

    static final String API_KEY = System.getenv("WATSONX_API_KEY");
    static final String URL = System.getenv("WATSONX_URL");
    static final String TAVILY_SEARCH_API_KEY = System.getenv("TAVILY_SEARCH_API_KEY");
    static final String PYTHON_INTERPRETER_DEPLOYMENT_ID = System.getenv("PYTHON_INTERPRETER_DEPLOYMENT_ID");

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.base-url", URL)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.timeout", "30s")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.built-in-tool.tavily-search.api-key", TAVILY_SEARCH_API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.built-in-tool.python-interpreter.deployment-id",
                    PYTHON_INTERPRETER_DEPLOYMENT_ID)

            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class));

    @Inject
    GoogleSearchTool googleSearchTool;

    @Inject
    WikipediaTool wikipediaTool;

    @Inject
    TavilySearchTool tavilySearchTool;

    @Inject
    WebCrawlerTool webCrawlerTool;

    @Inject
    WeatherTool weatherTool;

    @Inject
    PythonInterpreterTool pythonInterpreterTool;

    @Test
    void test_google_search_tool() {
        List<GoogleSearchResult> results = googleSearchTool.search("watsonx.ai java sdk", 1);
        assertNotNull(results);
        assertTrue(results.size() == 1);
        assertNotNull(results.get(0).url());
        assertNotNull(results.get(0).description());
        assertNotNull(results.get(0).title());
    }

    @Test
    void test_wikipedia_tool() {
        String result = wikipediaTool.search("watsonx.ai");
        assertNotNull(result);
        assertFalse(result.isBlank());
    }

    @Test
    void test_weather_tool() {
        String result = weatherTool.find("Rome");
        assertNotNull(result);
        assertFalse(result.isBlank());
    }

    @Test
    void test_web_crawler_tool() {
        String result = webCrawlerTool.process("https://github.com/IBM/watsonx-ai-java-sdk");
        assertNotNull(result);
    }

    @Test
    void test_tavily_search_tool() {
        List<TavilySearchResult> results = tavilySearchTool.search("watsonx.ai java sdk", 1);
        assertNotNull(results);
        assertTrue(results.size() == 1);
        assertNotNull(results.get(0).url());
        assertNotNull(results.get(0).title());
        assertNotNull(results.get(0).score());
    }

    @Test
    void test_python_interpreter_tool() {
        String result = pythonInterpreterTool.run("print(\"Hello World!\")");
        assertNotNull(result);
        assertEquals("Hello World!", result);
    }
}
