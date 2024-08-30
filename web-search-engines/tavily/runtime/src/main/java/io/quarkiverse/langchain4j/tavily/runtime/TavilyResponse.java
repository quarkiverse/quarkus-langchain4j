package io.quarkiverse.langchain4j.tavily.runtime;

import java.util.List;

public class TavilyResponse {

    private final String answer;
    private final String query;
    private final Double responseTime;
    private final List<String> images;
    private final List<String> followUpQuestions;
    private final List<TavilySearchResult> results;

    public TavilyResponse(String answer,
            String query,
            Double responseTime,
            List<String> images,
            List<String> followUpQuestions,
            List<TavilySearchResult> results) {
        this.answer = answer;
        this.query = query;
        this.responseTime = responseTime;
        this.images = images;
        this.followUpQuestions = followUpQuestions;
        this.results = results;
    }

    public String getAnswer() {
        return answer;
    }

    public String getQuery() {
        return query;
    }

    public Double getResponseTime() {
        return responseTime;
    }

    public List<String> getImages() {
        return images;
    }

    public List<String> getFollowUpQuestions() {
        return followUpQuestions;
    }

    public List<TavilySearchResult> getResults() {
        return results;
    }
}
