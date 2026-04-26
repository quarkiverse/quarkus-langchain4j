package io.quarkiverse.langchain4j.watsonx.runtime;

import static io.quarkiverse.langchain4j.runtime.OptionalUtil.firstOrDefault;
import static io.quarkiverse.langchain4j.watsonx.runtime.AuthenticatorCache.getOrCreateTokenGenerator;
import static java.util.Objects.isNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.util.TypeLiteral;

import com.ibm.watsonx.ai.tool.ToolService;
import com.ibm.watsonx.ai.tool.builtin.GoogleSearchTool;
import com.ibm.watsonx.ai.tool.builtin.PythonInterpreterTool;
import com.ibm.watsonx.ai.tool.builtin.RAGQueryTool;
import com.ibm.watsonx.ai.tool.builtin.TavilySearchTool;
import com.ibm.watsonx.ai.tool.builtin.WeatherTool;
import com.ibm.watsonx.ai.tool.builtin.WebCrawlerTool;
import com.ibm.watsonx.ai.tool.builtin.WikipediaTool;

import io.quarkiverse.langchain4j.watsonx.runtime.client.QuarkusRestClientConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.config.BuiltinToolConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.config.IAMConfig;
import io.quarkiverse.langchain4j.watsonx.runtime.config.LangChain4jWatsonxConfig;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import io.smallrye.config.ConfigValidationException;

@Recorder
public class BuiltinToolRecorder {

    private static final ConfigValidationException.Problem[] EMPTY_PROBLEMS = new ConfigValidationException.Problem[0];
    private static final String MISSING_BUILTIN_SERVICE_PROPERTY_ERROR = "To use the built-in service classes, you must set the property 'quarkus.langchain4j.watsonx.built-in-tool.%s'";
    private static final String INVALID_BASE_URL_ERROR = "The property 'quarkus.langchain4j.watsonx.base-url' does not have a correct url. Use one of the urls given in the documentation or use the property 'quarkus.langchain4j.watsonx.built-in-service.base-url' to set a custom url.";

    private static final TypeLiteral<Instance<ToolService>> TOOL_SERVICE_TYPE_LITERAL = new TypeLiteral<>() {
    };

    private final RuntimeValue<LangChain4jWatsonxConfig> runtimeConfig;

