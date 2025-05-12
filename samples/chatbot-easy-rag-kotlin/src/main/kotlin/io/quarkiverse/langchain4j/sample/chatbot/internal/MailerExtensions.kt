package io.quarkiverse.langchain4j.sample.chatbot.internal

import io.quarkus.mailer.Mail
import io.quarkus.mailer.Mailer
import kotlinx.coroutines.withContext

suspend fun Mailer.sendSuspending(
    mail: Mail,
    dispatcher: kotlinx.coroutines.CoroutineDispatcher,
) {
    val mailer = this
    return withContext(dispatcher) {
        mailer.send(mail)
    }
}
