package io.quarkiverse.langchain4j.cohere;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.scoring.ScoringModel;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.mockapi.CapturedRequest;
import io.quarkiverse.langchain4j.mockapi.MockModelServer;
import io.quarkiverse.langchain4j.mockapi.ScoringReply;
import io.quarkus.test.QuarkusExtensionTest;

public class CohereNamedModelTest {

    @RegisterExtension
    static final QuarkusExtensionTest test = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource(
                            new StringAsset("quarkus.langchain4j.cohere.c1.scoring-model.model-name=rerank-named\n"),
                            "application.properties"))
            .overrideConfigKey("quarkus.langchain4j.mock-api.enabled", "true")
            .overrideConfigKey("quarkus.langchain4j.c1.scoring-model.provider", "cohere");

    @Inject
    @ModelName("c1")
    ScoringModel scoringModel;

    @Inject
    MockModelServer mock;

    @Test
    void namedModelReachesTheMock() {
        mock.reset();
        mock.scoring().reply(ScoringReply.of(0.3, 0.7));

        assertThat(scoringModel.scoreAll(List.of(TextSegment.from("a"), TextSegment.from("b")), "q").content())
                .containsExactly(0.3, 0.7);

        CapturedRequest r = mock.scoring().singleRequest();
        assertThat(r.path()).isEqualTo("/cohere/v2/rerank");
        assertThat(r.bodyAsJson().at("/model").asText()).isEqualTo("rerank-named");
        assertThat(r.header("Authorization")).isEqualTo("Bearer mock-api-key");
    }
}
