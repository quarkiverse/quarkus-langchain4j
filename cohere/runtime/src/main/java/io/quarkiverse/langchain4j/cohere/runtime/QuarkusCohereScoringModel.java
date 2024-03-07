package io.quarkiverse.langchain4j.cohere.runtime;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static java.util.Collections.singletonList;
import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.toList;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;

import org.eclipse.microprofile.rest.client.ext.ClientHeadersFactory;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.scoring.ScoringModel;
import io.quarkiverse.langchain4j.cohere.runtime.api.CohereApi;
import io.quarkiverse.langchain4j.cohere.runtime.api.RerankRequest;
import io.quarkiverse.langchain4j.cohere.runtime.api.RerankResponse;
import io.quarkiverse.langchain4j.cohere.runtime.api.RerankResult;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;

public class QuarkusCohereScoringModel implements ScoringModel {

    private final CohereApi cohereApi;
    private final String model;
    private final Integer maxRetries;

    public QuarkusCohereScoringModel(
            String baseUrl,
            String apiKey,
            String model,
            Duration timeout,
            Integer maxRetries) {
        this.model = model;
        this.maxRetries = maxRetries;
        ClientHeadersFactory factory = new ClientHeadersFactory() {
            @Override
            public MultivaluedMap<String, String> update(MultivaluedMap<String, String> incomingHeaders,
                    MultivaluedMap<String, String> clientOutgoingHeaders) {
                MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();
                headers.put("Authorization", singletonList("Bearer " + apiKey));
                return headers;
            }
        };
        try {
            cohereApi = QuarkusRestClientBuilder.newBuilder()
                    .baseUri(new URI(baseUrl))
                    .clientHeadersFactory(factory)
                    .connectTimeout(timeout.toSeconds(), TimeUnit.SECONDS)
                    .readTimeout(timeout.toSeconds(), TimeUnit.SECONDS)
                    .build(CohereApi.class);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Response<List<Double>> scoreAll(List<TextSegment> segments, String query) {
        List<String> documents = segments.stream()
                .map(TextSegment::text)
                .collect(toList());
        RerankRequest request = new RerankRequest(model, query, documents);
        RerankResponse response = withRetry(() -> cohereApi.rerank(request), maxRetries);
        List<Double> scores = response.getResults().stream()
                .sorted(comparingInt(RerankResult::getIndex))
                .map(RerankResult::getRelevanceScore)
                .collect(toList());
        return Response.from(scores,
                new TokenUsage(response.getMeta().getBilledUnits().getSearchUnits()));
    }
}
