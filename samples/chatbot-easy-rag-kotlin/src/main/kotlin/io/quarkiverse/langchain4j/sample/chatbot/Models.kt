package io.quarkiverse.langchain4j.sample.chatbot

import kotlinx.serialization.Serializable

@Serializable
data class Link(val url: String, val title: String)

@Serializable
data class Answer(
    val message: String,
    val links: List<Link> = emptyList()
)
