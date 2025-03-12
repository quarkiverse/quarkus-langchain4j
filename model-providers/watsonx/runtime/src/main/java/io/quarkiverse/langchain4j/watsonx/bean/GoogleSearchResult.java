package io.quarkiverse.langchain4j.watsonx.bean;

import dev.langchain4j.Experimental;

@Experimental
public record GoogleSearchResult(String title, String description, String url) {
}