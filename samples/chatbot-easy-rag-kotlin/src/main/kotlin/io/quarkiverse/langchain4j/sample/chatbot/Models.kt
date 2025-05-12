package io.quarkiverse.langchain4j.sample.chatbot

import dev.langchain4j.model.output.structured.Description
import kotlinx.serialization.Serializable

typealias ChatMemoryId = String
typealias Question = String

@Serializable
data class Link(
    @Description("Link URL")
    val url: String,
    @Description("Link Title")
    val title: String)

@Serializable
data class Answer(
    @Description("Answer message")
    val message: String,
    @Description("Reference links, if relevant, or empty list")
    val links: List<Link> = emptyList()
)

val fallbackAnswer = Answer(
    message = "I'm sorry, I can't help you with that question. Try again later.",
    links = listOf(
        Link("https://www.example.com/support", "Contact support(example.com)")
    )
)

val greeting = Answer(
    message = """
        Hello, I'm your AI assistant and I can make mistakes.
        How can I help you today?
        """,
    links = listOf(
        Link("https://www.example.com/help", "Help(example.com)")
    )
)
