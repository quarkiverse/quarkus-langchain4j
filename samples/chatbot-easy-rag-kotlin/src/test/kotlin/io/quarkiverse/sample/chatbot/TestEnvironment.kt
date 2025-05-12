package io.quarkiverse.sample.chatbot

import me.kpavlov.aimocks.openai.MockOpenai

object TestEnvironment {
    val mockOpenai = MockOpenai(verbose = true)
}
