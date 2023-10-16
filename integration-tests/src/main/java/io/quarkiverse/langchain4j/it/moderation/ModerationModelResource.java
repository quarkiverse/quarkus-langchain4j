package io.quarkiverse.langchain4j.it.moderation;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

import dev.langchain4j.model.moderation.ModerationModel;

@Path("moderation")
public class ModerationModelResource {

    private final ModerationModel moderationModel;

    public ModerationModelResource(ModerationModel moderationModel) {
        this.moderationModel = moderationModel;
    }

    @GET
    @Path("blocking")
    @Produces("text/plain")
    public Boolean blocking() {
        return moderationModel.moderate("This should not be flagged").content().flagged();
    }
}
