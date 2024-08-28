package io.quarkiverse.langchain4j.tavily.runtime;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TavilySearchResult {

    private final String title;
    private final String url;
    private final String content;

    @JsonProperty("raw_content")
    private final String rawContent;
    private final Double score;

    public TavilySearchResult(String title, String url,
            String content, String rawContent,
            Double score) {
        this.title = title;
        this.url = url;
        this.content = content;
        this.rawContent = rawContent;
        this.score = score;
    }

    public String getTitle() {
        return title;
    }

    public String getUrl() {
        return url;
    }

    public String getContent() {
        return content;
    }

    public String getRawContent() {
        return rawContent;
    }

    public Double getScore() {
        return score;
    }
}
