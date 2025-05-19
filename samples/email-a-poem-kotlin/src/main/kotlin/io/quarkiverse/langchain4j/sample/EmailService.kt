package io.quarkiverse.langchain4j.sample

import dev.langchain4j.agent.tool.Tool
import io.quarkus.logging.Log
import io.quarkus.mailer.Mail
import io.quarkus.mailer.Mailer
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject

@ApplicationScoped
class EmailService(
    val mailer: Mailer
) {
    @Tool("send the given content by email")
    fun sendAnEmail(content: String) {
        Log.info("Sending an email: $content")
        mailer.send(Mail.withText("sendMeALetter@quarkus.io", "A poem for you", content))
    }
}
