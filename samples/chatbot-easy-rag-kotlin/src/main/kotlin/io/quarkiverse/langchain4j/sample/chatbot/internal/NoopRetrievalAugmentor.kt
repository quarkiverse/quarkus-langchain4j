@file:Suppress("kotlin:S6516")
package io.quarkiverse.langchain4j.sample.chatbot.internal

import dev.langchain4j.data.message.TextContent
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.rag.AugmentationRequest
import dev.langchain4j.rag.AugmentationResult
import dev.langchain4j.rag.RetrievalAugmentor

internal object NoopRetrievalAugmentor : RetrievalAugmentor {

    override fun augment(augmentationRequest: AugmentationRequest): AugmentationResult {
        val chatMessage = augmentationRequest.chatMessage()
        if (chatMessage is UserMessage) {
            val content = chatMessage.contents()[0]
            if (content is TextContent) {
                return AugmentationResult(
                    chatMessage,
                    listOf<dev.langchain4j.rag.content.Content>()
                )
            }
        }
        return AugmentationResult(
            augmentationRequest.chatMessage(),
            listOf<dev.langchain4j.rag.content.Content>()
        )
    }

    class Supplier : java.util.function.Supplier<RetrievalAugmentor> {
        override fun get(): RetrievalAugmentor {
            return NoopRetrievalAugmentor
        }
    }
}
