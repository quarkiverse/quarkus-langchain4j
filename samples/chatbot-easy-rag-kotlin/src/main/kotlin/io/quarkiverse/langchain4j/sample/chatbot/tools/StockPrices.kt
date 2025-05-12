package io.quarkiverse.langchain4j.sample.chatbot.tools

import dev.langchain4j.agent.tool.Tool
import io.quarkus.logging.Log
import io.quarkus.qute.CacheSectionHelper
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class StockPrices() {

    data class StockPrice(val symbol: String, val price: Double)

    private val symbols = listOf("AAPL", "GOOG", "MSFT")

    @Tool("returns current stock prices")
    fun stockPrices(): List<StockPrice> {
        for (symbol in symbols) {
            Log.info("Getting stock price for $symbol")
        }
        return symbols.map { StockPrice(it, Math.random() * 100) }
    }
}
