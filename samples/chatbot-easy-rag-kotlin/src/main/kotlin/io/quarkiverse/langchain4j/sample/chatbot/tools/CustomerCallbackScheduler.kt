package io.quarkiverse.langchain4j.sample.chatbot.tools

import dev.langchain4j.agent.tool.P
import dev.langchain4j.agent.tool.Tool
import io.quarkus.logging.Log
import io.quarkus.mailer.Mail
import io.quarkus.mailer.Mailer
import io.smallrye.common.annotation.RunOnVirtualThread
import jakarta.enterprise.context.ApplicationScoped

private val PHONE_NUMBER_REGEX = Regex("^\\+[1-9]\\d{1,14}\$")

@Suppress("unused")
@ApplicationScoped
class CustomerCallbackScheduler(
    private val mailer: Mailer,
) {
    @Tool(
        "Schedules a callback call for a customer. Returns non-empty list of error messages on error",
        name = "scheduleCallback"
    )
    @RunOnVirtualThread
    fun scheduleCallback(
        @P("customer name", required = true)
        customerName: String,
        @P("valid phone number with country code", required = true)
        phoneNumber: String,
        @P("customer problem", required = true)
        problem: String,
        @P("convenient time to call back", required = true)
        dateAndTime: String,
    ): List<String> {
        val errors = mutableListOf<String>()
        if (!phoneNumber.matches(PHONE_NUMBER_REGEX)) {
            errors += "Invalid phone number. Valid phone number with country code is expected"
        }
        if (problem.isBlank()) {
            errors += "What's the Customer's question?"
        }
        if (dateAndTime.isBlank()) {
            errors += "Convenient time to call back is required"
        }
        if (customerName.isBlank()) {
            errors += "Customer name cannot be blank"
        }
        if (errors.isNotEmpty()) {
            return errors
        }
        try {
            mailer.send(
                Mail.withHtml(
                    "callback@horizonfinancial.example",
                    "Customer callback requested",
                    """
                    <html>
                        <body>
                            <h1>Callback Requested</h1>
                            <div>
                                <b>Customer:</b> $customerName<br>
                                <b>Phone Number:</b> $phoneNumber<br>
                                <b>Date and time:</b> $dateAndTime<br>
                                <b>Customer's problem:</b>
                                <pre>$problem</pre
                            </div>
                        </body>
                    </html>
                    """
                )
            )
            return emptyList()
        } catch (e: Exception) {
            Log.warn("Error while scheduling callback", e)
            return listOf("Technical error while scheduling callback")
        }
    }
}