    public BuiltinToolRecorder(RuntimeValue<LangChain4jWatsonxConfig> runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    public Supplier<ToolService> toolService() {

        IAMConfig iamConfig = runtimeConfig.getValue().defaultConfig().iam();
        BuiltinToolConfig builtinToolConfig = runtimeConfig.getValue().builtInTool();

        String apiKey = runtimeConfig.getValue().defaultConfig().apiKey().orElse(null);

        String baseUrl = firstOrDefault(
                getWxBaseUrl(runtimeConfig.getValue().defaultConfig().baseUrl()),
                builtinToolConfig.baseUrl());

        if (isNull(baseUrl))
            throw new RuntimeException(INVALID_BASE_URL_ERROR);

        var configProblems = checkConfigurations(baseUrl, apiKey);
        if (!configProblems.isEmpty()) {
            throw new ConfigValidationException(configProblems.toArray(EMPTY_PROBLEMS));
        }

        Duration timeout = firstOrDefault(
                runtimeConfig.getValue().defaultConfig().timeout().orElse(Duration.ofSeconds(10)),
                builtinToolConfig.timeout());

        boolean logRequests = firstOrDefault(
                runtimeConfig.getValue().defaultConfig().logRequests().orElse(false),
                builtinToolConfig.logRequests());

        boolean logResponses = firstOrDefault(
                runtimeConfig.getValue().defaultConfig().logResponses().orElse(false),
                builtinToolConfig.logResponses());

        boolean curlRequestLogger = firstOrDefault(
                runtimeConfig.getValue().defaultConfig().logRequestsCurl().orElse(false),
                builtinToolConfig.logRequestsCurl());

        return new Supplier<ToolService>() {
            @Override
            public ToolService get() {
                var authenticator = getOrCreateTokenGenerator(iamConfig.baseUrl().orElse(null), apiKey);
                QuarkusRestClientConfig.setLogCurl(curlRequestLogger);
                try {
                    return ToolService.builder()
                            .baseUrl(baseUrl)
                            .timeout(timeout)
                            .logRequests(logRequests)
                            .logResponses(logResponses)
                            .authenticator(authenticator)
                            .build();
                } finally {
                    QuarkusRestClientConfig.clear();
                }
            }
        };

    }

    public Function<SyntheticCreationalContext<WebCrawlerTool>, WebCrawlerTool> webCrawler() {
        return new Function<>() {
            @Override
            public WebCrawlerTool apply(SyntheticCreationalContext<WebCrawlerTool> context) {
                return new WebCrawlerTool(context.getInjectedReference(TOOL_SERVICE_TYPE_LITERAL).get());
            }
        };
    }

    public Function<SyntheticCreationalContext<GoogleSearchTool>, GoogleSearchTool> googleSearch() {
        return new Function<>() {
            @Override
            public GoogleSearchTool apply(SyntheticCreationalContext<GoogleSearchTool> context) {
                return new GoogleSearchTool(context.getInjectedReference(TOOL_SERVICE_TYPE_LITERAL).get());
            }
        };
    }

    public Function<SyntheticCreationalContext<WeatherTool>, WeatherTool> weather() {
        return new Function<>() {
            @Override
            public WeatherTool apply(SyntheticCreationalContext<WeatherTool> context) {
                return new WeatherTool(context.getInjectedReference(TOOL_SERVICE_TYPE_LITERAL).get());
            }
        };
    }

    public Function<SyntheticCreationalContext<WikipediaTool>, WikipediaTool> wikipedia() {
        return new Function<>() {
            @Override
            public WikipediaTool apply(SyntheticCreationalContext<WikipediaTool> context) {
                return new WikipediaTool(context.getInjectedReference(TOOL_SERVICE_TYPE_LITERAL).get());
            }
        };
    }

    public Function<SyntheticCreationalContext<TavilySearchTool>, TavilySearchTool> tavilySearch() {
        var apiKey = runtimeConfig.getValue().builtInTool().tavilySearch().apiKey()
                .orElseThrow(new Supplier<RuntimeException>() {
                    @Override
                    public RuntimeException get() {
                        return new RuntimeException(
                                "To use the built-in service TavilySearchTool class, you must set the property 'quarkus.langchain4j.watsonx.built-in-tool.tavily-search.api-key");
                    }
                });

        return new Function<SyntheticCreationalContext<TavilySearchTool>, TavilySearchTool>() {
            @Override
            public TavilySearchTool apply(SyntheticCreationalContext<TavilySearchTool> context) {
                return new TavilySearchTool(context.getInjectedReference(TOOL_SERVICE_TYPE_LITERAL).get(), apiKey);
            }
        };
    }

    public Function<SyntheticCreationalContext<PythonInterpreterTool>, PythonInterpreterTool> pythonInterpreter() {
        var deploymentId = runtimeConfig.getValue().builtInTool().pythonInterpreter().deploymentId()
                .orElseThrow(new Supplier<RuntimeException>() {
                    @Override
                    public RuntimeException get() {
                        return new RuntimeException(
                                "To use the built-in service PythonInterpreterTool class, you must set the property 'quarkus.langchain4j.watsonx.built-in-tool.python-interpreter.deployment-id");
                    }
                });

        return new Function<SyntheticCreationalContext<PythonInterpreterTool>, PythonInterpreterTool>() {
            @Override
            public PythonInterpreterTool apply(SyntheticCreationalContext<PythonInterpreterTool> context) {
                return new PythonInterpreterTool(context.getInjectedReference(TOOL_SERVICE_TYPE_LITERAL).get(), deploymentId);
            }
        };
    }

    public Function<SyntheticCreationalContext<RAGQueryTool>, RAGQueryTool> ragQuery() {
        var vectorIndexIds = runtimeConfig.getValue().builtInTool().ragQuery().vectorIndexIds()
                .orElseThrow(new Supplier<RuntimeException>() {
                    @Override
                    public RuntimeException get() {
                        return new RuntimeException(
                                "To use the built-in service RAGQueryTool class, you must set the property 'quarkus.langchain4j.watsonx.built-in-tool.rag-query.vector-index-ids");
                    }
                });
        var projectId = runtimeConfig.getValue().defaultConfig().projectId();
        var spaceId = runtimeConfig.getValue().defaultConfig().spaceId();

        return new Function<SyntheticCreationalContext<RAGQueryTool>, RAGQueryTool>() {
            @Override
            public RAGQueryTool apply(SyntheticCreationalContext<RAGQueryTool> context) {
                RAGQueryTool.Builder builder = RAGQueryTool.builder()
                        .toolService(context.getInjectedReference(TOOL_SERVICE_TYPE_LITERAL).get())
                        .vectorIndexIds(vectorIndexIds);

                if (projectId.isPresent())
                    builder.projectId(projectId.get());

                if (spaceId.isPresent())
                    builder.spaceId(spaceId.get());

                return builder.build();
            }
        };
    }

    private String getWxBaseUrl(Optional<String> baseUrl) {
        if (baseUrl.isEmpty())
            return null;
        return switch (baseUrl.get()) {
            case "https://us-south.ml.cloud.ibm.com" -> "https://api.dataplatform.cloud.ibm.com/wx";
            case "https://eu-de.ml.cloud.ibm.com" -> "https://api.eu-de.dataplatform.cloud.ibm.com/wx";
            case "https://eu-gb.ml.cloud.ibm.com" -> "https://api.eu-gb.dataplatform.cloud.ibm.com/wx";
            case "https://jp-tok.ml.cloud.ibm.com" -> "https://api.jp-tok.dataplatform.cloud.ibm.com/wx";
            case "https://au-syd.ml.cloud.ibm.com" -> "https://api.au-syd.dai.cloud.ibm.com/wx";
            case "https://ca-tor.ml.cloud.ibm.com" -> "https://api.ca-tor.dai.cloud.ibm.com/wx";
            case "https://ap-south-1.aws.wxai.ibm.com" -> "https://api.ap-south-1.aws.data.ibm.com/wx";
            default -> null;
        };
    }

    private List<ConfigValidationException.Problem> checkConfigurations(String baseUrl, String apiKey) {
        List<ConfigValidationException.Problem> configProblems = new ArrayList<>();
        if (isNull(baseUrl))
            configProblems
                    .add(new ConfigValidationException.Problem(MISSING_BUILTIN_SERVICE_PROPERTY_ERROR.formatted("base-url")));

        if (isNull(apiKey))
            configProblems
                    .add(new ConfigValidationException.Problem(MISSING_BUILTIN_SERVICE_PROPERTY_ERROR.formatted("api-key")));

        return configProblems;
    }
}
