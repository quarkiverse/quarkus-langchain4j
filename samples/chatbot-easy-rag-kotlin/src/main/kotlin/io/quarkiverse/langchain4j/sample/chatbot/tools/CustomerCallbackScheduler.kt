package io.quarkiverse.langchain4j.sample.chatbot.tools

import dev.langchain4j.agent.tool.P
import dev.langchain4j.agent.tool.Tool
import io.quarkus.logging.Log
import io.quarkus.mailer.Mail
import io.quarkus.mailer.Mailer
import io.smallrye.common.annotation.RunOnVirtualThread
import jakarta.enterprise.context.ApplicationScoped

private val PHONE_NUMBER_REGEX = Regex("^\\+[1-9]\\d{1,14}\$")

@ApplicationScoped
class CustomerCallbackScheduler(
    private val mailer: Mailer,
) {
    @Tool(
        """
            Schedules a callback call for a customer
            when all the necessary information is provided:
            customer name, phone number, customer problem, convenient time to call back.

            Returns true if the scheduling was successful, false otherwise
            """,
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
    ): Boolean =
        try {
            require(phoneNumber.matches(PHONE_NUMBER_REGEX)) {
                "Invalid phone number"
            }
            require(problem.isNotBlank()) { "Customer problem cannot be blank" }
            require(customerName.isNotBlank()) { "Customer name cannot be blank" }
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
            true
        } catch (e: Exception) {
            Log.warn("Error while scheduling callback", e)
            false
        }

}

