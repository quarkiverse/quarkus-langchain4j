package io.quarkiverse.langchain4j.watsonx.deployment;

import org.jboss.jandex.DotName;

import com.ibm.watsonx.ai.textprocessing.textclassification.TextClassificationService;
import com.ibm.watsonx.ai.textprocessing.textextraction.TextExtractionService;
import com.ibm.watsonx.ai.tool.ToolService;
import com.ibm.watsonx.ai.tool.builtin.GoogleSearchTool;
import com.ibm.watsonx.ai.tool.builtin.PythonInterpreterTool;
import com.ibm.watsonx.ai.tool.builtin.RAGQueryTool;
import com.ibm.watsonx.ai.tool.builtin.TavilySearchTool;
import com.ibm.watsonx.ai.tool.builtin.WeatherTool;
import com.ibm.watsonx.ai.tool.builtin.WebCrawlerTool;
import com.ibm.watsonx.ai.tool.builtin.WikipediaTool;

public class WatsonxDotNames {
    public static final DotName WEB_CRAWLER_TOOL = DotName.createSimple(WebCrawlerTool.class);
    public static final DotName GOOGLE_SEARCH_TOOL = DotName.createSimple(GoogleSearchTool.class);
    public static final DotName WEATHER_TOOL = DotName.createSimple(WeatherTool.class);
    public static final DotName WIKIPEDIA_TOOL = DotName.createSimple(WikipediaTool.class);
    public static final DotName TAVILY_SEARCH_TOOL = DotName.createSimple(TavilySearchTool.class);
    public static final DotName PYTHON_INTERPRETER_TOOL = DotName.createSimple(PythonInterpreterTool.class);
    public static final DotName RAG_QUERY_TOOL = DotName.createSimple(RAGQueryTool.class);
    public static final DotName TEXT_EXTRACTION = DotName.createSimple(TextExtractionService.class);
    public static final DotName TEXT_CLASSIFICATION = DotName.createSimple(TextClassificationService.class);
    public static final DotName TOOL_SERVICE = DotName.createSimple(ToolService.class);
}
