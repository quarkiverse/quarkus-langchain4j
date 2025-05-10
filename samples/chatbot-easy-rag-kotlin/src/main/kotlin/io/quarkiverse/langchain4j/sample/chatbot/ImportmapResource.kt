package io.quarkiverse.langchain4j.sample.chatbot

import io.mvnpm.importmap.Aggregator
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces

/**
 * Dynamically create the import map
 */
@ApplicationScoped
@Path("/_importmap")
class ImportmapResource {

    private val importmap = Aggregator(
        mapOf(
            "icons/" to "/icons/",
            "components/" to "/components/",
            "fonts/" to "/fonts/"
        )
    ).aggregateAsJson()

    // See https://github.com/WICG/import-maps/issues/235
    // This does not seem to be supported by browsers yet...
    @GET
    @Path("/dynamic.importmap")
    @Produces("application/importmap+json")
    fun importMap(): String = this.importmap

    @GET
    @Path("/dynamic-importmap.js")
    @Produces("application/javascript")
    fun importMapJson(): String =
        // language=javascript
        """
            const im = document.createElement('script');
            im.type = 'importmap';
            im.textContent = JSON.stringify($importmap);
            document.currentScript.after(im);

            """.trimIndent()
}
