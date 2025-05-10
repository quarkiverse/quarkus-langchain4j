package io.quarkiverse.langchain4j.sample.chatbot

import kotlinx.serialization.Serializable

@Serializable
data class Link(val url: String, val title: String)

typealias ChatMemoryId = String

@Serializable
data class Answer(
    val message: String,
    val links: List<Link> = emptyList()
)

val fallbackAnswer = Answer(
    message = "I'm sorry, I can't help you with that question. Try again later.",
    links = listOf(
        Link("https://www.example.com/support", "Contact support(example.com)")
    )
)

val greeting = Answer(
    message = "Hello, I'm Bob, how can I help you?",
    links = listOf(
        Link("https://www.example.com/help", "Help(example.com)")
    )
)
