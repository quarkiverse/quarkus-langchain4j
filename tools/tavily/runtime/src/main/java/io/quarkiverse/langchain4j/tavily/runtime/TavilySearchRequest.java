package io.quarkiverse.langchain4j.tavily.runtime;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TavilySearchRequest {

    @JsonProperty("api_key")
    private final String apiKey;

    private final String query;

    @JsonProperty("search_depth")
    private final String searchDepth;

    @JsonProperty("include_answer")
    private final Boolean includeAnswer;

    @JsonProperty("include_raw_content")
    private final Boolean includeRawContent;

    @JsonProperty("max_results")
    private final Integer maxResults;

    @JsonProperty("include_domains")
    private final List<String> includeDomains;

    @JsonProperty("exclude_domains")
    private final List<String> excludeDomains;

    public TavilySearchRequest(String apiKey, String query,
            String searchDepth, Boolean includeAnswer,
            Boolean includeRawContent, Integer maxResults,
            List<String> includeDomains, List<String> excludeDomains) {
        this.apiKey = apiKey;
        this.query = query;
        this.searchDepth = searchDepth;
        this.includeAnswer = includeAnswer;
        this.includeRawContent = includeRawContent;
        this.maxResults = maxResults;
        this.includeDomains = includeDomains;
        this.excludeDomains = excludeDomains;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getQuery() {
        return query;
    }

    public String getSearchDepth() {
        return searchDepth;
    }

    public Boolean getIncludeAnswer() {
        return includeAnswer;
    }

    public Boolean getIncludeRawContent() {
        return includeRawContent;
    }

    public Integer getMaxResults() {
        return maxResults;
    }

    public List<String> getIncludeDomains() {
        return includeDomains;
    }

    public List<String> getExcludeDomains() {
        return excludeDomains;
    }
}
