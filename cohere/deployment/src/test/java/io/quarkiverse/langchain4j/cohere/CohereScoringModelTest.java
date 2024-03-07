package io.quarkiverse.langchain4j.cohere;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Percentage.withPercentage;

import java.util.List;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.ScoringModel;
import io.quarkus.test.QuarkusUnitTest;

@EnabledIfEnvironmentVariable(named = "COHERE_API_KEY", matches = ".+")
public class CohereScoringModelTest {

    @RegisterExtension
    static final QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(new StringAsset(
                            // to allow request/response logging (disabled because it exposes
                            // the api key...)
                            //                            "quarkus.rest-client.logging.scope=request-response\n" +
                            //                                    "quarkus.rest-client.logging.body-limit=10000\n" +
                            //                                    "quarkus.log.category.\"org.jboss.resteasy.reactive.client.logging\".level=DEBUG\n" +
                            "quarkus.langchain4j.cohere.api-key=${cohere.api.key}\n" +
                                    "quarkus.langchain4j.cohere.scoring-model.model-name=rerank-english-v2.0"),
                            "application.properties"));

    @Inject
    ScoringModel model;

    @Test
    void should_score_single_text() {
        // given
        String text = "labrador retriever";
        String query = "tell me about dogs";

        // when
        Response<Double> response = model.score(text, query);

        // then
        assertThat(response.content()).isCloseTo(0.034, withPercentage(1));

        assertThat(response.tokenUsage().totalTokenCount()).isEqualTo(1);

        assertThat(response.finishReason()).isNull();
    }

    @Test
    void should_score_multiple_segments_with_all_parameters() {
        // given
        TextSegment catSegment = TextSegment.from("maine coon");
        TextSegment dogSegment = TextSegment.from("labrador retriever");
        List<TextSegment> segments = asList(catSegment, dogSegment);

        String query = "tell me about dogs";

        // when
        Response<List<Double>> response = model.scoreAll(segments, query);

        // then
        List<Double> scores = response.content();
        assertThat(scores).hasSize(2);
        assertThat(scores.get(0)).isLessThan(scores.get(1));

        assertThat(response.tokenUsage().totalTokenCount()).isEqualTo(1);

        assertThat(response.finishReason()).isNull();
    }
}
