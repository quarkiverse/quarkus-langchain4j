@file:Suppress("kotlin:S6516")

package io.quarkiverse.langchain4j.sample.chatbot.internal

import dev.langchain4j.memory.ChatMemory
import dev.langchain4j.memory.chat.ChatMemoryProvider
import io.quarkiverse.langchain4j.runtime.aiservice.NoopChatMemory

internal object NoopChatMemoryProvider : ChatMemoryProvider {
    val chatMemory = NoopChatMemory()

    override fun get(memoryId: Any): ChatMemory = chatMemory

    class Supplier : java.util.function.Supplier<ChatMemoryProvider> {
        override fun get(): ChatMemoryProvider {
            return NoopChatMemoryProvider
        }
    }
}
