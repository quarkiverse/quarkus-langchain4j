package io.quarkiverse.langchain4j.watsonx.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.TokenCountEstimator;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.ScoringModel;
import io.quarkiverse.langchain4j.watsonx.bean.EmbeddingRequest;
import io.quarkiverse.langchain4j.watsonx.bean.ScoringRequest;
import io.quarkiverse.langchain4j.watsonx.bean.TextGenerationParameters;
import io.quarkiverse.langchain4j.watsonx.bean.TextGenerationRequest;
import io.quarkiverse.langchain4j.watsonx.bean.TokenizationRequest;
import io.quarkus.test.QuarkusUnitTest;

public class GenerationDefaultPropertiesTest extends WireMockAbstract {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.base-url", WireMockUtil.URL_WATSONX_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.iam.base-url", WireMockUtil.URL_IAM_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", WireMockUtil.API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-id", WireMockUtil.PROJECT_ID)
            .overrideConfigKey("quarkus.langchain4j.watsonx.mode", "generation")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(WireMockUtil.class));

    @Override
    void handlerBeforeEach() {
        mockServers.mockIAMBuilder(200)
                .response("my_super_token", new Date())
                .build();
    }

    static TextGenerationParameters parameters = TextGenerationParameters.builder()
            .minNewTokens(0)
            .maxNewTokens(200)
            .decodingMethod("greedy")
            .temperature(1.0)
            .timeLimit(WireMockUtil.DEFAULT_TIME_LIMIT)
            .build();

    @Inject
    ChatLanguageModel chatModel;

    @Inject
    StreamingChatLanguageModel streamingChatModel;

    @Inject
    EmbeddingModel embeddingModel;

    @Inject
    ScoringModel scoringModel;

    @Inject
    TokenCountEstimator tokenCountEstimator;

    @Test
    void check_config() throws Exception {
        var runtimeConfig = langchain4jWatsonConfig.defaultConfig();
        assertEquals(Optional.empty(), runtimeConfig.timeout());
        assertEquals(Optional.empty(), runtimeConfig.iam().timeout());
        assertEquals(false, runtimeConfig.logRequests().orElse(false));
        assertEquals(false, runtimeConfig.logResponses().orElse(false));
        assertEquals(WireMockUtil.VERSION, runtimeConfig.version());
        assertEquals(WireMockUtil.DEFAULT_CHAT_MODEL, runtimeConfig.generationModel().modelId());
        assertEquals("greedy", runtimeConfig.generationModel().decodingMethod());
        assertEquals(null, runtimeConfig.generationModel().lengthPenalty().decayFactor().orElse(null));
        assertEquals(null, runtimeConfig.generationModel().lengthPenalty().startIndex().orElse(null));
        assertEquals(200, runtimeConfig.generationModel().maxNewTokens());
        assertEquals(0, runtimeConfig.generationModel().minNewTokens());
        assertEquals(null, runtimeConfig.generationModel().randomSeed().orElse(null));
        assertEquals(null, runtimeConfig.generationModel().stopSequences().orElse(null));
        assertEquals(1.0, runtimeConfig.generationModel().temperature());
        assertEquals("\n", runtimeConfig.generationModel().promptJoiner());
        assertTrue(runtimeConfig.generationModel().topK().isEmpty());
        assertTrue(runtimeConfig.generationModel().topP().isEmpty());
        assertTrue(runtimeConfig.generationModel().repetitionPenalty().isEmpty());
        assertTrue(runtimeConfig.generationModel().truncateInputTokens().isEmpty());
        assertTrue(runtimeConfig.generationModel().includeStopSequence().isEmpty());
        assertEquals("urn:ibm:params:oauth:grant-type:apikey", runtimeConfig.iam().grantType());
        assertEquals(WireMockUtil.DEFAULT_EMBEDDING_MODEL, runtimeConfig.embeddingModel().modelId());
        assertTrue(runtimeConfig.embeddingModel().truncateInputTokens().isEmpty());
        assertEquals(WireMockUtil.DEFAULT_SCORING_MODEL, runtimeConfig.scoringModel().modelId());
        assertTrue(runtimeConfig.scoringModel().truncateInputTokens().isEmpty());
    }

    @Test
    void check_chat_model_config() throws Exception {
        var config = langchain4jWatsonConfig.defaultConfig();
        String modelId = config.generationModel().modelId();
        String spaceId = config.spaceId().orElse(null);
        String projectId = config.projectId().orElse(null);

        TextGenerationRequest body = new TextGenerationRequest(modelId, spaceId, projectId, "SystemMessage\nUserMessage",
                parameters);

        mockServers.mockWatsonxBuilder(WireMockUtil.URL_WATSONX_GENERATION_API, 200)
                .body(mapper.writeValueAsString(body))
                .response(WireMockUtil.RESPONSE_WATSONX_GENERATION_API)
                .build();

        assertEquals("AI Response", chatModel.generate(dev.langchain4j.data.message.SystemMessage.from("SystemMessage"),
                dev.langchain4j.data.message.UserMessage.from("UserMessage")).content().text());
    }

    @Test
    void check_scoring_model() throws Exception {
        var config = langchain4jWatsonConfig.defaultConfig();
        String modelId = config.scoringModel().modelId();
        String spaceId = config.spaceId().orElse(null);
        String projectId = config.projectId().orElse(null);

        var segments = List.of(
                TextSegment.from(
                        "The novel 'Moby-Dick' was written by Herman Melville and first published in 1851. It is considered a masterpiece of American literature and deals with complex themes of obsession, revenge, and the conflict between good and evil.\""),
                TextSegment.from(
                        "Harper Lee, an American novelist widely known for her novel 'To Kill a Mockingbird', was born in 1926 in Monroeville, Alabama. She received the Pulitzer Prize for Fiction in 1961."),
                TextSegment.from(
                        "Jane Austen was an English novelist known primarily for her six major novels, which interpret, critique and comment upon the British landed gentry at the end of the 18th century."),
                TextSegment.from(
                        "The 'Harry Potter' series, which consists of seven fantasy novels written by British author J.K. Rowling, is among the most popular and critically acclaimed books of the modern era."),
                TextSegment.from(
                        "'The Great Gatsby', a novel written by American author F. Scott Fitzgerald, was published in 1925. The story is set in the Jazz Age and follows the life of millionaire Jay Gatsby and his pursuit of Daisy Buchanan."),
                TextSegment.from(
                        "'The Great Gatsby', a novel written by American author F. Scott Fitzgerald, was published in 1925. The story is set in the Jazz Age and follows the life of millionaire Jay Gatsby and his pursuit of Daisy Buchanan."),
                TextSegment.from(
                        "To Kill a Mockingbird' is a novel by Harper Lee published in 1960. It was immediately successful, winning the Pulitzer Prize, and has become a classic of modern American literature."));

        ScoringRequest request = ScoringRequest.of(modelId, spaceId, projectId, "Who wrote 'To Kill a Mockingbird'?",
                segments, null);

        mockServers.mockWatsonxBuilder(WireMockUtil.URL_WATSONX_SCORING_API, 200)
                .body(mapper.writeValueAsString(request))
                .response(WireMockUtil.RESPONSE_WATSONX_SCORING_API.formatted(modelId))
                .build();

        Response<List<Double>> response = scoringModel.scoreAll(segments, "Who wrote 'To Kill a Mockingbird'?");
        assertNotNull(response);
        assertEquals(6, response.content().size());
        assertEquals(318, response.tokenUsage().inputTokenCount());
        assertEquals(-2.5847978591918945, response.content().get(0));
        assertEquals(8.770895957946777, response.content().get(1));
        assertEquals(-4.939967155456543, response.content().get(2));
        assertEquals(-3.349348306655884, response.content().get(3));
        assertEquals(-3.920926570892334, response.content().get(4));
        assertEquals(9.720501899719238, response.content().get(5));
    }

    @Test
    void check_embedding_model() throws Exception {
        var config = langchain4jWatsonConfig.defaultConfig();
        String modelId = config.embeddingModel().modelId();
        String spaceId = config.spaceId().orElse(null);
        String projectId = config.projectId().orElse(null);

        EmbeddingRequest request = new EmbeddingRequest(modelId, spaceId, projectId,
                List.of("Embedding THIS!"), null);

        mockServers.mockWatsonxBuilder(WireMockUtil.URL_WATSONX_EMBEDDING_API, 200)
                .body(mapper.writeValueAsString(request))
                .response(WireMockUtil.RESPONSE_WATSONX_EMBEDDING_API.formatted(modelId))
                .build();

        Response<Embedding> response = embeddingModel.embed("Embedding THIS!");
        assertNotNull(response);
        assertNotNull(response.content());
    }

    @Test
    void check_token_count_estimator() throws Exception {
        var config = langchain4jWatsonConfig.defaultConfig();
        String modelId = config.generationModel().modelId();
        String spaceId = config.spaceId().orElse(null);
        String projectId = config.projectId().orElse(null);

        var body = new TokenizationRequest(modelId, "test", spaceId, projectId);

        mockServers.mockWatsonxBuilder(WireMockUtil.URL_WATSONX_TOKENIZER_API, 200)
                .body(mapper.writeValueAsString(body))
                .response(WireMockUtil.RESPONSE_WATSONX_TOKENIZER_API.formatted(modelId))
                .build();

        assertEquals(11, tokenCountEstimator.estimateTokenCount("test"));
    }

    @Test
    void check_chat_streaming_model_config() throws Exception {
        var config = langchain4jWatsonConfig.defaultConfig();
        String modelId = config.generationModel().modelId();
        String spaceId = config.spaceId().orElse(null);
        String projectId = config.projectId().orElse(null);

        TextGenerationRequest body = new TextGenerationRequest(modelId, spaceId, projectId, "SystemMessage\nUserMessage",
                parameters);

        mockServers.mockWatsonxBuilder(WireMockUtil.URL_WATSONX_GENERATION_STREAMING_API, 200)
                .body(mapper.writeValueAsString(body))
                .responseMediaType(MediaType.SERVER_SENT_EVENTS)
                .response(WireMockUtil.RESPONSE_WATSONX_GENERATION_STREAMING_API)
                .build();

        var messages = List.of(
                dev.langchain4j.data.message.SystemMessage.from("SystemMessage"),
                dev.langchain4j.data.message.UserMessage.from("UserMessage"));

        var streamingResponse = new AtomicReference<AiMessage>();
        streamingChatModel.generate(messages, WireMockUtil.streamingResponseHandler(streamingResponse));

        await().atMost(Duration.ofMinutes(1))
                .pollInterval(Duration.ofSeconds(2))
                .until(() -> streamingResponse.get() != null);

        assertThat(streamingResponse.get().text())
                .isNotNull()
                .isEqualTo(". I'm a beginner");
    }
}
