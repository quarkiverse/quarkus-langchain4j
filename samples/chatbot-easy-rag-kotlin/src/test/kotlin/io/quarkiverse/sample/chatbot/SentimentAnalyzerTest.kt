package io.quarkiverse.sample.chatbot

import io.kotest.matchers.shouldBe
import io.quarkiverse.langchain4j.sample.chatbot.sentiment.Sentiment
import io.quarkiverse.langchain4j.sample.chatbot.sentiment.SentimentAnalyzer
import io.quarkus.test.junit.QuarkusTest
import io.quarkus.test.junit.QuarkusTestProfile
import io.quarkus.test.junit.TestProfile
import jakarta.inject.Inject
import kotlinx.coroutines.test.runTest
import me.escoffier.loom.loomunit.LoomUnitExtension
import me.escoffier.loom.loomunit.ShouldNotPin
import me.kpavlov.aimocks.openai.MockOpenai
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

private val mockOpenai = MockOpenai(verbose = true)

@QuarkusTest
@TestProfile(SentimentAnalyzerTest.TestProfile::class)
@ExtendWith(LoomUnitExtension::class)
@ShouldNotPin
class SentimentAnalyzerTest{

    @Inject
    private lateinit var sentimentAnalyzer: SentimentAnalyzer

    class TestProfile : QuarkusTestProfile {
        override fun getConfigOverrides(): Map<String, String> {
            return mapOf(
                "quarkus.langchain4j.openai.base-url" to mockOpenai.baseUrl(),
                "quarkus.langchain4j.easy-rag.ingestion-strategy" to "OFF",
                "app.sentiment-analyzer.model-name" to "mega-cool-gpt-1000"
            )
        }
    }

    @ParameterizedTest
    @CsvSource(
        "POSITIVE,POSITIVE" ,
        "NEGATIVE,NEGATIVE" ,
        "NEUTRAL,NEUTRAL",
        "something wrong,NEUTRAL"
    )
    @Tag("LLM")
    @Tag("integration")
    @Tag("rest")
    fun `LLM should analyzeSentiment`(
        reply: String,
        expectedSentiment: Sentiment
    ) = runTest {
        mockOpenai.completion {
            model = "mega-cool-gpt-1000"
            systemMessageContains("Analyze sentiment of given user message.")
            userMessageContains("Answer with $reply")
        } responds {
            assistantContent (reply)
        }

        val result = sentimentAnalyzer.analyzeSentiment("Answer with $reply")
        result shouldBe expectedSentiment
    }
}

