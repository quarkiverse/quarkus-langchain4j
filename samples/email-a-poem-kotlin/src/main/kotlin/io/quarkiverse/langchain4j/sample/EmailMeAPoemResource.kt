package io.quarkiverse.langchain4j.sample

import jakarta.ws.rs.GET
import jakarta.ws.rs.Path

@Path("/email-me-a-poem")
class EmailMeAPoemResource(private val service: MyAiService) {
    @GET
    fun emailMeAPoem(): String {
        return service.writeAPoem("Quarkus", 4)
    }
}
