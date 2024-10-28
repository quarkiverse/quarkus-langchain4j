package io.quarkiverse.langchain4j.watsonx.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Duration;
import java.util.Date;
import java.util.List;
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
import io.quarkiverse.langchain4j.watsonx.bean.EmbeddingParameters;
import io.quarkiverse.langchain4j.watsonx.bean.EmbeddingRequest;
import io.quarkiverse.langchain4j.watsonx.bean.ScoringParameters;
import io.quarkiverse.langchain4j.watsonx.bean.ScoringRequest;
import io.quarkiverse.langchain4j.watsonx.bean.TextGenerationParameters;
import io.quarkiverse.langchain4j.watsonx.bean.TextGenerationParameters.LengthPenalty;
import io.quarkiverse.langchain4j.watsonx.bean.TextGenerationRequest;
import io.quarkiverse.langchain4j.watsonx.bean.TokenizationRequest;
import io.quarkus.test.QuarkusUnitTest;

public class GenerationAllPropertiesTest extends WireMockAbstract {

    @RegisterExtension
    static QuarkusUnitTest unitTest = new QuarkusUnitTest()
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.base-url", WireMockUtil.URL_WATSONX_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.api-key", WireMockUtil.API_KEY)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.space-id", "my-space-id")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.project-id", WireMockUtil.PROJECT_ID)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.timeout", "60s")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.log-requests", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.log-responses", "true")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.version", "aaaa-mm-dd")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.iam.base-url", WireMockUtil.URL_IAM_SERVER)
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.iam.timeout", "60s")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.iam.grant-type", "grantME")
            .overrideConfigKey("quarkus.langchain4j.watsonx.mode", "generation")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.generation-model.model-id", "my_super_model")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.generation-model.prompt-joiner", "@")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.generation-model.decoding-method", "greedy")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.generation-model.length-penalty.decay-factor", "1.1")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.generation-model.length-penalty.start-index", "0")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.generation-model.max-new-tokens", "200")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.generation-model.min-new-tokens", "10")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.generation-model.random-seed", "2")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.generation-model.stop-sequences", "\n,\n\n")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.generation-model.temperature", "1.5")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.generation-model.top-k", "90")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.generation-model.top-p", "0.5")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.generation-model.repetition-penalty", "2.0")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.generation-model.truncate-input-tokens", "0")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.generation-model.include-stop-sequence", "false")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.embedding-model.model-id", "my_super_embedding_model")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.embedding-model.truncate-input-tokens", "10")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.scoring-model.model-id", "my_super_scoring_model")
            .overrideRuntimeConfigKey("quarkus.langchain4j.watsonx.scoring-model.truncate-input-tokens", "10")
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class).addClass(WireMockUtil.class));

    @Override
    void handlerBeforeEach() {
        mockServers.mockIAMBuilder(200)
                .grantType(langchain4jWatsonConfig.defaultConfig().iam().grantType())
                .response(WireMockUtil.BEARER_TOKEN, new Date())
                .build();
    }

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

    static TextGenerationParameters parameters = TextGenerationParameters.builder()
            .minNewTokens(10)
            .maxNewTokens(200)
            .decodingMethod("greedy")
            .lengthPenalty(new LengthPenalty(1.1, 0))
            .randomSeed(2)
            .stopSequences(List.of("\n", "\n\n"))
            .temperature(1.5)
            .timeLimit(60000L)
            .topK(90)
            .topP(0.5)
            .repetitionPenalty(2.0)
            .truncateInputTokens(0)
            .includeStopSequence(false)
            .build();

    static EmbeddingParameters embeddingParameters = new EmbeddingParameters(10);

    static ScoringParameters scoringParameters = new ScoringParameters(10);

    @Test
    void check_config() throws Exception {
        var runtimeConfig = langchain4jWatsonConfig.defaultConfig();
        assertEquals(WireMockUtil.URL_WATSONX_SERVER, runtimeConfig.baseUrl().toString());
        assertEquals(WireMockUtil.URL_IAM_SERVER, runtimeConfig.iam().baseUrl().toString());
        assertEquals(WireMockUtil.API_KEY, runtimeConfig.apiKey());
        assertEquals("my-space-id", runtimeConfig.spaceId().orElse(null));
        assertEquals(WireMockUtil.PROJECT_ID, runtimeConfig.projectId().orElse(null));
        assertEquals(Duration.ofSeconds(60), runtimeConfig.timeout().get());
        assertEquals(Duration.ofSeconds(60), runtimeConfig.iam().timeout().get());
        assertEquals("grantME", runtimeConfig.iam().grantType());
        assertEquals(true, runtimeConfig.logRequests().orElse(false));
        assertEquals(true, runtimeConfig.logResponses().orElse(false));
        assertEquals("aaaa-mm-dd", runtimeConfig.version());
        assertEquals("my_super_model", runtimeConfig.generationModel().modelId());
        assertEquals("greedy", runtimeConfig.generationModel().decodingMethod());
        assertEquals(1.1, runtimeConfig.generationModel().lengthPenalty().decayFactor().get());
        assertEquals(0, runtimeConfig.generationModel().lengthPenalty().startIndex().get());
        assertEquals(200, runtimeConfig.generationModel().maxNewTokens());
        assertEquals(10, runtimeConfig.generationModel().minNewTokens());
        assertEquals(2, runtimeConfig.generationModel().randomSeed().get());
        assertEquals(List.of("\n", "\n\n"), runtimeConfig.generationModel().stopSequences().get());
        assertEquals(1.5, runtimeConfig.generationModel().temperature());
        assertEquals(90, runtimeConfig.generationModel().topK().get());
        assertEquals(0.5, runtimeConfig.generationModel().topP().get());
        assertEquals(2.0, runtimeConfig.generationModel().repetitionPenalty().get());
        assertEquals(0, runtimeConfig.generationModel().truncateInputTokens().get());
        assertEquals(false, runtimeConfig.generationModel().includeStopSequence().get());
        assertEquals("@", runtimeConfig.generationModel().promptJoiner());
        assertEquals("my_super_embedding_model", runtimeConfig.embeddingModel().modelId());
        assertEquals(10, runtimeConfig.embeddingModel().truncateInputTokens().orElse(null));
        assertEquals("my_super_scoring_model", runtimeConfig.scoringModel().modelId());
        assertEquals(10, runtimeConfig.scoringModel().truncateInputTokens().orElse(null));
    }

    @Test
    void check_chat_model_config() throws Exception {
        var config = langchain4jWatsonConfig.defaultConfig();
        String modelId = config.generationModel().modelId();
        String spaceId = config.spaceId().orElse(null);
        String projectId = config.projectId().orElse(null);

        TextGenerationRequest body = new TextGenerationRequest(modelId, spaceId, projectId, "SystemMessage@UserMessage",
                parameters);
        mockServers.mockWatsonxBuilder(WireMockUtil.URL_WATSONX_GENERATION_API, 200, "aaaa-mm-dd")
                .body(mapper.writeValueAsString(body))
                .response(WireMockUtil.RESPONSE_WATSONX_GENERATION_API)
                .build();

        assertEquals("AI Response", chatModel.generate(dev.langchain4j.data.message.SystemMessage.from("SystemMessage"),
                dev.langchain4j.data.message.UserMessage.from("UserMessage")).content().text());
    }

    @Test
    void check_embedding_model() throws Exception {
        var config = langchain4jWatsonConfig.defaultConfig();
        String modelId = config.embeddingModel().modelId();
        String spaceId = config.spaceId().orElse(null);
        String projectId = config.projectId().orElse(null);
        EmbeddingRequest request = new EmbeddingRequest(modelId, spaceId, projectId,
                List.of("Embedding THIS!"), embeddingParameters);

        mockServers.mockWatsonxBuilder(WireMockUtil.URL_WATSONX_EMBEDDING_API, 200, "aaaa-mm-dd")
                .body(mapper.writeValueAsString(request))
                .response(WireMockUtil.RESPONSE_WATSONX_EMBEDDING_API.formatted(modelId))
                .build();

        Response<Embedding> response = embeddingModel.embed("Embedding THIS!");
        assertNotNull(response);
        assertNotNull(response.content());
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
                segments, scoringParameters);

        mockServers.mockWatsonxBuilder(WireMockUtil.URL_WATSONX_SCORING_API, 200, "aaaa-mm-dd")
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
    void check_token_count_estimator() throws Exception {
        var config = langchain4jWatsonConfig.defaultConfig();
        String modelId = config.generationModel().modelId();
        String spaceId = config.spaceId().orElse(null);
        String projectId = config.projectId().orElse(null);
        var body = new TokenizationRequest(modelId, "test", spaceId, projectId);

        mockServers.mockWatsonxBuilder(WireMockUtil.URL_WATSONX_TOKENIZER_API, 200, "aaaa-mm-dd")
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
        TextGenerationRequest body = new TextGenerationRequest(modelId, spaceId, projectId, "SystemMessage@UserMessage",
                parameters);

        mockServers.mockWatsonxBuilder(WireMockUtil.URL_WATSONX_GENERATION_STREAMING_API, 200, "aaaa-mm-dd")
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
