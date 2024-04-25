package io.quarkiverse.langchain4j.watsonx.bean;

import org.jboss.resteasy.reactive.RestForm;

public record IdentityTokenRequest(
        @RestForm(value = "grant_type") String grantType,
        @RestForm(value = "apikey") String apikey) {
}
