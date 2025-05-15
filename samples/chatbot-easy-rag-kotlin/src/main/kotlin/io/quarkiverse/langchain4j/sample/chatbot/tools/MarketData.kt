package io.quarkiverse.langchain4j.sample.chatbot.tools

import dev.langchain4j.agent.tool.P
import dev.langchain4j.agent.tool.Tool
import jakarta.enterprise.context.ApplicationScoped
import org.slf4j.LoggerFactory
import java.math.BigDecimal

typealias Symbol = String

private val logger = LoggerFactory.getLogger(MarketData::class.java)

@Suppress("unused", "MagicNumber")
@ApplicationScoped
class MarketData {

    data class StockPrice(val symbol: Symbol, val price: BigDecimal)

    private val stockPrices: Map<Symbol, StockPrice>

    private val symbols = listOf(
        "AAPL",
        "AMZN",
        "GOOG",
        "MSFT",
    )

    init {
        stockPrices =
            symbols.associateWith { symbol ->
                StockPrice(
                    symbol,
                    // Generate a random price between 50 and 300
                    BigDecimal((50_00..300_00).random()).movePointLeft(2)
                )
            }
    }

    @Tool("returns supported stock symbols", name = "stockSymbols")
    fun stockSymbols(): List<Symbol> = symbols

    @Tool("returns current stock prices", name = "stockPrices")
    fun stockPrices(
        @P("list of stock symbols to query or empty list to query all symbols")
        symbols: List<Symbol> = emptyList()
    ): List<StockPrice> = if (symbols.isEmpty()) {
        logger.debug("Received stock prices request for all symbols")
        stockPrices.values.toList()
    } else {
        logger.debug("Received stock prices request for symbols: {}", symbols)
        symbols
            .map { it.uppercase() }
            .mapNotNull { stockPrices[it] }
            .toList()
    }
}
